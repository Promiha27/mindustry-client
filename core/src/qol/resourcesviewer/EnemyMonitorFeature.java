package qol.resourcesviewer;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Item;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.blocks.power.PowerGraph;
import qol.core.Feature;
import qol.core.SafeSettings;
import qol.ui.QolWindow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static mindustry.Vars.content;
import static mindustry.Vars.player;
import static mindustry.Vars.state;

/**
 * Ported from the standalone Resources Viewer script mod (JS) - despite the name, it originally only
 * watched the ENEMY's power balance and core resources, not your own: a power graph running a deficit,
 * a tracked item dropping past its threshold, or an item hitting zero after having had some, each get a
 * log line in {@link EnemyMonitorWindow}. The original's own hand-positioned HUD panel (settings sliders
 * for x/y) was replaced by the shared {@link qol.ui.QolWindow} drag-to-reposition, same as every other
 * feature's panel; the per-item enable/threshold configuration survives as a dialog opened from the
 * window's title bar, using the exact same {@code spy_item_*}/{@code spy_threshold_*} settings keys so an
 * existing standalone install's tuning carries over.
 * <p>
 * On top of the original enemy-only watching, this also optionally watches YOUR OWN base: unlike the
 * enemy check (alarms once a tracked item is both falling AND already below a low absolute floor), the
 * own-base check ({@link #checkOwnBase}) alarms purely on RATE - a tracked item losing more than
 * {@code spySelfDropThreshold} units within one ~1-second check tick, regardless of how much is left.
 * The point isn't "you're running low" (you can just look at your own resource bar for that) but "this
 * is draining unusually fast right now", which is exactly what a static floor can't catch.
 */
public class EnemyMonitorFeature implements Feature{
    static final int MAX_LOGS = 8;
    static final float LOG_LIFETIME_MS = 8000f;
    static final float CHECK_INTERVAL_TICKS = 60f;

    final Map<Integer, Map<String, Integer>> lastResourceCount = new HashMap<>();
    final Map<Integer, Set<String>> hadResources = new HashMap<>();
    final Seq<LogEntry> logList = new Seq<>();

    final Map<String, Boolean> enabledItems = new HashMap<>();
    final Map<String, Integer> itemThresholds = new HashMap<>();

    float checkTimer = 0f;
    EnemyMonitorWindow window;

    @Override
    public String id(){
        return "resources-viewer";
    }

    @Override
    public String titleKey(){
        return "qol.feature.resources-viewer.title";
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
        loadItemSettings();
        window = new EnemyMonitorWindow(this);

        Events.on(WorldLoadEvent.class, e -> {
            lastResourceCount.clear();
            hadResources.clear();
            logList.clear();
            window.rebuild();
        });
        Events.run(Trigger.update, this::update);
    }

    @Override
    public void buildSettings(SettingsTable table){
        table.checkPref("watchOwnResources", true);
        table.sliderPref("spySelfDropThreshold", 300, 50, 2000, 50, i -> i + "/" + Core.bundle.get("resources-viewer-per-sec"));
    }

    boolean watchOwnResources(){
        return SafeSettings.getBool("watchOwnResources", true);
    }

    int selfDropThreshold(){
        return SafeSettings.getInt("spySelfDropThreshold", 300);
    }

    void loadItemSettings(){
        for(Item item : content.items()){
            enabledItems.put(item.name, SafeSettings.getBool("spy_item_" + item.name, true));
            itemThresholds.put(item.name, parseThreshold(SafeSettings.getString("spy_threshold_" + item.name, "500")));
        }
    }

    /**
     * The original standalone mod always stored this setting as a string (JS {@code String(parsed)}),
     * never a native int - {@link Core#settings} is a single untyped key-value store shared by every
     * mod, so a leftover value from that mod (or a hand-edited settings file) can be a string of
     * anything, not just digits. Reading it with {@code getInt} crashes outright on the type mismatch
     * (arc.Settings casts internally instead of converting); reading it as a string first and parsing
     * defensively here is what actually matches the value's real, original type.
     */
    static int parseThreshold(String raw){
        try{
            return Integer.parseInt(raw.trim());
        }catch(NumberFormatException e){
            return 500;
        }
    }

    boolean itemEnabled(Item item){
        return enabledItems.getOrDefault(item.name, true);
    }

    int itemThreshold(Item item){
        return itemThresholds.getOrDefault(item.name, 500);
    }

    void setItemEnabled(Item item, boolean enabled){
        enabledItems.put(item.name, enabled);
        Core.settings.put("spy_item_" + item.name, enabled);
    }

    void setItemThreshold(Item item, int threshold){
        itemThresholds.put(item.name, threshold);
        Core.settings.put("spy_threshold_" + item.name, String.valueOf(threshold));
    }

    void addLog(String logId, String text){
        logList.removeAll(l -> l.id.equals(logId));
        logList.add(new LogEntry(logId, text, Time.millis()));
        if(logList.size > MAX_LOGS) logList.remove(0);
        window.rebuild();
    }

    String teamTag(TeamData enemyData){
        return "[#" + enemyData.team.color + "]" + enemyData.team.localized() + "[]";
    }

