package mu;

import arc.Core;
import arc.func.Boolf;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.content.Planets;
import mindustry.ctype.Content;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.editor.BannedContentDialog;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Planet;
import mindustry.ui.Styles;

import static arc.Core.settings;
import static mindustry.Vars.*;

/**
 * Улучшенный диалог бана/раскрытия контента поверх ванильного v8 {@link BannedContentDialog}:
 * тот уже умеет поиск и фильтр по категории блоков, здесь сверху - ФИЛЬТР ПО ВКЛАДКЕ БАЗЫ
 * ДАННЫХ (планеты + прочие databaseTabs контента, «солнце» = все), настраиваемый размер кнопок
 * ({@code editor_content_buttons_size}) и режим «revealed» с инвертированной окраской
 * заголовков (раскрытое - акцент, нераскрытое - красное). Поля ванили приватные, поэтому
 * build()/rebuildTables() переписаны целиком (как в оригинале мода), а сам класс наследуется
 * только ради совместимости типов с полями CustomRulesDialog, куда он подставляется.
 */
public class BetterBannedContentDialog<T extends UnlockableContent> extends BannedContentDialog<T>{
    public boolean isRevealed = false;
    private final ContentType type;
    private Table selectedTable;
    private Table deselectedTable;
    private ObjectSet<T> contentSet;
    private final Boolf<T> pred;
    private String contentSearch;
    private Category selectedCategory;
    private Seq<T> filteredContent;
    private final Seq<UnlockableContent> allTabs;
    private UnlockableContent tab = Planets.sun;

    public BetterBannedContentDialog(String title, ContentType type, Boolf<T> pred){
        super(title, type, pred);
        this.type = type;
        this.pred = pred;
        contentSearch = "";

        selectedTable = new Table();
        deselectedTable = new Table();

        //свои shown/resized-листенеры не нужны: ванильные зовут build() виртуально - т.е. наш

        Seq<Content>[] allContent = content.getContentMap();
        ObjectSet<UnlockableContent> all = new ObjectSet<>();
        for(var contents : allContent){
            for(var c : contents){
                if(c instanceof UnlockableContent u){
                    all.addAll(u.databaseTabs);
                }
            }
        }
        allTabs = all.toSeq().sort();
        allTabs.insert(0, Planets.sun);
    }

    @Override
    public void show(ObjectSet<T> contentSet){
        this.contentSet = contentSet;
        super.show(contentSet);
    }

    @Override
    public void build(){
        cont.clear();
        if(contentSet == null) return;

        var cell = cont.table(t -> {
            t.table(s -> {
                s.label(() -> "@search").padRight(10);
                var field = s.field(contentSearch, value -> {
                    contentSearch = value.trim().replaceAll(" +", " ").toLowerCase();
                    rebuildTables();
                }).get();
                s.button(Icon.cancel, Styles.emptyi, () -> {
                    contentSearch = "";
                    field.setText("");
                    rebuildTables();
                }).padLeft(10f).size(35f);
                s.table(g -> {
                    for(var c : allTabs){
                        g.button(c == Planets.sun ? Icon.eyeSmall : c instanceof Planet p ? Icon.icons.get(p.icon, Icon.commandRally) : new TextureRegionDrawable(c.uiIcon), Styles.clearNoneTogglei, iconMed, () -> {
                            tab = c;
                            rebuildTables();
                        }).size(50f).checked(b -> tab == c).tooltip(c == Planets.sun ? "@all" : c.localizedName).with(but -> {
                            but.getStyle().imageUpColor = c instanceof Planet p ? p.iconColor : Color.white.cpy();
                        });
                    }
                }).padLeft(10f);
            });
            if(type == ContentType.block){
                t.row();
                t.table(c -> {
                    c.marginTop(8f);
                    c.defaults().marginRight(4f);
                    for(Category category : Category.values()){
                        c.button(ui.getIcon(category.name()), Styles.squareTogglei, () -> {
                            if(selectedCategory == category){
                                selectedCategory = null;
                            }else{
                                selectedCategory = category;
                            }
                            rebuildTables();
                        }).size(45f).update(i -> i.setChecked(selectedCategory == category)).padLeft(4f);
                    }
                    c.add("").padRight(4f);
                }).center();
            }
        });
        cont.row();
        if(!Core.graphics.isPortrait()) cell.colspan(2);

        filteredContent = content.<T>getBy(type).select(pred);
        if(!contentSearch.isEmpty()) filteredContent.removeAll(c -> !c.localizedName.toLowerCase().contains(contentSearch.toLowerCase()));

        Color red = Color.valueOf("f25555");

        cont.table(table -> {
            if(isRevealed){
                table.add("@revealed_content").color(Pal.accent).padBottom(-1).top().row();
            }else if(type == ContentType.block){
                table.add("@bannedblocks").color(red).padBottom(-1).top().row();
            }else{
                table.add("@bannedunits").color(red).padBottom(-1).top().row();
            }

            table.image().color(isRevealed ? Pal.accent : red).height(3f).padBottom(5f).fillX().top().row();
            table.pane(table2 -> selectedTable = table2).fill().expand().row();
            table.button("@addall", Icon.add, () -> {
                contentSet.addAll(filteredContent);
                rebuildTables();
            }).disabled(button -> contentSet.toSeq().containsAll(filteredContent)).padTop(10f).bottom().fillX();
        }).fill().expandY().uniform();

        if(Core.graphics.isPortrait()) cont.row();

        var cell2 = cont.table(table -> {
            if(isRevealed){
                table.add("@unrevealed_content").color(red).padBottom(-1).top().row();
            }else if(type == ContentType.block){
                table.add("@unbannedblocks").color(Pal.accent).padBottom(-1).top().row();
            }else{
                table.add("@unbannedunits").color(Pal.accent).padBottom(-1).top().row();
            }

            table.image().color(!isRevealed ? Pal.accent : red).height(3f).padBottom(5f).fillX().expandX().top().row();
            table.pane(table2 -> deselectedTable = table2).fill().expand().row();
            table.button("@addall", Icon.add, () -> {
                contentSet.removeAll(filteredContent);
                rebuildTables();
            }).disabled(button -> {
                Seq<T> array = content.getBy(type);
                array = array.copy();
                array.removeAll(contentSet.toSeq());
                return array.containsAll(filteredContent);
            }).padTop(10f).bottom().fillX();
        }).fill().expandY().uniform();
        if(Core.graphics.isPortrait()){
            cell2.padTop(10f);
        }else{
            cell2.padLeft(10f);
        }

        rebuildTables();
    }

