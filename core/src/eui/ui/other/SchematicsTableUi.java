package eui.ui.other;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import eui.icons.IconCategoriesConfig;
import eui.icons.IconCategoriesConfig.Category;
import eui.icons.Icons;
import mindustry.content.Blocks;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SchematicsDialog.SchematicImage;

import static mindustry.Vars.mobile;
import static mindustry.Vars.player;
import static mindustry.Vars.schematics;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/**
 * "eui-ShowSchematicsTable": a quick-access grid of schematics pinned near the minimap, tabbed into up to
 * {@code eui-SchematicsTableColumns} player-named categories, each cell holding up to
 * {@link #MAX_SCHEMATIC_ICONS} custom icons, tinted by a red-to-green core-affordability gradient, with
 * drag-and-drop to rearrange cells and a searchable preview-thumbnail picker to bind a schematic without
 * typing its name. By far the richest single feature in this mod - see the memory of this file's 8-round
 * Rhino debugging history for the full story; every workaround from that history (the for...of "pinning"
 * bug, ambiguous-overload button construction, the DragListener/InputListener bytecode mismatch) is a
 * Rhino-only concern and none of it appears here - see {@link eui.EUIMod}'s javadoc for the general
 * policy. What's preserved is the END BEHAVIOUR those workarounds were protecting, which this file
 * reaches directly with ordinary compiled Java (real {@code for}/{@code for-each} loops, a real
 * {@code InputListener} subclass, plain overloaded methods - none of it ambiguous to the Java compiler).
 * Ported from ui/other/schematics-table-ui.js.
 */
public class SchematicsTableUi{
    private static final String MULTI_ICON_SEPARATOR = ",";
    private static final int MAX_SCHEMATIC_ICONS = 4;

    private static final int SCHEMATIC_PICKER_ICON_SIZE = 140;
    private static final int SCHEMATIC_PICKER_CARD_WIDTH = SCHEMATIC_PICKER_ICON_SIZE + 16;
    private static final int SCHEMATIC_PICKER_CARD_PAD = 4;
    private static final int SCHEMATIC_PICKER_SLOT_WIDTH = SCHEMATIC_PICKER_CARD_WIDTH + SCHEMATIC_PICKER_CARD_PAD * 2;

    private boolean built = false;
    private Table contentTable;
    private Table previewTable;
    private BaseDialog setCategoryNameDialog;

    private int currentCategory = 0;
    private int lastCategory = 0;

    private int schematicButtonSize;
    private int categoryButtonSize;

    private int rows, columns;
    private Integer oldRows, oldColumns, oldSize;

    private Schematic hovered;

    private static class CellRef{
        final int category, column, row;
        CellRef(int category, int column, int row){ this.category = category; this.column = column; this.row = row; }
        boolean is(int c, int col, int r){ return category == c && column == col && row == r; }
    }

    private static class CellButton{
        final Button btn;
        final int category, column, row;
        CellButton(Button btn, int category, int column, int row){ this.btn = btn; this.category = category; this.column = column; this.row = row; }
    }

    private Seq<CellButton> cellButtons = new Seq<>();
    private CellRef dragSource;
    private CellRef dragHoverTarget;

    private static class SchematicEntry{
        final Schematic schematic;
        final String name;
        SchematicEntry(Schematic schematic, String name){ this.schematic = schematic; this.name = name; }
    }

    private BaseDialog schematicPickerDialog;
    private Table schematicPickerList;
    private TextField schematicPickerSearchField;
    private Cell<ScrollPane> schematicPickerPaneCell;
    private Seq<SchematicEntry> allSchematicEntries = new Seq<>();
    private String pickerTargetKey;
    private Cons<String> pickerOnPicked;
    private int pickerColumns = 3;

    //mobile double-tap-to-edit tracking
    private String lastTapped;
    private long lastTapTimeMs;

    //shared scroll-position buffer for the icon-picker pane (ModsDialog.java-style: one dialog, reused across opens)
    private float iconScrollY = 0;