    void update(){
        if(state.isMenu() || player == null || player.team() == null) return;

        boolean removedOld = logList.size > 0 && logList.contains(l -> Time.timeSinceMillis(l.time) > LOG_LIFETIME_MS);
        if(removedOld){
            logList.removeAll(l -> Time.timeSinceMillis(l.time) > LOG_LIFETIME_MS);
            window.rebuild();
        }

        if(!isEnabled()) return;

        checkTimer += Time.delta;
        if(checkTimer < CHECK_INTERVAL_TICKS) return;
        checkTimer = 0f;

        try{
            Team myTeam = player.team();
            Map<Integer, PowerEntry> powerByTeam = computePowerByTeam();
            checkEnemies(myTeam, powerByTeam);
            if(watchOwnResources()) checkOwnBase(myTeam, powerByTeam);
        }catch(Exception e){
            Log.err("[resources-viewer] check loop failed", e);
        }
    }

    Map<Integer, PowerEntry> computePowerByTeam(){
        Map<Integer, PowerEntry> powerByTeam = new HashMap<>();
        Groups.build.each(b -> {
            if(b.power == null || b.power.graph == null) return;
            PowerEntry entry = powerByTeam.computeIfAbsent(b.team.id, k -> new PowerEntry());
            PowerGraph graph = b.power.graph;
            if(entry.graphs.add(graph)){
                entry.balance += graph.getPowerBalance();
            }
        });
        return powerByTeam;
    }

    void checkEnemies(Team myTeam, Map<Integer, PowerEntry> powerByTeam){
        for(TeamData enemyData : state.teams.getActive()){
            if(enemyData.team == myTeam) continue;
            int teamId = enemyData.team.id;

            PowerEntry powerEntry = powerByTeam.get(teamId);
            if(powerEntry != null && powerEntry.balance < 0){
                int powerPerSec = Math.round(powerEntry.balance * 60);
                String powerText = "[crimson]⚡[] " + teamTag(enemyData) + " " + Core.bundle.get("resources-viewer-power-deficit") + ": [scarlet]" + powerPerSec + "/" + Core.bundle.get("resources-viewer-per-sec") + "[]";
                addLog(teamId + "_power", powerText);
            }

            Building core = enemyData.core();
            if(core == null) continue;

            Map<String, Integer> lastCounts = lastResourceCount.computeIfAbsent(teamId, k -> new HashMap<>());
            Set<String> had = hadResources.computeIfAbsent(teamId, k -> new HashSet<>());

            for(Item item : content.items()){
                if(!itemEnabled(item)) continue;

                int currentAmount = core.items.get(item);
                Integer previousAmount = lastCounts.get(item.name);
                String logId = teamId + "_" + item.name;
                int threshold = itemThreshold(item);

                if(currentAmount > 0) had.add(item.name);

                if(currentAmount == 0 && had.contains(item.name)){
                    String zeroText = "[scarlet]❌[] " + teamTag(enemyData) + " " + item.localizedName + ": [scarlet]" + Core.bundle.get("resources-viewer-zero") + " (0)[]";
                    addLog(logId, zeroText);
                }else if(previousAmount != null && currentAmount < previousAmount && currentAmount <= threshold){
                    int difference = previousAmount - currentAmount;
                    String alarmText = "[red]▼[] " + teamTag(enemyData) + " " + item.localizedName + ": [orange]-" + difference + "[] [gray](" + Core.bundle.get("resources-viewer-left") + ": " + currentAmount + ")[] [scarlet]⚠ " + Core.bundle.get("resources-viewer-alarm") + "[]";
                    addLog(logId, alarmText);
                }

                lastCounts.put(item.name, currentAmount);
            }
        }
    }

    void checkOwnBase(Team myTeam, Map<Integer, PowerEntry> powerByTeam){
        int teamId = myTeam.id;

        PowerEntry powerEntry = powerByTeam.get(teamId);
        if(powerEntry != null && powerEntry.balance < 0){
            int powerPerSec = Math.round(powerEntry.balance * 60);
            String powerText = "[crimson]⚡[] " + ownTag() + " " + Core.bundle.get("resources-viewer-power-deficit") + ": [scarlet]" + powerPerSec + "/" + Core.bundle.get("resources-viewer-per-sec") + "[]";
            addLog(teamId + "_power", powerText);
        }

        TeamData myData = state.teams.get(myTeam);
        Building core = myData == null ? null : myData.core();
        if(core == null) return;

        Map<String, Integer> lastCounts = lastResourceCount.computeIfAbsent(teamId, k -> new HashMap<>());
        int dropThreshold = selfDropThreshold();

        for(Item item : content.items()){
            if(!itemEnabled(item)) continue;

            int currentAmount = core.items.get(item);
            Integer previousAmount = lastCounts.get(item.name);
            String logId = teamId + "_" + item.name;

            if(previousAmount != null && currentAmount < previousAmount){
                int difference = previousAmount - currentAmount;
                if(difference >= dropThreshold){
                    String alarmText = "[red]▼[] " + ownTag() + " " + item.localizedName + ": [orange]-" + difference + "/" + Core.bundle.get("resources-viewer-per-sec") + "[] [scarlet]⚠ " + Core.bundle.get("resources-viewer-dropping-fast") + "[]";
                    addLog(logId, alarmText);
                }
            }

            lastCounts.put(item.name, currentAmount);
        }
    }

    String ownTag(){
        return "[white]" + Core.bundle.get("resources-viewer-your-base", "Your base") + "[]";
    }

    static class PowerEntry{
        float balance = 0f;
        Set<PowerGraph> graphs = new HashSet<>();
    }

    static class LogEntry{
        final String id, text;
        final long time;

        LogEntry(String id, String text, long time){
            this.id = id;
            this.text = text;
            this.time = time;
        }
    }
}
