package eui.draw;

import arc.Events;
import arc.func.Boolp;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.struct.Seq;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

/**
 * A tiny self-pumping queue of one-shot world-space animations, currently just "diverging circles" (used
 * by the under-attack alert to mark the fight location). Each queued task runs once per draw frame and
 * removes itself once it reports done. Ported from utils/draw/draw-tasks.js.
 */
public class DrawTasks{
    private static final Seq<Boolp> tasks = new Seq<>();

    static{
        Events.run(Trigger.draw, () -> Draw.draw(Layer.overlayUI + 0.01f, () -> {
            for(int i = tasks.size - 1; i >= 0; i--){
                if(tasks.get(i).get()) tasks.remove(i);
            }
        }));
    }

    public static class DivergingCirclesParams{
        public float maxRadius = 2000;
        public float startRadius = 0;
        public Color color;
        public float growSpeed = 1;
        public int circlesAmount = 3;
    }

    /**
     * Starts the effect immediately (registers the task synchronously) - matches the source, where
     * {@code drawTasks.divergingCircles(x, y, params)} is a plain call with no useful return value
     * (nothing is returned at all - JS implicitly yields {@code undefined}), NOT a deferred/queued
     * effect. {@code under-attack.js} calls this directly inline as one of {@code ingameAlert}'s call
     * arguments, so in the source the circles actually start at the moment the alert fires, not when the
     * alert's own toast popup later gets dequeued - {@code ingameAlert}'s "drawTask" callback parameter
     * ends up always undefined/never invoked as a result. Kept that way here too: this starts the
     * animation right away, and {@link eui.util.OutputWrapper#ingameAlert} has no matching parameter to
     * defer it through.
     */
    public static void divergingCircles(float x, float y, DivergingCirclesParams params){
        if(params == null) params = new DivergingCirclesParams();
        DivergingCirclesParams p = params;

        float[] radius = {p.startRadius};
        float startTime = arc.util.Time.time;

        tasks.add(() -> {
            Draw.color(p.color);
            for(int i = 0; i < p.circlesAmount; i++){
                if(p.color != null) Drawf.circles(x, y, radius[0] * (1 + 0.2f * i), p.color);
                else Drawf.circles(x, y, radius[0] * (1 + 0.2f * i));
            }
            radius[0] += (arc.util.Time.time - startTime) / 8f * p.growSpeed;
            return radius[0] > p.maxRadius;
        });
    }
}