    private void rebuildTables(){
        filteredContent = content.<T>getBy(type).select(pred);

        if(!contentSearch.isEmpty()) filteredContent.removeAll(c -> !c.localizedName.toLowerCase().contains(contentSearch.toLowerCase()));
        if(type == ContentType.block){
            filteredContent.removeAll(c -> selectedCategory != null && ((mindustry.world.Block)c).category != selectedCategory);
        }
        filteredContent.removeAll(c -> !(tab == Planets.sun || c.allDatabaseTabs || c.databaseTabs.contains(tab)));

        rebuildTable(selectedTable, true);
        rebuildTable(deselectedTable, false);
    }

    private void rebuildTable(Table table, boolean isSelected){
        table.clear();

        int buttonSize = settings.getInt("editor_content_buttons_size", 50);

        int cols;
        if(Core.graphics.isPortrait()){
            cols = Math.max(4, (int)((Core.graphics.getWidth() / Scl.scl() - 100f) / buttonSize));
        }else{
            cols = Math.max(4, (int)((Core.graphics.getWidth() / Scl.scl() - 300f) / buttonSize / 2));
        }

        if((isSelected && contentSet.isEmpty()) || (!isSelected && contentSet.size == content.<T>getBy(type).count(pred))){
            table.add("@empty").width(buttonSize * cols).padBottom(5f).get().setAlignment(Align.center);
            return;
        }
        Seq<T> array;
        if(!isSelected){
            array = content.getBy(type);
            array = array.copy();
            array.removeAll(contentSet.toSeq());
        }else{
            array = contentSet.toSeq();
        }
        array.sort();
        array.removeAll(c -> !filteredContent.contains(c));

        if(array.isEmpty()){
            table.add("@empty").width(buttonSize * cols).padBottom(5f).get().setAlignment(Align.center);
            return;
        }
        int i = 0;
        boolean requiresPad = true;

        for(T c : array){
            TextureRegion region = c.uiIcon;

            ImageButton button = new ImageButton(Tex.whiteui, Styles.clearNonei);
            button.getStyle().imageUp = new TextureRegionDrawable(region);
            button.resizeImage(buttonSize - 8f);
            if(isSelected) button.clicked(() -> {
                contentSet.remove(c);
                rebuildTables();
            });
            else button.clicked(() -> {
                contentSet.add(c);
                rebuildTables();
            });
            table.add(button).size(buttonSize).tooltip(c.localizedName);

            if(++i % cols == 0){
                table.row();
                requiresPad = false;
            }
        }

        if(requiresPad){
            table.add("").padRight(buttonSize * (cols - i));
        }
    }
}
