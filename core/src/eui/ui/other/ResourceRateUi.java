package eui.ui.other;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.util.Interval;
import arc.util.Timer;
import eui.util.Difference;
import eui.util.Formatting;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.type.Item;
import mindustry.ui.Styles;

import static mindustry.Vars.player;
import static mindustry.Vars.ui;

/**
 * "eui-ShowResourceRate": swaps the vanilla core-items HUD panel's collapsible content for one that also
 * shows each item's production rate (a signed "+N"/"-N" per second next to the raw count, via
 * {@link Difference}, one tracker per item type), and swaps it back to the original when the setting is
 * off. Both versions get wrapped with {@link PowerUi}'s power bar underneath. Ported from
 * ui/other/resource-rate-ui.js.
 * <p>
 * Reaches into the vanilla core-info widget tree the same way the source did:
 * {@code hudGroup.find("coreinfo")} -&gt; its 2nd child (the inner table built by
 * {@code HudFragment}'s {@code t.table(c -> {...})}) -&gt; ITS 1st child (the {@link Collapser} wrapping
 * {@code coreItems}, from {@code c.collapser(coreItems, ...)}) - see {@code HudFragment.java} around its
 * {@code t.name = "coreinfo"} block for the real structure this assumes; if that ever changes this
 * degrades to a no-op (null checks bail out) rather than crashing.
 */
public class ResourceRateUi{
    private final ObjectMap<Item, Difference> diffs = new ObjectMap<>();
    /* перф: перестройка таблицы (50+ виджетов) каждый кадр не нужна — троттлим до ~6 Гц,
     * {@link Difference} интерполирует по абсолютному Time.time, так что редкие сэмплы дают те же числа */
    private final Interval rebuildTimer = new Interval();

    private Table contentTable;
    private Collapser coreItemsCollapser;
    private Table oldCoreItemsTable;

    private boolean isReplaced = false;
    private boolean booted = false;

    public ResourceRateUi(){
        Events.on(ClientLoadEvent.class, e -> {
            contentTable = new Table(Styles.black6);
            contentTable.pack();

            Table coreInfo = ui.hudGroup.find("coreinfo");
            if(coreInfo == null || coreInfo.getChildren().size < 2) return;

            if(!(coreInfo.getChildren().get(1) instanceof Table inner) || inner.getChildren().isEmpty()) return;
            if(!(inner.getChildren().get(0) instanceof Collapser collapser)) return;
            coreItemsCollapser = collapser;

            if(collapser.getChildren().size > 0 && collapser.getChildren().get(0) instanceof Table t) oldCoreItemsTable = t;

            Timer.schedule(this::updateSwap, 0, 3);
        });

        Events.run(Trigger.update, () -> {
            if(isReplaced && rebuildTimer.get(10f)) rebuildTable();
        });
    }

    void updateSwap(){
        if(coreItemsCollapser == null) return;

        if(Core.settings.getBool("eui-ShowResourceRate", false)){
            if(!isReplaced || !booted){
                Table resourceTable = PowerUi.createTableWithBarFrom(contentTable);
                isReplaced = true;
                booted = true;
                coreItemsCollapser.setTable(resourceTable);
            }
        }else{
            if(isReplaced || !booted){
                Table resourceTable = PowerUi.createTableWithBarFrom(oldCoreItemsTable);
                isReplaced = false;
                booted = true;
                coreItemsCollapser.setTable(resourceTable);
            }
        }
    }

    void rebuildTable(){
        contentTable.clearChildren();
        buildTable();
    }

    void buildTable(){
        Table resourcesTable = contentTable.table().get();
        int[] i = {0};

        player.team().items().each((item, amount) -> {
            Difference diff = diffs.get(item);
            if(diff == null){
                diff = new Difference(1000, amount);
                diffs.put(item, diff);
            }

            float difference = diff.difference(amount);
            String color = difference >= 0 ? "[green]" : "[red]";
            String sign = difference >= 0 ? "+" : "";
            long roundedDiff = Math.round(difference);
            long shownAmount = amount;

            resourcesTable.image(item.uiIcon).left();
            resourcesTable.label(() -> Formatting.numberToString(shownAmount)).padLeft(2f).left().padRight(1f);
            resourcesTable.label(() -> "(" + color + sign + Formatting.numberToString(roundedDiff) + "[white])").left().padRight(2f);

            if(++i[0] % 4 == 0) resourcesTable.row();
        });

        contentTable.row();
    }
}
