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
import mindustry.input.DesktopInput;
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
 * Пересоздание курсоров происходит на смену настроек (слайдер/диалог/импорт пака); единственное
 * исключение - тинт-режимы {@code gradient}/{@code rainbow} ({@link TintMode}), которые throttled
 * (раз в {@link #REGEN_INTERVAL} кадров, см. {@link #updateAnimated()}) перестраивают только свои
 * слоты из кадрового цикла. Без анимированных слотов работы в кадровом цикле по-прежнему нет.
 * Итоговый размер клампится в {@link #MAX_CURSOR_SIZE}: Windows не принимает аппаратные курсоры
 * больше ~256px.
 */
public final class CursorCustomizer{
    public static final String scaleKey = "sonka-cursor-scale";
    /** ОС-лимит стороны курсора (Windows режет всё больше 256px) - итог вписывается с сохранением пропорций. */
    public static final int MAX_CURSOR_SIZE = 256;
    /** Лимит стороны исходной PNG: защита от случайной фотографии на мегабайты (курсор всё равно ужмётся до 256). */
    public static final int MAX_SOURCE_SIZE = 1024;
    public static final int MIN_PERCENT = 20, MAX_PERCENT = 200;
    /** Тайминги анимированного тинта - те же, что были у прежней отдельной cursor-rainbow фичи. */
    static final float RAINBOW_SPEED = 1.2f; // градусов в тик
    static final float GRADIENT_SWING = 40f; // масштаб sine по времени -> полный A->B->A свинг ~4с
    /** Порог яркости (0-255, среднее RGB) отсечения обводки от заливки в {@link #tintPixmap}.
     * У ванильных спрайтов курсоров чёткая бимодальность: тёмный контур ~60-120, светлая заливка
     * ~180-240, провал между - проверено гистограммой по всем 7 PNG. */
    static final int OUTLINE_LUMA_THRESHOLD = 150;
    static final int REGEN_INTERVAL = 6; // кадров между перестройками анимированных слотов

    /** Режим тинта слота: {@code flat} - обычный статический цвет ({@link #tint}), {@code gradient} -
     * шиммер между {@link #tint} и {@link #tint2}, {@code rainbow} - HSV-цикл (игнорирует оба цвета). */
    public enum TintMode{flat, gradient, rainbow}
    /** Закэшированный {@code TintMode.values()}: сам метод клонирует массив при каждом вызове, а
     * {@link #tintMode} дёргается из {@link #anyAnimated()} на КАЖДЫЙ кадр по КАЖДОМУ слоту (даже
     * когда анимированных слотов вообще нет - это единственный способ узнать, что их нет) - без
     * кэша это лишняя аллокация массива по 7 раз за кадр в самом частом случае. */
    private static final TintMode[] TINT_MODES = TintMode.values();

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

    /** Второй цвет градиента (первый - обычный {@link #tintKey}). */
    public static String tint2Key(Slot s){
        return "sonka-cursor-tint2-" + s.name;
    }

    /** Режим тинта слота ({@link TintMode#ordinal()}); ключа нет = flat. */
    public static String tintModeKey(Slot s){
        return "sonka-cursor-tintmode-" + s.name;
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

    /** Второй цвет градиента или null, если не задан. */
    public static @Nullable Color tint2(Slot s){
        if(!Core.settings.has(tint2Key(s))) return null;
        return new Color().set(Core.settings.getInt(tint2Key(s), 0xffffffff));
    }

    public static TintMode tintMode(Slot s){
        int m = Core.settings.getInt(tintModeKey(s), TintMode.flat.ordinal());
        return m >= 0 && m < TINT_MODES.length ? TINT_MODES[m] : TintMode.flat;
    }

    public static boolean isAnimated(Slot s){
        TintMode m = tintMode(s);
        return m == TintMode.gradient || m == TintMode.rainbow;
    }

    static boolean anyAnimated(){
        for(Slot s : slots) if(isAnimated(s)) return true;
        return false;
    }

    /**
     * Тинт, фактически применяемый к пиксмапе прямо сейчас: для {@code flat} - обычный
     * {@link #tint}, для {@code gradient}/{@code rainbow} - цвет текущего кадра анимации.
     * В отличие от {@link #tint}, всегда не-null для gradient/rainbow (белый по умолчанию, если
     * цвета ещё не выбраны).
     */
    public static Color resolvedTint(Slot s){
        TintMode m = tintMode(s);
        if(m == TintMode.rainbow){
            Color out = new Color();
            out.fromHsv((Time.time * RAINBOW_SPEED) % 360f, 1f, 1f);
            return out;
        }
        if(m == TintMode.gradient){
            Color a = tint(s), b = tint2(s);
            Color out = a != null ? new Color(a) : new Color(Color.white);
            out.lerp(b != null ? b : Color.white, 0.5f + 0.5f * Mathf.sin(Time.time, GRADIENT_SWING, 1f));
            return out;
        }
        Color flat = tint(s);
        return flat != null ? flat : Color.white;
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

    /** Исходник с уже нанесённым тинтом текущего кадра (без масштаба). Владение - у вызывающего. */
    public static Pixmap composed(Slot s){
        Pixmap p = basePixmap(s);
        if(tintMode(s) != TintMode.flat || tint(s) != null) tintPixmap(p, resolvedTint(s));
        return p;
    }

    /**
     * Помножить каждый пиксель на цвет (обычный мультипликативный тинт; альфа тинта тоже
     * учитывается) - КРОМЕ тёмной обводки/тени исходника, которую оставляем как есть.
     * Заливка ванильных курсоров белая - без этого исключения тинт красит её ровно в цвет тинта
     * без разбавления, и при совпадении с фоном игры (rainbow неизбежно проходит все оттенки)
     * курсор полностью теряет контраст - обводка была единственным, что вообще может отличаться
     * от фона, а её красило точно так же. Раз обводка не красится, она остаётся фиксированным
     * тёмным контуром вокруг заливки - силуэт виден, даже когда сама заливка сливается с фоном.
     */
    public static void tintPixmap(Pixmap pix, Color tint){
        float tr = tint.r, tg = tint.g, tb = tint.b, ta = tint.a;
        for(int y = 0; y < pix.height; y++){
            for(int x = 0; x < pix.width; x++){
                int c = pix.getRaw(x, y);
                int r0 = (c >>> 24) & 0xff, g0 = (c >>> 16) & 0xff, b0 = (c >>> 8) & 0xff;
                if((r0 + g0 + b0) / 3 < OUTLINE_LUMA_THRESHOLD) continue;
                int r = (int)(r0 * tr);
                int g = (int)(g0 * tg);
                int b = (int)(b0 * tb);
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

    static int animFrame;

    /**
     * Пересоздать ВСЕ курсоры по текущим настройкам. Зовётся только на смену настроек (слайдер,
     * диалог, импорт пака, сохранение из редактора) - никогда в кадровом цикле. Ошибка по одному
     * слоту (битый PNG и т.п.) не трогает остальные: слот откатывается к чистой ванили.
     */
    public static void rebuild(){
        if(Vars.headless || Vars.mobile || Vars.ui == null) return;
        rebuildSlots(slots, true);
    }

    /**
     * Throttled-перестройка ТОЛЬКО анимированных слотов (gradient/rainbow) - единственное место,
     * которое трогает курсоры из кадрового цикла ({@link mindustry.core.UI#update()}), и то не
     * чаще раза в {@link #REGEN_INTERVAL} кадров, и только если хоть один слот анимирован
     * (иначе выходит немедленно - обычный, полностью статичный случай стоит одну проверку).
     */
    public static void updateAnimated(){
        if(Vars.headless || Vars.mobile || Vars.ui == null || !anyAnimated()) return;
        if(animFrame++ % REGEN_INTERVAL != 0) return;
        Seq<Slot> animated = new Seq<>();
        for(Slot s : slots) if(isAnimated(s)) animated.add(s);
        rebuildSlots(animated, false);
    }

    /**
     * Общее тело пересборки для {@link #rebuild()} и {@link #updateAnimated()}.
     * @param forcePush форсировать сброс OS-курсора на новый экземпляр стрелки. Нужно {@code true}
     * на явную смену настройки ({@link #rebuild()}) - иначе смена цвета из диалога никогда не
     * пробьётся в кэш identity {@code Graphics.lastCursor} и курсор так и останется
     * старым/дефолтным. Throttled-анимация ({@link #updateAnimated()}) передаёт {@code false} -
     * там пуш и так решается по {@link #worldWantsArrow()} (см. ниже).
     */
    static void rebuildSlots(Seq<Slot> targets, boolean forcePush){
        //решаем ДО цикла - нужно и чтобы пропустить пересборку стрелки, если она в этот раз не
        //покажется (см. ниже), и как условие финального пуша.
        //ВАЖНО: наведение мыши на UI сюда не входит. SystemCursor.arrow - это ОБЁРТКА
        //(arc.Graphics.cursor(): для SystemCursor дереференсит в .cursor и ставит именно его) -
        //DesktopInput при переходе мир->UI пушит именно её (см. cursorType/changedCursor), и она
        //по-прежнему указывает на НАШ тинт. Стрелка реально пропадает с экрана только когда
        //DesktopInput вместо неё каждый кадр пушит контекстный курсор мира (drill/target/repair/
        //hand над блоком) - это и есть единственный случай, когда worldWantsArrow() лжёт false.
        //Гейтить ещё и по hasMouseOverUi() было ошибкой (баг b13 "цвет не меняется") - стрелка
        //визуально остаётся активной поверх UI почти всегда, а мы переставали её обновлять.
        boolean pushArrow = forcePush || worldWantsArrow();
        Cursor arrowCursor = null;
        for(Slot s : targets){
            //throttled-тик стрелки, пока в мире прямо сейчас нужен контекстный курсор - не просто
            //бесполезен, а ОПАСЕН: Graphics.cursor кэширует lastCursor по identity, и OS может
            //ПРЯМО СЕЙЧАС указывать на s.created снизу (старый курсор с прошлого успешного пуша).
            //dispose() ниже освобождает нативный хэндл - если следом НЕ вызвать Core.graphics.
            //cursor() с заменой (а без pushArrow мы его не вызовем), OS остаётся с висячей ссылкой
            //на освобождённый курсор - экранный курсор гаснет насовсем (баг "курсор не видно").
            //Но раз worldWantsArrow()==false означает, что DesktopInput каждый кадр и так пушит
            //контекстный курсор поверх стрелки - она и так не отображается, трогать её слот незачем.
            if(s.system == SystemCursor.arrow && !pushArrow) continue;
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
                //сперва подставляем новый, потом освобождаем старый (наш же прошлый s.created) -
                //обратный порядок (dispose, затем set) на миг оставляет слот без курсора вовсе
                Cursor old = s.created;
                s.system.set(cur);
                if(old != null) old.dispose();
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
        if(arrowCursor != null && pushArrow) Core.graphics.cursor(arrowCursor);
    }

    /**
     * В игровом мире (не над UI) прямо сейчас должна показываться именно стрелка, а не
     * контекстный курсор (drill/target/repair/unload/hand при наведении на руду/юнита/блок) -
     * {@link DesktopInput#cursorType}. Пока активного {@code DesktopInput} нет (нет игры/другой
     * обработчик ввода) считаем, что стрелка активна - это соответствует прежнему поведению.
     */
    static boolean worldWantsArrow(){
        return !(Vars.control != null && Vars.control.input instanceof DesktopInput di) || di.cursorType == SystemCursor.arrow;
    }
}
