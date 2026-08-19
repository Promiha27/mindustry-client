package qolc.wave;

import arc.Core;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.client.CommandsKt;
import mindustry.gen.Call;
import mindustry.net.Packets.AdminAction;

/**
 * Команда {@code !wave [count]} - пропуск одной или N волн. Порт features/wave.js.
 * <p>
 * У клиента уже есть нативный пропуск волны (кнопка у статуса волны + бинд {@code Binding.skipWave},
 * причём форк сам закомментировал ограничение "только когда врагов нет" в
 * {@code HudFragment.canSkipWave()}), поэтому от оригинала остался только реальный остаток: пропуск
 * СРАЗУ НЕСКОЛЬКИХ волн одной командой (по одной в ~1.2с, повторный вызов отменяет) и краткий
 * {@code !wave status}. Вся Rhino-акробатика оригинала с перебором имён класса AdminAction и четырёх
 * сигнатур Call.adminRequest здесь не нужна - сигнатуры известны статически.
 * <p>
 * На сервере пропуск исполняет {@code Call.adminRequest(player, AdminAction.wave, null)} - тот же
 * пакет, что шлёт нативная кнопка; сервер сам проверяет админку, так что фича не даёт ничего,
 * чего у игрока нет по правам.
 */
public final class WaveSkipCommand{
    /** Сколько волн осталось пропустить в запущенной серии; 0 = серия не идёт. */
    private static int left = 0;
    private static Timer.Task task;

    private WaveSkipCommand(){
    }

    public static void init(){
        CommandsKt.register("wave [count]", Core.bundle.get("client.command.wave.description"), (args, player) -> {
            if(!Vars.state.isGame()){
                player.sendMessage(Core.bundle.get("qolc.wave.not-in-game"));
                return;
            }

            if(args.length > 0 && (args[0].equals("status") || args[0].equals("s"))){
                player.sendMessage(Core.bundle.format("qolc.wave.status",
                    Vars.state.wave,
                    Vars.state.enemies,
                    Math.max(0, (int)(Vars.state.wavetime / 60f)),
                    Vars.state.rules.waitEnemies,
                    Vars.state.rules.waves,
                    player.admin));
                return;
            }

            if(!Vars.state.rules.waves){
                player.sendMessage(Core.bundle.get("qolc.wave.no-waves"));
                return;
            }

            if(task != null){
                cancel();
                player.sendMessage(Core.bundle.get("qolc.wave.cancelled"));
                return;
            }

            int count = 1;
            if(args.length > 0){
                if(!Strings.canParsePositiveInt(args[0])){
                    player.sendMessage(Core.bundle.get("qolc.wave.usage"));
                    return;
                }
                count = Math.min(Strings.parseInt(args[0]), 50);
            }

            if(count <= 1){
                skipOne();
                return;
            }

            player.sendMessage(Core.bundle.format("qolc.wave.skipping", count));
            left = count;
            //первую волну сразу, остальные по таймеру - как в оригинале (1.2с между пропусками, чтобы
            //сервер успевал развернуть спавн волны, иначе часть adminRequest'ов теряется)
            skipOne();
            left--;
            task = Timer.schedule(() -> {
                if(left <= 0 || !Vars.state.isGame()){
                    cancel();
                    return;
                }
                skipOne();
                left--;
                if(left <= 0){
                    cancel();
                    Vars.player.sendMessage(Core.bundle.format("qolc.wave.done", Vars.state.wave));
                }
            }, 1.2f, 1.2f);
        });
    }

    private static void skipOne(){
        if(Vars.net.client()){
            //серверная проверка админки - своя; не-админу сервер просто откажет
            Call.adminRequest(Vars.player, AdminAction.wave, null);
        }else{
            Vars.logic.skipWave();
        }
    }

    private static void cancel(){
        if(task != null){
            task.cancel();
            task = null;
        }
        left = 0;
    }
}
