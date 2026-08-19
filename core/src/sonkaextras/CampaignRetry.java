package sonkaextras;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.util.Log;
import mindustry.core.GameState.State;
import mindustry.game.EventType.SectorLaunchEvent;
import mindustry.game.EventType.WaveEvent;
import mindustry.game.Schematic;
import mindustry.io.SaveIO;
import mindustry.io.SaveIO.SaveException;
import mindustry.type.Sector;
import mindustry.world.blocks.storage.CoreBlock;

import static mindustry.Vars.*;

/**
 * Кампанейские "переиграть": две кнопки в меню паузы ({@code PausedDialog}, только desktop-ветка и
 * только {@code state.isCampaign()}).
 * <p>
 * <b>Рестарт сектора</b> ({@link #restartSector()}): начать сектор заново с нуля, как будто игрок
 * покинул его и высадился снова. Порядок важен и списан с ванильных путей:
 * <ul>
 * <li>{@code control.saves.resetSave()} ДО удаления слота - {@code Control.playSector} первым делом
 *     сохраняет текущую игру в {@code saves.getCurrent()}, то есть пересоздал бы только что
 *     удалённый файл сектора;</li>
 * <li>удаление слота + {@code sector.save = null} - {@code SaveSlot.delete()} поле сектора сам НЕ
 *     чистит (в движке это делает только аварийный путь SaveException в playSector), а именно по
 *     {@code sector.save == null} playSector уходит в ветку свежей высадки {@code playNewSector};</li>
 * <li>сброс {@code sector.info} ровно как у ванильного abandon для не-играемого сектора
 *     (PlanetDialog.abandonSectorConfirm: items/hasCore/production + saveInfo) - остальные поля info
 *     перезаписывает сама генерация, как и после обычного abandon+relaunch;</li>
 * <li>лоадаут: ваниль хранит выбор пер-тип-ядра в settings ("lastloadout-..."), но
 *     {@code universe.getLastLoadout()} читает только in-memory поле - после перезапуска игры оно
 *     пустое и высадка ушла бы в дефолт планеты. Поэтому перед высадкой пере-сеем last loadout из
 *     персистентного выбора ({@code universe.getLoadout(core)}) для того же типа ядра, который взял
 *     бы ванильный лаунч-диалог (bestCoreType origin-сектора при allowLaunchSchematics, иначе
 *     дефолт-ядро планеты). Ресурсы запуска ({@code launch-resources-seq}) остаются от последнего
 *     реального запуска - повторно они с origin-сектора НЕ списываются: за высадку уже заплачено,
 *     рестарт не должен брать вторую цену. Сектор без origin (стартовый, "free launch") повторяет
 *     ванильный free-launch путь: {@code universe.clearLoadoutInfo()} + базовый лоадаут.</li>
 * </ul>
 * Attack/preset-сектора отдельного кода не требуют: после сброса {@code playSector} уходит в тот же
 * {@code playNewSector} -> {@code world.loadSector}, который генерирует пресет-карту заново.
 * <p>
 * <b>Повтор волны</b>: на {@link WaveEvent} (движок фаерит его в {@code Logic.runWave()} сразу после
 * спавна волны) пишется ОДИН скользящий снапшот на сектор - фиксированный файл в
 * {@code saveDirectory} с расширением {@code .wavesav}. Расширение не {@code .msav} сознательно:
 * {@code Saves.load()} обходит {@code saveDirectory} по маске "*.msav" и чужой msav-файл с
 * sector-метой попал бы в список сейвов и в {@code sector.save}-привязку ("two corresponding
 * saves"). Кнопка "Повторить волну" грузит снапшот тем же путём, что грузится сектор-слот в
 * {@code Control.playSector} ({@code SaveIO.load} + {@code world.makeSectorContext}). Снапшот
 * инвалидируется на {@link SectorLaunchEvent} (любая СВЕЖАЯ высадка - и наш рестарт, и ванильный
 * abandon+relaunch), чтобы кнопка не воскресила волну из прошлого прохождения сектора. Автосейв
 * выключается тоглом {@link #autosaveKey} (Sonka Extras) - на слабом диске запись на старте волны
 * может быть заметна как фриз.
 */
