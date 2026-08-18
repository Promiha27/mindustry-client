package eui.ai;

import arc.math.geom.Point2;
import arc.struct.IntMap;
import arc.struct.Seq;
import mindustry.ai.Astar;
import mindustry.ai.Astar.TileHeuristic;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Edges;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BlockGroup;

import static mindustry.Vars.player;
import static mindustry.Vars.world;

/**
 * A* route planner for dragging a conveyor/junction/underflow-gate line from an existing belt toward
 * wherever the mouse currently is (see {@link eui.input.ConveyorDrag}), automatically inserting item
 * bridges to jump over the player's own buildings that are in the way. Ported from
 * utils/ai/pathfind.js.
 * <p>
 * Runs on the engine's real {@link Astar#pathfind(Tile, Tile, TileHeuristic, arc.func.Boolf)} - the JS
 * source called the exact same method (Rhino auto-imports {@code mindustry.ai.Astar} and adapts a plain
 * JS object with a {@code cost} property to the {@link TileHeuristic} interface). The debug-only tile
 * highlighting the source built behind a hardcoded {@code const debug = false} (and the dead
 * {@code isPathClear}/{@code isSameTransportationAxis} helpers, whose only call site was commented out
 * in the source) are both dropped - neither one is reachable behavior, just removed rather than ported
 * as permanently-off code paths.
 */
public class ConveyorPathfind{
    public static Seq<BuildPlan> conveyorPathfind(Tile source, Tile target, Tile lastRotationTo, Block conveyor){
        Placeable placeable = (tile, rotationTo) -> passable(tile, conveyor) && tile.build == null && isNoTransportationContact(tile, conveyor);
        PathLinear pathLinear = new PathLinear(conveyor, placeable);
        PathRotation pathRotation = new PathRotation(conveyor, placeable, 1);
        Seq<PathJump> pathJumps = Seq.with(new PathJump(Blocks.itemBridge, 3, 4));

        return pathfind(source, target, lastRotationTo, pathLinear, pathRotation, pathJumps);
    }

    public static Seq<BuildPlan> junctionPathfind(Tile source, Tile target, Tile lastRotationTo, Block block){
        Placeable linearPlaceable = (tile, rotationTo) -> passable(tile, block) && tile.build == null;
        Placeable rotationPlaceable = (tile, rotationTo) -> linearPlaceable.get(tile, rotationTo) && isNoTransportationContact(tile, block);
        PathLinear pathLinear = new PathLinear(block, linearPlaceable);
        PathRotation pathRotation = new PathRotation(Blocks.underflowGate, rotationPlaceable, 1);
        Seq<PathJump> pathJumps = Seq.with(new PathJump(Blocks.itemBridge, 3, 4));

        return pathfind(source, target, lastRotationTo, pathLinear, pathRotation, pathJumps);
    }

    public static Seq<BuildPlan> gatePathfind(Tile source, Tile target, Tile lastRotationTo){
        return junctionPathfind(source, target, lastRotationTo, Blocks.junction);
    }

    interface Placeable{
        boolean get(Tile tile, int rotationTo);
    }

    static class PathLinear{
        final Block block;
        final Placeable placeable;

        PathLinear(Block block, Placeable placeable){
            this.block = block;
            this.placeable = placeable;
        }
    }

    static class PathRotation{
        final Block block;
        final Placeable placeable;
        final float cost;

        PathRotation(Block block, Placeable placeable, float cost){
            this.block = block;
            this.placeable = placeable;
            this.cost = cost;
        }
    }

    static class PathJump{
        final Block block;
        final int length;
        final float cost;

        PathJump(Block block, int length, float cost){
            this.block = block;
            this.length = length;
            this.cost = cost;
        }
    }

    static class PossibleJump{
        final int direction;
        final int cost;

        PossibleJump(int direction, int cost){
            this.direction = direction;
            this.cost = cost;
        }
    }

    /** Either a marker that this tile is already covered by a neighboring jump's span ({@link #OVERLAP}, JS's {@code 'overlap'} string), or a real jump placement. */
    static class JumpMark{
        static final JumpMark OVERLAP = new JumpMark(true, null, null);

