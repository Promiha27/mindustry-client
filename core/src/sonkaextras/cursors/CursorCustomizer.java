package sonkaextras.cursors;

import arc.*;
import arc.Graphics.*;
import arc.Graphics.Cursor.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.Vars;
import mindustry.ui.Fonts;

/**
 * Кастомизация курсоров мыши (идея sonka, движковая фича): масштаб в процентах, замена текстур
 * своими PNG, покраска тинтом, пер-курсорный хотспот. Этот класс - ядро: модель слотов и
 * пересоздание OS-курсоров; UI живёт в {@link CursorsDialog}/{@link CursorEditorDialog},
 * паки - в {@link CursorPackIO}.
 * <p>
 * Как курсоры устроены в движке (итог исследования, чтобы не переоткрывать):
 * <ul>
 * <li>Спрайты - {@code core/assets/cursors/*.png}, все 64x64 RGBA. Хотспот у ванили всегда центр
 *     пиксмапы ({@code Graphics.newCursor(String, int)} передаёт width/2, height/2).</li>
 * <li>{@code arc.Graphics.newCursor(Pixmap, hx, hy)} - абстрактный бэкенд-метод; на десктопе
 *     (SDL) это SDL_CreateColorCursor, и он САМ dispose'ит переданную пиксмапу.</li>
 * <li>Системные слоты {@code SystemCursor.arrow/hand/ibeam} подменяются через {@code .set(Cursor)}
 *     ({@code Fonts.loadSystemCursors()} делает это на старте, ДО загрузки настроек);
 *     {@code SystemCursor.dispose()} освобождает подложенный кастомный курсор и обнуляет ссылку.</li>
 * <li>Контекстные курсоры ({@code ui.drillCursor/unloadCursor/targetCursor/repairCursor})
 *     создаёт {@code UI.loadSync()} - к этому моменту настройки уже загружены, поэтому наша
 *     точка входа {@link #load()} стоит именно там.</li>
 * <li>{@code Graphics.cursor(Cursor)} кэширует {@code lastCursor} по identity - после подмены
 *     реализации системного слота ОС-курсор сам не обновится; {@link #rebuild()} сбивает кэш,
 *     напрямую ставя новосозданный объект курсора-стрелки.</li>
 * <li>Ванильный {@code Fonts.cursorScale()} - захардкоженная единица (TODO апстрима, UI у него
 *     нет); наш процентный масштаб его заменяет. Единственное оставшееся использование -
 *     стартовый курсор в {@code Fonts.loadSystemCursors()}, когда настройки ещё не прочитаны.</li>
 * </ul>
 * Пересоздание курсоров происходит ТОЛЬКО на смену настроек (слайдер/диалог/импорт пака) - в
 * кадровом цикле никакой работы нет. Итоговый размер клампится в {@link #MAX_CURSOR_SIZE}:
 * Windows не принимает аппаратные курсоры больше ~256px.
 */
public final class CursorCustomizer{
    public static final String scaleKey = "sonka-cursor-scale";
    /** ОС-лимит стороны курсора (Windows режет всё больше 256px) - итог вписывается с сохранением пропорций. */
    public static final int MAX_CURSOR_SIZE = 256;
    /** Лимит стороны исходной PNG: защита от случайной фотографии на мегабайты (курсор всё равно ужмётся до 256). */
    public static final int MAX_SOURCE_SIZE = 1024;
    public static final int MIN_PERCENT = 20, MAX_PERCENT = 200;

    /** Один курсор игры: имя (ключи настроек/файлов/бандла), встроенный спрайт и способ установки. */
    public static final class Slot{
        public final String name;
        /** Имя встроенного спрайта: cursors/<sprite>.png в ассетах. */
        public final String sprite;
        /** Системный слот arc (arrow/hand/ibeam) или null для курсоров-полей UI. */
        public final @Nullable SystemCursor system;
        final @Nullable Cons<Cursor> uiSet;
        final @Nullable Prov<Cursor> uiGet;
        /** Последний созданный нами OS-курсор (для сбива identity-кэша lastCursor). */
        @Nullable Cursor created;

        Slot(String name, String sprite, SystemCursor system, Cons<Cursor> uiSet, Prov<Cursor> uiGet){
            this.name = name;
            this.sprite = sprite;
            this.system = system;
            this.uiSet = uiSet;
            this.uiGet = uiGet;
        }
    }