public final class CampaignRetry{
    public static final String autosaveKey = "sonka-wave-autosave";

    private CampaignRetry(){
    }

    /** Вызывается из Main.kt рядом с ChainWarn.init() - только вешает слушатели, до ClientLoadEvent. */
    public static void init(){
        Events.on(WaveEvent.class, e -> {
            if(net.client() || !state.isCampaign() || state.gameOver) return;
            if(!Core.settings.getBool(autosaveKey, true)) return;
            Sector sector = state.rules.sector;
            if(sector == null) return;
            try{
                //синхронно в кадре старта волны - как и штатный автосейв Saves.update(); поэтому тогл
                SaveIO.save(waveFile(sector));
            }catch(Throwable t){
                Log.err("[sonka] Не удалось записать снапшот волны", t);
            }
        });

        //свежая высадка (в т.ч. наш рестарт) = снапшот прошлого прохождения больше не валиден
        Events.on(SectorLaunchEvent.class, e -> deleteWaveSnapshot(e.sector));
    }

    /** Файл скользящего снапшота волны этого сектора (см. javadoc класса про расширение). */
    static Fi waveFile(Sector sector){
        return saveDirectory.child("sonka-wavereplay-" + sector.planet.name + "-" + sector.id + ".wavesav");
    }

    /** Есть ли что грузить кнопке "Повторить волну" (дешёвый exists - зовётся из disabled() меню паузы). */
    public static boolean hasWaveSnapshot(){
        Sector sector = state.rules.sector;
        return sector != null && waveFile(sector).exists();
    }

    public static void deleteWaveSnapshot(Sector sector){
        Fi file = waveFile(sector);
        //SaveIO.save на каждой перезаписи откладывает прошлую версию в сосед-"backup" - подчищаем оба
        if(file.exists()) file.delete();
        Fi backup = SaveIO.backupFileFor(file);
        if(backup.exists()) backup.delete();
    }

    /** Кнопка "Повторить волну": грузит снапшот старта текущей волны текущего сектора. */
    public static void loadWaveSnapshot(){
        Sector sector = state.rules.sector;
        if(sector == null || net.client()) return;
        Fi file = waveFile(sector);
        if(!file.exists()) return;

        ui.loadAnd(() -> {
            ui.paused.hide();
            try{
                SaveIO.load(file, world.makeSectorContext(sector));
                //как слот-ветка Control.playSector после load: сектор/облака явно, дальше в игру
                state.rules.sector = sector;
                state.rules.cloudColor = sector.planet.landCloudColor;
                state.set(State.playing);
            }catch(SaveException e){
                Log.err(e);
                logic.reset();
                ui.showErrorMessage("@save.corrupted");
            }
        });
    }

    /** Кнопка "Рестарт сектора": confirm + сброс (см. javadoc класса) + свежая высадка. */
    public static void restartSector(){
        Sector sector = state.rules.sector;
        if(sector == null || net.client()) return;

        ui.showConfirm("@confirm", "@client.sonka.restartsector.confirm", () -> {
            ui.paused.hide();
            Sector origin = sector.info.origin;

            //не дать playSector пересохранить обречённый сектор (см. javadoc про порядок)
            control.saves.resetSave();
            if(sector.save != null){
                sector.save.delete();
                sector.save = null;
            }
            sector.info.items.clear();
            sector.info.hasCore = false;
            sector.info.production.clear();
            sector.saveInfo();

            if(origin != null){
                //тот же выбор типа ядра, что сделал бы ванильный лаунч-диалог (PlanetDialog.lookAt)
                CoreBlock block = sector.allowLaunchSchematics()
                    ? (origin.info.bestCoreType instanceof CoreBlock b ? b : (CoreBlock)origin.planet.defaultCore)
                    : (CoreBlock)origin.planet.defaultCore;
                Schematic loadout = universe.getLoadout(block);
                if(loadout != null) universe.updateLoadout(block, loadout);
                control.playSector(origin, sector);
            }else{
                //стартовый сектор: ванильный free-launch путь (PlanetDialog: from == null)
                universe.clearLoadoutInfo();
                control.playSector(sector);
            }
        });
    }
}
