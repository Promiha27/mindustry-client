package sectorstats;

import arc.Core;
import arc.func.Cons2;
import arc.func.Intc;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.Texture.TextureFilter;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.math.geom.Vec2;
import arc.util.Log;
import arc.util.Strings;
import mindustry.ctype.UnlockableContent;
import mindustry.game.SectorInfo;
import mindustry.gen.Icon;
import mindustry.io.MapIO;
import mindustry.type.Sector;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.PlanetDialog;
import mindustry.world.WorldParams;

import static mindustry.Vars.logic;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * Feature 2: no-landing preview for a single sector - an info panel (resources/threat/waves, straight from
 * {@link Sector#info}, no generation needed) plus an asynchronously-rendered terrain image, reached via an
 * eye button next to the vanilla "Launch" label on the campaign screen's sector panel. Ported from
 * {@code buildQuickPreviewDialog()}, {@code renderSectorPreview()}, {@code generateSectorPreview()},
 * {@code attachEyeButtonToSectorPanel()} and {@code findLabelContaining()} in the source's
 * {@code scripts/main.js}.
 * <p>
 * Two deliberate deviations from the source, found while reading this repo's actual engine sources (the
 * source's author was working blind through a Rhino mod, without the engine's own code to check against):
 * <ul>
 * <li>Terrain rendering calls {@link MapIO#generatePreview}, the same helper the engine itself already
 * uses for map-preview pixmaps, instead of hand-rolling the block/floor/overlay/team {@code colorFor} pixel
 * loop the source had to debug three separate times (black tiles, grey enemy bases, then the zoom
 * cell-invalidation bug - see project memory) - it does the exact same {@code colorFor(tile.block(),
 * tile.floor(), tile.overlay(), tile.team())} call, correctly ordered, but also prefers each block's own
 * {@code minimapColor()} where available, which is a strictly better result for free.</li>
 * <li>{@code WorldParams.saveInfo = false} is set for both this preview and {@link LiveSectorPreview} -
 * confirmed by reading {@code World.loadSector()}/{@code setSectorRules()} in this repo, {@code saveInfo}
 * defaults to {@code true} and would otherwise overwrite this sector's real {@code SectorInfo} (e.g.
 * {@code resources}) with data generated purely for a look-only preview, even though the map load itself
 * is rolled back right after. This is exactly the flag {@code SectorGenerateDialog.apply()} - the actual
 * in-game map editor's own "generate this sector to preview it" code path the source's comments say they
 * were mirroring - sets for the same reason.</li>
 * </ul>
 * <p>
 * The eye button's watcher reads {@link PlanetDialog#selected} directly. The source had to find this via
 * reflection (scanning every {@code Sector}-typed field of {@code PlanetDialog} and taking the first
 * non-null one) because a Rhino mod has no compile-time access to private/package fields - here the field
 * is `public` and, per {@code PlanetDialog.updateSelected()} in this repo, is exactly the one the sector
 * panel itself is built from (not {@code hovered} or {@code launchSector}), which the reflection heuristic
 * could only approximate.
 */
public class SectorPreview{
    private final BaseDialog dialog;
    private final PreviewState state = new PreviewState();
    private Sector currentSector;

    public SectorPreview(){
        dialog = new BaseDialog(Core.bundle.get("campaignutils.sector-preview-title"));
        dialog.addCloseButton();
        dialog.hidden(state::dispose);

        dialog.buttons.button(Core.bundle.get("campaignutils.live-preview"), () -> {
            if(currentSector == null) return;
            Sector sector = currentSector;
            dialog.hide();
            LiveSectorPreview.enter(sector);
        }).width(420).height(64);
    }

    private void showFor(Sector sector){
        currentSector = sector;
        renderSectorPreview(dialog.cont, sector);
    }

    /**
     * Adds the eye button as a raw child of {@code Vars.ui.planet.sectorTop} (not a normal {@code Table}
     * cell - its position is set by hand every frame) and a watcher {@code Table} in {@code Core.scene}
     * that keeps it positioned and re-parents it whenever it gets orphaned. {@code sectorTop.clear()} runs
     * on every sector selection change ({@code PlanetDialog.updateSelected()}), which drops the button's
     * {@code parent} back to null just like {@code Group.clearChildren()} - so re-checking
     * {@code eyeBtn.parent != top} every frame, rather than adding the button once, is required here too,
     * not just a leftover caution from the source.
     */
    public void attachEyeButton(){
        Table eyeBtn = new Table();
        eyeBtn.button(Icon.eyeSmall, () -> {
            if(currentSector == null) return;
            showFor(currentSector);
            dialog.show();
        }).size(40);
        eyeBtn.pack();
        eyeBtn.visible = false;

        Table watcher = new Table();
        Core.scene.add(watcher);

        String[] lastPosError = {null};

        watcher.update(() -> {
            if(!Core.settings.getBool("campaignutils-show-eye-button", true)){
                eyeBtn.visible = false;
                return;
            }

            PlanetDialog planetDialog = ui.planet;
            Sector sector = planetDialog.selected;

            //hasBase() means the sector is already captured. Both preview paths load through
            //World.loadSector(), which regenerates the sector fresh from the planet's generator rather
            //than the player's actual save - showing that for a captured sector would be a misleadingly
            //empty/fresh map instead of the real base, so the button just doesn't appear there.
            if(sector == null || sector.hasBase()){
                eyeBtn.visible = false;
                return;
            }

            try{
                Table top = planetDialog.sectorTop;

                if(eyeBtn.parent != top){
                    top.addChild(eyeBtn);
                }

                //"@sectors.launch" is the real vanilla bundle key PlanetDialog itself formats this button's
                //text from (its launch-button text ternary's default case) - looking it up keeps this
                //working regardless of the game client's own language, instead of hardcoding one locale's
                //button text (a real bug the source shipped and fixed once already - see project memory).
                Element launchLabel = findLabelContaining(top, Core.bundle.get("sectors.launch"));
                if(launchLabel != null){
                    Vec2 pos = launchLabel.localToAscendantCoordinates(top, new Vec2(0, 0));
                    eyeBtn.setPosition(pos.x - eyeBtn.getWidth() - 8, pos.y);
                }else{
                    eyeBtn.setPosition(0, -eyeBtn.getHeight() - 8);
                }
                eyeBtn.visible = true;
                lastPosError[0] = null;
            }catch(Throwable t){
                String msg = Strings.neatError(t);
                if(!msg.equals(lastPosError[0])){
                    lastPosError[0] = msg;
                    Log.err("[campaign-utils] failed to position sector-preview eye button", t);
                }
                eyeBtn.visible = false;
            }
        });
    }

    /**
     * Depth-first search for the first descendant whose displayed text contains {@code text}. The source
     * duck-typed this (any element with a callable {@code getText()}) since Rhino has no compile-time
     * types to check; in real Java the vanilla launch button is a {@code TextButton} (from
     * {@code Table.button(String, Drawable, Runnable)}), so checking that plus plain {@code Label} covers
     * it without reflection.
     */
    private static Element findLabelContaining(Group group, String text){
        for(Element child : group.getChildren()){
            CharSequence childText = child instanceof TextButton button ? button.getText()
                : child instanceof Label label ? label.getText()
                : null;
            if(childText != null && childText.toString().contains(text)) return child;

            if(child instanceof Group childGroup){
                Element found = findLabelContaining(childGroup, text);
                if(found != null) return found;
            }
        }
        return null;
    }

    private static String sectorDisplayName(Sector sector){
        if(sector.preset != null) return sector.preset.localizedName;
        return Core.bundle.format("campaignutils.sector-name", sector.id);
    }

    /**
     * Works without landing, generates nothing - reads straight from {@link Sector#info}, the same
     * pre-scanned data the vanilla sector panel itself shows.
     */
    private static Table buildSectorStatsTable(Sector sector){
        Table t = new Table();
        SectorInfo info = sector.info;

        t.add(sectorDisplayName(sector)).left().get().setColor(Color.white);
        t.row();
        t.add(sector.hasBase() ? Core.bundle.get("campaignutils.has-base") :
            sector.hasEnemyBase() ? Core.bundle.get("campaignutils.enemy-base") :
            Core.bundle.get("campaignutils.no-base")).left().padTop(4).row();

        try{
            t.add(Core.bundle.format("campaignutils.threat", sector.displayThreat())).left().padTop(4).row();
        }catch(Throwable ignored){
            //displayThreat() might need state that isn't available in every context - not critical, just
            //skip the row (same defensive try/catch as the source).
        }

        if(info.attack && info.winWave > 0){
            t.add(Core.bundle.format("campaignutils.survive-waves", info.winWave)).left().padTop(4).row();
        }

        if(info.resources != null && info.resources.size > 0){
            t.add(Core.bundle.get("campaignutils.resources-on-map")).left().padTop(8).row();
            Table row = new Table();
            for(UnlockableContent res : info.resources){
                try{
                    row.image(res.uiIcon).size(28).padRight(4);
                }catch(Throwable ignored){
                }
            }
            t.add(row).left().row();
        }

        return t;
    }

    /**
     * Draws the info panel into {@code container} immediately and kicks off the async terrain render.
     * {@link PreviewState} survives across repeated calls on the same dialog so that picking a different
     * sector while a previous render is still in flight can't clobber the new result with a stale one, and
     * so the old texture always gets disposed instead of leaking.
     */
    private void renderSectorPreview(Table container, Sector sector){
        container.clearChildren();
        state.dispose();
        int myToken = state.genToken;

        container.add(buildSectorStatsTable(sector)).left().row();

        Table imageHolder = new Table();
        imageHolder.add(Core.bundle.get("campaignutils.generating-terrain")).pad(10);
        container.add(imageHolder).padTop(10).row();

        try{
            generateSectorPreview(sector, (texture, error) -> {
                if(myToken != state.genToken){
                    //a different sector was picked meanwhile - this result isn't needed anymore
                    if(texture != null){
                        try{
                            texture.dispose();
                        }catch(Throwable ignored){
                        }
                    }
                    return;
                }

                imageHolder.clearChildren();
                if(texture != null){
                    state.currentTexture = texture;
                    buildTerrainView(imageHolder, texture);
                }else{
                    Cell<Label> errCell = imageHolder.add(Core.bundle.format("campaignutils.preview-failed", error)).width(400);
                    Label errLabel = errCell.get();
                    errLabel.setColor(Color.scarlet);
                    errLabel.setWrap(true);
                }
            });
        }catch(Throwable t){
            Log.err("[campaign-utils] failed to start sector preview generation", t);
            imageHolder.clearChildren();
            imageHolder.add(Core.bundle.get("campaignutils.preview-start-failed")).get().setColor(Color.scarlet);
        }
    }

    /**
     * Loads the sector the same way the built-in map editor's sector-preview generator does
     * ({@code SectorGenerateDialog.apply()}: {@code Logic.reset()} + {@code World.loadSector(sector,
     * params)}, {@code saveInfo = false}) - a real load into {@code Vars.world}, so every system that
     * expects a live world (pathfinding, minimap, ore indexer) sees one, instead of an isolated/incomplete
     * {@code Tiles}. Rolls the load right back with a second {@code Logic.reset()} once the pixmap is
     * captured - no mission/capture ever happens.
     */
    private static void generateSectorPreview(Sector sector, Cons2<Texture, String> onDone){
        try{
            ui.loadAnd(() -> {
                try{
                    logic.reset();

                    WorldParams params = new WorldParams();
                    params.seedOffset = sector.id;
                    params.saveInfo = false; //see class javadoc
                    world.loadSector(sector, params);

                    Pixmap pixmap = MapIO.generatePreview(world.tiles);
                    Texture texture = new Texture(pixmap);
                    pixmap.dispose();

                    logic.reset(); //roll the sector load back - nothing here was ever landed on or captured
                    onDone.get(texture, null);
                }catch(Throwable t){
                    Log.err("[campaign-utils] failed to generate sector preview", t);
                    try{
                        logic.reset();
                    }catch(Throwable ignored){
                    }
                    onDone.get(null, Strings.neatError(t));
                }
            });
        }catch(Throwable t){
            Log.err("[campaign-utils] failed to start sector preview generation via world.loadSector", t);
            onDone.get(null, Strings.neatError(t));
        }
    }

    /** Zoomable/pannable terrain image, built once the async render above finishes. */
    private static void buildTerrainView(Table imageHolder, Texture texture){
        //nearest, not linear filtering - zooming in should show crisp tile squares, not a blur.
        texture.setFilter(TextureFilter.nearest);

        TextureRegionDrawable drawable = new TextureRegionDrawable(new TextureRegion(texture));

        int baseW = texture.width;
        int baseH = texture.height;
        //starting scale: small sectors shouldn't look tiny, but a sector that's already big shouldn't be
        //stretched further than necessary either.
        int[] zoom = {Math.max(1, Math.min(6, (int)Math.floor(480f / Math.max(baseW, baseH))))};

        Table imgTable = new Table();
        Cell<Image> imgCell = imgTable.image(drawable).size(baseW * zoom[0], baseH * zoom[0]);

        //Table.pane() is built for vertical lists (like the production dialog) and doesn't seem to give
        //free two-axis panning - a manually-built ScrollPane with flick scroll and both axes enabled does.
        ScrollPane scroll = new ScrollPane(imgTable);
        scroll.setFlickScroll(true);
        scroll.setScrollingDisabled(false, false);

        Table zoomRow = new Table();
        Label zoomLabel = new Label(zoom[0] + "x");
        Intc applyZoom = newZoom -> {
            zoom[0] = Math.max(1, Math.min(8, newZoom));
            imgCell.size(baseW * zoom[0], baseH * zoom[0]);
            //Cell.size() only changes the cell's own min/maxWidth, it does NOT mark the owning Table's
            //sizeInvalid - and pack() is just setSize(getPrefWidth(), getPrefHeight()), with no invalidate
            //of its own. Table.getPrefWidth() only recomputes column widths when sizeInvalid is true,
            //otherwise it returns the old cached size - without invalidate() here, pack() below would
            //silently return the same size as the first render and the zoom buttons would do nothing
            //(confirmed against this repo's own arc-core Table/Element classes).
            imgTable.invalidate();
            imgTable.pack();
            //pack() resizes the ScrollPane's content, but the pane doesn't recompute its own scroll bounds
            //from that alone - without this, zooming back out wouldn't restore the ability to scroll fully.
            scroll.layout();
            zoomLabel.setText(zoom[0] + "x");
        };

        zoomRow.button("-", () -> applyZoom.get(zoom[0] - 1)).size(40);
        zoomRow.add(zoomLabel).width(50);
        zoomRow.button("+", () -> applyZoom.get(zoom[0] + 1)).size(40);

        imageHolder.add(zoomRow).padBottom(6).row();
        //fixed size, same reasoning as the production dialog's pane() - grow() here ends up with random
        //ScrollPane bounds instead.
        imageHolder.add(scroll).size(500, 500);
    }

    private static final class PreviewState{
        int genToken;
        Texture currentTexture;

        void dispose(){
            if(currentTexture != null){
                try{
                    currentTexture.dispose();
                }catch(Throwable ignored){
                }
                currentTexture = null;
            }
            genToken++;
        }
    }
}
