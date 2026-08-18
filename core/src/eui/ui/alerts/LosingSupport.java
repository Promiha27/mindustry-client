package eui.ui.alerts;

import arc.Events;
import arc.func.Cons;
import arc.util.Time;
import eui.units.SupportUnits;
import eui.util.OutputWrapper;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Unit;

import static mindustry.Vars.player;

/**
 * Warns once per game (within the first 5 minutes) the first time one of your own support units
 * ({@link SupportUnits}) dies - a support unit lost early is often a sign you're about to lose more than
 * just that unit. Ported from ui/alerts/losing-support.js.
 */
public class LosingSupport{
    private static final float maxTime = 60 * 300; //5 minutes of ticks

    private boolean sent;
    private float timer;

    //Events.remove needs the exact same listener instance that was registered - see arc.Events'
    //javadoc - so this has to be a stored field, not a fresh method reference at each on()/remove() call
    private final Cons<UnitDestroyEvent> listener = this::onUnitDestroy;

    public LosingSupport(){
        Events.on(WorldLoadEvent.class, e -> {
            sent = false;
            timer = Time.time;
        });

        new Alert(
            () -> Events.on(UnitDestroyEvent.class, listener),
            () -> Events.remove(UnitDestroyEvent.class, listener)
        );
    }

    void onUnitDestroy(UnitDestroyEvent event){
        Unit unit = event.unit;
        if(sent || !SupportUnits.includes(unit.type.name) || unit.team != player.team()) return;
        if(Time.time - timer < maxTime){
            OutputWrapper.ingameAlert(arc.Core.bundle.get("alerts.losing-support"));
            sent = true;
        }
    }
}
