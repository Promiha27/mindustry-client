package eui.other;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import mindustry.game.EventType.Trigger;
import mindustry.world.Block;

import static mindustry.Vars.content;

/**
 * "eui-makeMineble": while enabled, clears every block's {@code playerUnmineable} flag so the player can
 * hand-mine ore/scrap out from under things that normally block manual mining - restored back to their
 * original unmineable state when turned back off. Ported from other/mine.js.
 */
public class Mine{
    private boolean prevStatus = false;
    private final Seq<Block> nowMineable = new Seq<>();

    public Mine(){
        Events.run(Trigger.update, this::update);
    }

    void update(){
        boolean status = Core.settings.getBool("eui-makeMineble", false);
        if(status == prevStatus) return;

        if(status) makeAllMineable(); else removeMineable();
        prevStatus = status;
    }

    void makeAllMineable(){
        content.blocks().each(b -> {
            if(!b.playerUnmineable) return;
            b.playerUnmineable = false;
            nowMineable.add(b);
        });
    }

    void removeMineable(){
        for(Block b : nowMineable) b.playerUnmineable = true;
        nowMineable.clear();
    }
}
