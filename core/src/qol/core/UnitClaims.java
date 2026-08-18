package qol.core;

import arc.struct.IntSet;

/**
 * Minimal shared registry of "this feature is currently puppeting this unit's command", in the same
 * spirit as {@link QueueCoordination}. Two features re-command units with a save/restore cycle -
 * Core Auto-Heal drafts flying healers, Mining Defaults' flee sends miners home from danger - and
 * poly-type units qualify for BOTH (they heal and mine). Without a claim, one feature could grab a
 * unit mid-way through the other's cycle: e.g. core-heal drafting a FLEEING miner would save "move"
 * as the command to restore, stranding the unit idle instead of returning it to mining. Features
 * must {@link #claim} before starting their cycle, skip units already claimed by someone else, and
 * {@link #release} when their cycle ends. Cleared centrally on world load (QolSuiteMod).
 */
public final class UnitClaims{
    private static final IntSet claimed = new IntSet();

    private UnitClaims(){
    }

    /** True if the unit was free and is now claimed by the caller; false = someone else owns it, don't touch. */
    public static boolean claim(int unitId){
        return claimed.add(unitId);
    }

    public static void release(int unitId){
        claimed.remove(unitId);
    }

    public static boolean isClaimed(int unitId){
        return claimed.contains(unitId);
    }

    public static void clear(){
        claimed.clear();
    }
}
