package testing.util;

import arc.Core;
import mindustry.game.Team;
import testing.dialogs.TUBaseDialog;
import testing.editor.TerrainPaintbrush;
import testing.editor.TerrainPainter;

public class TUVars{
    public static Team curTeam = Team.sharded;
    public static TUBaseDialog activeDialog;
    public static TerrainPainter painter;
    public static TerrainPaintbrush paintbrush;

    private TUVars(){
    }

    /** Создаётся из TestUtilsMod после ClientLoadEvent - конструкторы вешают Trigger-слушатели. */
    public static void init(){
        painter = new TerrainPainter();
        paintbrush = new TerrainPaintbrush();
    }

    /** Дельта, не зависящая от управления временем (time control). */
    public static float delta(){
        return Core.graphics.getDeltaTime() * 60;
    }
}
