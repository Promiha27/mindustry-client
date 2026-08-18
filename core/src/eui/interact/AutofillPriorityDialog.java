package eui.interact;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.ui.Label;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItemDynamic;
import mindustry.world.consumers.ConsumeItemFilter;
import mindustry.world.consumers.ConsumeItems;

import static mindustry.Vars.content;

/**
 * Per-block-type priority editor for {@link AutoFill} - blocks with a higher priority are
 * filled/serviced first when several are in range; -2 (the minimum) effectively excludes a block from
 * auto-fill entirely, since {@link AutoFill#update} starts its own running priority at -1 and rejects
 * anything strictly below it. Ported from ui/other/autofill-priority-ui.js.
 * <p>
 * The JS version went out of its way to read every {@code block.category} exactly once, into a plain
 * array of JS strings, up front in its dialog-build step - and never touched {@code Category} objects
 * again from its (frequently re-run) list-refresh function. That dance existed only to dodge a Rhino JIT
 * bug where repeatedly reading {@code .category.name()} from inside a hot, often-invoked callback would
 * "stick" to whichever value was read first (see memory: the mod's 8-round debugging history). Plain
 * compiled Java has no such bug - {@link #refreshList} below reads {@code entry.category} directly on
 * every call with no caching workaround needed.
 */
public class AutofillPriorityDialog{
    private static final int MIN_PRIORITY = -2;
    private static final int MAX_PRIORITY = 5;
    private static final String SETTINGS_KEY = "eui.autofill.priority";

    private BaseDialog dialog;
    private Table list;
    private TextField searchField;
    private final Seq<Entry> allEntries = new Seq<>();

    static class Entry{
        final Block block;
        final Category category;

        Entry(Block block, Category category){
            this.block = block;
            this.category = category;
        }
    }

    public AutofillPriorityDialog(){
        Events.on(ClientLoadEvent.class, e -> {
            try{
                buildDialog();
            }catch(Throwable t){
                Log.err("[eui] autofill-priority buildDialog error", t);
            }
        });
    }

    public void show(){
        if(dialog == null) return;
        if(searchField != null) searchField.setText("");
        refreshList(""); //reset the search and pick up any priority changes made since last open
        dialog.show();
    }

    static boolean isFillable(Block block){
        for(Consume c : block.consumers){
            if(c instanceof ConsumeItems || c instanceof ConsumeItemFilter || c instanceof ConsumeItemDynamic) return true;
        }
        return false;
    }

    static ObjectMap<String, Integer> loadConfig(){
        return Core.settings.getJson(SETTINGS_KEY, ObjectMap.class, ObjectMap::new);
    }

    static boolean matchesSearch(Entry entry, String filterLower){
        if(filterLower.isEmpty()) return true;
        Block block = entry.block;
        return block.localizedName.toLowerCase().contains(filterLower) || block.name.toLowerCase().contains(filterLower);
    }

    Table buildRow(Block block, ObjectMap<String, Integer> config){
        Table rowTable = new Table();
        rowTable.image(block.uiIcon).size(32).padRight(8);
        rowTable.add(block.localizedName).left().width(190).wrap();

        Label[] valueLabel = new Label[1];

        rowTable.button("-", () -> {
            int next = arc.math.Mathf.clamp(config.get(block.name, 0) - 1, MIN_PRIORITY, MAX_PRIORITY);
            config.put(block.name, next);
            Core.settings.putJson(SETTINGS_KEY, config);
            valueLabel[0].setText(String.valueOf(next));
        }).size(36).padLeft(8);

        valueLabel[0] = rowTable.add(String.valueOf(config.get(block.name, 0))).width(30).get();
        valueLabel[0].setAlignment(Align.center);

        rowTable.button("+", () -> {
            int next = arc.math.Mathf.clamp(config.get(block.name, 0) + 1, MIN_PRIORITY, MAX_PRIORITY);
            config.put(block.name, next);
            Core.settings.putJson(SETTINGS_KEY, config);
            valueLabel[0].setText(String.valueOf(next));
        }).size(36);

        return rowTable;
    }

    void refreshList(String filterText){
        if(list == null) return;
        list.clearChildren();

        try{
            refreshListInner(filterText);
        }catch(Throwable t){
            Log.err("[eui] autofill-priority refreshList error", t);
            list.add("refreshList error: " + t).color(Color.scarlet).wrap().width(400).row();
        }
    }

    void refreshListInner(String filterText){
        //re-read on every refresh (not cached) so a value just changed by -/+ shows up immediately if
        //the player starts typing in the search field right after
        ObjectMap<String, Integer> config = loadConfig();
        String filterLower = filterText == null ? "" : filterText.toLowerCase().trim();

        ObjectMap<Category, Seq<Block>> groups = new ObjectMap<>();
        for(Entry entry : allEntries){
            if(!matchesSearch(entry, filterLower)) continue;
            groups.get(entry.category, Seq::new).add(entry.block);
        }

        int shown = 0;
        for(Category category : Category.all){
            Seq<Block> bucket = groups.get(category);
            if(bucket == null || bucket.isEmpty()) continue;

            list.add(Core.bundle.get("eui.category." + category.name()))
                .color(Pal.accent).left().padTop(shown == 0 ? 0 : 12).padBottom(4).row();

            for(Block block : bucket){
                list.add(buildRow(block, config)).growX().row();
                shown++;
            }
        }

        if(shown == 0){
            list.add(Core.bundle.get("eui.autofill-priority.no-results")).pad(12).row();
        }
    }

    void buildDialog(){
        dialog = new BaseDialog(Core.bundle.get("eui.autofill-priority.title"));
        dialog.addCloseButton();

        //only block references are collected inside content.blocks().each() - .category is read in a
        //separate pass below, not inside this frequently-called-in-vanilla-Rhino callback. That
        //separation is no longer load-bearing in Java (see class javadoc) but keeping it costs nothing
        //and matches the source 1:1.
        Seq<Block> fillableBlocks = new Seq<>();
        content.blocks().each(block -> {
            if(block.uiIcon != null && block.uiIcon.found() && isFillable(block)){
                fillableBlocks.add(block);
            }
        });

        allEntries.clear();
        for(Block b : fillableBlocks){
            allEntries.add(new Entry(b, b.category));
        }
        allEntries.sort((a, b) -> a.block.localizedName.compareTo(b.block.localizedName));

        dialog.cont.add(Core.bundle.get("eui.autofill-priority.hint")).width(420).wrap().pad(6).row();

        Table searchRow = new Table();
        searchRow.add(Core.bundle.get("eui.autofill-priority.search") + ":").padRight(6);
        searchField = searchRow.field("", this::refreshList).growX().get();
        dialog.cont.add(searchRow).growX().pad(4).row();

        list = new Table();
        dialog.cont.pane(list).width(420).height(420).row();
    }
}
