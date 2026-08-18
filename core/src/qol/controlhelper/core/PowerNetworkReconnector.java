package qol.controlhelper.core;

import arc.Core;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.world.blocks.power.PowerGraph;
import mindustry.world.blocks.power.PowerNode;
import qol.controlhelper.core.requestexecutor.IRequest;
import qol.controlhelper.core.requestexecutor.RequestExecutor;

import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/**
 * One-shot action: when a base's power grid got accidentally split into several separate networks (the
 * usual cause is a removed/destroyed node), links as many of them back together as currently possible.
 * <p>
 * Sonka's ask, after using an earlier version of this that only ever tried linking every split-off graph
 * to the single BIGGEST one: with 3+ networks split apart, two small ones sitting near EACH OTHER (but
 * both far from the big one) never got reconnected at all, even though a perfectly good link between just
 * those two was sitting right there - "просто сделай чтобы мод подключался к другим узлам, у которых нет
 * энергии" (just connect to other powerless nodes, easier to restore the network). So this now tries every
 * PAIR of currently-distinct graphs, not just (split-off, biggest) pairs - each press greedily merges
 * whatever pairs have a valid link in range; repeated presses keep consolidating (a merge this press can
 * bring a previously-unreachable node within range of yet another graph next press), same as manually
 * bridging one gap at a time in-game.
 * <p>
 * A link can only be INITIATED from a power node (only {@link PowerNode} buildings accept the config that
 * adds/removes a link), but its other end can be ANY powered building, node or not - {@link
 * PowerNode#linkValid} places no such restriction on the target. That rules out reusing {@link
 * PowerNode#getNodeLinks} (the auto-link finder {@link
 * qol.controlhelper.core.buildingsdepowerer.BuildingsDepowerer#PowerBuild} relies on) here: it only ever
 * returns OTHER POWER NODES as candidates (its {@code valid} predicate hard-requires {@code other.block
 * instanceof PowerNode}), so a split-off graph made up entirely of generators/consumers with no node of
 * its own would never turn up a candidate from either direction through it. {@link #findLink} instead
 * scans {@code linkValid} directly against every building actually on the target graph, node or not,
 * picking the closest valid pair.
 * <p>
 * Candidate buildings are read from each {@link PowerGraph}'s own {@code all} list, NOT from filtering
 * {@code Groups.build} by graph - on sonka's client fork, power node buildings turned out to be absent
 * from {@code Groups.build} entirely (confirmed by diagnostic logging: a real depower/relink action that
 * demonstrably split a graph in two was completely invisible to a {@code Groups.build}-filtered scan,
 * finding "0 power node(s)" on a graph that just proved it had at least 3), even though they're correctly
 * tracked in their own graph's {@code all} Seq the whole time - the same {@code Groups.*}-vs-authoritative-
 * list divergence class of bug as the fog-related one {@code AssistShareFeature} hit for units. {@link
 * qol.controlhelper.core.buildingsdepowerer.BuildingsDepowerer#DepowerBuild} sidesteps this the same way,
 * by iterating {@code building.power.graph.all} instead of {@code Groups.build}.
 * <p>
 * Each graph is only used in at most ONE queued link per press (tracked via {@code merged}) - capacity
 * bookkeeping ({@code power.links.size < maxNodes}) isn't re-read mid-pass, so letting an already-merged
 * graph's node get picked again this same press could queue a second link past that node's real remaining
 * capacity before the first RPC round-trips and updates it. The resulting link is queued through the same
 * {@link RequestExecutor} every other tile-config action in this window uses, instead of an unthrottled
 * direct {@code Call.tileConfig}.
 */
public class PowerNetworkReconnector{
    final RequestExecutor requestExecutor;

    public PowerNetworkReconnector(RequestExecutor requestExecutor){
        this.requestExecutor = requestExecutor;
    }

    public void Reconnect(){
        Log.info("[QoL Suite] [reconnect-button] pressed");
        if(player == null || !state.isGame()) return;

        //Groups.build is only used to DISCOVER which graphs exist and roughly size them - reliable
        //enough for that (confirmed non-node buildings show up fine), unlike enumerating a graph's own
        //node membership, which must go through PowerGraph.all instead (see class javadoc).
        Seq<PowerGraph> seenGraphs = new Seq<>();
        PowerGraph main = null;
        int mainSize = -1;

        for(Building b : Groups.build){
            if(b.team != player.team() || b.power == null || b.power.graph == null) continue;

            PowerGraph g = b.power.graph;
            if(seenGraphs.contains(g)) continue;
            seenGraphs.add(g);
            if(g.all.size > mainSize){
                mainSize = g.all.size;
                main = g;
            }
        }

        Log.info("[QoL Suite] [reconnect-button] @ distinct graph(s) found (main size @)", seenGraphs.size, mainSize);

        if(main == null || seenGraphs.size < 2){
            ui.showInfoToast(Core.bundle.get("qol.power-reconnect.none", "No disconnected power networks found"), 3f);
            return;
        }

        Seq<PowerGraph> merged = new Seq<>();
        int linked = 0;

        for(int i = 0; i < seenGraphs.size; i++){
            PowerGraph a = seenGraphs.get(i);
            if(merged.contains(a)) continue;

            for(int j = i + 1; j < seenGraphs.size; j++){
                PowerGraph b = seenGraphs.get(j);
                if(merged.contains(b)) continue;

                Building[] pair = findLink(a, b);
                if(pair == null) pair = findLink(b, a);

                if(pair != null){
                    Log.info("[QoL Suite] [reconnect-button] linking @ -> @ (graph @ <-> graph @)",
                        pair[0].block.name, pair[1].block.name, a.getID(), b.getID());
                    requestExecutor.AddRequest(new IRequest.TileConfig(pair[0], pair[1].pos()));
                    merged.add(a);
                    merged.add(b);
                    linked++;
                    break;
                }
            }
        }

        Log.info("[QoL Suite] [reconnect-button] done, @ link(s) queued", linked);
        ui.showInfoToast(linked > 0
            ? Core.bundle.format("qol.power-reconnect.done", linked)
            : Core.bundle.get("qol.power-reconnect.none", "No disconnected power networks found"), 3f);
    }

    /**
     * Closest valid (source node, target building) pair where the source is a spare-capacity power node
     * on {@code fromGraph} and the target is any powered building on {@code toGraph}. Returns
     * {source, target} or null if no such pair exists (out of range, or {@code fromGraph} has no node with
     * spare link capacity at all).
     */
    Building[] findLink(PowerGraph fromGraph, PowerGraph toGraph){
        Building bestSource = null, bestTarget = null;
        float bestDist = Float.MAX_VALUE;

        for(Building b : fromGraph.all){
            if(!(b.block instanceof PowerNode node) || b.power.links.size >= node.maxNodes) continue;

            for(Building candidate : toGraph.all){
                if(!node.linkValid(b, candidate)) continue;
                float dist = b.dst2(candidate);
                if(dist < bestDist){
                    bestDist = dist;
                    bestSource = b;
                    bestTarget = candidate;
                }
            }
        }

        return bestSource != null ? new Building[]{bestSource, bestTarget} : null;
    }
}
