package newconsole;

import arc.scene.ui.layout.*;
import arc.struct.*;
import newconsole.io.*;
import newconsole.ui.*;
import newconsole.ui.dialogs.*;

@SuppressWarnings("CanBeFinal")
public class ConsoleVars {

    public static WidgetGroup group;
    public static FloatingWidget floatingWidget;

    public static Seq<Console> consoles = new Seq<>();
    public static int selectConsole = 0;

    public static SavesDialog saves;
    public static CopypasteDialog copypaste;
    public static FileBrowser fileBrowser;
    public static AutorunDialog autorun;

    public static boolean consoleEnabled = true;
    public static String startup = "console/startup.js";

    public static Console getCurrentConsole(){
        return consoles.get(selectConsole);
    }

    public static ScriptsManager getCurrentScriptsManager(){
        return consoles.get(selectConsole).scripts;
    }

    public static AutorunManager getCurrentAutorunManager(){
        return consoles.get(selectConsole).autorun;
    }
}
