package sonkaextras.cursors;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import sonkaextras.cursors.CursorCustomizer.*;

import static mindustry.Vars.ui;

/**
 * Мини-диалог тинта одного слота курсора (кнопка «Цвет...» в {@link CursorsDialog}): режим -
 * обычный статический цвет, двухцветный градиент-шиммер или радужный HSV-цикл (те же три режима,
 * что у build-beam фичи QoL Suite, только применённые к курсору вместо луча). Правки применяются
 * сразу - flat через {@link CursorCustomizer#rebuild()}, gradient/rainbow подхватывает throttled
 * {@link CursorCustomizer#updateAnimated()} из кадрового цикла.
 */
public class CursorColorDialog extends BaseDialog{
    final Slot slot;
    final Runnable onChanged;

    public CursorColorDialog(Slot slot, Runnable onChanged){
        super(Core.bundle.format("client.sonka.cursors.colordialog.title", Core.bundle.get("client.sonka.cursors.slot." + slot.name)));
        this.slot = slot;
        this.onChanged = onChanged;
        addCloseButton();
        shown(this::setup);
    }

    void setup(){
        cont.clear();

        cont.table(modes -> {
            for(TintMode m : TintMode.values()){
                modes.button("@client.sonka.cursors.colordialog." + m.name(), Styles.togglet, () -> setMode(m))
                    .checked(b -> CursorCustomizer.tintMode(slot) == m).growX().height(44f).pad(2f);
            }
        }).growX().width(420f).row();

        TintMode mode = CursorCustomizer.tintMode(slot);
        cont.table(sw -> {
            if(mode != TintMode.rainbow){
                sw.add("@client.sonka.cursors.color").left().padRight(8f);
                addSwatch(sw, CursorCustomizer.tintKey(slot));
            }
            if(mode == TintMode.gradient){
                sw.add("@client.sonka.cursors.color2").left().padLeft(16f).padRight(8f);
                addSwatch(sw, CursorCustomizer.tint2Key(slot));
            }
        }).padTop(14f).row();

        cont.add("@client.sonka.cursors.colordialog.hint").width(420f).wrap().pad(6f).color(Color.gray);
    }

    void setMode(TintMode m){
        Core.settings.put(CursorCustomizer.tintModeKey(slot), m.ordinal());
        apply();
        setup();
    }

    void addSwatch(Table t, String key){
        ImageButton swatch = t.button(Tex.whiteui, Styles.squarei, 24f, () ->
            ui.picker.show(new Color(Core.settings.getInt(key, whiteRgba())), false, picked -> {
                Core.settings.put(key, Color.rgba8888(picked.r, picked.g, picked.b, picked.a));
                apply();
            })
        ).size(32f).pad(4f).get();
        swatch.getStyle().imageUpColor = new Color(); //своя копия стиля (см. CursorEditorDialog) - мутировать безопасно
        swatch.update(() -> swatch.getStyle().imageUpColor.set(Core.settings.getInt(key, whiteRgba())));
    }

    static int whiteRgba(){
        return Color.rgba8888(1f, 1f, 1f, 1f);
    }

    /** Статический тинт применяется немедленно; gradient/rainbow подхватит throttled кадровый апдейт. */
    void apply(){
        if(CursorCustomizer.tintMode(slot) != TintMode.gradient && CursorCustomizer.tintMode(slot) != TintMode.rainbow){
            CursorCustomizer.rebuild();
        }
        if(onChanged != null) onChanged.run();
    }
}
