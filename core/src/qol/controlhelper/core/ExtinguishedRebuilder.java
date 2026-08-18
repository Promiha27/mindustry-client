package qol.controlhelper.core;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.entities.Fires;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.input.Placement;
import mindustry.ui.Fonts;
import mindustry.world.Tile;

import java.util.function.BooleanSupplier;

import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

/** Hold the rebuild-extinguished key and drag a box: any of your buildings that catch fire and burn down inside it get automatically requeued once the fire clears. */
public class ExtinguishedRebuilder{
    public static final KeyBind rebuildExtinguished = KeyBind.add("control-helper-rebuild-extinguished", KeyCode.o, "control-helper");

    public int firstX, firstY, secondX, secondY;
    public boolean selection = false;
    public final Color col1 = Color.valueOf("#ed8870");
    public final Color col2 = Color.valueOf("#e76243");
    public Seq<Selection> selections = new Seq<>();

    final BooleanSupplier masterEnabled;

    public ExtinguishedRebuilder(BooleanSupplier masterEnabled){
        this.masterEnabled = masterEnabled;
    }

    public void Init(){
        Events.run(EventType.Trigger.drawOver, () -> {
            if(masterEnabled.getAsBoolean() && selection) DrawSelection();
        });
        Events.run(EventType.Trigger.update, () -> {
            if(!masterEnabled.getAsBoolean()){
                selection = false;
                selections.clear();
                return;
            }
            if(!state.isGame() || control.input.commandMode){
                selection = false;
                selections.clear();
                return;
            }

            if(Core.input.keyDown(rebuildExtinguished)){
                secondX = TileX(Core.input.mouseX());
                secondY = TileY(Core.input.mouseY());
                if(!selection){
                    firstX = secondX;
                    firstY = secondY;
                }
                selection = true;
            }
            if(Core.input.keyRelease(rebuildExtinguished)){
                secondX = TileX(Core.input.mouseX());
                secondY = TileY(Core.input.mouseY());
                selections.add(new Selection(firstX, firstY, secondX, secondY));
                selection = false;
            }
            UpdateSelections();
        });
    }

    public void UpdateSelections(){
        for(Selection sel : selections) sel.Update();
        selections.remove(sel -> sel.finished);
    }

    public int TileX(float cursorX){
        Vec2 vec = Core.input.mouseWorld(cursorX, 0f);
        if(control.input.selectedBlock()) vec.sub(control.input.block.offset, control.input.block.offset);
        return World.toTile(vec.x);
    }

    public int TileY(float cursorY){
        Vec2 vec = Core.input.mouseWorld(0f, cursorY);
        if(control.input.selectedBlock()) vec.sub(control.input.block.offset, control.input.block.offset);
        return World.toTile(vec.y);
    }

    public void DrawSelection(){
        int x1 = Mathf.round(firstX), x2 = Mathf.round(secondX);
        int y1 = Mathf.round(firstY), y2 = Mathf.round(secondY);
        Placement.NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, x1, y1, x2, y2, false, Integer.MAX_VALUE, 1f);
        Color col = Draw.getColor();
        Lines.stroke(2f);
        Draw.color(col2);
        Lines.rect(result.x, result.y - 1f, result.x2 - result.x, result.y2 - result.y);
        Draw.color(col1);
        Lines.rect(result.x, result.y, result.x2 - result.x, result.y2 - result.y);
        Lines.stroke(1f);
        Draw.color(col);

        Font font = Fonts.outline;
        font.setColor(col2);
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        float z = Draw.z();
        Draw.z(210f);
        font.getData().setScale(1f / renderer.getDisplayScale());
        font.draw((int)((result.x2 - result.x) / 8f) + "x" + (int)((result.y2 - result.y) / 8f), result.x2, result.y);
        font.setColor(Color.white);
        font.getData().setScale(1f);
        font.setUseIntegerPositions(ints);
        Draw.z(z);
    }

    public static class Selection{
        public int x1, y1, x2, y2;
        public Seq<BuildPlan> brokenBlocks = new Seq<>();
        public boolean finished = false;

        public Selection(int x1, int y1, int x2, int y2){
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        public void Update(){
            Seq<Tile> fires = GetFires();
            if(fires != null && fires.size != 0){
                for(Building build : GetBuildingsOnTiles(fires)){
                    if(IsBreakPlannedOnPos(new Vec2(build.x, build.y))) continue;
                    brokenBlocks.add(new BuildPlan(build.tileX(), build.tileY(), build.rotation, build.block, build.config()));
                    control.input.tryBreakBlock(build.tileX(), build.tileY());
                }
            }else{
                RebuildBrokenBlocks();
                finished = true;
            }
        }

        public Seq<Tile> GetFires(){
            Placement.NormalizeResult result = Placement.normalizeArea(x1, y1, x2, y2, 0, false, Integer.MAX_VALUE);
            Seq<Tile> fires = new Seq<>();
            for(int x = 0; x <= Math.abs(result.x2 - result.x); x++){
                for(int y = 0; y <= Math.abs(result.y2 - result.y); y++){
                    int wx = x1 + x * Mathf.sign(x2 - x1);
                    int wy = y1 + y * Mathf.sign(y2 - y1);
                    if(!Fires.has(wx, wy)) continue;
                    Tile tile = world.tile(wx, wy);
                    if(tile != null) fires.add(tile);
                }
            }
            return fires;
        }

        public void RebuildBrokenBlocks(){
            if(player == null || player.unit() == null) return;
            for(BuildPlan plan : brokenBlocks){
                boolean found = false;
                for(BuildPlan p : player.unit().plans){
                    if(!p.breaking || p.build() == null || p.tile().x != plan.x || p.tile().y != plan.y || p.block != plan.block) continue;
                    found = true;
                    player.unit().plans.remove(p);
                    break;
                }
                if(found) continue;
                player.unit().addBuild(plan);
            }
        }

        public boolean IsBreakPlannedOnPos(Vec2 pos){
            if(player == null || player.unit() == null) return false;
            for(BuildPlan plan : player.unit().plans){
                if(!plan.breaking || plan.build() == null || !plan.build().within((Position)pos, 0.1f)) continue;
                return true;
            }
            return false;
        }

        public Seq<Building> GetBuildingsOnTiles(Seq<Tile> tiles){
            Seq<Building> buildings = new Seq<>();
            for(Tile tile : tiles){
                if(tile.build != null) buildings.add(tile.build);
            }
            return buildings;
        }
    }
}
