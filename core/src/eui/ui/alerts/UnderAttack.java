package eui.ui.alerts;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.OrderedMap;
import arc.util.Time;
import eui.draw.DrawTasks;
import eui.units.UnitsCounter;
import eui.util.RelativeValue;
import mindustry.entities.Units;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Unit;
import mindustry.world.Tile;

import static mindustry.Vars.player;

/**
 * Whenever one of your blocks is destroyed, looks for a nearby "destroyer" (a dangerous enemy unit whose
 * weapon range plausibly reaches the wreck) and tallies the total value of every dangerous enemy near
 * *that* destroyer - if the tally crosses one of four size thresholds, fires an alert sized to match
 * ("massive"/"large"/"medium"/"small attack"), with a diverging-circles marker at the wreck and a 1-minute
 * cooldown between alerts. Ported from ui/alerts/under-attack.js.
 */
public class UnderAttack{
    private static final float sendCooldown = 60 * 60; //1 minute of ticks
    private static final float searchSize = 36 * 8; //36 tiles around the destroyed block
    private static final float destroyerSearchSize = 80 * 8; //80 tiles around the destroyed block, to find the attacker

    /** Ordered largest-threshold-first so the loop below reports the biggest size that applies. */
    private static final OrderedMap<String, Float> attackSizes = new OrderedMap<>();
    static{
        attackSizes.put("massive", 67500f); // ~4.5 corvus
        attackSizes.put("large", 22500f); // ~6.5 quad
        attackSizes.put("medium", 7500f); // ~22.6 zenith
        attackSizes.put("small", 2500f); // ~7.5 zenith
    }

    private float lastCheckTime;

    private final Cons<BlockDestroyEvent> listener = this::onBlockDestroy;

    public UnderAttack(){
        Events.on(WorldLoadEvent.class, e -> lastCheckTime = -sendCooldown);

        new Alert(
            () -> Events.on(BlockDestroyEvent.class, listener),
            () -> Events.remove(BlockDestroyEvent.class, listener)
        );
    }

    void onBlockDestroy(BlockDestroyEvent event){
        if(Time.time - 6 < lastCheckTime) return; //no more than ~10 checks/second - a reactor chain-explosion can trigger many of these at once
        if(Time.time - sendCooldown < lastCheckTime) return;

        Tile tile = event.tile;
        if(tile.team() != player.team()) return;

        float x = tile.x * 8f;
        float y = tile.y * 8f;
        lastCheckTime = Time.time;

        Unit destroyer = Units.closestEnemy(player.team(), x, y, destroyerSearchSize, unit ->
            UnitsCounter.isDangerous(unit) && Mathf.dst(x, y, unit.x, unit.y) <= unit.range() * 1.5f
        );
        if(destroyer == null) return;

        float[] currentAttackValue = {0};
        Units.nearbyEnemies(player.team(), destroyer.x - searchSize, destroyer.y - searchSize, searchSize * 2, searchSize * 2, unit -> {
            if(!UnitsCounter.isDangerous(unit)) return;
            currentAttackValue[0] += RelativeValue.getUnitValue(unit.type.name);
        });

        for(var entry : attackSizes){
            if(currentAttackValue[0] > entry.value){
                eui.util.OutputWrapper.ingameAlert(Core.bundle.get("alerts." + entry.key + "-attack"));
                DrawTasks.DivergingCirclesParams params = new DrawTasks.DivergingCirclesParams();
                params.color = Color.red;
                DrawTasks.divergingCircles(x, y, params);
                return;
            }
        }
    }
}
