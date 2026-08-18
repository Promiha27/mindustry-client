package eui.units;

import arc.struct.ObjectSet;

/** Support/utility unit types - excluded from "dangerous enemy" reckoning (under-attack alert) and separately trackable in the units table. Ported from units/support-units.js. */
public class SupportUnits{
    public static final ObjectSet<String> NAMES = ObjectSet.with(
        "mono", "poly", "mega", "assembly-drone", "manifold"
    );

    public static boolean includes(String typeName){
        return NAMES.contains(typeName);
    }
}
