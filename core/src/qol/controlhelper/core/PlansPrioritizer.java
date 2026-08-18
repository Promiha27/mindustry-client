package qol.controlhelper.core;

import arc.Events;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.struct.Queue;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.entities.Fires;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import qol.core.QueueCoordination;
import qol.core.SafeSettings;

import java.util.function.BooleanSupplier;

import static mindustry.Vars.indexer;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

/** Reorders the build queue so turrets near enemies and liquid chains feeding a fire-fighting turret jump to the front. */
public class PlansPrioritizer{
    public Seq<PriorityFilter> filters = new Seq<>();
    public Seq<BuildPlan> prioritizedPlans = new Seq<>();

    final BooleanSupplier masterEnabled;

    public PlansPrioritizer(BooleanSupplier masterEnabled){
        this.masterEnabled = masterEnabled;
    }

    public void Init(){
        filters.add(new LiquidsFilter(), new TurretsFilter());
        Events.run(EventType.Trigger.update, () -> {
            if(!masterEnabled.getAsBoolean() || !IsEnabled() || !state.isGame() || player == null || player.unit() == null) return;
            //Force Build Schematic queues a run of demolish plans, then waits for those exact tiles to
            //clear before queuing the schematic itself - reordering the queue out from under it here
            //could push something else ahead of the still-pending demolish plans and delay that wait
            //indefinitely. See QueueCoordination.
            if(QueueCoordination.forceBuildPending) return;

            prioritizedPlans.removeAll(plan -> plan == null || plan.build() != null && !(plan.build() instanceof ConstructBuild) || plan.block == Blocks.air);

            Seq<BuildPlan> prioritize = new Seq<>();
            Queue<BuildPlan> plans = player.unit().plans;
            outer:
            for(BuildPlan plan : plans){
                if(prioritizedPlans.contains(plan)) continue;
                for(PriorityFilter filter : filters){
                    if(!filter.ShouldPreoritize(plan)) continue;
                    prioritize.add(plan);
                    prioritizedPlans.add(plan);
                    continue outer;
                }
            }
            for(BuildPlan plan : prioritize){
                plans.remove(plan);
                plans.addFirst(plan);
            }
        });
    }

    public boolean IsEnabled(){
        return SafeSettings.getBool("prioritizePlans", true);
    }

    public interface PriorityFilter{
        boolean ShouldPreoritize(BuildPlan plan);
    }

    public static class LiquidsFilter implements PriorityFilter{
        public Seq<Block> pumps = new Seq<>(new Block[]{Blocks.mechanicalPump, Blocks.rotaryPump, Blocks.impulsePump, Blocks.waterExtractor});
        public Seq<Block> distributionBlocks = new Seq<>(new Block[]{Blocks.conduit, Blocks.pulseConduit, Blocks.platedConduit, Blocks.bridgeConduit});
        public Seq<Block> stewerBlocks = new Seq<>(new Block[]{Blocks.wave, Blocks.tsunami});
        protected boolean foundFire = false;

        @Override
        public boolean ShouldPreoritize(BuildPlan plan){
            if(plan.breaking) return false;
            if(!stewerBlocks.contains(plan.block)) return true;
            if(!IsFireInRange(new Vec2(plan.getX(), plan.getY()), GetMaxRange())) return false;

            PosBuildPlan posBuildPlan = new PosBuildPlan();
            posBuildPlan.plan = plan;
            return LeadsToStewer(posBuildPlan);
        }

        public boolean IsFireInRange(Vec2 pos, float range){
            foundFire = false;
            indexer.eachBlock(player.team(), pos.x, pos.y, range, ez -> true, b -> {
                if(Fires.has(b.tileX(), b.tileY())) foundFire = true;
            });
            return foundFire;
        }

        public float GetMaxRange(){
            float maxRange = 0f;
            for(Block block : stewerBlocks){
                if(!(block instanceof LiquidTurret turret)) continue;
                if(turret.range > maxRange) maxRange = turret.range;
            }
            return maxRange * 1.5f;
        }

        public boolean LeadsToStewer(PosBuildPlan buildPlan){
            if(buildPlan.IsNull()) return false;
            if(IsPump(buildPlan)){
                for(PosBuildPlan output : GetPumpOutputs(buildPlan)){
                    if(LeadsToStewer(output)) return true;
                }
                return false;
            }
            PosBuildPlan cur = buildPlan;
            while(IsDistribution(cur)){
                cur = GetNext(cur);
                if(cur == null || cur.IsNull()) return false;
            }
            return IsStewer(cur);
        }

