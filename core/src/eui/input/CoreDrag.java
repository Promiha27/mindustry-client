package eui.input;

import arc.Core;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import eui.ai.AdjacentPosition;
import eui.draw.BuildPlanDraw;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Unit;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.player;

/**
 * Drag from a core toward the mouse to queue a single vault placement at the nearest free tile past the
 * core's footprint in that direction - a quick way to expand core storage without opening the block menu.
 * Gated by the "eui-DragBlock" setting. Ported from input/core-drag.js.
 */
public class CoreDrag implements Drag.Listener{
    private static final Block TARGET_BLOCK = Blocks.vault;

    private boolean listening = false;
    private BuildPlan buildPlan;

    public CoreDrag(){
        Drag.addListener(this);
    }

    /** Called once per frame from {@link eui.EUIMod}'s draw hook. */
    public void draw(){
        if(buildPlan != null) BuildPlanDraw.drawOne(buildPlan);
    }

    @Override
    public void dragStarted(Vec2 startPos, Tile startTile){
        if(startTile != null && startTile.block() instanceof CoreBlock && !Busy.isBusy() && !listening){
            if(Core.settings.getBool("eui-DragBlock", false)) listening = true;
        }
    }

    @Override
    public void dragged(Vec2 startPos, Tile startTile, Vec2 pos, Tile mouseTile){
        if(!listening) return;

        if(Core.input.keyTap(KeyCode.mouseRight)){
            buildPlan = null;
            endListen();
            return;
        }

        if(mouseTile.block() instanceof CoreBlock){
            buildPlan = null;
            return;
        }

        Tile position = AdjacentPosition.find(startTile, mouseTile, TARGET_BLOCK);
        if(position == null){
            buildPlan = null;
            return;
        }

        buildPlan = new BuildPlan(position.x, position.y, 0, TARGET_BLOCK);
    }

    @Override
    public void dragEnded(Vec2 startPos, Tile startTile, Vec2 pos, Tile mouseTile){
        if(listening) endListen();
    }

    void endListen(){
        if(buildPlan != null){
            Unit unit = player.unit();
            if(unit != null) unit.addBuild(buildPlan);
            buildPlan = null;
        }
        listening = false;
    }
}