        final boolean overlap;
        final Tile target;
        final PathJump pathJump;

        JumpMark(boolean overlap, Tile target, PathJump pathJump){
            this.overlap = overlap;
            this.target = target;
            this.pathJump = pathJump;
        }

        static JumpMark of(Tile target, PathJump pathJump){
            return new JumpMark(false, target, pathJump);
        }
    }

    static Seq<BuildPlan> pathfind(Tile source, Tile target, Tile lastRotationTo, PathLinear pathLinear, PathRotation pathRotation, Seq<PathJump> pathJumps){
        IntMap<Seq<PossibleJump>> blockedTilesMap = new IntMap<>();
        Seq<Tile> tiles = blockPathfind(source, target, pathLinear, pathRotation, pathJumps, blockedTilesMap);
        if(tiles == null) return new Seq<>();

        IntMap<Integer> rotationsMap = makeRotationsMap(tiles);
        IntMap<JumpMark> jumpsMap = makeJumpsMap(tiles, blockedTilesMap, pathJumps);

        return planner(tiles, lastRotationTo, pathLinear, pathRotation, rotationsMap, jumpsMap);
    }

    static Seq<Tile> blockPathfind(Tile source, Tile target, PathLinear pathLinear, PathRotation pathRotation, Seq<PathJump> pathJumps, IntMap<Seq<PossibleJump>> unplaceableTilesJumps){
        if(!passable(target, pathLinear.block)) return null;

        IntMap<Integer> rotations = new IntMap<>();
        float distanceSourceTarget = Math.abs(source.centerX() - target.centerX()) + Math.abs(source.centerY() - target.centerY());
        float centerX = (target.centerX() + source.centerX()) / 2f;
        float centerY = (target.centerY() + source.centerY()) / 2f;
        float r = distanceSourceTarget / 2f;
        float r2 = r * r;

        Seq<Tile> tilesSeq = Astar.pathfind(source, target, new TileHeuristic(){
            @Override
            public float cost(Tile tile){
                //never actually called - Astar's search loop always calls the 2-arg cost(from, tile)
                //overridden below; only present because the interface requires it
                return 1;
            }

            @Override
            public float cost(Tile from, Tile tile){
                if(rotations.containsKey(tile.pos())) return 0;
                Integer rotationFrom = rotations.get(from.pos());
                int rotationTo = tile.relativeTo(from);
                boolean isPathRotated = rotationFrom == null || rotationFrom != rotationTo;
                boolean passableTile = pathLinear.placeable.get(tile, rotationTo);
                float cost = 1;

                if(rotationFrom != null && rotationFrom == inverseRotation(rotationTo)) return 0;

                rotations.put(tile.pos(), rotationTo);

                Seq<PossibleJump> fromPossibleJumps = unplaceableTilesJumps.get(from.pos());
                PossibleJump jump = null;
                PathJump minPathJump = null;
                if(fromPossibleJumps != null){
                    for(PossibleJump pj : fromPossibleJumps){
                        if(pj.direction == rotationTo){ jump = pj; break; }
                    }
                    if(jump == null) return 27145;

                    for(PathJump pj : pathJumps){
                        if(pj.length >= jump.cost){ minPathJump = pj; break; }
                    }
                    if(minPathJump == null) return 27145;
                }

                if(isPathRotated && !pathRotation.placeable.get(from, rotationFrom == null ? -1 : rotationFrom)) return 1000;

                if(!passableTile){
                    Seq<PossibleJump> tilePossibleJumps = unplaceableTilesJumps.get(tile.pos());
                    if(tilePossibleJumps == null) tilePossibleJumps = new Seq<>();

                    if(jump == null){
                        tilePossibleJumps.add(new PossibleJump(rotationTo, 1));
                    }else{
                        tilePossibleJumps.add(new PossibleJump(rotationTo, jump.cost + 1));
                    }

                    unplaceableTilesJumps.put(tile.pos(), tilePossibleJumps);

                    cost += minPathJump == null ? pathJumps.first().cost : minPathJump.cost;
                }

                if(isPathRotated) cost += pathRotation.cost;

                Floor overlay = tile.overlay();
                Item ore = overlay == null ? null : overlay.itemDrop;
                if(ore != null) cost += oreCost(ore);

                return cost;
            }
        }, t -> {
            float dx = t.centerX() - centerX;
            float dy = t.centerY() - centerY;
            return dx * dx + dy * dy < r2 + 49;
        });

        if(tilesSeq.isEmpty()) return null;

        Seq<Tile> result = new Seq<>();
        result.add(source);
        result.addAll(tilesSeq);
        return result;
    }

