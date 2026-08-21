package sonkaextras.dataio;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.type.*;

import static mindustry.Vars.*;

/**
 * КОНТРАКТ «из чего состоит кампания» в этом движке - единственное место, где перечислено, какие
 * файлы и какие ключи settings относятся к прогрессу кампании. Им пользуются и гранулярный
 * импорт/экспорт ({@link DataExport}/{@link DataImport}), и профили кампании
 * ({@link sonkaextras.campaign.CampaignProfiles}) - любая правка контракта делается только здесь.
 * <p>
 * <b>Файлы</b> (всё в {@code saves/}):
 * <ul>
 * <li>{@code sector-<planet>-<id>.msav} - сейв сектора ({@code Saves.getSectorFile}); отличить от
 *     обычного слота ({@code <n>.msav}) можно и по имени, и по {@code SaveMeta.rules.sector} - для
 *     файловых операций достаточно имени, движок сам именует сектор-сейвы строго так;</li>
 * <li>{@code <имя>.msav-backup.msav} - резервная копия, которую {@code SaveIO.save} делает перед
 *     перезаписью ({@code SaveIO.backupFileFor}); {@code Saves.load} такие файлы пропускает;</li>
 * <li>{@code sonka-wavereplay-<planet>-<id>.wavesav} - снапшот начала волны (sonkaextras.CampaignRetry);</li>
 * <li>{@code remap_<planet>_<id>.msav} - временный файл ремапа пресетов при загрузке ({@code Saves.processSave}).</li>
 * </ul>
 * Превью {@code previews/save_slot_sector-*.png} - производный кэш, пересоздаётся лениво; в контракт
 * не входит, но профили переносят его вместе с сейвами, чтобы не показывать чужие картинки.
 * <p>
 * <b>Ключи settings.bin</b> (по источникам в движке):
 * <ul>
 * <li>{@code <content>-unlocked} (bool) - анлок контента, {@code UnlockableContent};</li>
 * <li>{@code req-<content>-<item>} (int) - вложенные в исследование ресурсы, {@code TechTree.TechNode.save};</li>
 * <li>{@code <planet>-s-<id>-info} (json) - {@code SectorInfo} сектора: захват, ресурсы, производство,
 *     волны, имя, {@code Sector.saveInfo};</li>
 * <li>{@code <planet>-campaign-rules} / {@code <planet>-campaign-stats} (json) - правила кампании
 *     планеты и её статистика, {@code Planet.saveRules/saveStats};</li>
 * <li>{@code <planet>-last-sector} (int) - последний выбранный сектор планеты, {@code Planet.setLastSector};</li>
 * <li>{@code utimei} / {@code turn} (int) - время вселенной и номер хода, {@code Universe.save};</li>
 * <li>{@code launch-resources-seq} (json) и {@code lastloadout-<core>} (string) - последний лаунч-груз
 *     и выбранный лоадаут на тип ядра, {@code Universe};</li>
 * <li>{@code last-sector-save} (string) - имя последнего сыгранного сектора, {@code Saves.saveSector};</li>
 * <li>{@code save-sector-<planet>-<id>-name} / {@code -autosave} - пер-сейвовые ключи сектор-слотов
 *     ({@code SaveSlot.getName/isAutosave}, индекс = имя файла без расширения);</li>
 * <li>{@code lastplanet} (string) - планета, открытая в PlanetDialog; {@code campaignselect} (bool) -
 *     показан ли стартовый выбор планеты (без него новая кампания снова предложит выбрать планету);</li>
 * <li>{@code unlocks} - легаси-ключ, который ваниль удаляет при сбросе исследований;</li>
 * <li>{@code autoresearchqueue-<root>} (json) - очередь авто-исследования клиента (ResearchAssistant),
 *     привязана к техдереву планеты.</li>
 * </ul>
 * Вне контракта сознательно: подсказки {@code *-hint-done}, {@code maxresources} (галочка диалога
 * лаунча), ачивки Steam, {@code uuid}/{@code usid-*} - это настройки игрока, а не кампании.
 * <p>
 * <b>Кэш в памяти</b>: флаг {@code UnlockableContent.unlocked} читается из settings в конструкторе
 * контента, {@code TechNode.finishedRequirements} - в {@code setupRequirements}, {@code Universe.seconds/turn}
 * - в конструкторе, {@code Sector.info}/{@code Planet.campaignRules/campaignStats} - при загрузке
 * контента, {@code Sector.save} - в {@code Saves.load}. {@link #reloadLive()} перечитывает всё это
 * после подмены settings/файлов; это best-effort - надёжный путь после подмены прогресса всё равно
 * перезапуск (см. javadoc CampaignProfiles).
 */