    public SchematicsTableUi(){
        Events.on(ClientLoadEvent.class, e -> {
            ui.hudGroup.fill(null, t -> {
                previewTable = t.table(Styles.black3).get();
                previewTable.visibility = this::previewTableVisibility;
                previewTable.update(() -> previewTable.color.a = Core.settings.getInt("eui-SchematicsTableAlpha", 100) / 100f);
                t.center();
                t.pack();
            });

            setCategoryNameDialog = new BaseDialog(Core.bundle.get("schematics-table.dialog.change-cathegory-name.title"));
            setCategoryNameDialog.addCloseButton();
            setCategoryNameDialog.cont.pane(table -> table.field(null, text -> {
                if(text == null || text.isEmpty()) return;
                Core.settings.put("category" + currentCategory + "name", text);
                rebuildTable();
            }).growX()).size(320f, 320f);

            schematicPickerDialog = new BaseDialog(Core.bundle.get("schematics-table.dialog.pick-schematic.title"));
            schematicPickerDialog.addCloseButton();

            Table searchRow = new Table();
            searchRow.add(Core.bundle.get("schematics-table.dialog.pick-schematic.search") + ":").padRight(6f);
            schematicPickerSearchField = searchRow.field("", this::refreshSchematicPickerList).growX().get();
            schematicPickerDialog.cont.add(searchRow).growX().pad(4f).row();

            schematicPickerList = new Table();
            schematicPickerPaneCell = schematicPickerDialog.cont.pane(schematicPickerList).scrollX(false)
                .width(SCHEMATIC_PICKER_SLOT_WIDTH * pickerColumns).height(560f);
            schematicPickerPaneCell.row();
        });

        Events.run(Trigger.update, this::update);
    }

    void update(){
        if(!Core.settings.getBool("eui-ShowSchematicsTable", true)){
            if(built) clearTable();
            return;
        }

        rows = Core.settings.getInt("eui-SchematicsTableRows", 4);
        columns = Core.settings.getInt("eui-SchematicsTableColumns", 5);
        schematicButtonSize = Core.settings.getInt("eui-SchematicsTableButtonSize", 30);
        categoryButtonSize = schematicButtonSize + 2;

        if(contentTable == null) setMarker();
        if(isRebuildNeeded()) rebuildTable();

        updatePosition();

        if(hovered != null && contentTable.hasMouse()){
            rebuildPreviewTable();
        }else{
            hovered = null;
        }
    }

    // ---------------------------------------------------------------- edit dialogs

    void showEditSchematicButtonDialog(int category, int column, int row){
        float size = mobile ? 320f : 560f;
        String schematicString = getSchematicString(category, column, row);
        BaseDialog dialog = new BaseDialog(Core.bundle.get("schematics-table.dialog.edit-schematic-button.title"));
        dialog.addCloseButton();

        addEditImageTable(dialog, schematicString + "image", size, true);
        dialog.cont.row();
        addEditSchematicTable(dialog, schematicString);
        dialog.cont.row();
        //(String, Drawable, Runnable), same 3-arg shape already used safely elsewhere in this mod - not
        //the button(Cons<Button>, Style, Runnable) overload the source's grid cells avoid (see buildTable)
        dialog.cont.button(Core.bundle.get("schematics-table.dialog.clear-cell"), Icon.trash, () -> {
            clearSchematicCell(schematicString);
            dialog.hide();
        }).width(240f).height(50f).pad(4f);

        dialog.show();
    }

    /** Clears one cell's binding (name + icons) back to "empty" - doesn't touch the schematic itself in the game's library. */
    void clearSchematicCell(String schematicString){
        Core.settings.remove(schematicString);
        Core.settings.remove(schematicString + "image");
        ui.announce(Core.bundle.get("schematics-table.dialog.clear-cell-announce"));
        rebuildTable();
    }

    void showEditImageDialog(String name){
        float size = mobile ? 320f : 640f;
        BaseDialog dialog = new BaseDialog(Core.bundle.get("schematics-table.dialog.change-image.title"));
        dialog.addCloseButton();
        addEditImageTable(dialog, name, size, false);
        dialog.show();
    }

