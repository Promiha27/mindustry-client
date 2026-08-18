package qol.controlhelper.core;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import qol.core.SafeSettings;

import java.util.function.BooleanSupplier;

import static mindustry.Vars.control;

/** Hotkeys to split the currently commanded unit selection down to a smaller random subset. */
public class UnitSplitter{
    public static final KeyBind split = KeyBind.add("control-helper-split", KeyCode.j, "control-helper");
    public static final KeyBind splitAdd1 = KeyBind.add("control-helper-split-add-1", KeyCode.unset, "control-helper");
    public static final KeyBind splitAdd2 = KeyBind.add("control-helper-split-add-2", KeyCode.unset, "control-helper");
    public static final KeyBind splitAdd3 = KeyBind.add("control-helper-split-add-3", KeyCode.unset, "control-helper");

    final BooleanSupplier masterEnabled;

    public UnitSplitter(BooleanSupplier masterEnabled){
        this.masterEnabled = masterEnabled;
    }

    public void Init(){
        Events.run(EventType.Trigger.update, () -> {
            if(!masterEnabled.getAsBoolean()) return;
            if(Core.input.keyTap(split)) Split(0.5f);
            if(Core.input.keyTap(splitAdd1)) Split(SafeSettings.getInt("splitAdd1.size", 0) / 100f);
            if(Core.input.keyTap(splitAdd2)) Split(SafeSettings.getInt("splitAdd2.size", 0) / 100f);
            if(Core.input.keyTap(splitAdd3)) Split(SafeSettings.getInt("splitAdd3.size", 0) / 100f);
        });
    }

    public void Split(float percent){
        if(!control.input.commandMode) return;

        Seq<Unit> selectedUnits = control.input.selectedUnits;
        Seq<Unit> validUnits = new Seq<>();
        selectedUnits.each(u -> {
            if(u.isValid() && u.isCommandable()) validUnits.add(u);
        });

        if(validUnits.size == 0) return;

        Seq<Unit> units = new Seq<>();
        int targetCount = Mathf.clamp(Math.round(validUnits.size * percent), 1, validUnits.size);
        validUnits.shuffle();
        for(int i = 0; i < targetCount; i++) units.add(validUnits.get(i));
        control.input.selectedUnits = units;
    }
}