public final class CampaignInventory{
    public static final String SECTOR_PREFIX = "sector-";
    public static final String WAVE_PREFIX = "sonka-wavereplay-";
    public static final String REMAP_PREFIX = "remap_";
    public static final String BACKUP_SUFFIX = "-backup." + saveExtension;
    public static final String PREVIEW_PREFIX = "save_slot_";

    private CampaignInventory(){
    }

    //---- файлы ----

    /** Относится ли файл из saves/ к кампании (сектор-сейвы, их бэкапы, снапшоты волн, временные ремапы). */
    public static boolean isCampaignSaveName(String name){
        return name.startsWith(SECTOR_PREFIX) || name.startsWith(WAVE_PREFIX) || name.startsWith(REMAP_PREFIX);
    }

    /** Резервная копия сейва, которую делает SaveIO перед перезаписью; Saves.load их не грузит. */
    public static boolean isBackupSaveName(String name){
        return name.endsWith("backup." + saveExtension);
    }

    /** Обычный (не кампанейский) сейв-слот: {@code <n>.msav}, без бэкапов. */
    public static boolean isRegularSaveName(String name){
        return name.endsWith("." + saveExtension) && !isBackupSaveName(name) && !isCampaignSaveName(name);
    }

    /** Все кампанейские файлы в saves/ (включая бэкапы и wavesav), только верхний уровень. */
    public static Seq<Fi> campaignSaveFiles(){
        return campaignSaveFiles(saveDirectory);
    }

    public static Seq<Fi> campaignSaveFiles(Fi dir){
        Seq<Fi> out = new Seq<>();
        if(!dir.exists()) return out;
        for(Fi f : dir.list()){
            if(!f.isDirectory() && isCampaignSaveName(f.name())) out.add(f);
        }
        return out;
    }

    /** Только основные сектор-сейвы (без бэкапов/wavesav) - то, что считается «секторами» профиля. */
    public static Seq<Fi> sectorFiles(Fi dir){
        return campaignSaveFiles(dir).select(f -> f.name().startsWith(SECTOR_PREFIX) && f.name().endsWith("." + saveExtension) && !isBackupSaveName(f.name()));
    }

    /** Имя планеты из имени файла {@code sector-<planet>-<id>.msav}; null если имя не разбирается. */
    public static @Nullable String planetOf(String fileName){
        if(!fileName.startsWith(SECTOR_PREFIX)) return null;
        String stem = fileName.substring(SECTOR_PREFIX.length());
        int dot = stem.indexOf('.');
        if(dot != -1) stem = stem.substring(0, dot);
        int dash = stem.lastIndexOf('-');
        if(dash <= 0) return null;
        return stem.substring(0, dash);
    }

    /** Превью сектор-сейвов в previews/ (производный кэш). */
    public static Seq<Fi> sectorPreviewFiles(){
        Seq<Fi> out = new Seq<>();
        if(!mapPreviewDirectory.exists()) return out;
        for(Fi f : mapPreviewDirectory.list()){
            if(!f.isDirectory() && f.name().startsWith(PREVIEW_PREFIX + SECTOR_PREFIX)) out.add(f);
        }
        return out;
    }

    //---- ключи settings ----

