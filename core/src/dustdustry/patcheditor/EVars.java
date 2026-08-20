package dustdustry.patcheditor;

import dustdustry.patcheditor.core.resolve.*;

public class EVars{
    //thisMod (Vars.mods.getMod(Main.class)) выброшен: у вшитой копии LoadedMod'а нет,
    //а поле в оригинале нигде не читалось

    public static String githubNotesRepo = "Dustdustry/PatchNotes";
    public static String githubNotesBranch = "main";

    public static void init(){
        ObjectResolver.patch = new PatchResolution();
        ObjectResolver.content = new ContentResolution();
    }
}
