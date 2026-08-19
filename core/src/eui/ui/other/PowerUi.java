package eui.ui.other;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import eui.util.Formatting;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.world.meta.BlockFlag;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.PowerGraph;

import static mindustry.Vars.indexer;
import static mindustry.Vars.player;

/**
 * "eui-showPowerBar": total net power balance/stored-% across every distinct power graph your
 * generators/reactors sit on (deduplicated - a graph spanning many buildings is only counted once),
 * exposed as a reusable {@link #createTableWithBarFrom} wrapper so {@link ResourceRateUi} (and any future
 * caller) can hang the bar under its own content table. Ported from ui/other/power-ui.js.
 */
public class PowerUi{
    private static final float powerBarDefaultWidth = 300;
    private static final float powerBarDefaultHeight = 25;

    private static Bar powerBar;

    /* перф: от Seq графов нужен был только size (в подписи бара) и membership-дедупликация внутри
     * update - заменено на счётчик + переиспользуемый ObjectSet (O(1) contains, ноль аллокаций/кадр) */
    private static int graphCount;
    private static final ObjectSet<PowerGraph> seenGraphs = new ObjectSet<>();
    private static final float[] newStored = new float[1], newMax = new float[1], newCurrent = new float[1];
    private static float storedNetPower, maxNetPower, currentNetPower;
    private static long debugTimerMs = 0;

    static{
        Events.run(Trigger.update, PowerUi::update);
    }

    public static Table createTableWithBarFrom(Table table){
        if(powerBar == null){
            powerBar = new Bar(() -> Formatting.powerToString(currentNetPower, graphCount), () -> Pal.accent, PowerUi::currentPowerStatus);
        }

        Table wrapper = new Table();
        wrapper.add(table);
        wrapper.row();
        wrapper.add(powerBar).visible(PowerUi::powerBarVisible).width(powerBarDefaultWidth).height(powerBarDefaultHeight).pad(4f);
        return wrapper;
    }

    static void update(){
        if(!Core.settings.getBool("eui-showPowerBar", true)) return;

        newStored[0] = 0;
        newMax[0] = 0;
        newCurrent[0] = 0;
        seenGraphs.clear();

        accumulate(indexer.getFlagged(player.team(), BlockFlag.generator), newStored, newMax, newCurrent, seenGraphs);
        accumulate(indexer.getFlagged(player.team(), BlockFlag.reactor), newStored, newMax, newCurrent, seenGraphs);

        //when a power node gets removed, the network briefly reads as 0 power for ~half a second -
        //debounce that blip instead of flashing the bar to zero and back
        if(currentNetPower != 0 && newCurrent[0] == 0){
            long now = System.currentTimeMillis();
            if(debugTimerMs == 0){
                debugTimerMs = now;
                return;
            }else if(now < debugTimerMs + 500){
                return;
            }
        }

        debugTimerMs = 0;
        storedNetPower = newStored[0];
        maxNetPower = newMax[0];
        currentNetPower = newCurrent[0];
        graphCount = seenGraphs.size;
    }

    static void accumulate(Seq<Building> buildings, float[] stored, float[] max, float[] current, ObjectSet<PowerGraph> seenGraphs){
        for(Building b : buildings){
            if(b == null || b.power == null) continue;
            PowerGraph graph = b.power.graph;
            if(seenGraphs.contains(graph)) continue;

            stored[0] += graph.getBatteryStored();
            max[0] += graph.getTotalBatteryCapacity();
            current[0] += graph.getPowerBalance();

            //storing more than 100 graphs at once can lag
            if(graph.getPowerBalance() != 0 && seenGraphs.size < 100) seenGraphs.add(graph);
        }
    }

    static float currentPowerStatus(){
        if(maxNetPower == 0) return 0;
        return storedNetPower / maxNetPower;
    }

    static boolean powerBarVisible(){
        return Core.settings.getBool("eui-showPowerBar", true) && (storedNetPower != 0 || currentNetPower != 0);
    }
}
