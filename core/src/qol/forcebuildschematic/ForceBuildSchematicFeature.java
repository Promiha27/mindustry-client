package qol.forcebuildschematic;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.input.KeyCode;
import arc.struct.Seq;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.Block;
import mindustry.world.Tile;
import qol.core.Feature;
import qol.core.QueueCoordination;
import qol.ui.QolWindow;

import java.util.HashSet;
import java.util.Set;

import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Ported from the standalone Force Build Schematic script mod (JS). Hold Ctrl and click while a
 * schematic is previewed at the cursor: every one of your own buildings under its footprint (plus the
 * whole bounding box, including empty tiles) gets queued for demolition first, then - once the site is
 * actually clear - the schematic's plans get queued for real. A plain click still places the schematic
 * normally, exactly like vanilla.
 */
public class ForceBuildSchematicFeature implements Feature{
    /**
     * Empirically-tuned pixel nudge for the Ctrl-held highlight only (not the actual demolish/build
     * logic, which works in tile coordinates and needs no correction) - carried over from the original
     * script mod, which found the naive tile-center formula rendered a few pixels off.
     */
    static final float HIGHLIGHT_OFFSET_X = -4f, HIGHLIGHT_OFFSET_Y = -4f;

    boolean mouseWasDown = false;
    Pending pending;

    @Override
    public String id(){
        return "force-build-schematic";
    }

    @Override
    public String titleKey(){
        return "qol.feature.force-build-schematic.title";
    }

    @Override
    public boolean hasWindow(){
        return false;
    }

    @Override
    public QolWindow window(){
        return null;
    }

    @Override
    public void init(){
        Events.run(Trigger.draw, this::drawHighlight);
        Events.run(Trigger.update, this::update);
    }

    @Override
    public void buildSettings(SettingsTable table){
    }

    boolean ctrlDown(){
        return Core.input.keyDown(KeyCode.controlLeft) || Core.input.keyDown(KeyCode.controlRight);
    }

    void drawHighlight(){
        if(!isEnabled() || state.isMenu() || player == null || player.team() == null || !ctrlDown()) return;

        Seq<BuildPlan> selectPlans = control.input.selectPlans;
        if(selectPlans.isEmpty()) return;

        Draw.color(Color.purple, 0.4f);
        for(BuildPlan p : selectPlans){
            int size = p.block != null ? p.block.size : 1;
            for(Tile t : footprintTiles(p.x, p.y, size)){
                float wx = t.x * tilesize + tilesize / 2f + HIGHLIGHT_OFFSET_X;
                float wy = t.y * tilesize + tilesize / 2f + HIGHLIGHT_OFFSET_Y;
                Fill.square(wx, wy, tilesize / 2f);
            }
        }
        Draw.reset();
    }

    void update(){
        //kept in sync unconditionally, ahead of every early return below, so it can never get stuck
        //true if this feature is disabled or the player dies mid-sequence
        QueueCoordination.forceBuildPending = pending != null;

        if(!isEnabled() || state.isMenu() || player == null || player.team() == null || player.dead()) return;

        Unit unit = player.unit();

        if(pending != null){
            if(unit == null){ pending = null; return; }

            boolean stillBlocked = false;
            for(Tile t : pending.tiles){
                Building build = t.build;
                if(build != null && build.team == player.team()){
                    stillBlocked = true;
                    break;
                }
            }

            if(!stillBlocked){
                for(BuildPlan p : pending.plans){
                    unit.addBuild(new BuildPlan(p.x, p.y, p.rotation, p.block, p.config));
                }
                pending = null;
            }
            return;
        }

        boolean ctrlDown = ctrlDown();
        boolean mouseDown = Core.input.keyDown(KeyCode.mouseLeft);
        boolean mouseJustPressed = mouseDown && !mouseWasDown;
        mouseWasDown = mouseDown;

        if(!ctrlDown || !mouseJustPressed) return;

        Seq<BuildPlan> selectPlans = control.input.selectPlans;
        if(selectPlans.isEmpty() || unit == null) return;

        Seq<BuildPlan> previewPlans = new Seq<>();
        for(BuildPlan p : selectPlans){
            previewPlans.add(new BuildPlan(p.x, p.y, p.rotation, p.block, p.config));
        }

        Set<Long> seen = new HashSet<>();
        Seq<Tile> demolishTiles = new Seq<>();

        for(BuildPlan p : previewPlans){
            int size = p.block != null ? p.block.size : 1;
            for(Tile t : footprintTiles(p.x, p.y, size)) tryDemolish(t.x, t.y, unit, seen, demolishTiles);
        }

        int minX = previewPlans.get(0).x, maxX = previewPlans.get(0).x;
        int minY = previewPlans.get(0).y, maxY = previewPlans.get(0).y;
        for(BuildPlan p : previewPlans){
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        for(int wx = minX; wx <= maxX; wx++){
            for(int wy = minY; wy <= maxY; wy++) tryDemolish(wx, wy, unit, seen, demolishTiles);
        }

        selectPlans.clear();

        if(demolishTiles.isEmpty()){
            for(BuildPlan p : previewPlans) unit.addBuild(new BuildPlan(p.x, p.y, p.rotation, p.block, p.config));
            return;
        }

        pending = new Pending(previewPlans, demolishTiles);
    }

    void tryDemolish(int wx, int wy, Unit unit, Set<Long> seen, Seq<Tile> demolishTiles){
        Tile tile = world.tile(wx, wy);
        if(tile == null) return;

        Building build = tile.build;
        if(build == null || build.team != player.team()) return;

        long key = (long)build.tile.x << 32 | (build.tile.y & 0xFFFFFFFFL);
        if(!seen.add(key)) return;

        demolishTiles.add(build.tile);

        BuildPlan breakPlan = new BuildPlan(build.tile.x, build.tile.y);
        breakPlan.breaking = true;
        unit.addBuild(breakPlan);
    }

    static Seq<Tile> footprintTiles(int px, int py, int size){
        Seq<Tile> out = new Seq<>();
        int off = (size - 1) / 2;
        for(int ddx = 0; ddx < size; ddx++){
            for(int ddy = 0; ddy < size; ddy++){
                Tile t = world.tile(px - off + ddx, py - off + ddy);
                if(t != null) out.add(t);
            }
        }
        return out;
    }

    static class Pending{
        final Seq<BuildPlan> plans;
        final Seq<Tile> tiles;

        Pending(Seq<BuildPlan> plans, Seq<Tile> tiles){
            this.plans = plans;
            this.tiles = tiles;
        }
    }
}
