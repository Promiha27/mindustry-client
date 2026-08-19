package eui.ui.units;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import mindustry.gen.Player;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;

import static mindustry.Vars.player;

/**
 * Draws a marker (line/square/circle/target, per "eui-playerCursorStyle") at another player's mouse
 * cursor position, from their controlled unit - a spectator-friendly way to see where teammates are
 * looking/aiming. The "eui-TrackPlayerCursor" setting; the local player's own cursor is skipped by
 * default (system cursor already shows it). Ported from ui/units/player-tracker.js.
 * <p>
 * DEDUPE-PASS NOTE: three player-cursor renderers coexist in this client, and this one deliberately
 * stays default-OFF (it always was) rather than being deleted, because it is NOT a strict subset of the
 * other two - its team-colored marker styles (7 variants incl. plain line) and the "show own cursor"
 * option exist nowhere else. The native "drawcursors" setting (default off, red dot + name near your
 * mouse, {@code BlockRenderer}) is the baseline; mi2u's "enPlayerCursor" (default ON in mi2u's settings
 * category, {@code RendererExt.drawPlayer}) draws aim-point dash-lines with shooting state and an
 * off-screen-player name badge. Enable at most one for a clean picture.
 */
public class PlayerTracker{
    public static void drawCursor(Player p){
        if(p == player && !Core.settings.getBool("eui-ShowOwnCursor", false)) return;

        float unitX = p.x, unitY = p.y;
        float cursorX = p.mouseX, cursorY = p.mouseY;
        int style = Core.settings.getInt("eui-playerCursorStyle", 7);
        Color teamColor = p.team().color;

        Runnable drawLine = () -> {
            Lines.stroke(1, teamColor);
            Draw.alpha(0.7f);
            Lines.line(unitX, unitY, cursorX, cursorY);
            Draw.reset();
        };

        Draw.draw(Layer.overlayUI + 0.01f, () -> {
            switch(style){
                case 1 -> Drawf.square(cursorX, cursorY, 2, teamColor); //square (inspired by Mindustry Ranked Server's spectator mode)
                case 2 -> { drawLine.run(); Drawf.square(cursorX, cursorY, 2, teamColor); } //square + line
                case 3 -> Drawf.circles(cursorX, cursorY, 1, teamColor); //circle
                case 4 -> { drawLine.run(); Drawf.circles(cursorX, cursorY, 1, teamColor); } //circle + line
                case 5 -> Drawf.target(cursorX, cursorY, 3, teamColor); //target
                case 6 -> { drawLine.run(); Drawf.target(cursorX, cursorY, 3, teamColor); } //target + line
                default -> drawLine.run(); //line (original/default)
            }
        });
        Draw.reset();
    }
}