        public boolean IsPump(PosBuildPlan buildPlan){
            if(buildPlan.plan != null && pumps.contains(buildPlan.plan.block)) return true;
            return buildPlan.build != null && pumps.contains(buildPlan.build.block);
        }

        public boolean IsDistribution(PosBuildPlan buildPlan){
            if(buildPlan.plan != null && distributionBlocks.contains(buildPlan.plan.block)) return true;
            return buildPlan.build != null && distributionBlocks.contains(buildPlan.build.block);
        }

        public boolean IsStewer(PosBuildPlan buildPlan){
            if(buildPlan.plan != null && stewerBlocks.contains(buildPlan.plan.block)) return true;
            return buildPlan.build != null && stewerBlocks.contains(buildPlan.build.block);
        }

        public PosBuildPlan GetNext(PosBuildPlan buildPlan){
            int x, y, trns, rot;
            if(buildPlan.plan != null){
                trns = buildPlan.plan.block.size / 2;
                rot = buildPlan.plan.rotation;
                x = buildPlan.plan.x;
                y = buildPlan.plan.y;
            }else if(buildPlan.build != null){
                trns = buildPlan.build.block.size / 2;
                rot = buildPlan.build.rotation;
                x = buildPlan.build.tileX();
                y = buildPlan.build.tileY();
            }else{
                return null;
            }
            int nextX = x + Geometry.d4(rot).x * trns;
            int nextY = y + Geometry.d4(rot).y * trns;
            return GetAt(nextX, nextY);
        }

        public Seq<PosBuildPlan> GetPumpOutputs(PosBuildPlan buildPlan){
            if(buildPlan.IsNull()) return new Seq<>();
            int trns = 0, _x = 0, _y = 0;
            if(buildPlan.plan != null){
                trns = buildPlan.plan.block.size / 2;
                _x = buildPlan.plan.x;
                _y = buildPlan.plan.y;
            }else if(buildPlan.build != null){
                trns = buildPlan.build.block.size / 2;
                _x = buildPlan.build.tileX();
                _y = buildPlan.build.tileY();
            }
            Seq<PosBuildPlan> out = new Seq<>();
            for(int x = _x - trns - 1; x <= _x + trns + 1; x++){
                for(int y = _y - trns - 1; y <= _y + trns + 1; y++){
                    if(x <= _x + trns && x >= _x - trns && y <= _y + trns && y >= _y - trns) continue;
                    PosBuildPlan cur = GetAt(x, y);
                    if(cur.IsNull() || !(IsDistribution(cur) || IsStewer(cur))) continue;
                    out.add(cur);
                }
            }
            return out;
        }

        public PosBuildPlan GetAt(int x, int y){
            PosBuildPlan out = new PosBuildPlan();
            out.build = world.build(x, y);
            for(BuildPlan plan : player.unit().plans){
                if(!plan.within(x, y, 0.1f)) continue;
                out.plan = plan;
                break;
            }
            return out;
        }

        public static class PosBuildPlan{
            public BuildPlan plan;
            public Building build;

            public boolean IsNull(){
                return plan == null && build == null;
            }
        }
    }

    public static class TurretsFilter implements PriorityFilter{
        public Seq<Block> priorityBlocks = new Seq<>(new Block[]{Blocks.scatter, Blocks.lancer, Blocks.arc, Blocks.swarmer, Blocks.salvo, Blocks.fuse, Blocks.cyclone});

        @Override
        public boolean ShouldPreoritize(BuildPlan plan){
            if(plan.breaking) return false;
            if(!priorityBlocks.contains(plan.block)) return false;

            boolean[] foundEnemy = {false};
            Units.nearbyEnemies(player.team(), plan.getX(), plan.getY(), GetMaxRange(), u -> foundEnemy[0] = true);
            return foundEnemy[0];
        }

        public float GetMaxRange(){
            float maxRange = 0f;
            for(Block block : priorityBlocks){
                if(!(block instanceof Turret turret)) continue;
                if(turret.range > maxRange) maxRange = turret.range;
            }
            return maxRange * 1.5f;
        }
    }
}
