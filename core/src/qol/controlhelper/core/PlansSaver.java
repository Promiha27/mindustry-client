package qol.controlhelper.core;

import arc.Core;
import arc.Events;
import arc.struct.Queue;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.input.Binding;
import qol.controlhelper.util.ArrayUtils;
import qol.core.SafeSettings;

import java.util.function.BooleanSupplier;

import static mindustry.Vars.player;
import static mindustry.Vars.state;

/** Remembers the build queue across a respawn/death and restores it once the new unit spawns. */
public class PlansSaver{
    protected Queue<BuildPlan> plans = new Queue<>();
    protected boolean resetPlans = false;
    public long maxResetTime = 2000L;
    protected long resetTime = 0L;

    final BooleanSupplier masterEnabled;

    public PlansSaver(BooleanSupplier masterEnabled){
        this.masterEnabled = masterEnabled;
    }

    public void Init(){
        Events.run(EventType.Trigger.update, () -> {
            if(!masterEnabled.getAsBoolean() || !IsEnabled() || !state.isGame() || player == null) return;

            if(Core.input.keyTap(Binding.respawn) || player.dead()){
                if(resetPlans) return;
                if(plans.size == 0) return;
                resetPlans = true;
                resetTime = System.currentTimeMillis();
            }

            Unit unit = player.unit();
            if(unit == null) return;

            if(resetPlans && (unit.plans.size == 0 || !ArrayUtils.AreSame(unit.plans, plans))){
                resetPlans = false;
                if(System.currentTimeMillis() - resetTime > maxResetTime) return;
                Queue<BuildPlan> newPlans = new Queue<>();
                plans.each(newPlans::add);
                unit.plans = newPlans;
            }
            if(!resetPlans){
                plans = ArrayUtils.Copy(unit.plans);
            }
        });
    }

    public boolean IsEnabled(){
        return SafeSettings.getBool("plansSaver", true);
    }
}
