package sonkaextras.maptags;

import arc.*;
import arc.graphics.*;
import arc.struct.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

/**
 * Диалог управления полным списком тегов карт: переименование/удаление тега сразу везде,
 * счётчик карт на тег. По образцу {@code SchematicsDialog.showAllTags()}, но упрощён - у карт
 * нет отдельного персистентного упорядоченного списка тегов (см. {@link MapTags}, javadoc
 * класса), поэтому здесь нет ни ручной пересортировки (вверх/вниз), ни "заготовки" тега без
 * единой карты - список тегов строится каждый раз заново из {@link MapTags#allTags()}, тег
 * появляется только когда реально на что-то навешен (создаётся из {@link MapTagEditDialog}).
 * <p>
 * Точка входа - как у схем (кнопка-карандаш у панели фильтра в MapListDialog) плюс отдельная
 * кнопка в секции Sonka Extras (см. ChainWarn.init()) - тот же дубль входа, что и у прочих
 * sonkaextras-диалогов (курсоры, экспорт/импорт данных и т.п.).
 */
public class MapTagsDialog extends BaseDialog{
    private static final float tagh = 42f;

    /** Зовётся после любого изменения (переименование/удаление), чтобы список карт мог перефильтроваться. */
    public Runnable onChange = () -> {};

    public MapTagsDialog(){
        super("@client.sonka.maptags.manage");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();
        cont.pane(p -> {
            p.margin(12f).defaults().fillX().left();

            ObjectMap<String, Integer> counts = MapTags.allTags();
            Seq<String> tags = counts.keys().toSeq();
            tags.sort();

            if(tags.isEmpty()){
                p.add("@client.sonka.maptags.none").color(Color.lightGray).pad(10f);
                return;
            }

            for(String tag : tags){
                int count = counts.get(tag, 0);

                p.table(Tex.whiteui, n -> {
                    n.setColor(Pal.gray);
                    n.margin(5f);

                    n.table(t -> {
                        t.add(tag).left().row();
                        t.add(Core.bundle.format("client.sonka.maptags.tagged", count)).left().color(Color.lightGray);
                    }).growX().fillY();

                    n.table(b -> {
                        b.margin(2);

                        b.button(Icon.pencil, Styles.emptyi, () -> {
                            ui.showTextInput("@client.sonka.maptags.rename", "@name", tag, result -> {
                                if(result.equals(tag) || result.isEmpty()) return;
                                MapTags.renameTag(tag, result);
                                onChange.run();
                                setup();
                            });
                        }).size(tagh).tooltip("@client.sonka.maptags.rename").row();

                        b.button(Icon.trash, Styles.emptyi, () -> {
                            Runnable doDelete = () -> {
                                MapTags.deleteTag(tag);
                                onChange.run();
                                setup();
                            };
                            if(Core.input.shift()){
                                doDelete.run();
                            }else{
                                ui.showConfirm("@client.sonka.maptags.delete.confirm", doDelete);
                            }
                        }).size(tagh).tooltip("@save.delete");
                    }).fillY();
                }).pad(4).minWidth(260f).row();
            }
        }).grow().scrollX(false);
    }
}
