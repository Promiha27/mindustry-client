package qol.payloaddrop;

import arc.Events;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.ObjectMap;
import arc.util.Time;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.CommandAI;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Payloadc;
import mindustry.gen.Unit;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import qol.core.Feature;
import qol.core.SafeSettings;
import qol.core.UnitClaims;

import static mindustry.Vars.player;
import static mindustry.Vars.state;

/**
 * Emergency cargo drop: a commandable carrier (mega, quad, oct - any {@link Payloadc} type) that
 * falls below the configured health fraction while holding payloads is switched to the vanilla
 * unload command, over the same {@link Call#setUnitCommand} RPC the command UI uses. The server
 * handles the actual dumping itself (CommandAI's unloadPayload branch runs server-side only), so
 * the carried units hit the ground even if the carrier dies a moment later - instead of vanishing
 * with it (or exploding, under the unitPayloadsExplode rule).
 * <p>
 * Once the hold is empty the unit's previous command is restored - except cargo-acquisition
 * commands (load units/blocks, loop), which would just re-pick-up what was dumped; those restore
 * to plain move (idle in place) instead. One emergency per low-health stretch: after an episode
 * ends - completed, timed out or manually overridden - the unit isn't touched again until its
 * health has recovered above the threshold, so reloading a still-wounded carrier on purpose isn't
 * fought. Manual re-command mid-drop = hands-off immediately (same sync-grace detection as
 * core-heal). Claims the unit via {@link UnitClaims} so core-heal can't draft a dumping mega.
 */
public class PayloadDropFeature implements Feature{
    /** How often (in ticks) carriers are scanned. Emergencies are time-critical - matches the fastest scans in the mod. */
    static final float SCAN_INTERVAL_TICKS = 30f;
    /** See CoreHealFeature - a just-sent command RPC needs a server round-trip before the client sees it. */
    static final long COMMAND_SYNC_GRACE_MS = 2000L;
    /**
     * Dropping can be physically impossible where the unit hovers (ground units over deep water,
     * blocks over non-buildable floor) - vanilla's unload command then just keeps hovering forever.
     * If the payload count hasn't shrunk for this long, give up and hand back the old command with
     * the cargo still aboard rather than leaving the unit stuck in place.
     */
    static final long STUCK_RESTORE_MS = 8000L;
    /** Health must recover this far above the threshold before a unit is eligible again - keeps the "recovered" check from flickering. */
    static final float REARM_MARGIN = 0.1f;

    /** id -> command the unit had before the emergency. Presence here = "dumping under this feature". */
    final IntMap<UnitCommand> savedCommand = new IntMap<>();
    /** id -> Time.millis() of our unload RPC, for the sync-grace check. */
    final IntMap<Long> lastCommandSent = new IntMap<>();
    /** id -> payload count last seen / when it last shrank, for the stuck-drop bailout. */
    final IntMap<Integer> lastPayCount = new IntMap<>();
    final IntMap<Long> lastPayShrink = new IntMap<>();
    /**
     * Units that already had their one emergency this low-health stretch. Cleared once the unit
     * heals past threshold + {@link #REARM_MARGIN} - until then it's never triggered again, so a
     * player deliberately reloading a wounded carrier isn't fought over the cargo.
     */
    final IntSet firedLow = new IntSet();

    final IntSeq dumpIds = new IntSeq();
    final IntSeq dropIds = new IntSeq();
    final IntSeq rearmIds = new IntSeq();
    final ObjectMap<UnitCommand, IntSeq> restoreBatch = new ObjectMap<>();

    float scanTimer = 0f;

    @Override
    public String id(){
        return "payload-drop";
    }

    @Override
    public String titleKey(){
        return "qol.feature.payload-drop.title";
    }

    @Override
    public void init(){
        Events.on(WorldLoadEvent.class, e -> {
            savedCommand.clear();
            lastCommandSent.clear();
            lastPayCount.clear();
            lastPayShrink.clear();
            firedLow.clear();
            scanTimer = 0f;
        });
        Events.run(Trigger.update, this::update);
    }

    @Override
    public void buildSettings(SettingsTable table){
        table.sliderPref("payloaddrop-hp", 25, 5, 75, 5, v -> v + "%");
    }

    float thresholdFrac(){
        return SafeSettings.getInt("payloaddrop-hp", 25) / 100f;
    }

