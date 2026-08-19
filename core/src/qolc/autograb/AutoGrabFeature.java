package qolc.autograb;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.client.CommandsKt;
import mindustry.content.Blocks;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import arc.graphics.g2d.Draw;

/**
 * Автосбор предмета из своих зданий вокруг юнита: {@code !grab <item>} - и юнит сам вытягивает,
 * например, кремний из всех плавилен в радиусе, пока есть место в трюме. Порт features/autograb.js.
 * <p>
 * Не дублирует нативный AutoTransfer клиента: тот РАЗДАЁТ предметы из ядра/контейнеров нуждающимся
 * блокам (и его экспериментальный drain сливает всё подряд в контейнеры), а здесь - целевой сбор
 * ОДНОГО выбранного предмета в собственный инвентарь. Транспортировка - штатный
 * {@code Call.requestItem}, тот же RPC, что клик по инвентарю здания, поэтому MP-безопасно.
 * <p>
 * Ключ подсветки {@code qol-grab-effects} оставлен как в оригинале - настройка игрока переживает порт.
 */
public final class AutoGrabFeature{
    /** Радиус поиска зданий в world-юнитах (27 тайлов, как в оригинале). */
    private static final float range = 216f;

    private static boolean active = false;
    private static Item item = null;
    private static int minAmount = 10;
    private static final Seq<Building> targets = new Seq<>();
    private static int index = 0;
    private static long lastGrab = 0, lastSearch = 0;

    private AutoGrabFeature(){
    }

    public static void init(){
        Events.on(WorldLoadEvent.class, e -> {
            active = false;
            item = null;
            targets.clear();
        });

        Events.run(Trigger.update, AutoGrabFeature::update);
        Events.run(Trigger.draw, AutoGrabFeature::draw);

        CommandsKt.register("grab [option] [value]", Core.bundle.get("client.command.grab.description"), AutoGrabFeature::runCommand);
        CommandsKt.register("gr [option] [value]", Core.bundle.get("client.command.grab.description"), AutoGrabFeature::runCommand);
    }

    private static void runCommand(String[] args, Player player){
        if(args.length == 0){
            player.sendMessage(Core.bundle.get("qolc.grab.usage"));
            return;
        }

        switch(args[0]){
            case "toggle", "t" -> {
                active = args.length > 1 ? parseToggle(active, args[1]) : !active;
                player.sendMessage(Core.bundle.format("qolc.grab.toggled", onOff(active)));
            }
            case "effects", "e" -> {
                boolean effects = !Core.settings.getBool("qol-grab-effects", true);
                Core.settings.put("qol-grab-effects", effects);
                player.sendMessage(Core.bundle.format("qolc.grab.effects", onOff(effects)));
            }
            case "min" -> {
                if(args.length < 2 || !Strings.canParsePositiveInt(args[1]) || Strings.parseInt(args[1]) < 1){
                    player.sendMessage(Core.bundle.get("qolc.grab.bad-min"));
                    return;
                }
                minAmount = Strings.parseInt(args[1]);
                player.sendMessage(Core.bundle.format("qolc.grab.min-set", minAmount));
            }
            case "status", "s" -> player.sendMessage(Core.bundle.format("qolc.grab.status",
                onOff(active), item == null ? "-" : item.localizedName, minAmount,
                onOff(Core.settings.getBool("qol-grab-effects", true))));
            default -> {
                Item found = findItem(args[0]);
                if(found == null){
                    player.sendMessage(Core.bundle.format("qolc.grab.item-not-found", args[0]));
                    return;
                }
                item = found;
                active = true;
                player.sendMessage(Core.bundle.format("qolc.grab.enabled", found.emoji() + " " + found.localizedName));
            }
        }
    }

    private static Item findItem(String name){
        String lower = name.toLowerCase();
        Item exact = Vars.content.items().find(i -> i.name.equals(lower));
        if(exact != null) return exact;
        return Vars.content.items().find(i -> i.name.contains(lower) || i.localizedName.toLowerCase().contains(lower));
    }

    private static boolean parseToggle(boolean current, String arg){
        return switch(arg){
            case "1", "true", "on" -> true;
            case "0", "false", "off" -> false;
            default -> !current;
        };
    }

    private static String onOff(boolean value){
        return Core.bundle.get(value ? "qolc.on" : "qolc.off");
    }

    private static void update(){
        if(!active || item == null || !Vars.state.isGame()) return;
        Unit unit = Vars.player.unit();
        if(unit == null || unit.dead()) return;

        long now = Time.millis();
        if(now > lastSearch){
            targets.clear();
            Vars.indexer.allBuildings(unit.x, unit.y, range, b -> {
                if(b.team == Vars.player.team() && b.items != null && b.block != Blocks.air) targets.add(b);
            });
            lastSearch = now + 1000;
        }

        if(targets.isEmpty() || now - lastGrab < 250) return;

        int space = unit.type.itemCapacity - unit.stack.amount;
        if(unit.stack.amount > 0 && unit.stack.item != item) space = 0;
        if(space <= 0) return;

        //по одному зданию за заход, круговым перебором - равномерно тянет со всех, как оригинал
        for(int checked = 0; checked < targets.size; checked++){
            index = (index + 1) % targets.size;
            Building b = targets.get(index);
            if(b == null || !b.isValid() || b.team != Vars.player.team()){
                targets.remove(index);
                if(targets.isEmpty()) return;
                index %= targets.size;
                checked--;
                continue;
            }
            if(unit.dst2(b) > range * range) continue;

            int has = b.items.get(item);
            if(has >= minAmount){
                Call.requestItem(Vars.player, b, item, Math.min(has, space));
                lastGrab = now;
                return;
            }
        }
    }

    private static void draw(){
        if(!active || item == null || targets.isEmpty()) return;
        if(!Core.settings.getBool("qol-grab-effects", true)) return;

        Draw.z(Layer.overlayUI);
        //Drawf.select сам ставит цвет (и сбрасывает Draw) - пульсацию передаём альфой через Tmp-цвет
        float alpha = Math.abs(Mathf.sin(Time.time / 15f));
        for(Building b : targets){
            if(b.isValid() && b.items.get(item) >= minAmount){
                Drawf.select(b.x, b.y, b.block.size * Vars.tilesize / 2f + 2f, Tmp.c1.set(Pal.accent).a(alpha));
            }
        }
        Draw.reset();
    }
}
