package qol.erekirautopower;

import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.TapEvent;
import mindustry.gen.Building;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.power.BeamNode;
import qol.core.Feature;
import qol.ui.QolWindow;

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Set;

import static arc.Core.bundle;
import static mindustry.Vars.content;
import static mindustry.Vars.indexer;
import static mindustry.Vars.player;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * Ported from the standalone Erekir Auto Power script mod (JS). Double-click a vent on Erekir: places a
 * turbine condenser centered on it, then auto-routes a beam-node chain (greedy straight-line first,
 * falling back to A* around obstacles) to the nearest power network - preferring a direct axis-aligned
 * connection with zero nodes if one's in range.
 */
public class ErekirAutoPowerFeature implements Feature{
    static final float DOUBLE_TAP_TIME = 25f;
    static final int MAX_CHAIN_NODES = 40;
    static final int SEARCH_RADIUS_TILES = 200;
    static final int MAX_ASTAR_ITERATIONS = 4000;

    static final Set<String> VENT_NAMES = Set.of(
        "rhyolite-vent", "carbon-vent", "arkyic-vent",
        "yellow-stone-vent", "red-stone-vent", "crystalline-vent"
    );
    static final int[][] VENT_OFFSETS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1}, {0, 0}, {0, 1},
        {1, -1}, {1, 0}, {1, 1}
    };

    Block genBlock, nodeBlock;
    int genHalf;

    Tile lastTapTile;
    float lastTapFrame = -1000f;

    @Override
    public String id(){
        return "erekir-autopower";
    }

    @Override
    public String titleKey(){
        return "qol.feature.erekir-autopower.title";
    }

    @Override
    public boolean hasWindow(){
        return false;
    }

    @Override
    public QolWindow window(){
        return null;
    }

    @Override
    public void init(){
        genBlock = content.block("turbine-condenser");
        nodeBlock = content.block("beam-node");
        genHalf = genBlock != null ? genBlock.size / 2 : 0;

        Events.on(TapEvent.class, e -> {
            try{
                handleTap(e.tile);
            }catch(Exception err){
                Log.err("[erekir-autopower]", err);
            }
        });
    }

    @Override
    public void buildSettings(SettingsTable table){
    }

    void handleTap(Tile tile){
        if(!isEnabled() || tile == null || genBlock == null || nodeBlock == null
            || player == null || player.team() == null || player.unit() == null) return;

        boolean isDoubleTap = tile == lastTapTile && (Time.time - lastTapFrame) < DOUBLE_TAP_TIME;
        lastTapTile = tile;
        lastTapFrame = Time.time;
        if(!isDoubleTap) return;

        ensureUnlocked();

        if(!isVentTile(tile)) return;

        Tile anchor = findVentAnchor(tile);
        if(anchor == null){
            ui.hudfrag.showToast("[scarlet]" + bundle.get("erekir-autopower-no-anchor"));
            return;
        }

        placeGeneratorAndConnect(anchor);
    }

    void ensureUnlocked(){
        try{
            if(!genBlock.unlocked()) genBlock.unlock();
            if(!nodeBlock.unlocked()) nodeBlock.unlock();
        }catch(Exception err){
            Log.err(err);
        }
    }

    boolean isVentTile(Tile tile){
        return tile.floor() != null && VENT_NAMES.contains(tile.floor().name);
    }

    Tile findVentAnchor(Tile tile){
        for(int[] off : VENT_OFFSETS){
            int cx = tile.x + off[0], cy = tile.y + off[1];
            if(Build.validPlace(genBlock, player.team(), cx, cy, 0)){
                return world.tile(cx, cy);
            }
        }
        return null;
    }

    void placeGeneratorAndConnect(Tile anchorTile){
        queueBuild(anchorTile, genBlock);

        Seq<Tile> chain = findChainToPower(anchorTile, nodeBlock, MAX_CHAIN_NODES);
        if(chain == null){
            ui.hudfrag.showToast("[scarlet]" + bundle.get("erekir-autopower-no-network"));
            return;
        }
        for(Tile t : chain) queueBuild(t, nodeBlock);
    }

    /** {@code beam-node}'s {@code range} field is already tile-denominated; the 10-tile floor matches what the original mod tuned this to. */
    float getNodeRange(Block block){
        float detected = block instanceof BeamNode beamNode ? beamNode.range : 0f;
        return Math.max(10f, detected);
    }

    static int clampInt(int v, int lo, int hi){
        return Math.max(lo, Math.min(hi, v));
    }

    /** Nearest tile on the border of a {@code half}-radius square footprint centered on {@code centerTile}, toward {@code towardTile}. Returns {@code centerTile} unchanged when {@code half <= 0}. */
    Tile edgeOfBlock(Tile centerTile, int half, Tile towardTile){
        if(half <= 0) return centerTile;
        int tx = clampInt(towardTile.x, centerTile.x - half, centerTile.x + half);
        int ty = clampInt(towardTile.y, centerTile.y - half, centerTile.y + half);
        Tile result = world.tile(tx, ty);
        return result != null ? result : centerTile;
    }

    Tile getFootprintTarget(Tile fromTile, Building building){
        int size = building.block != null ? building.block.size : 1;
        int half = size / 2;
        return edgeOfBlock(building.tile, half, fromTile);
    }

    /** Gap between two blocks along one axis, accounting for both footprints' half-sizes; 0 means the footprints already overlap on that axis. */
    static int axisGap(int ca, int halfA, int cb, int halfB){
        int diff = cb - ca;
        int combined = halfA + halfB;
        if(diff > combined) return diff - combined;
        if(diff < -combined) return diff + combined;
        return 0;
    }

    boolean isDirectlyAlignable(Tile anchorTile, Building targetBuilding, float range){
        int targetHalf = targetBuilding.block != null ? targetBuilding.block.size / 2 : 0;
        float safe = Math.max(1f, range);
        int gapX = axisGap(anchorTile.x, genHalf, targetBuilding.tile.x, targetHalf);
        int gapY = axisGap(anchorTile.y, genHalf, targetBuilding.tile.y, targetHalf);
        if(gapX == 0 && Math.abs(gapY) <= safe) return true;
        return gapY == 0 && Math.abs(gapX) <= safe;
    }

    /** Any power building in range that {@code anchorTile} could connect to with zero nodes - preferred over the nearest one if that nearest one isn't axis-aligned. */
    Building findAlignedPowerBuilding(Tile anchorTile, float range, int radius){
        Building[] best = {null};
        float[] bestDst = {Float.MAX_VALUE};
        indexer.eachBlock(player.team(), anchorTile.worldx(), anchorTile.worldy(), radius * (float)tilesize,
            b -> b.power != null && isDirectlyAlignable(anchorTile, b, range),
            b -> {
                float d = anchorTile.dst(b.tile);
                if(d < bestDst[0]){ bestDst[0] = d; best[0] = b; }
            });
        return best[0];
    }

    Building findNearestPowerBuilding(Tile tile, int radius){
        Building[] best = {null};
        float[] bestDst = {Float.MAX_VALUE};
        indexer.eachBlock(player.team(), tile.worldx(), tile.worldy(), radius * (float)tilesize,
            b -> b.power != null,
            b -> {
                float d = tile.dst(b.tile);
                if(d < bestDst[0]){ bestDst[0] = d; best[0] = b; }
            });
        return best[0];
    }

    Seq<Tile> findChainToPower(Tile startTile, Block block, int maxNodes){
        float range = getNodeRange(block);

        if(findAlignedPowerBuilding(startTile, range, SEARCH_RADIUS_TILES) != null){
            return new Seq<>();
        }

        Building nearestPower = findNearestPowerBuilding(startTile, SEARCH_RADIUS_TILES);
        if(nearestPower == null) return null;

        Tile target = getFootprintTarget(startTile, nearestPower);
        Tile chainStart = edgeOfBlock(startTile, genHalf, target);

        Seq<Tile> greedy = greedyChain(chainStart, target, block, range, maxNodes);
        if(greedy != null) return greedy;

        return aStarChain(chainStart, target, block, range, maxNodes);
    }

    Seq<Tile> greedyChain(Tile start, Tile target, Block block, float range, int maxNodes){
        Seq<Tile> chain = new Seq<>();
        int curX = start.x, curY = start.y;
        int tgtX = target.x, tgtY = target.y;
        int stepDist = Math.max(1, Math.round(range));
        float safeRange = Math.max(1f, range);

        for(int i = 0; i < maxNodes; i++){
            int ddx = tgtX - curX, ddy = tgtY - curY;

            if(ddx == 0 && Math.abs(ddy) <= safeRange) return chain;
            if(ddy == 0 && Math.abs(ddx) <= safeRange) return chain;

            boolean horizontal = Math.abs(ddx) >= Math.abs(ddy);
            int baseX, baseY;
            if(horizontal){
                int signX = ddx >= 0 ? 1 : -1;
                int moveX = signX * Math.min(stepDist, Math.abs(ddx));
                if(moveX == 0) moveX = signX * stepDist;
                baseX = curX + moveX;
                baseY = curY;
            }else{
                int signY = ddy >= 0 ? 1 : -1;
                int moveY = signY * Math.min(stepDist, Math.abs(ddy));
                if(moveY == 0) moveY = signY * stepDist;
                baseX = curX;
                baseY = curY + moveY;
            }

            Tile next = findValidOnAxis(baseX, baseY, horizontal, block, range);
            if(next == null) return null;

            chain.add(next);
            curX = next.x;
            curY = next.y;
        }
        return null;
    }

    /** Nearest valid tile to (bx,by), searched only along the current axis, so the link back to the previous node stays strictly horizontal/vertical. */
    Tile findValidOnAxis(int bx, int by, boolean horizontal, Block block, float range){
        int maxJitter = Math.max(2, Math.round(range / 2f));
        int[] offsets = new int[maxJitter * 2 + 1];
        offsets[0] = 0;
        for(int k = 1; k <= maxJitter; k++){
            offsets[2 * k - 1] = k;
            offsets[2 * k] = -k;
        }
        for(int off : offsets){
            int cx = horizontal ? bx + off : bx;
            int cy = horizontal ? by : by + off;
            if(Build.validPlace(block, player.team(), cx, cy, 0)) return world.tile(cx, cy);
        }
        return null;
    }

    Seq<Tile> aStarChain(Tile start, Tile target, Block block, float range, int maxNodes){
        HashMap<Long, Float> gScore = new HashMap<>();
        HashMap<Long, Tile> cameFrom = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));

        gScore.put(key(start), 0f);
        open.add(new Node(start, heuristic(start, target, range)));

        int iterations = 0;
        while(!open.isEmpty() && iterations++ < MAX_ASTAR_ITERATIONS){
            Node cur = open.poll();
            Tile current = cur.tile;
            long curKey = key(current);
            Float curGBoxed = gScore.get(curKey);
            if(curGBoxed == null) continue;
            float curG = curGBoxed;

            //stale queue entry (a cheaper path to this tile was already found and processed)
            if(cur.f - heuristic(current, target, range) > curG + 0.001f) continue;

            if(curG >= maxNodes) continue;

            int aDx = target.x - current.x, aDy = target.y - current.y;
            float safeR = Math.max(1f, range);
            if((aDx == 0 && Math.abs(aDy) <= safeR) || (aDy == 0 && Math.abs(aDx) <= safeR)){
                return reconstruct(cameFrom, current);
            }

            for(Tile next : neighborCandidates(current, range)){
                if(!Build.validPlace(block, player.team(), next.x, next.y, 0)) continue;
                long nk = key(next);
                float tentativeG = curG + 1f;
                Float existing = gScore.get(nk);
                if(existing == null || tentativeG < existing){
                    gScore.put(nk, tentativeG);
                    cameFrom.put(nk, current);
                    open.add(new Node(next, tentativeG + heuristic(next, target, range)));
                }
            }
        }
        return null;
    }

    static long key(Tile t){
        return (long)t.x << 32 | (t.y & 0xFFFFFFFFL);
    }

    float heuristic(Tile tile, Tile target, float range){
        return (float)Math.ceil(tile.dst(target) / range);
    }

    /** Candidates around a tile within the node's range - only along the two axes (beam-node can't link diagonally), on a sparse grid for search speed. */
    Seq<Tile> neighborCandidates(Tile tile, float range){
        Seq<Tile> result = new Seq<>();
        int r = Math.max(1, Math.round(range));
        int step = Math.max(1, Math.round(r / 4f));
        for(int v = -r; v <= r; v += step){
            if(v == 0) continue;
            if(Math.abs(v) > range) continue;
            Tile tx = world.tile(tile.x + v, tile.y);
            if(tx != null) result.add(tx);
            Tile ty = world.tile(tile.x, tile.y + v);
            if(ty != null) result.add(ty);
        }
        return result;
    }

    Seq<Tile> reconstruct(HashMap<Long, Tile> cameFrom, Tile current){
        Seq<Tile> path = new Seq<>();
        Tile cur = current;
        while(cameFrom.containsKey(key(cur))){
            path.insert(0, cur);
            cur = cameFrom.get(key(cur));
        }
        return path;
    }

    void queueBuild(Tile tile, Block block){
        player.unit().addBuild(new BuildPlan(tile.x, tile.y, 0, block));
    }

    static class Node{
        final Tile tile;
        final float f;

        Node(Tile tile, float f){
            this.tile = tile;
            this.f = f;
        }
    }
}
