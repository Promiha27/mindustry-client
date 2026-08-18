package eui.ai;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.entities.units.BuildPlan;
import mindustry.world.Block;
import mindustry.world.Tile;

import static mindustry.Vars.player;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

/**
 * Finds the closest free tile on {@code fromTile}'s perimeter (at a distance that clears both blocks'
 * footprints) that's also closest to {@code toTile} - used to pick where a vault/conveyor/junction
 * should start next to whatever the player is dragging from. Ported from utils/ai/adjacent-position.js.
 * <p>
 * The perimeter step size is deliberately kept as a float, not rounded to a tile count - for an
 * odd-sized footprint (e.g. a size-3 core) {@code targetBlock.size/2f + fromTile.block().size/2f} lands
 * on a half-tile offset, and the fractional perimeter walk below (still stepping by whole tiles via
 * {@link Math#floor}) is what the source's own arithmetic relied on to land on the correct ring of
 * tiles for that case. A plain integer division here would silently pick the wrong ring for any
 * odd-sized block.
 */
public class AdjacentPosition{
    public static Tile find(Tile fromTile, Tile toTile, Block targetBlock){
        return find(fromTile, toTile, targetBlock, false);
    }

    public static Tile find(Tile fromTile, Tile toTile, Block targetBlock, boolean ignoreBlocks){
        if(fromTile.build == null) return fromTile;

        float stepAmount = targetBlock.size / 2f + fromTile.block().size / 2f;
        Seq<Tile> perimeterTiles = getPerimeterTiles(fromTile, stepAmount);

        Tile best = null;
        float minDistance = Float.POSITIVE_INFINITY;
        for(Tile t : perimeterTiles){
            if(t == null) continue; //off the edge of the map

            float vectorX = toTile.x - t.x;
            float vectorY = toTile.y - t.y;
            float distance = Mathf.dst(vectorX, vectorY);

            if(distance < minDistance){
                if(!ignoreBlocks && !new BuildPlan(t.centerX(), t.centerY(), 0, targetBlock).placeable(player.team())) continue;

                minDistance = distance;
                best = t;
            }
        }

        return best;
    }

    static Seq<Tile> getPerimeterTiles(Tile tile, float size){
        Seq<Tile> tiles = new Seq<>();
        float x = tile.build.x / tilesize;
        float y = tile.build.y / tilesize;

        for(float i = -size; i <= size; i++){
            for(float j = -size; j <= size; j++){
                float xoffset = Math.abs(i);
                float yoffset = Math.abs(j);
                if(xoffset + yoffset >= size * 2) continue;
                if(xoffset < size && yoffset < size) continue;
                tiles.add(world.tile((int)Math.floor(x + i), (int)Math.floor(y + j)));
            }
        }

        return tiles;
    }
}
