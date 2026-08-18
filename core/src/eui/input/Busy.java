package eui.input;

import static mindustry.Vars.control;
import static mindustry.Vars.ui;

/** Whether the player is already doing something that a drag-based feature (core-drag, conveyor-drag,
 *  schematic area select) shouldn't interrupt or start alongside. Ported from input/busy.js. */
public class Busy{
    public static boolean isBusy(){
        return ui.chatfrag.shown() || ui.schematics.isShown() || control.input.isUsingSchematic() || control.input.selectedBlock();
    }
}