    static Seq<BuildPlan> planner(Seq<Tile> tiles, Tile lastRotationTo, PathLinear pathLinear, PathRotation pathRotation, IntMap<Integer> rotationsMap, IntMap<JumpMark> jumpsMap){
        Seq<BuildPlan> plans = new Seq<>();
        if(tiles.isEmpty()) return plans;
        Tile last = tiles.peek();

        Integer rotation = null;
        for(int i = 0; i < tiles.size - 1; i++){
            Tile current = tiles.get(i);
            Tile prev = i > 0 ? tiles.get(i - 1) : null;
            JumpMark jump = jumpsMap.get(current.pos());
            rotation = rotationsMap.get(current.pos());
            Integer prevRotation = prev != null ? rotationsMap.get(prev.pos()) : -1;

            BuildPlan buildPlan = null;
            if(jump != null){
                if(!jump.overlap){
                    Block block = jump.pathJump.block;
                    buildPlan = makePlan(current, block, rotation);
                    if(jump.target != null){
                        buildPlan.config = new Point2(jump.target.x - current.x, jump.target.y - current.y);
                    }
                }
                //else: 'overlap' tile, already covered by a neighboring jump's span - no plan here
            }else if(!rotation.equals(prevRotation)){
                buildPlan = makePlan(current, pathRotation.block, rotation);
            }else{
                buildPlan = makePlan(current, pathLinear.block, rotation);
            }

            if(buildPlan != null) plans.add(buildPlan);
        }

        Integer lastRotation = rotationsMap.get(last.pos());
        var lastRotationToBuild = lastRotationTo.build;
        if(lastRotationToBuild != null){
            for(int i = 0; i < 4; i++){
                Tile neighbour = last.nearby(i);
                if(neighbour != null && neighbour.build != null && neighbour.build == lastRotationToBuild){
                    lastRotation = (int)last.relativeTo(neighbour);
                }
            }
        }

        BuildPlan lastPlan;
        JumpMark lastJump = jumpsMap.get(last.pos());
        if(lastJump != null){
            lastPlan = makePlan(last, lastJump.pathJump.block, lastRotation);
        }else if(!java.util.Objects.equals(rotation, lastRotation) && !pathLinear.block.rotate){
            lastPlan = makePlan(last, pathRotation.block, lastRotation);
        }else{
            lastPlan = makePlan(last, pathLinear.block, lastRotation);
        }

        plans.add(lastPlan);
        return plans;
    }

    static BuildPlan makePlan(Tile tile, Block block, Integer rotation){
        return new BuildPlan(tile.x, tile.y, rotation == null ? 0 : rotation, block);
    }

    static IntMap<Integer> makeRotationsMap(Seq<Tile> tiles){
        int length = tiles.size;
        IntMap<Integer> rotationMap = new IntMap<>();
        for(int i = 0; i < length - 1; i++){
            Tile current = tiles.get(i);
            Tile next = tiles.get(i + 1);
            int rotation = current.relativeTo(next);
            rotationMap.put(current.pos(), rotation);
        }

        Tile last = tiles.get(length - 1);
        Tile penultimate = length >= 2 ? tiles.get(length - 2) : null;
        int lastRotation = penultimate != null ? rotationMap.get(penultimate.pos()) : 0;
        rotationMap.put(last.pos(), lastRotation);

        return rotationMap;
    }

