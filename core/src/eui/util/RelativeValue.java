package eui.util;

import arc.struct.ObjectMap;

/**
 * Static "how much is this worth" table (raw resources -&gt; crafted resources -&gt; unit build costs),
 * used to rank/compare enemy unit groups by total value ({@code units-counter.js}) for the
 * under-attack/losing-support alerts. Every number here is carried over as-is from the source - it's a
 * flat data table, not logic, so there's nothing to "port" beyond copying the formulas exactly. Serpolo
 * units only; Erekir units simply aren't in this table (same as the source), so
 * {@link #getUnitValue(String)} returns 0 for any of them - meaning the under-attack alert effectively
 * can't fire from Erekir-only attacks. Ported from utils/relative-value.js.
 */
public class RelativeValue{
    private static final ObjectMap<String, Float> resourceValues = new ObjectMap<>();
    private static final ObjectMap<String, Float> tierValues = new ObjectMap<>();
    private static final ObjectMap<String, Float> unitsValues = new ObjectMap<>();

    static{
        resourceValues.put("copper", 1 / 2.90f);
        resourceValues.put("lead", 1 / 2.90f);
        resourceValues.put("sand", 1 / 3.42f);
        resourceValues.put("coal", 1 / 2.52f);
        resourceValues.put("titanium", 1 / 2.23f);
        resourceValues.put("thorium", 1 / 1.99f);

        float coal = r("coal"), sand = r("sand"), titanium = r("titanium"), thorium = r("thorium"), lead = r("lead"), copper = r("copper");

        resourceValues.put("energy", coal / 120);
        float energy = r("energy");
        resourceValues.put("water", energy * 3);
        float water = r("water");
        resourceValues.put("oil", energy * 180 / 15 + water * 9 / 15 + sand / 15);
        float oil = r("oil");
        resourceValues.put("cryo", energy * 5 + water + titanium / 24);
        float cryo = r("cryo");
        resourceValues.put("graphite", coal * 1.7f);
        float graphite = r("graphite");
        resourceValues.put("silicon", sand * 2 + coal * 1 + energy * 20);
        float silicon = r("silicon");
        resourceValues.put("metaglass", sand * 1 + lead * 1 + energy * 30);
        float metaglass = r("metaglass");
        resourceValues.put("plastanium", titanium * 2 + energy * 180 + oil * 15);
        float plastanium = r("plastanium");
        resourceValues.put("phaseFabric", sand * 10 + thorium * 4 + energy * 600);
        float phaseFabric = r("phaseFabric");
        resourceValues.put("surgeAlloy", silicon * 3 + titanium * 2 + copper * 7 + energy * 240 / 1.25f);
        float surgeAlloy = r("surgeAlloy");

        tierValues.put("second", silicon * 40 + graphite * 40 + energy * 180 * 10);
        float second = tierValues.get("second");
        tierValues.put("third", second + silicon * 130 + titanium * 80 + metaglass * 40 + energy * 360 * 30);
        float third = tierValues.get("third");
        //NOTE: the source has a comma where a "+" clearly belongs here ("... + resourceValues.cryo*60*90,
        //resourceValues.energy*780*90"), so JS's comma operator throws away everything before the last
        //comma and "fourth" silently evaluates to just energy*780*90. Kept exactly as the source actually
        //computes it (a real behavioural bug, not a translation artifact) - fixing it would change every
        //alert threshold downstream of it.
        tierValues.put("fourth", energy * 780 * 90);
        float fourth = tierValues.get("fourth");
        tierValues.put("fifth", fourth + silicon * 1000 + plastanium * 600 + surgeAlloy * 500 + phaseFabric * 350 + cryo * 180 * 240 + energy * 1500 * 240);

        unitsValues.put("alpha", 1f);
        unitsValues.put("beta", 2f);
        unitsValues.put("gamma", 3f);
        unitsValues.put("dagger", silicon * 10 + lead * 10 + energy * 72 * 15);
        unitsValues.put("nova", silicon * 30 + lead * 20 + titanium * 20 + energy * 72 * 40);
        unitsValues.put("crawler", coal * 20 + silicon * 10 + energy * 72 * 12);
        unitsValues.put("flare", silicon * 15 + energy * 72 * 15);
        unitsValues.put("mono", silicon * 30 + lead * 15 + energy * 72 * 35);
        unitsValues.put("risso", silicon * 20 + metaglass * 35 + energy * 72 * 45);
        unitsValues.put("retusa", silicon * 15 + metaglass * 25 + titanium * 20 + energy * 72 * 50);

        tieredUnit("mace", "dagger", "second");
        tieredUnit("fortress", "dagger", "third");
        tieredUnit("scepter", "dagger", "fourth");
        tieredUnit("reign", "dagger", "fifth");
        tieredUnit("pulsar", "nova", "second");
        tieredUnit("quasar", "nova", "third");
        tieredUnit("vela", "nova", "fourth");
        tieredUnit("corvus", "nova", "fifth");
        tieredUnit("atrax", "crawler", "second");
        tieredUnit("spiroct", "crawler", "third");
        tieredUnit("arkyid", "crawler", "fourth");
        tieredUnit("toxopid", "crawler", "fifth");
        tieredUnit("horizon", "flare", "second");
        tieredUnit("zenith", "flare", "third");
        tieredUnit("antumbra", "flare", "fourth");
        tieredUnit("eclipse", "flare", "fifth");
        tieredUnit("poly", "mono", "second");
        tieredUnit("mega", "mono", "third");
        tieredUnit("quad", "mono", "fourth");
        tieredUnit("oct", "mono", "fifth");
        tieredUnit("minke", "risso", "second");
        tieredUnit("bryde", "risso", "third");
        tieredUnit("sei", "risso", "fourth");
        tieredUnit("omura", "risso", "fifth");
        tieredUnit("oxynoe", "retusa", "second");
        tieredUnit("cyerce", "retusa", "third");
        tieredUnit("aegires", "retusa", "fourth");
        tieredUnit("navanax", "retusa", "fifth");
    }

    static float r(String name){
        return resourceValues.get(name);
    }

    static void tieredUnit(String name, String base, String tier){
        unitsValues.put(name, unitsValues.get(base) + tierValues.get(tier));
    }

    public static float getResourceValue(String name){
        return resourceValues.get(name, 0f);
    }

    public static float getUnitValue(String name){
        return unitsValues.get(name, 0f);
    }
}
