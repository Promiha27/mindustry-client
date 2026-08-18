package qol.resourceforecast;

import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.type.Item;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import qol.core.Feature;
import qol.ui.QolWindow;

import static mindustry.Vars.content;
import static mindustry.Vars.player;
import static mindustry.Vars.state;

/**
 * Live forecast for the OWN core's resources: per item, the smoothed fill/drain rate and an ETA -
 * "empty in 0:45" for a draining item, "full in 2:10" for a filling one. The window shows the most
 * urgent drains first, so "silicon runs out during the next wave" is visible minutes ahead instead
 * of at the OUT-OF-X toast. Complements the enemy-resource monitor, which watches the OTHER teams;
 * this one is the dashboard for your own economy.
 * <p>
 * Rates are an exponential moving average over 1-second samples of the core's item module (shared
 * across all cores of the team) - raw deltas flap wildly with burst producers/consumers, the EMA
 * settles within a few seconds while still following real trend changes. Pure client-side reading
 * of already-synced state; works identically in multiplayer.
 */
public class ResourceForecastFeature implements Feature{
    /** Sampling period in ticks (1s) - also the window refresh rate. */
    static final float SAMPLE_INTERVAL_TICKS = 60f;
    /** EMA smoothing factor per sample: ~0.3 keeps ~3-4s of memory. */
    static final float EMA_ALPHA = 0.3f;
    /** Rates smaller than this (items/sec, either way) count as noise, not a trend. */
    static final float RATE_FLOOR = 0.15f;
    /** At most this many rows in the window, most urgent first. */
    static final int MAX_ROWS = 12;

    float[] prevAmount;
    float[] ema;
    boolean hasPrev = false;

    /** Prebuilt display rows (icon+amount+rate+ETA markup), most urgent drain first. */
    public final Seq<String> rows = new Seq<>();

    ResourceForecastWindow window;
    float sampleTimer = 0f;

    @Override
    public String id(){
        return "resource-forecast";
    }

    @Override
    public String titleKey(){
        return "qol.feature.resource-forecast.title";
    }

    @Override
    public boolean hasWindow(){
        return true;
    }

    @Override
    public QolWindow window(){
        return window;
    }

    @Override
    public void init(){
        prevAmount = new float[content.items().size];
        ema = new float[content.items().size];
        window = new ResourceForecastWindow(this);

        Events.on(WorldLoadEvent.class, e -> {
            hasPrev = false;
            java.util.Arrays.fill(ema, 0f);
            rows.clear();
            sampleTimer = 0f;
        });
        Events.run(Trigger.update, this::update);
    }

    @Override
    public void buildSettings(SettingsTable table){
        //nothing beyond the enable toggle and the window's own size slider
    }

    void update(){
        if(!state.isGame() || !isEnabled() || player == null) return;

        sampleTimer += Time.delta;
        if(sampleTimer < SAMPLE_INTERVAL_TICKS) return;
        float dtSec = sampleTimer / 60f;
        sampleTimer = 0f;

        CoreBuild core = player.team().core();
        if(core == null){
            rows.clear();
            refreshWindow();
            return;
        }

        for(Item item : content.items()){
            float cur = core.items.get(item);
            if(hasPrev){
                float rate = (cur - prevAmount[item.id]) / dtSec;
                ema[item.id] += (rate - ema[item.id]) * EMA_ALPHA;
            }
            prevAmount[item.id] = cur;
        }
        hasPrev = true;

        buildRows(core);
        refreshWindow();
    }

    void buildRows(CoreBuild core){
        rows.clear();

        //draining items, most urgent (soonest empty) first
        Seq<Item> draining = content.items().select(i -> ema[i.id] < -RATE_FLOOR && core.items.get(i) > 0);
        draining.sort(i -> core.items.get(i) / -ema[i.id]);
        for(Item item : draining){
            if(rows.size >= MAX_ROWS) return;
            float seconds = core.items.get(item) / -ema[item.id];
            rows.add(item.emoji() + " [lightgray]" + mindustry.core.UI.formatAmount(core.items.get(item))
                + " [scarlet]" + rateText(ema[item.id]) + " [white]→ 0 " + etaText(seconds));
        }

        //filling items that will actually hit the cap
        for(Item item : content.items()){
            if(rows.size >= MAX_ROWS) return;
            if(ema[item.id] <= RATE_FLOOR) continue;
            int cur = core.items.get(item);
            int room = core.storageCapacity - cur;
            if(room <= 0) continue;
            rows.add(item.emoji() + " [lightgray]" + mindustry.core.UI.formatAmount(cur)
                + " [heal]" + rateText(ema[item.id]) + " [white]→ full " + etaText(room / ema[item.id]));
        }
    }

    static String rateText(float rate){
        return (rate > 0 ? "+" : "") + (Math.abs(rate) >= 10f ? String.valueOf(Math.round(rate)) : String.valueOf(Math.round(rate * 10f) / 10f)) + "/s";
    }

    static String etaText(float seconds){
        if(seconds > 60f * 99f) return ">99m";
        int total = Math.max(0, Math.round(seconds));
        return (total / 60) + ":" + (total % 60 < 10 ? "0" : "") + (total % 60);
    }

    void refreshWindow(){
        if(window != null && window.attached() && !window.minimized){
            window.rebuild();
        }
    }
}
