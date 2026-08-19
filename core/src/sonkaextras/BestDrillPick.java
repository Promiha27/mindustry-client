package sonkaextras;

import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.production.BeamDrill;
import mindustry.world.blocks.production.Drill;

import static mindustry.Vars.*;

/**
 * СКМ (pick/пипетка) по руде = автоматически выбрать ЛУЧШИЙ доступный бур для неё (просьба sonka).
 * Встраивается в {@code PlacementFragment.updatePick} тем же приёмом, каким форк уже подменяет
 * СКМ по ядру на "лучшее доступное ядро": если под курсором нет здания/плана, но есть руда -
 * подставляем бур вместо null.
 * <p>
 * "Лучший" = среди буров, которые (а) физически могут копать эту руду ({@code tier >= hardness}),
 * (б) доступны игроку сейчас ({@code isVisible && unlockedNow && !banned}) - берём максимальный
 * {@code tier}, при равенстве - меньший {@code drillTime} (быстрее). Напольная руда - семейство
 * {@link Drill} (включая эрекирские {@code BurstDrill}-наследники), стенная (Эрекир) -
 * {@link BeamDrill}. Контент-дривен: модовые буры участвуют автоматически.
 */
public final class BestDrillPick{

    private BestDrillPick(){
    }

    /** Лучший доступный бур для руды под курсором; null = тут не руда или бура нет. */
    public static Block bestFor(Tile tile, Team team){
        if(tile == null) return null;

        //напольная руда: только на пустом тайле (block=air), иначе пипетка и так взяла бы здание
        if(tile.block() == Blocks.air){
            Item drop = tile.drop();
            if(drop == null) return null;
            return best(drop, false);
        }

        //стенная руда Эрекира (wallDrop у рудной стены)
        Item wallDrop = tile.wallDrop();
        if(wallDrop != null) return best(wallDrop, true);

        return null;
    }

    static Block best(Item drop, boolean wall){
        Block bestBlock = null;
        int bestTier = -1;
        float bestTime = Float.MAX_VALUE;

        for(Block b : content.blocks()){
            int tier;
            float time;
            if(!wall && b instanceof Drill d){
                tier = d.tier;
                time = d.drillTime;
            }else if(wall && b instanceof BeamDrill d){
                tier = d.tier;
                time = d.drillTime;
            }else{
                continue;
            }

            if(tier < drop.hardness) continue;
            if(!b.isVisible() || !b.unlockedNow() || state.rules.isBanned(b)) continue;

            //выше tier = лучше; при равном - быстрее (меньший drillTime)
            if(tier > bestTier || (tier == bestTier && time < bestTime)){
                bestBlock = b;
                bestTier = tier;
                bestTime = time;
            }
        }
        return bestBlock;
    }
}
