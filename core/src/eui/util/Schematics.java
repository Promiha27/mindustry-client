package eui.util;

import arc.struct.IntSet;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.world;

/**
 * Captures a rectangular tile range into an in-memory {@link Schematic} - the core of the drag-to-select
 * tool ({@link eui.interact.SchematicSelector}). First pass grows the requested rectangle out to cover
 * every multi-tile block that's only partially inside it (so a schematic never contains half a building);
 * second pass records one {@link Stile} per distinct building found in the (possibly grown) rectangle.
 * Ported from utils/schematics.js, itself borrowed (per that file's own comment) from
 * Pointifix/EvictionToolkit.
 */
public class Schematics{
    public static Schematic create(int x, int y, int x2, int y2){
        if(x == x2 && y == y2) return null;

        int ox = x, oy = y, ox2 = x2, oy2 = y2;

        int minx = x2, miny = y2, maxx = x, maxy = y;
        boolean found = false;
        for(int cx = x; cx <= x2; cx++){
            for(int cy = y; cy <= y2; cy++){
                Building linked = world.build(cx, cy);
                Block realBlock = realBlock(linked);

                if(linked != null && realBlock != null && (realBlock.isVisible() || realBlock instanceof CoreBlock)){
                    int top = realBlock.size / 2;
                    int bot = realBlock.size % 2 == 1 ? -realBlock.size / 2 : -(realBlock.size - 1) / 2;
                    minx = Math.min(linked.tileX() + bot, minx);
                    miny = Math.min(linked.tileY() + bot, miny);
                    maxx = Math.max(linked.tileX() + top, maxx);
                    maxy = Math.max(linked.tileY() + top, maxy);
                    found = true;
                }
            }
        }

        if(!found) return new Schematic(new Seq<>(), new StringMap(), 1, 1);

        x = minx;
        y = miny;
        x2 = maxx;
        y2 = maxy;

        int width = x2 - x + 1, height = y2 - y + 1;
        int offsetX = -x, offsetY = -y;
        IntSet counted = new IntSet();
        Seq<Stile> tiles = new Seq<>();
        for(int cx = ox; cx <= ox2; cx++){
            for(int cy = oy; cy <= oy2; cy++){
                Building tile = world.build(cx, cy);
                Block realBlock = realBlock(tile);

                if(tile != null && !counted.contains(tile.pos()) && realBlock != null
                    && (realBlock.isVisible() || realBlock instanceof CoreBlock)){
                    Object config = tile instanceof ConstructBuild consBuild ? consBuild.lastConfig : tile.config();

                    try{
                        tiles.add(new Stile(realBlock, tile.tileX() + offsetX, tile.tileY() + offsetY, config, (byte)tile.rotation));
                    }catch(Exception ignored){
                        //source note: "sometimes it throws an error but error is not important" - kept as-is
                    }
                    counted.add(tile.pos());
                }
            }
        }

        return new Schematic(tiles, new StringMap(), width, height);
    }

    /**
     * The JS source read this branch as {@code cons.current} - {@code cons} is not defined anywhere in
     * that file or its imports, so on real Rhino this would throw a ReferenceError for every
     * still-under-construction tile inside the selection (silently caught by schematic-selector.js's
     * broader error handling upstream, per the memory of this mod's debugging history). The clearly
     * intended value - "what block is this constructing into" - is {@link ConstructBuild#current} on the
     * tile itself, which is what's actually used here.
     */
    static Block realBlock(Building linked){
        if(linked == null) return null;
        if(linked instanceof ConstructBuild consBuild) return consBuild.current;
        return linked.block;
    }
}
