package qol.schematicsanitizer;

import arc.Core;
import arc.util.Log;
import mindustry.game.Schematic;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import qol.core.Feature;

import static mindustry.Vars.schematics;
import static mindustry.Vars.ui;

/**
 * Strips broken tiles (a {@link Schematic.Stile} whose {@code block} is null) out of the schematic
 * library. Foo's client redraws every schematic's name label each frame and calls
 * {@code Schematic.requirements()} right in {@code draw()} (to tint the name red when the core can't
 * afford it), which reads {@code t.block.requirements} unguarded - one null-block tile anywhere in the
 * library hard-crashes the game the moment the schematics dialog is opened. Such tiles can't come from
 * .msch files ({@code Schematics.read} maps unknown blocks to air and drops them), only from some mod
 * constructing Stiles at runtime - the culprit mod is still unidentified, so this cleans up after it
 * generically.
 */
public class SchematicSanitizerFeature implements Feature{
    @Override
    public String id(){
        return "schematic-sanitizer";
    }

    @Override
    public String titleKey(){
        return "qol.feature.schematic-sanitizer.title";
    }

    @Override
    public void init(){
        //startup sweep catches schematics injected by other mods' load hooks; no toast - the HUD isn't
        //up yet and there's nobody mid-game to warn. Ordering vs those mods' ClientLoadEvent listeners
        //isn't guaranteed, which is why the dialog hook below is the actual safety net.
        sanitize(false);

        //runs on every open, before the first draw() of the frame that would crash - this also covers
        //broken schematics created mid-session, long after the startup sweep
        ui.schematics.shown(() -> sanitize(true));
    }

    @Override
    public void buildSettings(SettingsTable table){
    }

    void sanitize(boolean toast){
        if(!isEnabled()) return;

        int removed = 0;
        StringBuilder names = new StringBuilder();
        for(Schematic s : schematics.all()){
            int before = s.tiles.size;
            s.tiles.removeAll(t -> t.block == null);
            int diff = before - s.tiles.size;
            if(diff == 0) continue;

            removed += diff;
            if(names.length() > 0) names.append(", ");
            names.append(s.name());
            //the schematic's name is the best lead on which mod built it - keep it loud in the log
            Log.warn("[QoL Suite] schematic '@' had @ null-block tile(s) removed", s.name(), diff);
        }

        if(removed > 0 && toast){
            ui.showInfoToast(Core.bundle.format("qol.schematic-sanitizer.cleaned", removed, names.toString()), 6f);
        }
    }
}
