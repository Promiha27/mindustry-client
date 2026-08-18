package eui.units;

import arc.struct.ObjectSet;

/** Unit types excluded entirely from the units-value table/top list (missile "units" are not real combat units). Ported from units/blacklist.js. */
public class Blacklist{
    public static final ObjectSet<String> NAMES = ObjectSet.with(
        "anthicus-missile", "quell-missile", "disrupt-missile", "scathe-missile"
    );

    public static boolean includes(String typeName){
        return NAMES.contains(typeName);
    }
}
