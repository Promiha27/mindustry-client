package eui.interact;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.geom.Vec2;
import eui.input.Drag;
import eui.util.Schematics;
import mindustry.game.Schematic;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.Tile;

import static mindustry.Vars.control;

/**
 * Drag-to-select-an-area tool: while armed (see {@link #setActive}, toggled by the bottom panel's
 * selection button - phase C), the next left-click drag anywhere on the map captures whatever's inside
 * the dragged rectangle into an ad-hoc {@link Schematic} ({@link Schematics#create}) and immediately arms
 * it for placement via {@code control.input.useSchematic}, instead of requiring the player to have saved
 * one beforehand. Ported from interact/schematic-selector.js.
 * <p>
 * COLLISION: this engine already ships the same tool natively, bound to {@code Binding.schematicSelect}
 * (hold "F" and drag over built tiles - see {@code DesktopInput.java}'s handling of it). The one
 * difference is that the native gesture requires a controllable unit (same limitation qol-suite's own
 * {@code CopyAnywhereFeature} exists to lift for spectators); this port has no such requirement, so for a
 * player who does have a unit the two are close to fully redundant. Ported anyway for behavioural parity
 * with the source - see {@code eui.EUIMod}'s javadoc.
 */
public class SchematicSelector implements Drag.Listener{
    private static final SchematicSelector INSTANCE = new SchematicSelector();
    private static boolean active = false;

    private boolean drawing = false;
    private float startDrawX, startDrawY, endDrawX, endDrawY;

    /**
     * Hook for the bottom panel's toggle button (phase C) to learn that a capture just completed, so it
     * can un-press itself - mirrors the JS mod's {@code schemSelectionEnd} event, which the same button
     * listened for.
     */
    public static Runnable onSelectionEnd;

    private SchematicSelector(){}

    public static boolean isActive(){
        return active;
    }

    public static void setActive(boolean value){
        if(value == active) return;
        active = value;
        if(value){
            INSTANCE.drawing = true;
            INSTANCE.startDrawX = INSTANCE.startDrawY = INSTANCE.endDrawX = INSTANCE.endDrawY = 0;
            Drag.addListener(INSTANCE);
        }else{
            INSTANCE.drawing = false;
            Drag.removeListener(INSTANCE);
        }
    }

    /** Called once per frame from {@link eui.EUIMod}'s draw hook. */
    public static void draw(){
        if(!INSTANCE.drawing) return;
        Draw.draw(Layer.overlayUI + 0.01f, () -> {
            Draw.z(Layer.darkness + 1);
            Lines.stroke(1, Pal.accent);
            Lines.rect(INSTANCE.startDrawX, INSTANCE.startDrawY, INSTANCE.endDrawX - INSTANCE.startDrawX, INSTANCE.endDrawY - INSTANCE.startDrawY);
            Draw.reset();
        });
    }

    @Override
    public void dragged(Vec2 startPos, Tile startTile, Vec2 pos, Tile tile){
        startDrawX = startPos.x;
        startDrawY = startPos.y;
        endDrawX = pos.x;
        endDrawY = pos.y;
    }

    @Override
    public void dragEnded(Vec2 startPos, Tile startTile, Vec2 pos, Tile mouseTile){
        if(mouseTile == null || startTile == null) return;

        Schematic schem = Schematics.create(
            Math.min(startTile.centerX(), mouseTile.centerX()), Math.min(startTile.centerY(), mouseTile.centerY()),
            Math.max(startTile.centerX(), mouseTile.centerX()), Math.max(startTile.centerY(), mouseTile.centerY())
        );

        control.input.lastSchematic = schem;
        if(schem != null){
            control.input.useSchematic(schem);
            setActive(false);
            if(onSelectionEnd != null) onSelectionEnd.run();
        }
    }
}