    /** Все курсоры игры. Порядок = порядок в диалоге. */
    public static final Seq<Slot> slots = Seq.with(
        new Slot("arrow", "cursor", SystemCursor.arrow, null, null),
        new Slot("hand", "hand", SystemCursor.hand, null, null),
        new Slot("ibeam", "ibeam", SystemCursor.ibeam, null, null),
        new Slot("drill", "drill", null, c -> Vars.ui.drillCursor = c, () -> Vars.ui.drillCursor),
        new Slot("unload", "unload", null, c -> Vars.ui.unloadCursor = c, () -> Vars.ui.unloadCursor),
        new Slot("target", "target", null, c -> Vars.ui.targetCursor = c, () -> Vars.ui.targetCursor),
        new Slot("repair", "repair", null, c -> Vars.ui.repairCursor = c, () -> Vars.ui.repairCursor)
    );

    private CursorCustomizer(){
    }

    public static String tintKey(Slot s){
        return "sonka-cursor-tint-" + s.name;
    }

    /** Хотспот в координатах ИСХОДНОЙ пиксмапы (Point2.pack); ключа нет = центр, как у ванили. */
    public static String hotspotKey(Slot s){
        return "sonka-cursor-hotspot-" + s.name;
    }

    /** Папка кастомных текстур: <data>/cursors/<слот>.png. Наличие файла = замена активна. */
    public static Fi cursorsDir(){
        return (Vars.dataDirectory != null ? Vars.dataDirectory : Core.settings.getDataDirectory()).child("cursors");
    }

    public static Fi customFile(Slot s){
        return cursorsDir().child(s.name + ".png");
    }

    public static int scalePercent(){
        return Mathf.clamp(Core.settings.getInt(scaleKey, 100), MIN_PERCENT, MAX_PERCENT);
    }

    /** Тинт слота или null, если не задан (ключа нет = некрашеный, белый ключ - тоже валидный "тинт"). */
    public static @Nullable Color tint(Slot s){
        if(!Core.settings.has(tintKey(s))) return null;
        return new Color().set(Core.settings.getInt(tintKey(s), 0xffffffff));
    }

    /**
     * Исходная пиксмапа слота: кастомный PNG, если он есть и валиден, иначе встроенный спрайт.
     * Битый/слишком большой кастомный файл не роняет игру - логируется и игнорируется.
     * Владение пиксмапой - у вызывающего.
     */
    public static Pixmap basePixmap(Slot s){
        Fi f = customFile(s);
        if(f.exists()){
            try{
                Pixmap p = new Pixmap(f);
                if(p.width > 0 && p.height > 0 && p.width <= MAX_SOURCE_SIZE && p.height <= MAX_SOURCE_SIZE) return p;
                Log.warn("[sonka-cursors] custom cursor '@' has bad size @x@ (max @), ignored", s.name, p.width, p.height, MAX_SOURCE_SIZE);
                p.dispose();
            }catch(Throwable t){
                Log.err("[sonka-cursors] failed to read custom cursor '" + s.name + "', ignored", t);
            }
        }
        return new Pixmap(Core.files.internal("cursors/" + s.sprite + ".png"));
    }

    /** Исходник с уже нанесённым тинтом (без масштаба). Владение - у вызывающего. */
    public static Pixmap composed(Slot s){
        Pixmap p = basePixmap(s);
        Color t = tint(s);
        if(t != null) tintPixmap(p, t);
        return p;
    }

    /** Помножить каждый пиксель на цвет (обычный мультипликативный тинт; альфа тинта тоже учитывается). */
    public static void tintPixmap(Pixmap pix, Color tint){
        float tr = tint.r, tg = tint.g, tb = tint.b, ta = tint.a;
        for(int y = 0; y < pix.height; y++){
            for(int x = 0; x < pix.width; x++){
                int c = pix.getRaw(x, y);
                int r = (int)(((c >>> 24) & 0xff) * tr);
                int g = (int)(((c >>> 16) & 0xff) * tg);
                int b = (int)(((c >>> 8) & 0xff) * tb);
                int a = (int)((c & 0xff) * ta);
                pix.setRaw(x, y, (r << 24) | (g << 16) | (b << 8) | a);
            }
        }
    }

