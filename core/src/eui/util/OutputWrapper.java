package eui.util;

import arc.Core;
import arc.Events;
import arc.scene.actions.Actions;
import arc.scene.style.Drawable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.math.Interp;
import arc.util.Align;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Icon;
import mindustry.gen.Tex;

import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/**
 * A rate-limited toast queue: alerts (losing-support, under-attack, ...) call {@link #ingameAlert} to
 * queue a warning-icon popup, at most one shown at a time with a per-message minimum display time -
 * without this, several alerts firing in the same tick (e.g. a base-wide multi-block explosion) would
 * all pop at once and instantly overlap/replace each other. Ported from utils/output-wrapper.js.
 */
public class OutputWrapper{
    private static final int maxQueueSize = 50;
    private static long nextTimeMs = System.currentTimeMillis() + 10000;
    private static final Seq<QueueItem> queue = new Seq<>();

    static{
        Events.run(Trigger.update, OutputWrapper::pump);
    }

    interface Delayer{
        boolean delay();
    }

    static class QueueItem{
        final Runnable sender;
        final Delayer delayer;
        final float showTime; //seconds

        QueueItem(Runnable sender, Delayer delayer, float showTime){
            this.sender = sender;
            this.delayer = delayer;
            this.showTime = showTime;
        }
    }

    public static void debug(String text){
        addInQueue(() -> ui.announce(text, 10), () -> false, 10);
    }

    public static void ingameAlert(String text){
        addInQueue(() -> showToast(Icon.warning, text), () -> !ui.hudfrag.shown, 5);
    }

    static void addInQueue(Runnable sender, Delayer delayer, float showTime){
        queue.add(new QueueItem(sender, delayer, showTime));
        if(queue.size > maxQueueSize) queue.remove(0);
    }

    static void pump(){
        QueueItem item = queuePop();
        if(item != null) item.sender.run();
    }

    static QueueItem queuePop(){
        long now = System.currentTimeMillis();
        if(nextTimeMs > now || queue.isEmpty()) return null;

        QueueItem item = queue.remove(0);

        if(item.delayer.delay()){
            queue.add(item);
            nextTimeMs = now + 1000;
            return null;
        }

        nextTimeMs = now + (long)(item.showTime * 1000);
        return item;
    }

    /** From https://github.com/QmelZ/hackustry/blob/master/scripts/libs/toast.js (per the source's own comment). */
    static void showToast(Drawable icon, String text){
        if(icon == null || text == null) return;

        Table table = new Table(Tex.button);
        table.update(() -> {
            if(!ui.hudfrag.shown) table.remove();
        });
        table.margin(12f);
        table.image(icon).pad(3f);
        table.add(text).wrap().width(280f).get().setAlignment(Align.center, Align.center);
        table.pack();

        Table container = Core.scene.table();
        if(Core.settings.getBool("eui-ShowAlertsBottom", false)){
            //TODO (source): what is this random numbers? (4.2, 4.8)
            container.setTranslation(0, -table.getMarginBottom() * 4.2f);
            if(state.isMenu()) container.bottom().left().add(table); else container.bottom().add(table);
            container.actions(
                Actions.translateBy(0, table.getMarginBottom() * 4.2f, 1, Interp.fade),
                Actions.delay(2),
                Actions.run(() -> container.actions(
                    Actions.translateBy(0, -table.getMarginBottom() * 4.8f, 1, Interp.fade),
                    Actions.remove()
                ))
            );
        }else{
            if(state.isMenu()) container.top().right().add(table); else container.top().add(table);
            container.setTranslation(0, table.getPrefHeight());
            container.actions(
                Actions.translateBy(0, -table.getPrefHeight(), 1, Interp.fade),
                Actions.delay(2.5f),
                Actions.run(() -> container.actions(
                    Actions.translateBy(0, table.getPrefHeight(), 1, Interp.fade),
                    Actions.remove()
                ))
            );
        }
    }
}
