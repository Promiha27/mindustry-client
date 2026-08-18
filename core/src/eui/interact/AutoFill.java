package eui.interact;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import mindustry.content.Items;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType.Trigger;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.Turret.TurretBuild;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.blocks.units.UnitFactory.UnitPlan;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItemDynamic;
import mindustry.world.consumers.ConsumeItemFilter;
import mindustry.world.consumers.ConsumeItems;

import static mindustry.Vars.buildingRange;
import static mindustry.Vars.content;
import static mindustry.Vars.indexer;
import static mindustry.Vars.player;

/**
 * Auto-fill: while carrying an item, walks the player toward whichever nearby building most needs a
 * hand-off (weighted by the per-block-type priority set in {@link AutofillPriorityDialog}), and when
 * empty-handed and near the core, fetches whatever the highest-priority nearby building needs next.
 * Ported from interact/auto-fill.js.
 * <p>
 * NOTE - this client fork already ships {@code mindustry.client.utils.AutoTransfer}, itself explicitly
 * "based on Ferlern/extended-ui" (see its own doc comment). That's the exact upstream this mod forked
 * from, so the two features are close cousins doing overlapping jobs (auto depositing/fetching items
 * near the core) through different, independently-toggled settings keys ("autotransfer" vs
 * "eui-auto-fill") - see EUIMod's javadoc for the full collision note. Both default off, but turning
 * both on at once will have them compete over the same item transfers.
 */
public class AutoFill{
    private static final String PRIORITY_SETTINGS_KEY = "eui.autofill.priority";
    private static final int MIN_ACCEPT_AMOUNT = 5;

    public AutoFill(){
        Events.run(Trigger.update, this::update);
    }

    void update(){
        if(!Core.settings.getBool("eui-auto-fill", false) || !InteractTimer.canInteract()) return;
        if(player.unit() == null) return;

        ItemStack stack = player.unit().stack;
        Team team = player.team();
        CoreBuild core = player.closestCore();
        boolean coreAvailable = Core.settings.getBool("eui-interact-core", false) && core != null;

        ObjectMap<String, Integer> config = Core.settings.getJson(PRIORITY_SETTINGS_KEY, ObjectMap.class, ObjectMap::new);

        //request: Building (deposit target) or Item (fetch-from-core target); a plain mutable holder
        //instead of the array-boxing trick, since eachBlock's callback needs to update it across calls
        Best best = new Best();
        indexer.eachBlock(team, player.x, player.y, buildingRange, b -> true, b -> {
            if(!InteractTimer.canInteract()) return;

            Block block = b.block;
            Consume itemConsumer = findItemConsumer(block);
            if(itemConsumer == null) return;

            int blockPriority = config.get(block.name, 0);

            //insert requests (depositing into a building) win over fetch requests at equal priority
            if(blockPriority < best.priority) return;
            if(blockPriority == best.priority && best.request instanceof Building) return;

            int acceptAmount = b.acceptStack(stack.item, stack.amount, player.unit());
            if(acceptAmount >= MIN_ACCEPT_AMOUNT){
                best.request = b;
                best.priority = blockPriority;
                return;
            }

            if(blockPriority <= best.priority) return;

            Item newRequest = null;
            if(!coreAvailable) return;
            if(block instanceof ItemTurret turret){
                if(!((TurretBuild)b).ammo.isEmpty()) return;
                newRequest = getBestAmmo(turret, core);
            }else if(block instanceof UnitFactory factory){
                newRequest = getUnitFactoryRequest(b, factory, core);
            }else if(b.items != null){
                newRequest = getItemRequest(itemConsumer, b, core);
            }
            if(newRequest != null){
                best.request = newRequest;
                best.priority = blockPriority;
            }
        });

        Object request = best.request;
        if(request instanceof Building target){
            Call.transferInventory(player, target);
            InteractTimer.increase();
            return;
        }

        if(!coreAvailable || request == null || !player.within(core, buildingRange)) return;

        if(stack.amount > 0){
            Call.transferInventory(player, core);
            Call.dropItem(0);
        }else{
            Call.requestItem(player, core, (Item)request, 999);
        }
        InteractTimer.increase();
    }

    /** Mutable best-candidate-so-far holder for {@link #update()}'s eachBlock scan. */
    static class Best{
        Object request; //Building (deposit target) or Item (fetch-from-core target)
        int priority = -1;
    }

    static Consume findItemConsumer(Block block){
        for(Consume c : block.consumers){
            if(c instanceof ConsumeItems || c instanceof ConsumeItemFilter || c instanceof ConsumeItemDynamic) return c;
        }
        return null;
    }

    static Item getBestAmmo(ItemTurret turret, CoreBuild core){
        Item best = null;
        float bestDamage = 0;
        for(ObjectMap.Entry<Item, BulletType> entry : turret.ammoTypes.entries()){
            float totalDamage = entry.value.damage + entry.value.splashDamage;
            if(totalDamage > bestDamage && core.items.get(entry.key) >= 20){
                best = entry.key;
                bestDamage = totalDamage;
            }
        }
        return best;
    }

    static Item getUnitFactoryRequest(Building build, UnitFactory block, CoreBuild core){
        int currentPlan = ((UnitFactory.UnitFactoryBuild)build).currentPlan;
        if(currentPlan == -1) return null;
        UnitPlan plan = block.plans.get(currentPlan);
        return findRequiredItem(plan.requirements, build, core);
    }

    static Item getItemRequest(Consume consumesItems, Building build, CoreBuild core){
        if(consumesItems instanceof ConsumeItemFilter filter){
            return getFilterRequest(filter, build, core);
        }else if(consumesItems instanceof ConsumeItems items){
            return findRequiredItem(items.items, build, core);
        }else{
            return null;
        }
    }

    static Item getFilterRequest(ConsumeItemFilter filter, Building build, CoreBuild core){
        Item[] request = {null};
        boolean[] stop = {false};
        content.items().each(item -> {
            if(filter.filter.get(item) && item != Items.blastCompound && core.items.get(item) >= 20){
                if(build.acceptStack(item, 20, player.unit()) >= MIN_ACCEPT_AMOUNT && request[0] == null && !stop[0]){
                    request[0] = item;
                }else{
                    stop[0] = true;
                }
            }
        });
        return request[0];
    }

    static Item findRequiredItem(ItemStack[] stacks, Building build, CoreBuild core){
        for(ItemStack itemStack : stacks){
            Item item = itemStack.item;
            if(core.items.get(item) >= 20 && build.acceptStack(item, 20, player.unit()) >= MIN_ACCEPT_AMOUNT){
                return item;
            }
        }
        return null;
    }
}