    /** Итоговый размер стороны после масштаба и ОС-клампа (для подписей/превью). */
    public static int effectiveSize(int baseSize){
        int v = Math.max(1, Math.round(baseSize * scalePercent() / 100f));
        return Math.min(v, MAX_CURSOR_SIZE);
    }

    /** Собрать OS-курсор слота: исходник -> тинт -> масштаб (nearest, пиксель-арт не мылим) -> хотспот. */
    static Cursor create(Slot s){
        Pixmap pix = composed(s);
        int baseW = pix.width, baseH = pix.height;
        int percent = scalePercent();
        int w = Math.max(1, Math.round(baseW * percent / 100f));
        int h = Math.max(1, Math.round(baseH * percent / 100f));
        if(Math.max(w, h) > MAX_CURSOR_SIZE){
            float f = MAX_CURSOR_SIZE / (float)Math.max(w, h);
            w = Math.max(1, (int)(w * f));
            h = Math.max(1, (int)(h * f));
        }
        if(w != baseW || h != baseH){
            Pixmap scaled = Pixmaps.scale(pix, w, h, false);
            pix.dispose();
            pix = scaled;
        }
        //ваниль ставит хотспот в центр; кастомный задаётся в координатах исходника и масштабируется
        int hx = w / 2, hy = h / 2;
        int hs = Core.settings.getInt(hotspotKey(s), -1);
        if(hs != -1){
            hx = Mathf.clamp(Math.round(Point2.x(hs) * (w / (float)baseW)), 0, w - 1);
            hy = Mathf.clamp(Math.round(Point2.y(hs) * (h / (float)baseH)), 0, h - 1);
        }
        return Core.graphics.newCursor(pix, hx, hy); //бэкенд сам dispose'ит пиксмапу
    }

    /**
     * Точка входа из {@code UI.loadSync()} (настройки уже загружены). На мобиле кастомизация не
     * применяется - там курсора не видно, воспроизводим ваниль один в один и выходим.
     */
    public static void load(){
        if(Vars.headless || Vars.ui == null) return;
        if(Vars.mobile){
            Vars.ui.drillCursor = Core.graphics.newCursor("drill", Fonts.cursorScale());
            Vars.ui.unloadCursor = Core.graphics.newCursor("unload", Fonts.cursorScale());
            Vars.ui.targetCursor = Core.graphics.newCursor("target", Fonts.cursorScale());
            Vars.ui.repairCursor = Core.graphics.newCursor("repair", Fonts.cursorScale());
            return;
        }
        rebuild();
    }

    /**
     * Пересоздать ВСЕ курсоры по текущим настройкам. Зовётся только на смену настроек (слайдер,
     * диалог, импорт пака, сохранение из редактора) - никогда в кадровом цикле. Ошибка по одному
     * слоту (битый PNG и т.п.) не трогает остальные: слот откатывается к чистой ванили.
     */
    public static void rebuild(){
        if(Vars.headless || Vars.mobile || Vars.ui == null) return;
        Cursor arrowCursor = null;
        for(Slot s : slots){
            Cursor cur;
            try{
                cur = create(s);
            }catch(Throwable t){
                Log.err("[sonka-cursors] failed to build cursor '" + s.name + "'", t);
                try{
                    cur = Core.graphics.newCursor(s.sprite);
                }catch(Throwable t2){
                    Log.err("[sonka-cursors] fallback failed for '" + s.name + "', slot left as is", t2);
                    continue;
                }
            }
            if(s.system != null){
                s.system.dispose(); //освобождает предыдущий подложенный курсор (или ничего на первом заходе)
                s.system.set(cur);
            }else{
                Cursor old = s.uiGet.get();
                s.uiSet.get(cur);
                if(old != null && !(old instanceof SystemCursor)) old.dispose();
            }
            s.created = cur;
            if(s.system == SystemCursor.arrow) arrowCursor = cur;
        }
        //Graphics.cursor кэширует lastCursor по identity - подмена реализации SystemCursor.arrow
        //сама по себе ОС-курсор не обновит. Прямая установка нового объекта сбивает кэш; со
        //следующего кадра DesktopInput/сцена ставят контекстный курсор как обычно.
        if(arrowCursor != null) Core.graphics.cursor(arrowCursor);
    }
}
