package eui.ui.other;

import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import mindustry.game.Schematic;
import mindustry.game.Schematic.Stile;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.payloads.PayloadConveyor;
import mindustry.world.blocks.payloads.PayloadRouter;
import mindustry.world.blocks.power.Battery;
import mindustry.world.blocks.power.BeamNode;
import mindustry.world.blocks.power.PowerDiode;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.storage.Unloader;

/**
 * sonka 2026-08-22: авто-иконки ячейки по содержимому схемы - "если в схеме больше всего
 * кремниевых тиглей, то тигель добавляется в ячейку, если есть другие заводы, но их меньше -
 * они вторым, третьим значком и так далее; так же с бурами, заводами юнитов, турелями; конвейеры,
 * трубы и прочее игнорировать".
 * <p>
 * Ранжирование: вес блока = количество x площадь (size²), а не голый счётчик - иначе 4 мендера
 * 1x1 обгоняли бы 2 турели 3x3, хотя "схема на турели" очевидно про турели; при равном весе -
 * стабильно по id контента. Берём до {@link SchematicsTableUi#MAX_CELL_ICONS} штук.
 * <p>
 * "Сантехника" ({@link #isPlumbing}) не участвует: транспорт предметов/жидкостей/payload, мосты,
 * разгрузчики, энергоузлы/батареи/диоды, стены. Если после фильтра ничего не осталось (схема из
 * одних конвейеров/стен) - фолбэк на всё подряд, чтобы у ячейки всё равно была осмысленная иконка.
 */
public class SchemAutoIcons{

    public static Seq<String> names(Schematic s){
        Seq<String> names = new Seq<>();
        if(s == null || s.tiles == null || s.tiles.isEmpty()) return names;

        ObjectIntMap<Block> weight = weigh(s, true);
        if(weight.isEmpty()) weight = weigh(s, false);
        if(weight.isEmpty()) return names;

        final ObjectIntMap<Block> w = weight;
        Seq<Block> blocks = w.keys().toSeq();
        blocks.sort((a, b) -> {
            int wa = w.get(a, 0), wb = w.get(b, 0);
            if(wa != wb) return Integer.compare(wb, wa);
            return Integer.compare(a.id, b.id);
        });
        int n = Math.min(blocks.size, SchematicsTableUi.MAX_CELL_ICONS);
        for(int i = 0; i < n; i++) names.add(blocks.get(i).name);
        return names;
    }

    static ObjectIntMap<Block> weigh(Schematic s, boolean filterPlumbing){
        ObjectIntMap<Block> weight = new ObjectIntMap<>();
        for(Stile t : s.tiles){
            Block b = t.block;
            if(b == null) continue;
            if(filterPlumbing && isPlumbing(b)) continue;
            weight.put(b, weight.get(b, 0) + b.size * b.size);
        }
        return weight;
    }

    /** Блоки-"сантехника", которые не характеризуют схему: транспорт, мосты, разгрузчики, энергосеть, стены. */
    public static boolean isPlumbing(Block b){
        return b instanceof Conveyor          //+ ArmoredConveyor, StackConveyor? (StackConveyor - отдельный класс, ниже)
            || b instanceof StackConveyor
            || b instanceof Duct              //+ OverflowDuct? (ниже отдельно на случай иной иерархии)
            || b instanceof OverflowDuct
            || b instanceof DuctRouter
            || b instanceof DuctJunction
            || b instanceof DuctBridge
            || b instanceof Junction
            || b instanceof Router
            || b instanceof StackRouter
            || b instanceof Sorter
            || b instanceof OverflowGate
            || b instanceof ItemBridge        //+ BufferedItemBridge, LiquidBridge
            || b instanceof DirectionBridge   //+ DirectionLiquidBridge
            || b instanceof Unloader
            || b instanceof DirectionalUnloader
            || b instanceof Conduit           //+ ArmoredConduit
            //LiquidRouter - это и жидкостный роутер 1x1, и БАКИ (liquid tank/container) - баки схему
            //характеризуют (схема-хранилище), игнорируем только одноклеточный роутер
            || (b instanceof LiquidRouter && b.size == 1)
            || b instanceof LiquidJunction
            || b instanceof PayloadConveyor
            || b instanceof PayloadRouter
            || b instanceof PowerNode         //+ LongPowerNode
            || b instanceof BeamNode
            || b instanceof Battery
            || b instanceof PowerDiode
            || b instanceof Wall;             //+ ShieldWall, Door?, AutoDoor? (Door extends Wall)
    }
}