    /**
     * multiSelect - schematic-cell mode: clicking an icon adds/removes it from up to
     * {@link #MAX_SCHEMATIC_ICONS} selected icons (already-selected ones highlighted). Without it
     * (category tabs) a single click replaces the one selected icon, as before.
     */
    void addEditImageTable(BaseDialog dialog, String name, float size, boolean multiSelect){
        dialog.cont.pane(table -> {
            for(Category category : IconCategoriesConfig.CATEGORIES){
                if(IconCategoriesConfig.isErrorCategory(category.name)) continue;

                Seq<String> iconsToDisplay;
                if(IconCategoriesConfig.USE_ICON_CATEGORIES){
                    if(category.name.equals("Other")) iconsToDisplay = IconCategoriesConfig.getOtherIcons();
                    else if(category.name.equals("Mods")) iconsToDisplay = IconCategoriesConfig.getModIconsCategory();
                    else iconsToDisplay = Seq.with(category.icons);
                }else{
                    iconsToDisplay = IconCategoriesConfig.getAllCategoryIcons();
                }

                if(iconsToDisplay.isEmpty() && !category.name.equals("Other") && !category.name.equals("Mods")) continue;

                String collapsedKey = "eui-iconCategory-collapsed-" + category.name;
                boolean[] isCollapsed = {Core.settings.getBool(collapsedKey, false)};

                Table iconsContent = new Table();
                int[] col = {0};
                for(String iconName : iconsToDisplay){
                    Drawable iconDrawable = Icons.getIconDrawable(iconName);

                    arc.scene.ui.ImageButton imageButton;
                    if(multiSelect){
                        imageButton = iconsContent.button(iconDrawable, Styles.clearTogglei, () -> {
                            Seq<String> names = toggleImageName(name, iconName);
                            boolean nowSelected = names.contains(iconName);
                            ui.announce(Core.bundle.get(nowSelected
                                ? "schematics-table.dialog.change-image.added-announce-text"
                                : "schematics-table.dialog.change-image.removed-announce-text") + " " + iconName);
                            rebuildTable();
                        }).update(b -> b.setChecked(getImageNames(name).contains(iconName))).size(48f).pad(4f).get();
                    }else{
                        imageButton = iconsContent.button(iconDrawable, Styles.cleari, () -> {
                            Core.settings.put(name, iconName);
                            ui.announce(Core.bundle.get("schematics-table.dialog.change-image.setted-announce-text") + " " + iconName);
                            rebuildTable();
                        }).size(48f).pad(4f).get();
                    }
                    imageButton.resizeImage(48f * 0.8f);

                    if(++col[0] % IconCategoriesConfig.ICONS_PER_ROW == 0) iconsContent.row();
                }

                table.row();
                table.table(null, headerTable -> {
                    headerTable.left().defaults().left();

                    headerTable.table(null, iconNameTable -> {
                        String categoryIconName = category.icon != null ? category.icon : "info";
                        iconNameTable.image(Icons.getIconDrawable(categoryIconName)).size(48f).pad(4f).padLeft(8f);
                        iconNameTable.add(category.name).color(Color.lightGray).padLeft(8f).padRight(8f);
                    });

                    headerTable.image(Tex.whiteui).color(Color.gray).height(3f).growX().pad(4f);

                    headerTable.button(Icon.downOpen, Styles.emptyi, () -> {
                        isCollapsed[0] = !isCollapsed[0];
                        Core.settings.put(collapsedKey, isCollapsed[0]);
                    }).update(btn -> {
                        if(!btn.getChildren().isEmpty() && btn.getChildren().first() instanceof Image img){
                            img.setDrawable(isCollapsed[0] ? Icon.upOpen : Icon.downOpen);
                        }
                        btn.setChecked(isCollapsed[0]);
                    }).size(40f).padLeft(8f).padRight(8f);
                }).growX().padTop(8f).padBottom(4f);

                table.row();
                Table wrapperTable = new Table();
                wrapperTable.collapser(collapserTable -> collapserTable.left().add(iconsContent), () -> isCollapsed[0]);
                table.add(wrapperTable).growX();
            }
        }).size(size * 2, size).scrollX(false).update(s -> iconScrollY = s.getScrollY()).get().setScrollYForce(iconScrollY);
    }

