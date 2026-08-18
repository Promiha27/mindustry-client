package eui.ui.alerts;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Timer;
import mindustry.game.EventType.ClientLoadEvent;

/**
 * An alert that hooks/unhooks its own event listener(s) as a unit, so flipping "eui-ShowAlerts" off
 * actually detaches every alert's listeners (not just a "drop the toast" guard inside them) and back on
 * re-attaches them cleanly. {@link LosingSupport}/{@link UnderAttack} each register one instance with
 * their own start/cancel hooks. Ported from ui/alerts/alert.js.
 */
public class Alert{
    private static final Seq<Alert> alerts = new Seq<>();
    private static boolean prevStatus = false;

    static{
        Events.on(ClientLoadEvent.class, e -> Timer.schedule(Alert::pollSetting, 0, 1));
    }

    private final Runnable startHook, cancelHook;
    private boolean enabled = false;

    public Alert(Runnable startHook, Runnable cancelHook){
        this.startHook = startHook;
        this.cancelHook = cancelHook;
        alerts.add(this);
    }

    void start(){
        if(!enabled) startHook.run();
        enabled = true;
    }

    void cancel(){
        if(enabled) cancelHook.run();
        enabled = false;
    }

    static void pollSetting(){
        boolean status = Core.settings.getBool("eui-ShowAlerts", true);
        if(status != prevStatus){
            if(status){
                for(Alert a : alerts) a.start();
            }else{
                for(Alert a : alerts) a.cancel();
            }
            prevStatus = status;
        }
    }
}
