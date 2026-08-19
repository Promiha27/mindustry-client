package eui;

import arc.Core;
import arc.Events;
import arc.util.Log;
import eui.core.ButtonSetting;
import eui.core.LabelSetting;
import eui.input.ConveyorDrag;
import eui.input.CoreDrag;
import eui.input.Drag;
import eui.interact.ActionDelayHotkey;
import eui.interact.AutofillPriorityDialog;
import eui.interact.AutoUnit;
import eui.interact.SchematicSelector;
import eui.other.ExtendZoom;
import eui.other.Mine;
import eui.ui.alerts.LosingSupport;
import eui.ui.alerts.UnderAttack;
import eui.ui.blocks.BlockInfoUi;
import eui.ui.blocks.EfficiencyOverlay;
import eui.ui.blocks.ProgressBarOverlay;
import eui.ui.other.BottomPanelUi;
import eui.ui.other.ResourceRateUi;
import eui.ui.other.SchematicsImportExport;
import eui.ui.other.SchematicsTableUi;
import eui.ui.units.DrawCycle;
import eui.ui.units.UnitsTableUi;
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
 * that file's {@code coreModules} loop, minus the try/catch-per-module wrapper Rhino needed. Every
 * feature module from the source is ported and wired in below, grouped by the three porting phases they
 * landed in (input/automation, HUD/overlays, schematics-table/settings/bottom-panel) - see each feature's
 * own class javadoc for what it does and any behavioural notes from the port.
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
 * <li>RESOLVED (dedupe pass): {@code eui.interact.AutoFill} vs. this client's own
 * {@code mindustry.client.utils.AutoTransfer} - both forked from the same Extended-UI upstream and did
 * overlapping core item hand-off. The eui loop and its bottom-panel toggle ("eui-auto-fill", plus the
 * "eui-interact-core" gate only it read) were removed; its unique value - the per-block-type priority
 * config ({@link AutofillPriorityDialog}, key "eui.autofill.priority") - was merged into AutoTransfer as
 * a service-order modifier (-2 = exclude a block). The dialog stays, now configuring AutoTransfer.</li>
 * <li>{@link ExtendZoom} vs. this fork's native "Min Zoom" settings slider (Settings > Client, key
 * "minzoom") - see {@link ExtendZoom}'s own javadoc; this port is largely inert here.</li>
 * <li>{@link SchematicSelector} (bottom-panel toggle + drag) vs. this engine's own native
 * {@code Binding.schematicSelect} (hold "F", drag over built tiles - see
 * {@code DesktopInput.java}'s handling of it) - both do the exact same "select an area, capture it as a
 * schematic, arm it for placement" job. The native one only works while the player has a controllable
 * unit (same limitation qol-suite's own {@code CopyAnywhereFeature} exists to lift for spectators); this
 * port's version has no such requirement either, so the two are close to fully redundant for a player
 * who does have a unit.</li>
 * <li>{@link ConveyorDrag} ("eui-DragPathfind") vs. this engine's own native conveyor drag-placement,
 * which already routes around obstacles automatically ({@code Placement.pathfindLine(...)}, gated by the
 * native "conveyorpathfinding" setting, called from {@code InputHandler.java} whenever a conveyor/rail is
 * drag-placed normally) - no separate armed tool needed there at all. {@link CoreDrag} ("eui-DragBlock",
 * drag from a core to auto-place a vault) has no such native equivalent found, so it isn't redundant.</li>
 * <li>{@code eui-showMinimap} vs. the native Settings > Graphics "Minimap" checkbox
 * ({@code SettingsMenuDialog.java}'s {@code graphics.checkPref("minimap", ...)}) - both write the exact
 * same {@code Core.settings} key ("minimap"), so this is a second toggle for the same switch rather than
 * a functional collision; whichever was flipped most recently wins, harmlessly.</li>
 * </ul>
 */
public class EUIMod{
    private AutofillPriorityDialog autofillPriorityDialog;
    private SchematicsImportExport schematicsImportExport;
    private boolean lastMinimapSetting;

    public EUIMod(){
        //self-disable if the original jar/script mod is still dropped in the mods folder - two copies
        //would double-register every event handler. "extended-ui" is this mod's internal id from its own
        //mod.hjson (displayName "Extended UI++", but the id itself was never renamed - see memory).
        if(mindustry.Vars.mods.locateMod("extended-ui") != null){
            Log.info("[eui] External extended-ui script mod is also loaded - baked-in copy is standing down.");
            return;
        }

        //--- phase A: input/automation ---
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

        //--- phase B: HUD/overlays ---
        new BlockInfoUi();
        new EfficiencyOverlay();
        new ProgressBarOverlay();
        new DrawCycle();
        new UnitsTableUi();
        new ResourceRateUi();
        new LosingSupport();
        new UnderAttack();

        //--- phase C: schematics table, import/export, bottom panel ---
        SchematicsTableUi schematicsTableUi = new SchematicsTableUi();
        schematicsImportExport = new SchematicsImportExport(schematicsTableUi);
        new BottomPanelUi(autofillPriorityDialog);

        //settings-ui.js's one remaining piece of standalone logic (not a "feature" of its own) - the
        //mod's own "eui-showMinimap" toggle just proxies the engine's real "minimap" setting
        lastMinimapSetting = Core.settings.getBool("eui-showMinimap", true);
        Core.settings.put("minimap", lastMinimapSetting);
        Events.run(Trigger.update, () -> {
            boolean current = Core.settings.getBool("eui-showMinimap", true);
            if(current != lastMinimapSetting){
                Core.settings.put("minimap", current);
                lastMinimapSetting = current;
            }
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

            table.pref(new LabelSetting("eui-hud-header", Core.bundle.get("eui.hud.title", "HUD")));
            table.checkPref("eui-ShowBlockInfo", true);
            table.checkPref("eui-showPowerBar", true);
            table.checkPref("eui-showFactoryProgress", true);
            table.checkPref("eui-ShowResourceRate", false);
            table.checkPref("eui-ShowAlerts", true);
            table.checkPref("eui-ShowAlertsBottom", false);

            table.pref(new LabelSetting("eui-units-header", Core.bundle.get("eui.units.title", "Units")));
            table.checkPref("eui-showUnitBar", true);
            table.checkPref("eui-ShowUnitTable", true);
            table.sliderPref("eui-UnitsTableAlpha", 100, 0, 100, 5, i -> i + "%");
            table.checkPref("eui-TrackPlayerCursor", false);
            table.sliderPref("eui-playerCursorStyle", 7, 1, 7, 1, i -> i + "");
            table.checkPref("eui-ShowOwnCursor", false);
            table.checkPref("eui-TrackLogicControl", false);

            table.pref(new LabelSetting("eui-efficiency-header", Core.bundle.get("eui.efficiency.title", "Efficiency")));
            table.checkPref("eui-ShowEfficiency", false);
            table.sliderPref("eui-EfficiencyTimer", 15, 10, 180, 5, i -> i + "s");

            table.pref(new LabelSetting("eui-schematics-header", Core.bundle.get("eui.schematics.title", "Schematics table")));
            table.checkPref("eui-ShowSchematicsTable", true);
            table.checkPref("eui-ShowSchematicsPreview", true);
            table.sliderPref("eui-SchematicsTableRows", 4, 2, 20, 1, i -> i + "");
            table.sliderPref("eui-SchematicsTableColumns", 5, 4, 16, 1, i -> i + "");
            table.sliderPref("eui-SchematicsTableButtonSize", 30, 20, 80, 2, i -> i + "");
            table.sliderPref("eui-SchematicsTableX", 10, 0, 2000, 10, i -> i + "px");
            table.sliderPref("eui-SchematicsTableY", 160, 0, 2000, 10, i -> i + "px");
            table.sliderPref("eui-SchematicsTableAlpha", 100, 0, 100, 5, i -> i + "%");
            table.pref(new ButtonSetting("eui-schematics-export", () -> schematicsImportExport.showExportDialog()));
            table.pref(new ButtonSetting("eui-schematics-import", () -> schematicsImportExport.showImportDialog()));

            table.pref(new LabelSetting("eui-misc-header", Core.bundle.get("eui.misc.title", "Misc")));
            table.checkPref("eui-showMinimap", true);
            table.checkPref("eui-showInteractSettings", true);
        });
    }
}
