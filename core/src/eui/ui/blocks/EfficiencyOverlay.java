package eui.ui.blocks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.struct.IntMap;
import arc.util.Time;
import eui.draw.BarBuilder;
import eui.util.CameraUtil;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.graphics.Layer;
import mindustry.world.Block;
import mindustry.world.blocks.power.ImpactReactor;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.production.BeamDrill;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Separator;
import mindustry.world.blocks.production.SolidPump;
import mindustry.world.meta.BlockStatus;

/**
 * "eui-ShowEfficiency": a floating "how busy has this building actually been lately" percentage over
 * crafters/drills/reactors - the fraction of a rolling time window ("eui-EfficiencyTimer" seconds) the
 * building's {@link Building#status()} reported {@link BlockStatus#active}. Ported from
 * ui/blocks/efficiency.js.
 * <p>
 * The source injected this by dynamically subclassing each matching {@link Block}'s {@code buildType}
 * (Rhino {@code extend()}) to wrap its own {@code draw()} - not portable to compiled Java (no runtime
 * subclassing), and not needed either: this instead walks every building in play once a draw frame
 * ({@link Groups#build}) and draws the label for whichever ones match the same block-type filter, which
 * is exactly what draw() being called per-instance amounted to anyway. Block-type membership is
 * expressed with fewer {@code instanceof} checks than the source's if/else chain by relying on the real
 * class hierarchy directly - e.g. {@link SolidPump} alone covers both it and {@link
 * mindustry.world.blocks.production.Fracker} (which extends it), {@link GenericCrafter} alone covers
 * {@link mindustry.world.blocks.heat.HeatProducer}/{@code HeatCrafter}/{@code AttributeCrafter} (which
 * all extend it), and {@link Drill} alone covers {@code BurstDrill} (which extends it) - only {@link
 * BeamDrill} needs its own check, since it extends {@link Block} directly rather than {@link Drill}.
 * <p>
 * TODO (source): does not work correctly for liquid.
 */
public class EfficiencyOverlay{
    static class State{
        float prevValue;
        float value;
        float timeMs;
        float startTimeMs;
    }

    private static final IntMap<State> storage = new IntMap<>();

    public EfficiencyOverlay(){
        Events.on(WorldLoadEvent.class, e -> storage.clear());
        Events.run(Trigger.draw, EfficiencyOverlay::draw);
    }

    static void draw(){
        if(!Core.settings.getBool("eui-ShowEfficiency", false)) return;

        //перф: настройка не меняется внутри кадра — читаем один раз, а не на каждое здание
        int timer = Core.settings.getInt("eui-EfficiencyTimer", 15);

        for(Building b : Groups.build){
            //перф: за кадром метку всё равно не видно — куллинг до любых аллокаций/подсчётов
            if(!CameraUtil.isIn(b.x, b.y)) continue;
            if(!isTracked(b.block)) continue;

            float efficiency = countEfficiency(b, timer);
            String text = BarBuilder.buildPercentLabel(efficiency);
            Draw.z(Layer.effect + 1);
            BarBuilder.drawLabel(text, b.x, b.y, Color.white, true);
            Draw.reset();
        }
    }

    static boolean isTracked(Block block){
        return block instanceof SolidPump || block instanceof Separator || block instanceof GenericCrafter
            || block instanceof Drill || block instanceof BeamDrill || block instanceof ImpactReactor || block instanceof NuclearReactor;
    }

    static float countEfficiency(Building build, int timer){
        State state = storage.get(build.id());
        float points = build.status() == BlockStatus.active ? 0.001f : 0;
        float currentTimeMs = Time.time / 60f * 1000f;
        float millisecondTimer = timer * 1000f;

        if(state == null){
            state = new State();
            state.startTimeMs = currentTimeMs;
            state.timeMs = currentTimeMs;
            storage.put(build.id(), state);
            return 0;
        }

        float passedTime = currentTimeMs - state.timeMs;

        if(currentTimeMs - state.startTimeMs > millisecondTimer){
            state.prevValue = state.value;
            state.value = 0;
            state.startTimeMs = currentTimeMs;
            state.timeMs = currentTimeMs;
            return state.prevValue / timer;
        }else{
            float measurement = (currentTimeMs - state.startTimeMs) / millisecondTimer;

            state.value += passedTime * points;
            state.timeMs = currentTimeMs;

            float countedValue = state.value * measurement / (timer * measurement);
            float countedPrevValue = state.prevValue * (1 - measurement) / timer;
            return countedValue + countedPrevValue;
        }
    }
}
