package scheme.tools;

import arc.Events;
import arc.func.Func;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.struct.StringMap;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.*;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.input.Placement;
import mindustry.input.Placement.NormalizeResult;
import mindustry.world.Block;
import mindustry.world.Tile;

import static arc.Core.*;
import static mindustry.Vars.*;

/**
 * "Проклятые схемы" из Scheme Size: слои копирования (постройки/пол/стат.блоки/оверлеи/
 * весь ландшафт). Выделение F-рамкой создаёт схему выбранного слоя (хук в DesktopInput),
 * а установка таких схем идёт через админ-инструменты (setNet у хоста, см. хук в
 * InputHandler.flushPlans). У клиента нативно есть только env-копия целиком в редакторе
 * (Alt+F) - слои и установка вне редактора уникальны.
 */
public class SchematicLayers{

    /** Текущий слой копирования. */
    public static Layer layer = Layer.building;

    public static void load(){
        Events.run(WorldLoadEvent.class, () -> layer = Layer.building);
    }

    public static Layer nextLayer(){
        layer = layer.next();
        ui.showInfoFade(bundle.format("scheme.layer", bundle.get("scheme.layer." + layer.name())));
        return layer;
    }

    /** Замена schematics.create в местах создания схем выделением: учитывает выбранный слой. */
    public static Schematic create(int x, int y, int x2, int y2, boolean editorEnv){
        if(layer == Layer.building) return schematics.create(x, y, x2, y2, editorEnv);
        return layer.create(x, y, x2, y2);
    }

    /** Схема считается "проклятой", если состоит только из env-блоков (и мы не в редакторе). */
    public static boolean isCursed(Seq<BuildPlan> plans){
        if(plans.isEmpty()) return false;
        if(state.rules.editor) return false;
        return !plans.contains(plan -> plan.block.isVisible());
    }

    /** Можно ли ставить проклятые схемы: включены админ-инструменты и мы хост (или "не проверять"). */
    public static boolean cursedUsable(){
        return settings.getBool("adminsenabled") && (!net.client() || settings.getBool("adminsalways"));
    }

    public enum Layer{
        building(null),
        floor(Tile::floor),
        block(tile -> tile.build == null && tile.block() != Blocks.air ? tile.block() : null),
        overlay(tile -> tile.overlay() != Blocks.air ? tile.overlay() : null),
        terrain();

        private final Func<NormalizeResult, Schematic> create;

        Layer(Func<Tile, Block> provider){
            this.create = result -> create(result.x, result.y, result.x2, result.y2, provider);
        }

        Layer(){
            this.create = result -> createTerrain(result.x, result.y, result.x2, result.y2);
        }

        public Layer next(){
            return values()[(ordinal() + 1) % values().length];
        }

        public Schematic create(int x, int y, int x2, int y2){
            NormalizeResult result = Placement.normalizeArea(x, y, x2, y2, 0, false, maxSchematicSize);
            return create.get(result);
        }

        private Schematic create(int x1, int y1, int x2, int y2, Func<Tile, Block> provider){
            Seq<Stile> tiles = new Seq<>();

            for(int x = x1; x <= x2; x++){
                for(int y = y1; y <= y2; y++){
                    Tile tile = world.tile(x, y);
                    if(tile == null) continue;
                    Block block = provider.get(tile);
                    if(block != null){
                        Object config = tile.build != null ? tile.build.config() : null;
                        byte rotation = tile.build != null ? (byte)tile.build.rotation : 0;
                        tiles.add(new Stile(block, x - x1, y - y1, config, rotation));
                    }
                }
            }

            if(tiles.isEmpty()) return new Schematic(tiles, new StringMap(), 1, 1);

            int minx = tiles.min(st -> st.x).x;
            int miny = tiles.min(st -> st.y).y;

            tiles.each(st -> {
                st.x -= minx;
                st.y -= miny;
            });

            return new Schematic(tiles, new StringMap(), tiles.max(st -> st.x).x + 1, tiles.max(st -> st.y).y + 1);
        }

        private Schematic createTerrain(int x1, int y1, int x2, int y2){
            x1 = Mathf.clamp(x1, 0, world.width()); y1 = Mathf.clamp(y1, 0, world.height());
            x2 = Mathf.clamp(x2, 0, world.width()); y2 = Mathf.clamp(y2, 0, world.height());

            Seq<Stile> tiles = new Seq<>();
            int width = x2 - x1 + 1, height = y2 - y1 + 1;

            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    Tile tile = world.tile(x + x1, y + y1);
                    if(tile == null) continue;

                    tiles.add(new Stile(tile.floor(), x, y, null, (byte)0));
                    if(tile.block() != Blocks.air && tile.build == null) tiles.add(new Stile(tile.block(), x, y, null, (byte)0));
                    if(tile.overlay() != Blocks.air) tiles.add(new Stile(tile.overlay(), x, y, null, (byte)0));
                }
            }

            return new Schematic(tiles, new StringMap(), width, height);
        }
    }
}
