package eui.interact;

import arc.Core;
import arc.Events;
import arc.util.Time;
import mindustry.game.EventType.WorldLoadEvent;

/**
 * Shared cooldown gate between the "eui-action-delay" configured pause and any two automatic actions
 * (auto-fill, auto-unit). Ported from interact/interact-timer.js.
 */
public class InteractTimer{
    private static float timer = 0;

    static{
        Events.on(WorldLoadEvent.class, e -> timer = Time.time);
    }

    public static void increase(){
        timer = Time.time + Time.toSeconds * (Core.settings.getInt("eui-action-delay", 500) / 1000f);
        timer += 0.01f; //prevent overflow
    }

    public static boolean canInteract(){
        return Time.time >= timer;
    }
}