    /** Относится ли ключ settings к прогрессу кампании (контракт - см. javadoc класса). */
    public static boolean isCampaignKey(String key){
        if(key == null) return false;
        switch(key){
            case "utimei":
            case "turn":
            case "launch-resources-seq":
            case "last-sector-save":
            case "lastplanet":
            case "campaignselect":
            case "unlocks":
                return true;
        }
        if(key.endsWith("-unlocked")) return true;
        if(key.startsWith("req-")) return true;
        if(key.startsWith("lastloadout-")) return true;
        if(key.startsWith("save-" + SECTOR_PREFIX)) return true;
        if(key.startsWith("autoresearchqueue-")) return true;
        if(key.endsWith("-campaign-rules") || key.endsWith("-campaign-stats") || key.endsWith("-last-sector")) return true;
        return isSectorInfoKey(key);
    }

    /** {@code <planet>-s-<id>-info}: между "-s-" и "-info" строго число (защита от случайных совпадений). */
    static boolean isSectorInfoKey(String key){
        if(!key.endsWith("-info")) return false;
        int s = key.lastIndexOf("-s-");
        if(s <= 0) return false;
        String id = key.substring(s + 3, key.length() - "-info".length());
        if(id.isEmpty()) return false;
        for(int i = 0; i < id.length(); i++){
            if(!Character.isDigit(id.charAt(i))) return false;
        }
        return true;
    }

    /** Снимок всех кампанейских ключей из живых settings (значения - как хранит arc: Boolean/Integer/Long/Float/String/byte[]). */
    public static ObjectMap<String, Object> snapshotProgress(){
        ObjectMap<String, Object> out = new ObjectMap<>();
        for(String key : Core.settings.keys()){
            if(isCampaignKey(key)) out.put(key, Core.settings.get(key, null));
        }
        return out;
    }

    /** Отбор кампанейских ключей из произвольной карты (например, прочитанного settings.bin архива). */
    public static ObjectMap<String, Object> filterProgress(ObjectMap<String, Object> all){
        ObjectMap<String, Object> out = new ObjectMap<>();
        for(var e : all){
            if(isCampaignKey(e.key)) out.put(e.key, e.value);
        }
        return out;
    }

    /** Удаляет ВСЕ кампанейские ключи из живых settings (перед подстановкой другого профиля - чтобы не смешивать). */
    public static int clearProgress(){
        Seq<String> keys = new Seq<>();
        for(String key : Core.settings.keys()){
            if(isCampaignKey(key)) keys.add(key);
        }
        for(String key : keys) Core.settings.remove(key);
        return keys.size;
    }

    /** Кладёт ключи в живые settings как есть (типы сохранены). */
    public static void putProgress(ObjectMap<String, Object> progress){
        for(var e : progress){
            if(e.value != null) Core.settings.put(e.key, e.value);
        }
    }

    //---- перечитывание кэша в памяти ----

    /**
     * Best-effort перечитывание кампанейского состояния из settings/файлов после их подмены без
     * перезапуска: анлоки, вложенные ресурсы техдерева, вселенная, инфо секторов, правила/статистика
     * планет, список сейвов и меши планет. Вызывать только из главного меню.
     */
    public static void reloadLive(){
        content.each(c -> {
            if(c instanceof UnlockableContent u) u.reloadUnlock();
        });
        for(TechNode node : TechTree.all){
            node.reloadRequirements();
        }
        universe.reload();
        for(Planet planet : content.planets()){
            planet.loadRules();
            planet.loadStats();
            for(Sector sector : planet.sectors){
                sector.loadInfo();
            }
        }
        //сейвы: load() сам сбрасывает sector.save у всех планет и пересобирает список с диска
        try{
            control.saves.load();
            control.saves.unload();
        }catch(Throwable t){
            Log.err("[sonka-dataio] saves reload failed", t);
        }
        for(Planet planet : content.planets()){
            try{
                if(planet.sectors.any() && planet.generator != null) planet.reloadMeshAsync();
            }catch(Throwable t){
                Log.err("[sonka-dataio] planet mesh reload failed: " + planet.name, t);
            }
        }
    }
}