    static IntMap<JumpMark> makeJumpsMap(Seq<Tile> tiles, IntMap<Seq<PossibleJump>> blockedTilesMap, Seq<PathJump> pathJumps){
        int length = tiles.size;
        Tile lastTile = tiles.get(length - 1);
        IntMap<JumpMark> jumpsMap = new IntMap<>();

        for(int i = 0; i < length; i++){
            if(!tileBlockedAt(tiles, blockedTilesMap, lastTile, i + 1)) continue;
            if(jumpsMap.containsKey(tiles.get(i).pos())) continue;

            int j = i;
            int maxJumpSize = 0;
            int jumpSize = 0;
            Seq<Tile> matchingTiles = new Seq<>();
            matchingTiles.add(tiles.get(i));

            do{
                j++;
                if(!tileBlockedAt(tiles, blockedTilesMap, lastTile, j)){
                    matchingTiles.add(tiles.get(j));
                    if(maxJumpSize < jumpSize) maxJumpSize = jumpSize;
                    jumpSize = 0;
                }else{
                    jumpSize++;
                    jumpsMap.put(tiles.get(j).pos(), JumpMark.OVERLAP);
                }
            }while(tileBlockedAt(tiles, blockedTilesMap, lastTile, j) || tileBlockedAt(tiles, blockedTilesMap, lastTile, j + 1));

            PathJump minPathJump = null;
            for(PathJump pj : pathJumps){
                if(pj.length >= maxJumpSize){ minPathJump = pj; break; }
            }
            if(minPathJump == null) return jumpsMap;

            for(int k = 0; k < matchingTiles.size - 1; k++){
                Tile muchTile = matchingTiles.get(k);
                Tile nextMuchTile = matchingTiles.get(k + 1);

                jumpsMap.put(muchTile.pos(), JumpMark.of(nextMuchTile, minPathJump));
                jumpsMap.put(nextMuchTile.pos(), JumpMark.of(null, minPathJump));
            }
        }
        return jumpsMap;
    }

    /** {@code idx} out of [0, tiles.size) is treated as "not blocked", same as JS's {@code tiles[idx]} being {@code undefined} there. The last tile is never considered blocked, matching the source. */
    static boolean tileBlockedAt(Seq<Tile> tiles, IntMap<Seq<PossibleJump>> blockedTilesMap, Tile lastTile, int idx){
        if(idx < 0 || idx >= tiles.size) return false;
        Tile tile = tiles.get(idx);
        return blockedTilesMap.get(tile.pos()) != null && tile != lastTile;
    }

    static int inverseRotation(int rotation){
        return (rotation + 2) % 4;
    }

    static boolean isLinearTransporter(Block block){
        return block == Blocks.conveyor
            || block == Blocks.titaniumConveyor
            || block == Blocks.armoredConveyor
            || block == Blocks.plastaniumConveyor
            || block == Blocks.junction
            || block == Blocks.vault
            || block == Blocks.container
            || block == Blocks.conduit
            || block == Blocks.pulseConduit
            || block == Blocks.platedConduit;
    }

    static boolean passable(Tile tile, Block block){
        return tile.passable() && (tile.block() == block || new BuildPlan(tile.centerX(), tile.centerY(), 0, block).placeable(player.team()));
    }

    static boolean isNoTransportationContact(Tile tile, Block block){
        return isNoContact(tile, block, b ->
            b.group == BlockGroup.drills
                || (b.group == BlockGroup.transportation && !isLinearTransporter(b) && !(b instanceof CoreBlock))
                || b instanceof GenericCrafter
        );
    }

    static boolean isNoContact(Tile tile, Block block, java.util.function.Predicate<Block> shouldAvoid){
        boolean noContact = true;
        for(Point2 point : Edges.getEdges(block.size)){
            Tile neighbour = world.tile(tile.x + point.x, tile.y + point.y);
            if(neighbour == null) continue;

            if(shouldAvoid.test(neighbour.block())){
                noContact = false;
            }
        }
        return noContact;
    }

    static int oreCost(Item ore){
        return 1 + ore.hardness;
    }
}
