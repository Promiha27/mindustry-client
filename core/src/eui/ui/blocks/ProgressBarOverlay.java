package eui.ui.blocks;

import arc.Core;
import arc.Events;
import eui.draw.BarBuilder;
import eui.util.CameraUtil;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;

/**
 * "eui-showFactoryProgress": a floating build-progress bar over unit factories/reconstructors, so their
 * current build progress is visible without opening the block's own UI. Ported from
 * ui/blocks/progress-bar.js - same "walk {@link Groups#build} once a draw frame instead of dynamically
 * subclassing per-block draw()" rewrite as {@link EfficiencyOverlay}, see its javadoc for why.
 * <p>
 * The source's shared drawable checked {@code build.currentPlan == -1} as its "nothing selected yet, skip
 * it" guard regardless of which of the two block types it was drawing for - on a Reconstructor build,
 * which has no {@code currentPlan} property at all, that read {@code undefined} in Rhino and the
 * {@code == -1} comparison was always false, so the guard silently never applied there (progress always
 * shown, gated only by its own {@code constructTime}). Expressed directly here instead of relying on that
 * cross-type-undefined-comparison quirk: the {@code currentPlan == -1} check only exists in the
 * {@link UnitFactory.UnitFactoryBuild} branch, matching the same actual behaviour.
 */
public class ProgressBarOverlay{
    private static final float factoryProgressBarSize = 4;

    public ProgressBarOverlay(){
        Events.run(Trigger.draw, ProgressBarOverlay::draw);
    }

    static void draw(){
        if(!Core.settings.getBool("eui-showFactoryProgress", true)) return;

        for(Building build : Groups.build){
            //перф: бар за кадром не виден — куллинг до instanceof-веток и построения строки
            if(!CameraUtil.isIn(build.x, build.y)) continue;

            float progress;

            if(build instanceof UnitFactory.UnitFactoryBuild ufb){
                if(ufb.currentPlan == -1) continue;
                progress = ufb.progress / ((UnitFactory)build.block).plans.get(ufb.currentPlan).time;
            }else if(build instanceof Reconstructor.ReconstructorBuild rb){
                progress = rb.progress / ((Reconstructor)build.block).constructTime;
            }else{
                continue;
            }

            String text = BarBuilder.buildPercentLabel(progress);
            BarBuilder.draw(build.x, build.y, progress, build.block.size, factoryProgressBarSize, text, build.team.color, 1);
        }
    }
}