    void drop(int id){
        savedCommand.remove(id);
        lastCommandSent.remove(id);
        lastPayCount.remove(id);
        lastPayShrink.remove(id);
        UnitClaims.release(id);
    }

    void update(){
        if(!state.isGame() || !isEnabled() || player == null || player.team().data() == null) return;

        scanTimer += Time.delta;
        if(scanTimer < SCAN_INTERVAL_TICKS) return;
        scanTimer = 0f;

        float threshold = thresholdFrac();

        //trigger pass: loaded carriers below the health threshold start dumping
        dumpIds.clear();
        player.team().data().units.each(u -> {
            if(!u.isCommandable() || !(u.controller() instanceof CommandAI ai)) return;
            if(!(u instanceof Payloadc pay) || !pay.hasPayload()) return;
            if(!u.type.commands.contains(UnitCommand.unloadPayloadCommand)) return;
            if(u.healthf() >= threshold) return;
            if(savedCommand.containsKey(u.id) || firedLow.contains(u.id)) return;
            if(UnitClaims.isClaimed(u.id)) return;

            UnitCommand current = ai.currentCommand();
            //the player beat us to it - that's their unload, don't adopt or restore-from it
            if(current == null || current == UnitCommand.unloadPayloadCommand) return;

            UnitClaims.claim(u.id);
            savedCommand.put(u.id, current);
            lastCommandSent.put(u.id, Time.millis());
            lastPayCount.put(u.id, pay.payloads().size);
            lastPayShrink.put(u.id, Time.millis());
            firedLow.add(u.id);
            dumpIds.add(u.id);
        });
        if(dumpIds.size > 0){
            Call.setUnitCommand(player, dumpIds.toArray(), UnitCommand.unloadPayloadCommand);
        }

        //maintenance pass: restore emptied (or hopelessly stuck) carriers, let go of anything the
        //player re-commanded manually
        dropIds.clear();
        restoreBatch.clear();
        for(IntMap.Entry<UnitCommand> entry : savedCommand){
            Unit u = Groups.unit.getByID(entry.key);
            if(u == null || !u.isValid() || u.team != player.team()
                || !(u.controller() instanceof CommandAI ai) || !(u instanceof Payloadc pay)){
                dropIds.add(entry.key);
                continue;
            }
            if(ai.currentCommand() != UnitCommand.unloadPayloadCommand){
                if(Time.timeSinceMillis(lastCommandSent.get(entry.key, 0L)) > COMMAND_SYNC_GRACE_MS){
                    dropIds.add(entry.key); //player took over - hands off (firedLow stays until healed)
                }
                continue;
            }

            int count = pay.payloads().size;
            if(count < lastPayCount.get(entry.key, 0)){
                lastPayCount.put(entry.key, count);
                lastPayShrink.put(entry.key, Time.millis());
            }

            if(!pay.hasPayload() || Time.timeSinceMillis(lastPayShrink.get(entry.key, 0L)) > STUCK_RESTORE_MS){
                //re-picking up the cargo we just dumped is the one thing an acquisition command
                //must not be handed back to do - those become plain move (= idle where it stands)
                UnitCommand back = entry.value;
                if(back == UnitCommand.loadUnitsCommand || back == UnitCommand.loadBlocksCommand
                    || back == UnitCommand.loopPayloadCommand){
                    back = UnitCommand.moveCommand;
                }
                restoreBatch.get(back, IntSeq::new).add(entry.key);
                dropIds.add(entry.key);
            }
        }
        for(int i = 0; i < dropIds.size; i++){
            drop(dropIds.get(i));
        }
        restoreBatch.each((command, ids) -> Call.setUnitCommand(player, ids.toArray(), command));

        //re-arm pass: a unit that healed clear of the threshold gets its one-shot back
        rearmIds.clear();
        for(IntSet.IntSetIterator it = firedLow.iterator(); it.hasNext;){
            int id = it.next();
            Unit u = Groups.unit.getByID(id);
            if(u == null || !u.isValid() || u.healthf() > threshold + REARM_MARGIN){
                rearmIds.add(id);
            }
        }
        for(int i = 0; i < rearmIds.size; i++){
            firedLow.remove(rearmIds.get(i));
        }
    }
}
