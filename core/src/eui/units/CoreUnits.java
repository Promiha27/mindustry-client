package eui.units;

import arc.struct.ObjectSet;

/**
 * Core-spawned starter unit types, kept as an empty list per the source (its comment: "core units are no
 * longer hidden from selection" - the set used to list alpha/beta/gamma/evoke/incite/emanate but was
 * emptied out, left as dead infrastructure rather than removed). Ported from units/core-units.js.
 */
public class CoreUnits{
    public static final ObjectSet<String> NAMES = ObjectSet.with();

    public static boolean includes(String typeName){
        return NAMES.contains(typeName);
    }
}
