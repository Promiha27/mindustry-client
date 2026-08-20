package sonkaextras.cursors;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import sonkaextras.cursors.CursorCustomizer.*;

import static mindustry.Vars.*;

/**
 * Главный диалог кастомизации курсоров (кнопка «Курсоры...» в секции Sonka Extras): список всех
 * курсоров игры с превью и пер-курсорными действиями - своя PNG-текстура (кладётся в
 * <data>/cursors/<слот>.png; наличие файла = замена активна), тинт-цвет через ванильный
 * ColorPicker, пиксельный редактор и сброс. Превью показывает текстуру с тинтом (тинт - это
 * цвет Image, ровно тот же мультипликативный тинт, что движок применяет к пиксмапе), итоговый
 * размер после масштаба написан подписью. Все изменения применяются сразу же
 * ({@link CursorCustomizer#rebuild()}).
 * <p>
 * Текстуры превью пересоздаются на каждый setup() и живут парой со своей пиксмапой до
 * закрытия/перестройки (паттерн ванильного CanvasEditDialog: Texture(Pixmap) не забирает
 * владение пиксмапой).
 */
public class CursorsDialog extends BaseDialog{
    private final Seq<Disposable> resources = new Seq<>();

    public CursorsDialog(){
        super("@client.sonka.cursors.title");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
        hidden(this::disposeResources);
    }

    void disposeResources(){
        resources.each(Disposable::dispose);
        resources.clear();
    }

    void setup(){
        disposeResources();
        cont.clear();

        cont.add("@client.sonka.cursors.hint").width(620f).wrap().pad(6f).row();

        cont.pane(p -> {
            for(Slot s : CursorCustomizer.slots){
                buildRow(p, s);
                p.row();
            }
        }).growX().maxHeight(Core.graphics.getHeight() / Scl.scl(1f) * 0.7f).row();
    }

    void buildRow(Table p, Slot s){
        p.table(Tex.pane, row -> {
            //превью: текстура-исходник (кастом или ваниль) на шахматке, тинт - цветом Image
            Pixmap pix = CursorCustomizer.basePixmap(s);
            Texture tex = new Texture(pix);
            tex.setFilter(TextureFilter.nearest);
            resources.add(tex);
            resources.add(pix);
            Image img = new Image(new TextureRegion(tex));
            Color tint = CursorCustomizer.tint(s);
            if(tint != null) img.setColor(tint);
            row.stack(new Image(Tex.alphaBg), img).size(48f).pad(4f);

            boolean custom = CursorCustomizer.customFile(s).exists();
            row.table(text -> {
                text.left();
                text.add(Core.bundle.get("client.sonka.cursors.slot." + s.name) + (custom ? " [accent]PNG[]" : "")).left().row();
                text.add(pix.width + "x" + pix.height + " > " + CursorCustomizer.effectiveSize(pix.width) + "x" + CursorCustomizer.effectiveSize(pix.height) + "px")
                    .color(Color.gray).left();
            }).growX().padLeft(8f);

            row.defaults().size(48f).pad(2f);
            row.button(Icon.file, Styles.cleari, () -> pickFile(s)).tooltip("@client.sonka.cursors.file");
            row.button(Icon.pick, Styles.cleari, () -> pickColor(s)).tooltip("@client.sonka.cursors.color");
            row.button(Icon.refresh, Styles.cleari, () -> reset(s)).tooltip("@client.sonka.cursors.reset")
                .disabled(b -> !CursorCustomizer.customFile(s).exists()
                    && !Core.settings.has(CursorCustomizer.tintKey(s))
                    && !Core.settings.has(CursorCustomizer.hotspotKey(s)));
        }).growX().pad(2f);
    }

    /** «Файл...»: выбор PNG, валидация размером/декодированием, копия в <data>/cursors/, применение. */
    void pickFile(Slot s){
        FileChooser.open("png").submit(fi -> {
            try{
                Pixmap p = new Pixmap(fi);
                int w = p.width, h = p.height;
                p.dispose();
                if(w <= 0 || h <= 0 || w > CursorCustomizer.MAX_SOURCE_SIZE || h > CursorCustomizer.MAX_SOURCE_SIZE){
                    ui.showErrorMessage(Core.bundle.format("client.sonka.cursors.file.badsize", w, h, CursorCustomizer.MAX_SOURCE_SIZE));
                    return;
                }
                CursorCustomizer.cursorsDir().mkdirs();
                fi.copyTo(CursorCustomizer.customFile(s));
                CursorCustomizer.rebuild();
                setup();
            }catch(Throwable t){
                Log.err("[sonka-cursors] failed to import png for '" + s.name + "'", t);
                ui.showErrorMessage(Core.bundle.get("client.sonka.cursors.file.invalid") + "\n" + t.getMessage());
            }
        });
    }

    /** «Цвет»: тинт через ванильный ColorPicker (без альфы - прозрачность курсору задаёт сама текстура). */
    void pickColor(Slot s){
        Color current = CursorCustomizer.tint(s);
        ui.picker.show(current == null ? new Color(Color.white) : current, false, c -> {
            Core.settings.put(CursorCustomizer.tintKey(s), c.rgba());
            CursorCustomizer.rebuild();
            setup();
        });
    }

    /** «Сброс»: слот к чистой ванили - удалить кастомный PNG, тинт и хотспот. */
    void reset(Slot s){
        CursorCustomizer.customFile(s).delete();
        Core.settings.remove(CursorCustomizer.tintKey(s));
        Core.settings.remove(CursorCustomizer.hotspotKey(s));
        CursorCustomizer.rebuild();
        setup();
    }
}
