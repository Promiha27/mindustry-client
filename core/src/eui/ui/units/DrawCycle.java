package eui.ui.units;

import arc.Core;
import arc.Events;
import mindustry.ai.types.LogicAI;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;

/**
 * Per-frame driver for the three per-unit HUD overlays above: iterates every live unit once and, per
 * setting, draws its health/shield bar ({@link HealthShieldBar}), its controlling player's cursor marker
 * ({@link PlayerTracker}), and/or its logic-control tether line ({@link LogicTracker}). Ported from
 * ui/units/draw-cycle.js.
 */
public class DrawCycle{
    private static final boolean force = false;

    public DrawCycle(){
        Events.run(Trigger.draw, DrawCycle::update);
    }

    static void update(){
        boolean showUnitBar = Core.settings.getBool("eui-showUnitBar", true);
        boolean trackPlayerCursor = Core.settings.getBool("eui-TrackPlayerCursor", false);
        boolean trackLogicControl = Core.settings.getBool("eui-TrackLogicControl", false);

        if(!showUnitBar && !trackPlayerCursor && !trackLogicControl) return;

        for(Unit unit : Groups.unit){
            if(showUnitBar){
                if(HealthShieldBar.drawUnitHealthBar(unit, force)){
                    HealthShieldBar.drawUnitShieldBar(unit, true, force);
                }else{
                    HealthShieldBar.drawUnitShieldBar(unit, false, force);
                }
            }
            if(trackPlayerCursor){
                Player p = unit.getPlayer();
                if(p != null) PlayerTracker.drawCursor(p);
            }
            if(trackLogicControl && unit.controller() instanceof LogicAI){
                LogicTracker.drawLogicLine(unit);
            }
        }
    }
}