    void addEditSchematicTable(BaseDialog dialog, String name){
        String hintText = Core.bundle.get("schematics-table.dialog.change-schematic.title");
        dialog.cont.pane(table -> {
            table.labelWrap(hintText).growX();
            table.row();

            String currentName = Core.settings.getString(name, "");
            var currentLabel = table.labelWrap(currentSchematicLabelText(currentName)).growX().color(Pal.accent).get();
            table.row();

            table.button(Core.bundle.get("schematics-table.dialog.pick-schematic"), () ->
                showSchematicPickerDialog(name, pickedName -> currentLabel.setText(currentSchematicLabelText(pickedName)))
            ).width(240f).height(44f).pad(4f);
        }).size(Core.graphics.getWidth() / 2f, 120f);
    }

    String currentSchematicLabelText(String name){
        return name != null && !name.isEmpty()
            ? Core.bundle.get("schematics-table.dialog.pick-schematic.current") + " " + name
            : Core.bundle.get("schematics-table.dialog.pick-schematic.none-selected");
    }

    // ---------------------------------------------------------------- schematic picker

    void showSchematicPickerDialog(String settingKey, Cons<String> onPicked){
        pickerTargetKey = settingKey;
        pickerOnPicked = onPicked;

        //fill-screen adaptive grid, recomputed on every open rather than fixed
        pickerColumns = Math.max(1, (int)((Core.graphics.getWidth() - 80) / SCHEMATIC_PICKER_SLOT_WIDTH));
        float paneHeight = Math.max(300, Core.graphics.getHeight() - 220);
        if(schematicPickerPaneCell != null){
            schematicPickerPaneCell.width(SCHEMATIC_PICKER_SLOT_WIDTH * pickerColumns).height(paneHeight);
            schematicPickerDialog.cont.invalidateHierarchy();
        }

        //recollected on every open (not cached) so freshly imported/created schematics show up without a restart
        allSchematicEntries = new Seq<>();
        for(Schematic s : schematics.all()) allSchematicEntries.add(new SchematicEntry(s, s.name()));
        allSchematicEntries.sort((a, b) -> a.name.compareTo(b.name));

        if(schematicPickerSearchField != null) schematicPickerSearchField.setText("");
        refreshSchematicPickerList("");
        schematicPickerDialog.show();
    }

    void refreshSchematicPickerList(String filterText){
        if(schematicPickerList == null) return;
        schematicPickerList.clearChildren();

        try{
            refreshSchematicPickerListInner(filterText);
        }catch(Throwable t){
            Log.err("[eui] schematics-table picker refreshList error", t);
            schematicPickerList.add("refreshList error: " + t).color(Color.scarlet).wrap().width(400f).row();
        }
    }

    void refreshSchematicPickerListInner(String filterText){
        String filterLower = filterText == null ? "" : filterText.toLowerCase().trim();
        int shown = 0;

        for(SchematicEntry entry : allSchematicEntries){
            if(!filterLower.isEmpty() && !entry.name.toLowerCase().contains(filterLower)) continue;

            schematicPickerList.add(buildSchematicPickerCard(entry.name, entry.schematic))
                .width(SCHEMATIC_PICKER_CARD_WIDTH).pad(SCHEMATIC_PICKER_CARD_PAD).top();
            shown++;
            if(shown % pickerColumns == 0) schematicPickerList.row();
        }

        if(shown == 0){
            schematicPickerList.add(Core.bundle.get("schematics-table.dialog.pick-schematic.no-results")).pad(12f).row();
        }
    }

    Button buildSchematicPickerCard(String name, Schematic schematic){
        Button card = new Button(Styles.defaulti);
        try{
            SchematicImage preview = new SchematicImage(schematic);
            card.add(preview).size(SCHEMATIC_PICKER_ICON_SIZE).pad(2f);
            card.row();
        }catch(Throwable t){
            Log.err("[eui] schematics-table picker preview failed for " + name, t);
        }
        card.add(name).width(SCHEMATIC_PICKER_ICON_SIZE).wrap().get().setAlignment(Align.center);
        card.clicked(() -> pickSchematic(name));
        return card;
    }

    void pickSchematic(String name){
        if(pickerTargetKey == null) return;
        Core.settings.put(pickerTargetKey, name);
        ui.announce(Core.bundle.get("schematics-table.dialog.change-image.setted-announce-text") + " " + name);
        rebuildTable();
        if(pickerOnPicked != null) pickerOnPicked.get(name);
        schematicPickerDialog.hide();
    }

    // ---------------------------------------------------------------- table build/layout

