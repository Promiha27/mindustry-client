package eui.ui.units;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.struct.IntMap;
import eui.draw.BarBuilder;
import eui.util.CameraUtil;
import mindustry.entities.abilities.Ability;
import mindustry.entities.abilities.ForceFieldAbility;
import mindustry.entities.abilities.ShieldArcAbility;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

/**
 * Floating health/shield bars over damaged units - shown for a fixed display time after the value last
 * changed, then fades out, so a unit sitting at full health/shield stays clean but taking (or healing)
 * any damage flashes its bar back up. Ported from ui/units/health-shield-bar.js.
 * <p>
 * The source tracked both health and shield state in one shared-by-id map, and had a latent bug on a
 * unit's very first shield-bar draw: it wrote the new map entry, then unconditionally dereferenced the
 * (still null, in the local variable) previous-status reference right after - crashing that draw call
 * every time (silently swallowed by the JS mod's central per-callback try/catch, which this port
 * deliberately doesn't carry over - see {@code eui.EUIMod}'s javadoc on why that indirection is dropped).
 * Rewritten below with two straightforward, null-safe per-unit-id maps instead of reproducing the crash.
 */
public class HealthShieldBar{
    private static final float unitBarSize = 4;
    private static final float unitBarDisplayTimeMs = 5000;
    private static final float unitBarFadeTimeMs = 3000;
    private static final float unitBarTotalDisplayTimeMs = unitBarDisplayTimeMs + unitBarFadeTimeMs;

    static class ShieldState{
        float value;
        float maxShield;
        long timeMs;
    }

    static class HealthState{
        float value;
        long timeMs;
    }

    private static final IntMap<ShieldState> shieldStates = new IntMap<>();
    private static final IntMap<HealthState> healthStates = new IntMap<>();

    static{
        Events.on(WorldLoadEvent.class, e -> {
            shieldStates.clear();
            healthStates.clear();
        });
        //avoids an unbounded memory leak - the source's own comment on this listener
        Events.on(UnitDestroyEvent.class, e -> {
            shieldStates.remove(e.unit.id);
            healthStates.remove(e.unit.id);
        });
    }

    public static void drawUnitShieldBar(Unit unit, boolean offset, boolean force){
        ShieldState prev = shieldStates.get(unit.id);
        float maxShield = prev != null && prev.maxShield > 0 ? prev.maxShield : findMaxShield(unit);
        float value = unit.shield / maxShield;

        if(prev == null){
            prev = new ShieldState();
            prev.maxShield = Math.max(unit.shield, maxShield);
            prev.value = value;
            prev.timeMs = System.currentTimeMillis();
            shieldStates.put(unit.id, prev);
        }else if(prev.value != value){
            prev.value = value;
            prev.timeMs = System.currentTimeMillis();
        }

        float unitX = unit.x;
        float unitY = offset ? unit.y + unitBarSize - 1 : unit.y;

        if(!isBarNecessary(unitX, unitY, value, prev.timeMs, force)) return;
        float alpha = getBarAlpha(prev.timeMs, force);

        Draw.draw(Layer.overlayUI + 0.01f, () -> BarBuilder.draw(unitX, unitY + 2, value, unit.hitSize / 6, unitBarSize, "", Pal.accent, alpha));
    }

    /** @return true if a health bar was actually drawn - {@link DrawCycle} uses that to decide whether the shield bar underneath needs an offset. */
    public static boolean drawUnitHealthBar(Unit unit, boolean force){
        float value = unit.health / unit.maxHealth;
        HealthState prev = healthStates.get(unit.id);

        if(prev == null){
            prev = new HealthState();
            prev.value = value;
            prev.timeMs = System.currentTimeMillis();
            healthStates.put(unit.id, prev);
        }else if(prev.value != value){
            prev.value = value;
            prev.timeMs = System.currentTimeMillis();
        }

        float unitX = unit.x;
        float unitY = unit.y;

        if(!isBarNecessary(unitX, unitY, value, prev.timeMs, force)) return false;
        float alpha = getBarAlpha(prev.timeMs, force);

        Draw.draw(Layer.overlayUI + 0.01f, () -> BarBuilder.draw(unitX, unitY + 2, value, unit.hitSize / 6, unitBarSize, "", Color.scarlet, alpha));

        return true;
    }

    /** Only {@link ForceFieldAbility}/{@link ShieldArcAbility} carry a shield-capacity field in this engine - every other ability type is skipped, matching what {@code ability.max} would actually resolve to on the source's generic loop over all ability types. */
    static float findMaxShield(Unit unit){
        float max = 0;
        for(Ability ability : unit.abilities){
            float abilityMax = ability instanceof ForceFieldAbility ff ? ff.max : ability instanceof ShieldArcAbility sa ? sa.max : 0;
            if(abilityMax > max) max = abilityMax;
        }
        return max > 0 ? max : 40;
    }

    static boolean isBarNecessary(float x, float y, float value, long timeMs, boolean force){
        if(!CameraUtil.isIn(x, y)) return false;
        if(value <= 0) return false;
        if(force) return true;
        if(value >= 1) return false;

        return System.currentTimeMillis() - timeMs < unitBarTotalDisplayTimeMs;
    }

    static float getBarAlpha(long timeMs, boolean force){
        if(force) return 1;
        return 1 - (System.currentTimeMillis() - timeMs - unitBarDisplayTimeMs) / unitBarFadeTimeMs;
    }
}
