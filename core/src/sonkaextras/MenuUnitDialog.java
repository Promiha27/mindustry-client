package sonkaextras;

import arc.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustrytool.features.background.BackgroundFeature;

import static mindustry.Vars.*;

/**
 * Выбор юнита, летающего в фоне главного меню. Настройка {@link #settingKey} хранит имя типа
 * ({@code UnitType.name}); пустая/отсутствующая = ваниль-поведение (случайный юнит). Саму подмену
 * делает {@link MenuRenderer#updateCursedness()}, так что выбор переживает и клавишу H
 * (перегенерация фона), и смену cursedness-уровня, и пересоздание рендера.
 * <p>
 * В сетке показаны ВСЕ юниты с найденным спрайтом ({@code region.found()}) - ровно тот фильтр,
 * которым сам MenuRenderer отбирает кандидатов на высоких cursedness-уровнях (наземные там тоже
 * летают, это осознанная фича клиента), летающие - первыми.
 * <p>
 * Взаимодействие с mindustrytool "Background" ({@link BackgroundFeature}): та фича через Reflect
 * подменяет {@code ui.menufrag.renderer} на свою обёртку ({@code CustomMenuRenderer} для
 * статичной картинки или {@code GifMenuRenderer} для gif - обе реализуют {@code WrapsMenuRenderer}),
 * которая рисует картинку/гифку, а при opacity < 100% сперва рисует ОРИГИНАЛЬНЫЙ MenuRenderer под
 * ней - в этом случае
 * выбранный юнит остаётся виден сквозь полупрозрачную картинку, и мгновенное применение здесь
 * добирается до оригинального рендера внутри обёртки. При opacity = 100% картинка полностью
 * закрывает ваниль-фон - выбор юнита просто не виден (и не мешает), пока Background не выключат.
 */
public class MenuUnitDialog extends BaseDialog{
    /** Настройка: имя типа юнита для фона меню; пусто = случайный (ваниль). */
    public static final String settingKey = "menu-unit";

    public MenuUnitDialog(){
        super("@client.menuunit.title");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
    }

    private void setup(){
        cont.clear();

        cont.button("@client.menuunit.random", Icon.refresh, Styles.togglet, () -> {
            Core.settings.remove(settingKey);
            applyToMenu();
        }).update(b -> b.setChecked(Core.settings.getString(settingKey, "").isEmpty()))
            .growX().height(50f).pad(4f).row();

        // все юниты, которые рендер реально может нарисовать; летающие - первыми
        Seq<UnitType> units = content.units().select(u -> u.region != null && u.region.found());
        units.sort(Structs.comps(Structs.comparingBool(u -> !u.flying), Structs.comparingInt(u -> u.id)));

        int cols = Mathf.clamp((int)(Core.graphics.getWidth() / Scl.scl(64f)) - 4, 4, 14);

        cont.pane(p -> {
            int i = 0;
            for(UnitType unit : units){
                ImageButton b = p.button(Tex.whiteui, Styles.clearTogglei, 40, () -> {
                    Core.settings.put(settingKey, unit.name);
                    applyToMenu();
                }).size(56f).tooltip(unit.localizedName).get();
                b.getStyle().imageUp = new TextureRegionDrawable(unit.uiIcon);
                b.update(() -> b.setChecked(unit.name.equals(Core.settings.getString(settingKey, ""))));
                if(++i % cols == 0) p.row();
            }
        }).grow().pad(4f);
    }

    /**
     * Мгновенно применяет выбор к уже построенному фону меню. Если mindustrytool Background
     * активен, {@code renderer} - это его обёртка: юнит меняем в оригинальном рендере внутри неё
     * (см. javadoc класса), саму картинку не трогаем.
     */
    public static void applyToMenu(){
        if(ui == null || ui.menufrag == null) return;
        MenuRenderer renderer = ui.menufrag.renderer;
        if(renderer instanceof BackgroundFeature.WrapsMenuRenderer wrapped) renderer = wrapped.originalRenderer();
        if(renderer == null) return;

        UnitType chosen = content.unit(Core.settings.getString(settingKey, ""));
        if(chosen != null && chosen.region != null && chosen.region.found()){
            renderer.flyerType = chosen; // без updateCursedness(): не перекатываем количество летунов
        }else{
            renderer.updateCursedness(); // «случайный»: заново катим ваниль-рандом
        }
    }
}
