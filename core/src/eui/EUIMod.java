package eui;

import arc.Core;
import arc.Events;
import arc.util.Log;
import eui.core.LabelSetting;
import eui.input.ConveyorDrag;
import eui.input.CoreDrag;
import eui.input.Drag;
import eui.interact.ActionDelayHotkey;
import eui.interact.AutoFill;
import eui.interact.AutofillPriorityDialog;
import eui.interact.AutoUnit;
import eui.interact.SchematicSelector;
import eui.other.ExtendZoom;
import eui.other.Mine;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Icon;

import static mindustry.Vars.mobile;
import static mindustry.Vars.ui;

/**
 * Baked directly into the client as native code (not loaded via the mod system), same approach as
 * {@link qol.QolSuiteMod} - see its javadoc and {@code mindustry.client.Main.init()} for why this has to
 * be instantiated before {@code ClientLoadEvent} fires. This is Extended UI++'s own independent
 * {@code eui.*} package / {@code eui-*} settings-key namespace - deliberately not sharing any of
 * qol-suite's classes (own {@link eui.core.LabelSetting}/{@link eui.core.ButtonSetting} instead of qol's,
 * own settings category), so either baked-in mod can be disabled/removed on its own without touching the
 * other.
 * <p>
 * Ported from the "extended-ui" Rhino mod's {@code scripts/main.js}, which loaded every feature module
 * through a single-point-of-failure-tolerant {@code require()} chain (see {@link eui.input.Drag}'s
 * javadoc for why that indirection isn't needed here) - this constructor is the direct Java equivalent of
 * that file's {@code coreModules} loop, minus the try/catch-per-module wrapper Rhino needed. Features are
 * added here incrementally, phase by phase (see the task history) - not every JS module is ported yet;
 * each phase's features are wired in as they land.
 * <p>
 * The settings category is likewise simpler than the source: {@code ui/other/settings-ui.js} hid almost
 * all of its prefs behind a separate custom dialog opened from a single button, purely to work around
 * Rhino not being able to natively subclass {@code SettingsMenuDialog.SettingsTable.Setting} (it had to
 * fall back to a dynamic {@code extend(...)} hack, with its own try/catch fallback for when that hack
 * failed). {@link eui.core.LabelSetting}/{@link eui.core.ButtonSetting} are ordinary compiled Java
 * subclasses with no such risk, so every pref lives directly in one flat "Extended UI++" category here -
 * same as {@code qol.QolSuiteMod.buildSettings()} already does.
 * <p>
 * KNOWN COLLISIONS with already-baked-in client/qol-suite functionality (ported anyway, per the task
 * spec - just flagged here):
 * <ul>
 * <li>{@link AutoFill} vs. this client's own {@code mindustry.client.utils.AutoTransfer} - both fork from
 * the same Extended-UI upstream and do overlapping core item hand-off, through different settings keys
 * ("autotransfer" vs "eui-auto-fill"). See {@link AutoFill}'s own javadoc.</li>
 * <li>{@link ExtendZoom} vs. this fork's native "Min Zoom" settings slider (Settings > Client, key
 * "minzoom") - see {@link ExtendZoom}'s own javadoc; this port is largely inert here.</li>
 * </ul>
 */
public class EUIMod{
    private AutofillPriorityDialog autofillPriorityDialog;

    public EUIMod(){
        //self-disable if the original jar/script mod is still dropped in the mods folder - two copies
        //would double-register every event handler. "extended-ui" is this mod's internal id from its own
        //mod.hjson (displayName "Extended UI++", but the id itself was never renamed - see memory).
        if(mindustry.Vars.mods.locateMod("extended-ui") != null){
            Log.info("[eui] External extended-ui script mod is also loaded - baked-in copy is standing down.");
            return;
        }

        //--- phase A: input/automation ---
        new AutoFill();
        new AutoUnit();
        autofillPriorityDialog = new AutofillPriorityDialog();
        new ActionDelayHotkey();
        new ExtendZoom();
        new Mine();

        ConveyorDrag conveyorDrag = new ConveyorDrag();
        CoreDrag coreDrag = new CoreDrag();

        Events.run(Trigger.update, Drag::update);
        Events.run(Trigger.draw, () -> {
            conveyorDrag.draw();
            coreDrag.draw();
            SchematicSelector.draw();
        });

        Events.on(ClientLoadEvent.class, e -> buildSettings());
    }

    /**
     * One shared "Extended UI++" category, sectioned by a bold {@link LabelSetting} header per group of
     * related prefs - grows across phases as more features land. The auto-fill priority dialog and the
     * schematic area-select tool aren't opened from here (matching the source): both are opened from the
     * bottom panel's buttons instead (phase C), not the settings screen.
     */
    void buildSettings(){
        ui.settings.addCategory(Core.bundle.get("eui.settings", "Extended UI++"), Icon.settings, table -> {
            table.pref(new LabelSetting("eui-interact-header", Core.bundle.get("eui.interact.title", "Interact")));
            table.sliderPref("eui-action-delay", 500, 0, 3000, 25, i -> i + " ms");
            table.checkPref("eui-makeMineble", false);
            if(!mobile){
                table.checkPref("eui-DragBlock", false);
                table.checkPref("eui-DragPathfind", false);
            }

            table.pref(new LabelSetting("eui-camera-header", Core.bundle.get("eui.camera.title", "Camera")));
            table.sliderPref("eui-maxZoom", 10, 1, 10, 1, i -> i + "");
        });
    }
}
