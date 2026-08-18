package eui.draw;

import arc.graphics.g2d.Draw;
import arc.struct.Seq;
import mindustry.entities.units.BuildPlan;
import mindustry.graphics.Layer;

/**
 * Draws a live preview of one or several pending {@link BuildPlan}s using each plan's own block
 * region/config-icon drawers, at the same layer/depth the engine's own plan queue draws at. Used by the
 * drag-pathfind previews ({@link eui.input.ConveyorDrag}, {@link eui.input.CoreDrag}). Ported from
 * utils/draw/build-plan.js.
 * <p>
 * {@link Seq} already implements {@code arc.util.Eachable}, so it's passed straight through as the
 * "list" argument the block's drawer needs to look up neighbouring plans - no separate wrapper type
 * needed (the JS source's {@code new Eachable(plans)} exists only because a plain JS array isn't one).
 */
public class BuildPlanDraw{
    public static void draw(Seq<BuildPlan> plans){
        Draw.draw(Layer.plans + 0.01f, () -> {
            for(BuildPlan plan : plans){
                plan.block.drawPlanRegion(plan, plans);
                plan.block.drawPlanConfigTop(plan, plans);
            }
        });
    }

    public static void drawOne(BuildPlan plan){
        draw(Seq.with(plan));
    }
}