    void setMarker(){
        contentTable = new Table(Styles.black3);
        contentTable.name = "eui-schematics-table";
        contentTable.visibility = () -> built && ui.hudfrag.shown;

        ui.hudGroup.addChild(contentTable);
        contentTable.toBack(); //behind the placement menu (PlacementFragment), never on top of it

        updatePosition();
        contentTable.pack();
    }

    void updatePosition(){
        if(contentTable == null) return;

        int offsetX = clamp(parseIntSetting("eui-SchematicsTableX", 10), 0, 5000);
        int offsetY = clamp(parseIntSetting("eui-SchematicsTableY", 160), 0, 5000);

        contentTable.setPosition(Core.graphics.getWidth() - offsetX, Core.graphics.getHeight() - offsetY, Align.topRight);
    }

    /** Reads a setting that may be stored as either an int or a string - the source went through this migration itself; kept defensive here too since it shares the same "eui-SchematicsTableX/Y" settings keys an existing install may already have on disk. */
    static int parseIntSetting(String key, int def){
        Object raw = Core.settings.get(key, def);
        if(raw instanceof Integer i) return i;
        if(raw instanceof String s){
            try{
                return Integer.parseInt(s.trim());
            }catch(NumberFormatException ignored){
                return def;
            }
        }
        return def;
    }

    static int clamp(int value, int min, int max){
        return Math.max(min, Math.min(value, max));
    }

    boolean isRebuildNeeded(){
        if(!built) return true;

        if(oldColumns == null || oldRows == null || oldSize == null){
            oldRows = rows;
            oldColumns = columns;
            oldSize = schematicButtonSize;
        }
        if(rows != oldRows || columns != oldColumns || schematicButtonSize != oldSize){
            oldRows = rows;
            oldColumns = columns;
            oldSize = schematicButtonSize;
            return true;
        }

        if(lastCategory != currentCategory){
            lastCategory = currentCategory;
            return true;
        }

        return false;
    }

    void rebuildTable(){
        if(contentTable == null) return;
        clearTable();
        buildTable();
    }

    void buildTable(){
        if(contentTable == null) return;

        cellButtons = new Seq<>();
        dragSource = null;
        dragHoverTarget = null;

        Table wrapped = contentTable.table().margin(3f).get();

        Table categoryButtonsTable = wrapped.table().get();
        for(int i = 0; i < columns; i++){
            int index = i;
            var categoryButton = categoryButtonsTable.button(getCategoryImage(index), Styles.clearTogglei, () -> currentCategory = index)
                .update(b -> b.setChecked(currentCategory == index)).width(categoryButtonSize).height(categoryButtonSize)
                .tooltip(getCategoryTooltip(index)).get();
            categoryButton.resizeImage(categoryButtonSize * 0.8f);

            if(!mobile){
                categoryButton.clicked(KeyCode.mouseRight, () -> showEditImageDialog("category" + index + "image"));
            }else{
                categoryButton.clicked(() -> {
                    if(mobileDoubleTap("category" + index + "image")){
                        showEditImageDialog("category" + index + "image");
                        setCategoryNameDialog.show();
                    }
                });
            }
        }

        wrapped.row();
        var categoryLabel = wrapped.labelWrap(getCategoryLabelText()).width(categoryButtonSize * (float)columns).padTop(6f).padBottom(6f).get();
        categoryLabel.setAlignment(Align.center);
        if(!mobile) categoryLabel.clicked(KeyCode.mouseRight, () -> setCategoryNameDialog.show());

        wrapped.row();
        Table schematicButtonsTable = wrapped.table().get();
        for(int i = 0; i < rows; i++){
            int row = i;
            for(int j = 0; j < columns; j++){
                int column = j;
                Schematic schematic = findSchematic(currentCategory, column, row);
                String schematicImageKey = getSchematicString(currentCategory, column, row) + "image";
                Seq<String> iconNames = getImageNames(schematicImageKey);

                //built manually (new Button + Table.add), not the overloaded button(Cons<Button>,
                //Style,Runnable) convenience - see class javadoc, the source needed this to dodge a
                //Rhino overload-ambiguity that doesn't exist for the Java compiler; kept anyway since
                //it's already proven simple and correct.
                Button btn = new Button(Styles.defaulti);
                btn.clicked(() -> {
                    if(schematic != null) mindustry.Vars.control.input.useSchematic(schematic);
                });

                try{
                    buildSchematicButtonContent(btn, iconNames, schematicButtonSize);
                }catch(Throwable t){
                    Log.err("[eui] schematics-table: buildSchematicButtonContent failed at " + schematicImageKey + ", falling back to single icon", t);
                    btn.clearChildren();
                    Drawable fallback = !iconNames.isEmpty() ? Icons.getIconDrawable(iconNames.first()) : null;
                    btn.image(fallback != null ? fallback : defaultSchematicImage()).size(schematicButtonSize * 0.6f);
                }

                var cellButton = schematicButtonsTable.add(btn).update(b -> {
                    b.setDisabled(false);
                    b.color.set(cellHighlightColor(currentCategory, column, row, schematic));
                }).width(schematicButtonSize).height(schematicButtonSize).pad(1f).tooltip(getSchematicTooltip(schematic)).get();

                cellButton.hovered(() -> hovered = schematic);
                if(!mobile){
                    cellButton.clicked(KeyCode.mouseRight, () -> showEditSchematicButtonDialog(currentCategory, column, row));
                }else{
                    String tapKey = getSchematicString(currentCategory, column, row);
                    cellButton.clicked(() -> {
                        if(mobileDoubleTap(tapKey)) showEditSchematicButtonDialog(currentCategory, column, row);
                    });
                }

                cellButtons.add(new CellButton(btn, currentCategory, column, row));
                attachCellDrag(btn, currentCategory, column, row);
            }
            schematicButtonsTable.row();
        }

        contentTable.pack();
        built = true;
    }

