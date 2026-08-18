package eui.interact;

import arc.Core;
import arc.Events;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

import java.util.Objects;

import static mindustry.Vars.player;

/**
 * Auto-unit: while a target unit type is picked (from bottom-panel-ui, see EUIMod's Phase C notes),
 * automatically takes control of the first eligible AI unit of that type - either right away, or within
 * a short window after one spawns. Ported from interact/auto-unit.js.
 */
public class AutoUnit{
    /** How long (in ticks) the scan for an eligible unit stays active after the target type changes or a matching unit spawns. */
    private static final float TRY_TIME = 300;

    private String prevSelectedUnitType;
    private float checkEndTime = 0;

    public AutoUnit(){
        Events.run(Trigger.update, this::update);
        Events.on(UnitCreateEvent.class, this::onUnitCreate);
    }

    void update(){
        if(player.unit() == null) return;
        String selectedUnitType = Core.settings.getString("eui-auto-unit", null);

        if(!Objects.equals(selectedUnitType, prevSelectedUnitType)){
            prevSelectedUnitType = selectedUnitType;
            checkEndTime = Time.time + TRY_TIME;
        }
        if(Time.time > checkEndTime) return;
        if(!isCheckNeeded(selectedUnitType)) return;

        for(Unit unit : Groups.unit){
            if(unit.isAI() && !unit.dead() && isEligible(unit, selectedUnitType)){
                //multiplayer: Call.unitControl sends the request to the server
                Call.unitControl(player, unit);
                InteractTimer.increase();
                checkEndTime = 0;
                break;
            }
        }
    }

    void onUnitCreate(UnitCreateEvent event){
        if(player.unit() == null) return;
        String selectedUnitType = Core.settings.getString("eui-auto-unit", null);

        if(!isCheckNeeded(selectedUnitType)) return;
        if(!isEligible(event.unit, selectedUnitType)) return;
        checkEndTime = Time.time + TRY_TIME;
    }

    static boolean isCheckNeeded(String selectedUnitType){
        mindustry.type.UnitType currentType = player.unit().type;
        if(currentType == null || selectedUnitType == null || currentType.name.equals(selectedUnitType)) return false;
        return true;
    }

    static boolean isEligible(Unit unit, String selectedUnitType){
        if(unit.team != player.team()) return false;
        return unit.type.name.equals(selectedUnitType);
    }
}
