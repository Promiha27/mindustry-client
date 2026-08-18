package qol.controlhelper.core.buildingsdepowerer;

import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerNode;
import qol.controlhelper.core.requestexecutor.IRequest;
import qol.controlhelper.core.requestexecutor.RequestExecutor;

import static mindustry.Vars.player;
import static mindustry.Vars.ui;

public class BuildingsDepowerer{
    public final Seq<Block> depowerBlocks;
    public Seq<Block> nodeTypes = new Seq<>(new Block[]{Blocks.powerNode, Blocks.powerNodeLarge, Blocks.surgeTower});

    final RequestExecutor requestExecutor;

    public BuildingsDepowerer(Seq<Block> depowerBlocks, RequestExecutor requestExecutor){
        this.depowerBlocks = depowerBlocks;
        this.requestExecutor = requestExecutor;
    }

    public void DepowerBuilds(){
        int matchedBuildings = 0, queuedLinks = 0;
        for(Building building : Groups.build){
            if(building == null || building.team != player.team() || !depowerBlocks.contains(building.block)) continue;
            matchedBuildings++;
            queuedLinks += DepowerBuild(building);
        }
        ui.showInfoFade("[accent]Depower: " + matchedBuildings + " building(s), " + queuedLinks + " node link(s) queued to remove.");
    }

    public int DepowerBuild(Building building){
        int queued = 0;
        for(Building b : building.power.graph.all){
            if(!nodeTypes.contains(b.block) || !(b.block instanceof PowerNode) || !b.power.links.contains(building.pos())) continue;
            requestExecutor.AddRequest(new IRequest.TileConfig(b, building.pos()));
            queued++;
        }
        return queued;
    }

    public void PowerBuilds(){
        int matchedBuildings = 0;
        int[] queuedLinks = {0};
        for(Building building : Groups.build){
            if(building == null || building.team != player.team() || !depowerBlocks.contains(building.block)) continue;
            matchedBuildings++;
            queuedLinks[0] += PowerBuild(building);
        }
        ui.showInfoFade("[accent]Power: " + matchedBuildings + " building(s), " + queuedLinks[0] + " node link(s) queued to add.");
    }

    public int PowerBuild(Building building){
        int[] queued = {0};
        PowerNode.getNodeLinks(building.tile, building.block, building.team, other -> {
            if(!building.power.links.contains(other.pos()) && nodeTypes.contains(other.block)){
                requestExecutor.AddRequest(new IRequest.TileConfig(other, building.pos()));
                queued[0]++;
            }
        });
        return queued[0];
    }
}