    void clearTable(){
        if(!built || contentTable == null) return;

        ui.hudGroup.removeChild(contentTable);
        contentTable = null;
        built = false;
        cellButtons = new Seq<>();
        dragSource = null;
        dragHoverTarget = null;
    }

    // ---------------------------------------------------------------- drag & drop

    /**
     * Only one category is ever built/on-screen at a time, so cross-category dragging is unreachable
     * here by construction (matches the source - not requested, not needed). Cell repainting during a
     * drag happens through the SAME per-frame {@code .update(...)} callback that already paints the
     * affordability color in {@link #buildTable} ({@link #cellHighlightColor}), rather than painting
     * directly from this listener - painting here too would just get overwritten the very next frame by
     * that update callback anyway.
     */
    void attachCellDrag(Button btn, int category, int column, int row){
        boolean[] dragging = {false};
        float[] start = {0, 0};
        float DRAG_SLOP = 10f;

        btn.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(button != KeyCode.mouseLeft) return false;
                dragging[0] = false;
                start[0] = x;
                start[1] = y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer){
                try{
                    if(!dragging[0] && (Math.abs(x - start[0]) > DRAG_SLOP || Math.abs(y - start[1]) > DRAG_SLOP)){
                        dragging[0] = true;
                        dragSource = new CellRef(category, column, row);
                    }
                    if(!dragging[0]) return;

                    var hit = Core.scene.hit(event.stageX, event.stageY, true);
                    CellButton target = null;
                    if(hit != null){
                        for(CellButton c : cellButtons){
                            if(c.btn == hit || hit.isDescendantOf(c.btn)){ target = c; break; }
                        }
                    }
                    dragHoverTarget = (target != null && !target.btn.equals(btn) && !(target.category == category && target.column == column && target.row == row))
                        ? new CellRef(target.category, target.column, target.row) : null;
                }catch(Throwable t){
                    Log.err("[eui] schematics-table cell drag failed", t);
                }
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                try{
                    if(dragging[0] && dragHoverTarget != null){
                        swapSchematicCells(category, column, row, dragHoverTarget.column, dragHoverTarget.row);
                        rebuildTable();
                        return;
                    }
                }catch(Throwable t){
                    Log.err("[eui] schematics-table cell drop failed", t);
                }
                dragging[0] = false;
                dragSource = null;
                dragHoverTarget = null;
            }
        });
    }

    /** Swaps a cell's binding (name + icon list) with another in the same category; an empty side stays empty at its new spot. */
    void swapSchematicCells(int category, int columnA, int rowA, int columnB, int rowB){
        String keyA = getSchematicString(category, columnA, rowA);
        String keyB = getSchematicString(category, columnB, rowB);

        String nameA = Core.settings.getString(keyA, "");
        String imageA = Core.settings.getString(keyA + "image", "");
        String nameB = Core.settings.getString(keyB, "");
        String imageB = Core.settings.getString(keyB + "image", "");

        setOrRemoveSetting(keyA, nameB);
        setOrRemoveSetting(keyA + "image", imageB);
        setOrRemoveSetting(keyB, nameA);
        setOrRemoveSetting(keyB + "image", imageA);
    }

    static void setOrRemoveSetting(String key, String value){
        if(value != null && !value.isEmpty()) Core.settings.put(key, value); else Core.settings.remove(key);
    }

    Color cellHighlightColor(int category, int column, int row, Schematic schematic){
        if(dragSource != null && dragSource.is(category, column, row)) return Color.gray;
        if(dragHoverTarget != null && dragHoverTarget.is(category, column, row)) return Pal.accent;
        return schematicAffordabilityColor(schematic);
    }

    // ---------------------------------------------------------------- preview panel

    void rebuildPreviewTable(){
        previewTable.clearChildren();

        var requirements = hovered.requirements();
        float powerConsumption = hovered.powerConsumption() * 60;
        float powerProduction = hovered.powerProduction() * 60;
        var core = player.core();

        previewTable.add(new SchematicImage(hovered)).maxSize(800f);
        previewTable.row();

        previewTable.table(null, requirementsTable -> {
            int[] i = {0};
            requirements.each((item, amount) -> {
                requirementsTable.image(item.uiIcon).left();
                requirementsTable.label(() -> {
                    if(core == null || state.rules.infiniteResources || core.items.has(item, amount)) return "[lightgray]" + amount;
                    return (core.items.has(item, amount) ? "[lightgray]" : "[scarlet]") + Math.min(core.items.get(item), amount) + "[lightgray]/" + amount;
                }).padLeft(2f).left().padRight(4f);

                if(++i[0] % 4 == 0) requirementsTable.row();
            });
        });

        previewTable.row();

        if(powerConsumption != 0 || powerProduction != 0){
            previewTable.table(null, powerTable -> {
                if(powerProduction != 0){
                    powerTable.image(Icon.powerSmall).color(Pal.powerLight).padRight(3f);
                    powerTable.add("+" + arc.util.Strings.autoFixed(powerProduction, 2)).color(Pal.powerLight).left();
                    if(powerConsumption != 0) powerTable.add().width(15f);
                }
                if(powerConsumption != 0){
                    powerTable.image(Icon.powerSmall).color(Pal.remove).padRight(3f);
                    powerTable.add("-" + arc.util.Strings.autoFixed(powerConsumption, 2)).color(Pal.remove).left();
                }
            });
        }
    }

    boolean previewTableVisibility(){
        return Core.settings.getBool("eui-ShowSchematicsPreview", true) && contentTable != null && contentTable.visible && hovered != null;
    }

    // ---------------------------------------------------------------- misc lookups

    String getCategoryTooltip(int categoryId){
        return Core.settings.getString("category" + categoryId + "name", Core.bundle.get("schematics-table.default-cathegory-tooltip"));
    }

    String getCategoryLabelText(){
        String defaultText = mobile
            ? Core.bundle.get("schematics-table.default-cathegory-mobile-name")
            : Core.bundle.get("schematics-table.default-cathegory-desktop-name");
        return Core.settings.getString("category" + currentCategory + "name", defaultText);
    }

    /** Blocks.empty (EmptyFloor, "no block") - not Blocks.air (AirBlock, the placeable removal tool) - and read directly rather than through Icons/IconCategoriesConfig, to not depend on their cache init timing. */
    static Drawable defaultSchematicImage(){
        return new TextureRegionDrawable(Blocks.empty.uiIcon);
    }

    Drawable getCategoryImage(int categoryId){
        try{
            String imageName = Core.settings.getString("category" + categoryId + "image", "");
            if(imageName.isEmpty()) return defaultSchematicImage();

            Drawable drawable = Icons.getIconDrawable(imageName);
            return drawable != null ? drawable : defaultSchematicImage();
        }catch(Throwable t){
            return defaultSchematicImage();
        }
    }

    /** Icon-name list for a "...image" setting - comma-separated (a bare single name, with no comma, still parses as a one-element list, so pre-existing single-icon settings keep working). */
    static Seq<String> getImageNames(String settingKey){
        String raw = Core.settings.getString(settingKey, "");
        if(raw.isEmpty()) return new Seq<>();
        Seq<String> names = new Seq<>();
        for(String s : raw.split(MULTI_ICON_SEPARATOR)) if(!s.isEmpty()) names.add(s);
        return names;
    }

    static Seq<String> toggleImageName(String settingKey, String iconName){
        Seq<String> names = getImageNames(settingKey);
        if(names.contains(iconName)){
            names.remove(iconName);
        }else{
            if(names.size >= MAX_SCHEMATIC_ICONS){
                ui.announce(Core.bundle.format("schematics-table.dialog.change-image.max-icons-announce-text", MAX_SCHEMATIC_ICONS), 3f);
                return names;
            }
            names.add(iconName);
        }
        Core.settings.put(settingKey, String.join(MULTI_ICON_SEPARATOR, names));
        return names;
    }

    static String getSchematicString(int category, int column, int row){
        return "schematic" + category + "." + column + "." + row;
    }

    /** 0-1 icon: one centered icon, as before. 2-4: a compact 2-per-row mini-grid so they fit in a small cell. A name that no longer resolves (e.g. its mod was removed) is just skipped, not drawn as a blank. */
    static void buildSchematicButtonContent(Button btn, Seq<String> iconNames, int buttonSize){
        if(iconNames.size <= 1){
            Drawable drawable = iconNames.size == 1 ? Icons.getIconDrawable(iconNames.first()) : null;
            btn.image(drawable != null ? drawable : defaultSchematicImage()).size(buttonSize * 0.6f);
            return;
        }

        float cellSize = buttonSize * 0.42f;
        int[] col = {0};
        for(String iconName : iconNames){
            Drawable drawable = Icons.getIconDrawable(iconName);
            btn.image(drawable != null ? drawable : defaultSchematicImage()).size(cellSize).pad(1f);
            if(++col[0] % 2 == 0) btn.row();
        }
    }

    /**
     * Green ({@link Pal#heal}) when the core covers every requirement; below that, a gradient from red
     * ({@link Pal#remove}, ~nothing available) to orange (almost affordable) by the worst-covered
     * requirement's ratio. White when it can't be judged (no schematic/core, or infinite resources).
     */
    private static final Color affordabilityColor = new Color();

    static Color schematicAffordabilityColor(Schematic schematic){
        if(schematic == null || state.rules.infiniteResources) return Color.white;

        var core = player.core();
        if(core == null) return Color.white;

        float[] ratio = {1};
        schematic.requirements().each((item, amount) -> {
            if(amount > 0){
                float r = Math.min(1, (float)core.items.get(item) / amount);
                if(r < ratio[0]) ratio[0] = r;
            }
        });

        if(ratio[0] >= 1) return Pal.heal;
        return affordabilityColor.set(Pal.remove).lerp(Color.orange, ratio[0]);
    }

    String getSchematicTooltip(Schematic schematic){
        return schematic != null
            ? Core.bundle.get("schematics-table.use-schematic") + " " + schematic.name()
            : Core.bundle.get("schematics-table.default-cathegory-desktop-name");
    }

    static Schematic findSchematic(int category, int column, int row){
        String name = Core.settings.getString(getSchematicString(category, column, row), null);
        if(name == null) return null;
        for(Schematic s : schematics.all()) if(s.name().equals(name)) return s;
        return null;
    }

    boolean mobileDoubleTap(String name){
        long now = System.currentTimeMillis();
        if(name.equals(lastTapped) && now - lastTapTimeMs < 250){
            return true;
        }else{
            lastTapped = name;
            lastTapTimeMs = now;
            return false;
        }
    }

    // ---------------------------------------------------------------- external API (used by schematics import/export)

    /** Rebuild after an import replaces/adds schematics, so the table picks up the change immediately. */
    public void rebuildTableIfBuilt(){
        try{
            if(built && contentTable != null) rebuildTable();
        }catch(Throwable ignored){
        }
    }
}
