package qol.controlhelper.core;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.input.Binding;
import mindustry.type.UnitType;
import qol.core.SafeSettings;

import java.util.function.BooleanSupplier;

import static mindustry.Vars.control;

/** Double-tapping "select all units" within {@link #resetDelay} toggles deselecting support units (poly/mega) from the selection. */
public class SupportsIgnorer{
    public long resetDelay = 500L;
    public long lastTapTime = 0L;
    protected boolean deselectNextFrame = false;
    protected boolean deselected = false;
    public Seq<UnitType> unitsToIgnore = new Seq<>(new UnitType[]{UnitTypes.poly, UnitTypes.mega});

    final BooleanSupplier masterEnabled;

    public SupportsIgnorer(BooleanSupplier masterEnabled){
        this.masterEnabled = masterEnabled;
    }

    public void Init(){
        Events.run(EventType.Trigger.update, () -> {
            if(!masterEnabled.getAsBoolean() || !IsEnabled()) return;

            if(deselectNextFrame){
                if(!deselected) DeselectUnits();
                deselectNextFrame = false;
            }
            if(Core.input.keyTap(Binding.selectAllUnits)){
                if(System.currentTimeMillis() - lastTapTime <= resetDelay){
                    deselectNextFrame = true;
                    deselected = !deselected;
                }else{
                    deselected = true;
                }
                lastTapTime = System.currentTimeMillis();
            }
        });
    }

    public void DeselectUnits(){
        Seq<Unit> units = new Seq<>();
        for(Unit unit : control.input.selectedUnits){
            if(unitsToIgnore.contains(unit.type)) continue;
            units.add(unit);
        }
        control.input.selectedUnits = units;
    }

    public boolean IsEnabled(){
        return SafeSettings.getBool("ignoreSupportUnits", true);
    }
}
