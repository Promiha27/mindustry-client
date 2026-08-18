package eui.input;

import arc.Core;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.world.Tile;

import static mindustry.Vars.world;

/**
 * Shared left-click drag gesture tracker (tap - hold - release, with world position and tile at each
 * step). Three Extended UI++ features need exactly this same polling (core-drag vault placement,
 * conveyor/junction pathfind-drag, and the schematic area-select tool), so it's factored out here once
 * as a plain typed listener list instead of tripling the same ~15 lines of tap/release detection.
 * <p>
 * Ported from the JS mod's utils/event/drag.js + utils/event/events.js pair, but NOT as a generic
 * string-keyed event bus with a central try/catch per handler (that indirection existed only to keep one
 * broken Rhino module from taking every other {@code require()}'d module down with it - see
 * EUIMod's javadoc). Java's single compilation unit doesn't have that failure mode, so this is just an
 * ordinary listener list, updated once per frame from {@link eui.EUIMod}.
 */
public class Drag{
    public interface Listener{
        default void dragStarted(Vec2 startPos, Tile startTile){}
        default void dragged(Vec2 startPos, Tile startTile, Vec2 pos, Tile tile){}
        default void dragEnded(Vec2 startPos, Tile startTile, Vec2 pos, Tile tile){}
    }

    private static final Seq<Listener> listeners = new Seq<>();
    private static Vec2 startPos;
    private static Tile startTile;
    private static boolean dragging = false;

    public static void addListener(Listener l){
        listeners.add(l);
    }

    public static void removeListener(Listener l){
        listeners.remove(l);
    }

    public static void update(){
        boolean tap = Core.input.keyTap(KeyCode.mouseLeft);
        boolean release = Core.input.keyRelease(KeyCode.mouseLeft);
        if(!dragging && !tap && !release) return;

        //mouseWorld() reuses an internal Vec2 every call - copy it so startPos doesn't alias the
        //very same instance mouseWorld() returns for "pos" further down
        Vec2 pos = Core.input.mouseWorld(Core.input.mouseX(), Core.input.mouseY()).cpy();
        Tile mouseTile = world.tileWorld(pos.x, pos.y);
        if(mouseTile == null) return;

        if(tap){
            startPos = pos;
            startTile = mouseTile;
            dragging = true;
            for(Listener l : listeners) l.dragStarted(startPos, startTile);
        }
        if(release && dragging){
            dragging = false;
            for(Listener l : listeners) l.dragEnded(startPos, startTile, pos, mouseTile);
        }
        if(dragging){
            for(Listener l : listeners) l.dragged(startPos, startTile, pos, mouseTile);
        }
    }
}
