package eui.ui.units;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import eui.util.CameraUtil;
import mindustry.ai.types.LogicAI;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;

/**
 * Draws a purple line from a logic-controlled unit to the processor block controlling it (the "eui-TrackLogicControl" setting). Ported from ui/units/logic-tracker.js.
 * <p>
 * перф: линия рисуется только если хотя бы один из концов (юнит/процессор) в кадре, и через
 * {@code Draw.z} вместо {@code Draw.draw(лямбда)} - без аллокаций на юнита.
 */
public class LogicTracker{
    public static void drawLogicLine(Unit unit){
        if(!(unit.controller() instanceof LogicAI logicAI)) return;
        Building processor = logicAI.controller;
        if(processor == null) return;

        float unitX = unit.x, unitY = unit.y;
        float processorX = processor.x, processorY = processor.y;

        if(!CameraUtil.isIn(unitX, unitY) && !CameraUtil.isIn(processorX, processorY)) return;

        float prevZ = Draw.z();
        Draw.z(Layer.overlayUI + 0.01f);
        Lines.stroke(1, Color.purple);
        Draw.alpha(0.7f);
        Lines.line(unitX, unitY, processorX, processorY);
        Draw.reset();
        Draw.z(prevZ);
    }
}
