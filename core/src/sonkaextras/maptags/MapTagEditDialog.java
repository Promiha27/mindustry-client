package sonkaextras.maptags;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.input.*;
import arc.scene.ui.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.maps.Map;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

/**
 * Диалог тегов ОДНОЙ карты: чекбоксы уже известных тегов (из {@link MapTags#allTags()}) +
 * поле создания нового тега (сразу применяется к этой карте). По образцу
 * {@code SchematicsDialog.buildTags}/showEdit, но без иконок-эмодзи-тегов (карты обходятся
 * простыми текстовыми - см. javadoc {@link MapTags}, лишняя сущность не нужна) и без поля
 * имени/описания (те у карты редактируются в редакторе карт, теги - независимая надстройка).
 */
public class MapTagEditDialog extends BaseDialog{
    private static final float tagh = 42f;

    private final Map map;
    /** Зовётся после каждого изменения набора тегов этой карты (добавление/снятие/новый тег). */
    public Runnable onChange = () -> {};

    public MapTagEditDialog(Map map){
        super(Core.bundle.format("client.sonka.maptags.edit.title", map.plainName()));
        this.map = map;
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();

        Seq<String> current = MapTags.getTags(map);
        current.sort();

        cont.add("@client.sonka.maptags.current").padTop(4f).row();
        cont.table(t -> {
            t.left();
            t.defaults().pad(3).height(tagh);
            if(current.isEmpty()){
                t.add("@client.sonka.maptags.none").color(Color.lightGray);
            }
            for(String tag : current){
                t.table(Tex.button, i -> {
                    i.add(tag).padRight(4).height(tagh).labelAlign(Align.center);
                    i.button(Icon.cancelSmall, Styles.emptyi, () -> {
                        MapTags.removeTag(map, tag);
                        onChange.run();
                        setup();
                    }).size(tagh).padRight(-9f).padLeft(-9f);
                });
            }
        }).fillX().left().row();

        cont.add("@client.sonka.maptags.addexisting").padTop(10f).row();
        Seq<String> known = MapTags.allTags().keys().toSeq();
        known.sort();
        known.removeAll(current::contains);

        cont.table(p -> {
            p.left().defaults().fillX().left();

            float sum = 0f;
            Table row = new Table().left();
            for(String tag : known){
                TextButton b = new TextButton(tag, Styles.togglet);
                b.changed(() -> {
                    MapTags.addTag(map, tag);
                    onChange.run();
                    setup();
                });
                b.getLabel().setWrap(false);
                b.pack();

                float w = b.getPrefWidth() + Scl.scl(6f);
                if(w + sum >= Core.graphics.getWidth() * (Core.graphics.isPortrait() ? 0.85f : 0.55f) && sum > 0){
                    p.add(row).row();
                    row = new Table().left();
                    sum = 0f;
                }
                row.add(b).height(tagh).pad(2);
                sum += w;
            }
            if(sum > 0) p.add(row).row();
            if(known.isEmpty()) p.add("@client.sonka.maptags.noneother").color(Color.lightGray);
        }).fillX().row();

        cont.table(t -> {
            t.left();
            t.image(Icon.add).padRight(4f);
            TextField field = t.field("", res -> {}).growX().get();
            field.setMessageText("@client.sonka.maptags.newtag");
            Cons<String> submit = out -> {
                String trimmed = out.trim();
                if(trimmed.isEmpty()) return;
                MapTags.addTag(map, trimmed);
                onChange.run();
                field.clearText();
                setup();
            };
            field.keyDown(KeyCode.enter, () -> submit.get(field.getText()));
            t.button(Icon.addSmall, Styles.emptyi, () -> submit.get(field.getText())).size(tagh);
        }).fillX().padTop(10f);
    }
}
