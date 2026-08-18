package eui.input;

import arc.Core;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import eui.ai.AdjacentPosition;
import eui.ai.ConveyorPathfind;
import eui.draw.BuildPlanDraw;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Unit;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.Vars.player;

/**
 * Drag from an existing conveyor/junction/underflow-gate toward the mouse to auto-route a line of the
 * same block type there via {@link ConveyorPathfind} (bridging over obstacles automatically), previewed
 * live and queued as build plans on release. Gated by the "eui-DragPathfind" setting. Ported from
 * input/conveyor.js.
 */
public class ConveyorDrag implements Drag.Listener{
    private boolean listening = false;
    private Seq<BuildPlan> buildPlans = new Seq<>();
    private Tile lastStartTile, lastMouseTile;

    interface Pathfinder{
        Seq<BuildPlan> find(Tile source, Tile target, Tile lastRotationTo, Block block);
    }

    public ConveyorDrag(){
        Drag.addListener(this);
    }

    /** Called once per frame from {@link eui.EUIMod}'s draw hook. */
    public void draw(){
        if(!buildPlans.isEmpty()) BuildPlanDraw.draw(buildPlans);
    }

    static Pathfinder selector(Block block){
        if(block == Blocks.conveyor || block == Blocks.titaniumConveyor) return ConveyorPathfind::conveyorPathfind;
        if(block == Blocks.junction) return ConveyorPathfind::junctionPathfind;
        if(block == Blocks.underflowGate) return (source, target, lastRotationTo, b) -> ConveyorPathfind.gatePathfind(source, target, lastRotationTo);
        return null;
    }

    @Override
    public void dragStarted(Vec2 startPos, Tile startTile){
        if(startTile != null && selector(startTile.block()) != null && !Busy.isBusy() && !listening){
            if(Core.settings.getBool("eui-DragPathfind", false)) listening = true;
        }
    }

    @Override
    public void dragged(Vec2 startPos, Tile startTile, Vec2 pos, Tile mouseTile){
        if(!listening) return;

        if(Core.input.keyTap(KeyCode.mouseRight)){
            buildPlans.clear();
            endListen();
            return;
        }
        if(startTile == lastStartTile && mouseTile == lastMouseTile) return;
        if(startTile == mouseTile){
            buildPlans.clear();
            return;
        }

        lastStartTile = startTile;
        lastMouseTile = mouseTile;
        Tile destination = AdjacentPosition.find(mouseTile, startTile, Blocks.copperWall);
        if(destination == null) return;

        Block startBlock = startTile.block();
        Pathfinder pathfinder = selector(startBlock);
        if(pathfinder != null) buildPlans = pathfinder.find(startTile, destination, mouseTile, startBlock);
    }

    @Override
    public void dragEnded(Vec2 startPos, Tile startTile, Vec2 pos, Tile mouseTile){
        if(listening) endListen();
    }

    void endListen(){
        if(!buildPlans.isEmpty()){
            Unit unit = player.unit();
            if(unit != null){
                for(BuildPlan plan : buildPlans) unit.addBuild(plan);
            }
            buildPlans.clear();
        }
        listening = false;
        lastStartTile = null;
        lastMouseTile = null;
    }
}
