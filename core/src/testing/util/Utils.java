package testing.util;

import arc.scene.Element;
import arc.util.Strings;
import mindustry.core.UI;
import mindustry.game.SpawnGroup;
import testing.content.TUFx;

import static arc.Core.*;
import static mindustry.Vars.*;

public class Utils{
    private Utils(){
    }

    public static void spawnIconEffect(String sprite){
        TUFx.iconEffect.at(player.x, player.y, 0, "test-utils-" + sprite);
    }

    public static String round(float f){
        if(f >= 1_000_000_000){
            return Strings.autoFixed(f / 1_000_000_000, 1) + UI.billions;
        }else if(f >= 1_000_000){
            return Strings.autoFixed(f / 1_000_000, 1) + UI.millions;
        }else if(f >= 1000){
            return Strings.autoFixed(f / 1000, 1) + UI.thousands;
        }else{
            return (int)f + "";
        }
    }

    public static int countSpawns(SpawnGroup group){
        if(group.spawn != -1) return 1; //у группы своя точка спавна - считаем её одной валидной

        if(group.type.flying){
            return spawner.countFlyerSpawns();
        }
        return spawner.countGroundSpawns();
    }

    public static boolean hasMouse(){
        Element e = scene.hit(input.mouseX(), input.mouseY(), false);
        return e != null && !e.fillParent;
    }
}
