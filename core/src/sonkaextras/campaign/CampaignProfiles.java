package sonkaextras.campaign;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.game.EventType.*;
import mindustry.type.*;
import sonkaextras.dataio.*;
import sonkaextras.dataio.DataExport.*;

import java.io.*;
import java.util.*;
import java.util.EnumSet;

import static mindustry.Vars.*;

/**
 * Профили кампании - несколько независимых кампаний с переключением (идея sonka: «проходишь мод,
 * который полностью меняет кампанию, но не хочешь терять основной сейв»).
 * <p>
 * <b>Что такое кампания</b> - контракт в {@link CampaignInventory}: кампанейские файлы в
 * {@code saves/} (сектор-сейвы, их бэкапы, снапшоты волн) + кампанейские ключи {@code settings.bin}
 * (анлоки, исследования, инфо секторов, вселенная, лоадауты, статистика планет...). Превью
 * сектор-сейвов ({@code previews/}) переезжают вместе с сейвами, чтобы не показывать чужие картинки.
 * <p>
 * <b>Хранение</b>: {@code <data>/campaigns/<id>/} с {@code profile.json} (имя/даты), {@code saves/}
 * (сектор-сейвы НЕактивного профиля), {@code previews/}, {@code progress.json} (снапшот кампанейских
 * ключей, типизированный JSON - {@link SettingsBin}). АКТИВНЫЙ профиль живёт на штатных местах
 * ({@code saves/} + settings) - ваниль и все наши фичи (CampaignRetry, Campaign Utils) работают без
 * правок; его папка {@code saves/} пуста. Активный id - ключ {@link #ACTIVE_KEY} в settings и дубль в
 * файле-маркере {@link #MARKER} (см. ниже зачем). Профиль «default» создаётся неявно из текущего
 * состояния при первом использовании фичи: ничего не двигается, текущее состояние просто
 * объявляется профилем «Основная».
 * <p>
 * <b>Переключение A→B</b> ({@link #switchTo}): (1) ПОЛНЫЙ бэкап A в
 * {@code <data>/backups/<ts>-campaign-<A>.zip} (формат dataio - импортируется обратно любым из
 * диалогов); (2) снапшот ключей A → {@code campaigns/A/progress.json}, кампанейские файлы A →
 * {@code campaigns/A/saves/} (перемещение); (3) из settings удаляются ВСЕ кампанейские ключи A,
 * кладутся ключи B из его {@code progress.json}, файлы B копируются в {@code saves/}; (4) активный
 * id + маркер, {@code settings.forceSave()}; (5) только теперь стираются копии B в его папке.
 * Любой сбой на шагах 2-4 - откат: файлы B убираются из живых папок, файлы A возвращаются, ключи A
 * восстанавливаются из снапшота в памяти, активный id = A; если и откат упал - ошибка называет
 * бэкап-zip. Только из главного меню, не в сети и не во время асинхронной загрузки сейвов.
 * <p>
 * <b>Почему перезапуск, а не live-reload</b>: кампанейское состояние закэшировано в памяти по
 * десятку мест ({@code UnlockableContent.unlocked}, {@code TechNode.finishedRequirements},
 * {@code Universe.seconds/turn}, {@code Sector.info}, {@code Planet.campaignRules/Stats},
 * {@code Sector.save}, меши планет, плюс кэши вшитых модов - Campaign Utils, ResearchAssistant,
 * CampaignRetry). Best-effort перечитывание есть ({@link CampaignInventory#reloadLive()} для
 * импорта прогресса), но доказать его полноту для всех портированных модов нельзя, а цена ошибки -
 * смешение двух кампаний в одном settings.bin. Поэтому после переключения игра ПЕРЕЗАПУСКАЕТСЯ
 * (как {@code mods.reload()} этого форка); в меню до выхода кампанейские ключи никто не пишет
 * ({@code Universe.update} идёт только в кампании, {@code Control.dispose} сохраняет только из игры).
 * <p>
 * <b>Маркер</b>: перезапуск в форке = запуск нового процесса + выход старого; если новый процесс
 * прочитал бы settings.bin в момент записи старым, arc откатился бы на резервную копию settings -
 * ДОпереключательную, и на диске оказались бы сейвы B при ключах A. Чтобы это не прошло незамеченным,
 * при старте ({@link #checkOnStartup}) id из маркера сверяется с ключом settings; расхождение -
 * предупреждение с путём к бэкап-zip. На практике гонка маловероятна (JVM стартует секунды, старый
 * процесс выходит за миллисекунды), но цена - потеря кампании, поэтому страховка дешёвая и нужная.
 */
public final class CampaignProfiles{
    public static final String ACTIVE_KEY = "sonka-campaign-active";
    public static final String DIR = "campaigns";
    public static final String DEFAULT_ID = "default";
    static final String PROFILE_JSON = "profile.json";
    static final String PROGRESS_JSON = "progress.json";
    static final String MARKER = "active-profile.txt";

    /** Состояние «маркер и settings разошлись» - выставляется на старте, показывает диалог профилей. */
    public static @Nullable String startupWarning;
    /** id из маркера / из settings при расхождении (для кнопок разрешения в диалоге). */
    public static @Nullable String mismatchDiskId, mismatchSettingsId;

    private CampaignProfiles(){
    }

    public static class Profile{
        public String id;
        public String name;
        public long created;
        public long lastSwitched;

        public Fi dir(){
            return root().child(id);
        }

        public Fi savesDir(){
            return dir().child("saves");
        }

        public Fi previewsDir(){
            return dir().child("previews");
        }

        public Fi progressFile(){
            return dir().child(PROGRESS_JSON);
        }

        public boolean active(){
            return id.equals(activeId());
        }

        /** Где сейчас лежат сектор-сейвы профиля: активный - штатная saves/, остальные - своя папка. */
        public Fi liveSavesDir(){
            return active() ? saveDirectory : savesDir();
        }

        public int sectors(){
            return CampaignInventory.sectorFiles(liveSavesDir()).size;
        }

        /** Локализованные имена планет по именам файлов секторов (неизвестная планета - как в файле). */
        public Seq<String> planets(){
            ObjectSet<String> names = new ObjectSet<>();
            Seq<String> out = new Seq<>();
            for(Fi f : CampaignInventory.sectorFiles(liveSavesDir())){
                String p = CampaignInventory.planetOf(f.name());
                if(p == null || !names.add(p)) continue;
                Planet planet = content != null ? content.planet(p) : null;
                out.add(planet != null ? planet.localizedName : p);
            }
            return out;
        }

        /** Последняя игра = самый свежий сектор-сейв; нет сейвов - дата последнего переключения/создания. */
        public long lastPlayed(){
            long max = 0;
            for(Fi f : CampaignInventory.sectorFiles(liveSavesDir())) max = Math.max(max, f.lastModified());
            return max > 0 ? max : Math.max(lastSwitched, created);
        }

        /** Есть ли у профиля хоть какой-то прогресс (для подписи «пустая»). */
        public boolean empty(){
            if(sectors() > 0) return false;
            return active() ? CampaignInventory.snapshotProgress().isEmpty() : !progressFile().exists();
        }
    }

    static Fi root(){
        return dataDirectory.child(DIR);
    }

    public static String activeId(){
        return Core.settings.getString(ACTIVE_KEY, DEFAULT_ID);
    }

    /** Можно ли сейчас трогать кампанию: главное меню, не в сети, сейвы не грузятся асинхронно. */
    public static boolean canSwitch(){
        return state.isMenu() && !net.active() && (control == null || !control.saves.loading);
    }

    /**
     * Первое использование: объявить текущее состояние профилем «default». Ничего не двигает. Также
     * чинит ситуацию, когда активный id указывает на удалённую вручную папку.
     */
    public static void ensureInit(){
        root().mkdirs();
        String id = activeId();
        Fi dir = root().child(id);
        if(!dir.child(PROFILE_JSON).exists()){
            Profile p = new Profile();
            p.id = id;
            p.name = id.equals(DEFAULT_ID) ? Core.bundle.get("client.sonka.campaign.default") : id;
            p.created = p.lastSwitched = System.currentTimeMillis();
            save(p);
        }
        if(!Core.settings.has(ACTIVE_KEY)) Core.settings.put(ACTIVE_KEY, id);
        if(!root().child(MARKER).exists()) writeMarker(id);
    }

    public static Seq<Profile> list(){
        ensureInit();
        Seq<Profile> out = new Seq<>();
        for(Fi dir : root().list()){
            if(!dir.isDirectory() || !dir.child(PROFILE_JSON).exists()) continue;
            Profile p = load(dir);
            if(p != null) out.add(p);
        }
        //активный первым, остальные по имени
        out.sort(Structs.comps(Structs.comparingBool(p -> !p.active()), Structs.comparing(p -> p.name.toLowerCase())));
        return out;
    }

    public static Profile active(){
        ensureInit();
        return load(root().child(activeId()));
    }

    static @Nullable Profile load(Fi dir){
        try{
            Jval j = Jval.read(dir.child(PROFILE_JSON).readString());
            Profile p = new Profile();
            p.id = dir.name();
            p.name = j.getString("name", dir.name());
            p.created = j.getLong("created", 0L);
            p.lastSwitched = j.getLong("lastSwitched", 0L);
            return p;
        }catch(Throwable t){
            Log.err("[sonka-campaign] broken profile.json in " + dir.name(), t);
            return null;
        }
    }

    static void save(Profile p){
        Jval j = Jval.newObject();
        j.put("name", p.name);
        j.put("created", p.created);
        j.put("lastSwitched", p.lastSwitched);
        p.dir().mkdirs();
        p.dir().child(PROFILE_JSON).writeString(j.toString(Jval.Jformat.formatted));
    }

    static void writeMarker(String id){
        try{
            root().child(MARKER).writeString(id);
        }catch(Throwable t){
            Log.err("[sonka-campaign] marker write failed", t);
        }
    }

    /** Уникальный id папки из имени: безопасное имя файла + числовой суффикс при коллизии. */
    static String newId(String name){
        String base = Strings.sanitizeFilename(name).trim().toLowerCase().replace(' ', '-');
        if(base.isEmpty() || base.equals(DEFAULT_ID)) base = "profile";
        String id = base;
        int i = 2;
        while(root().child(id).exists()) id = base + "-" + i++;
        return id;
    }

    //---- операции ----

    /** Новый ПУСТОЙ профиль: без сейвов и без прогресса - переключение на него = «новая игра». */
    public static Profile create(String name){
        ensureInit();
        Profile p = new Profile();
        p.id = newId(name);
        p.name = name;
        p.created = System.currentTimeMillis();
        save(p);
        p.savesDir().mkdirs();
        return p;
    }

    /** Копия профиля (активного - из живого состояния, иначе из его папки). */
    public static Profile duplicate(Profile src, String name) throws IOException{
        Profile p = create(name);
        try{
            if(src.active()){
                for(Fi f : CampaignInventory.campaignSaveFiles()) f.copyTo(p.savesDir().child(f.name()));
                p.previewsDir().mkdirs();
                for(Fi f : CampaignInventory.sectorPreviewFiles()) f.copyTo(p.previewsDir().child(f.name()));
                p.progressFile().writeString(SettingsBin.toJson(CampaignInventory.snapshotProgress()));
            }else{
                if(src.savesDir().exists()) for(Fi f : src.savesDir().list()) f.copyTo(p.savesDir().child(f.name()));
                if(src.previewsDir().exists()){
                    p.previewsDir().mkdirs();
                    for(Fi f : src.previewsDir().list()) f.copyTo(p.previewsDir().child(f.name()));
                }
                if(src.progressFile().exists()) src.progressFile().copyTo(p.progressFile());
            }
        }catch(Throwable t){
            p.dir().deleteDirectory();
            throw new IOException(t);
        }
        return p;
    }

    public static void rename(Profile p, String name){
        p.name = name;
        save(p);
    }

    /** Удаление НЕактивного профиля: сначала бэкап-zip в backups/, потом папка. Возвращает бэкап. */
    public static Fi delete(Profile p) throws IOException{
        if(p.active()) throw new IOException("cannot delete the active profile");
        Fi backup = DataExport.backupFile("campaign-" + p.id + "-deleted");
        DataExport.write(backup, payloadOf(p));
        p.dir().deleteDirectory();
        return backup;
    }

    /** Содержимое профиля в формате dataio: категории «сейвы кампании» + «прогресс кампании». */
    public static Payload payloadOf(Profile p){
        if(p.active()){
            Payload live = DataExport.collect(EnumSet.of(DataCategory.campaignSaves, DataCategory.campaignProgress));
            live.label = p.name;
            return live;
        }
        Payload pl = new Payload();
        pl.label = p.name;
        int n = 0;
        if(p.savesDir().exists()){
            for(Fi f : p.savesDir().list()){
                if(f.isDirectory() || CampaignInventory.isBackupSaveName(f.name())) continue;
                pl.files.put("saves/" + f.name(), f);
                n++;
            }
        }
        pl.counts.put(DataCategory.campaignSaves.name(), n);
        pl.categories.add(DataCategory.campaignSaves);
        if(p.progressFile().exists()){
            try{
                pl.progress = SettingsBin.fromJson(p.progressFile().readString());
                pl.counts.put(DataCategory.campaignProgress.name(), pl.progress.size);
                pl.categories.add(DataCategory.campaignProgress);
            }catch(Throwable t){
                Log.err("[sonka-campaign] broken progress.json of " + p.id, t);
            }
        }
        return pl;
    }

    /** Экспорт профиля целиком в zip (тот же формат, что «сейвы кампании + прогресс» в dataio). */
    public static void export(Profile p, Fi out) throws IOException{
        DataExport.write(out, payloadOf(p));
    }

    /** Импорт zip (нашего или ванильного) как НОВОГО профиля - без переключения и без касания активного. */
    public static Profile importProfile(Fi zip, String name) throws IOException{
        try(DataImport.Archive a = DataImport.scan(zip)){
            if(!a.has(DataCategory.campaignSaves) && !a.has(DataCategory.campaignProgress)){
                throw new IOException(Core.bundle.get("client.sonka.campaign.import.nocampaign"));
            }
            Profile p = create(name);
            try{
                if(a.has(DataCategory.campaignSaves)){
                    for(var e : a.entries.get(DataCategory.campaignSaves)){
                        String n = e.getName().replace('\\', '/');
                        n = n.substring(n.lastIndexOf('/') + 1);
                        if(n.isEmpty() || n.contains("..")) continue;
                        try(InputStream in = a.zipStream(e); OutputStream out = p.savesDir().child(n).write(false, 8192)){
                            arc.util.io.Streams.copy(in, out);
                        }
                    }
                }
                if(a.progress != null) p.progressFile().writeString(SettingsBin.toJson(a.progress));
            }catch(Throwable t){
                p.dir().deleteDirectory();
                throw t;
            }
            return p;
        }
    }

    //---- переключение ----

    /** Полный бэкап живой (активной) кампании. */
    public static Fi backupActive(String suffix) throws IOException{
        Fi out = DataExport.backupFile(suffix);
        DataExport.export(out, EnumSet.of(DataCategory.campaignSaves, DataCategory.campaignProgress));
        return out;
    }

    /**
     * Переключение на профиль (алгоритм и откат - см. javadoc класса). Возвращает бэкап-zip прежней
     * кампании. После успешного возврата вызывающий ОБЯЗАН перезапустить игру.
     */
    public static Fi switchTo(Profile target) throws IOException{
        if(!canSwitch()) throw new IOException(Core.bundle.get("client.sonka.campaign.menuonly"));
        Profile from = active();
        if(from == null) throw new IOException("active profile is broken");
        if(from.id.equals(target.id)) throw new IOException("already active");
        if(!target.dir().child(PROFILE_JSON).exists()) throw new IOException("profile folder is missing: " + target.id);

        //(1) полный бэкап A - до любых изменений
        Fi backup = backupActive("campaign-" + from.id);

        //отпустить кэш сейвов (текстуры превью, SaveSlot'ы) - файлы сейчас поедут
        try{
            if(control != null) control.saves.unload();
        }catch(Throwable ignored){
        }

        ObjectMap<String, Object> progressA = CampaignInventory.snapshotProgress();
        Seq<Fi[]> movedOut = new Seq<>(); //[живой путь, путь в папке A]
        Seq<Fi> copiedIn = new Seq<>();   //живые файлы, скопированные из B
        try{
            //(2) A → своя папка
            from.progressFile().writeString(SettingsBin.toJson(progressA));
            from.savesDir().mkdirs();
            from.previewsDir().mkdirs();
            for(Fi f : CampaignInventory.campaignSaveFiles()){
                Fi dst = from.savesDir().child(f.name());
                f.moveTo(dst);
                movedOut.add(new Fi[]{f, dst});
            }
            for(Fi f : CampaignInventory.sectorPreviewFiles()){
                Fi dst = from.previewsDir().child(f.name());
                f.moveTo(dst);
                movedOut.add(new Fi[]{f, dst});
            }

            //(3) ключи: чисто убрать A, положить B
            CampaignInventory.clearProgress();
            if(target.progressFile().exists()){
                CampaignInventory.putProgress(SettingsBin.fromJson(target.progressFile().readString()));
            }
            //файлы B - копией (оригиналы в папке B стираются только после forceSave)
            saveDirectory.mkdirs();
            mapPreviewDirectory.mkdirs();
            if(target.savesDir().exists()){
                for(Fi f : target.savesDir().list()){
                    if(f.isDirectory()) continue;
                    Fi dst = saveDirectory.child(f.name());
                    f.copyTo(dst);
                    copiedIn.add(dst);
                }
            }
            if(target.previewsDir().exists()){
                for(Fi f : target.previewsDir().list()){
                    if(f.isDirectory()) continue;
                    Fi dst = mapPreviewDirectory.child(f.name());
                    f.copyTo(dst);
                    copiedIn.add(dst);
                }
            }

            //(4) активный id: ключ + маркер, и сразу на диск
            Core.settings.put(ACTIVE_KEY, target.id);
            writeMarker(target.id);
            long now = System.currentTimeMillis();
            from.lastSwitched = now;
            save(from);
            target.lastSwitched = now;
            save(target);
            Core.settings.forceSave();
        }catch(Throwable t){
            Log.err("[sonka-campaign] switch " + from.id + " -> " + target.id + " failed, rolling back", t);
            try{
                rollback(from, progressA, movedOut, copiedIn);
            }catch(Throwable t2){
                Log.err("[sonka-campaign] ROLLBACK FAILED", t2);
                throw new IOException(Core.bundle.format("client.sonka.campaign.switch.rollbackfail", t.getMessage(), backup.path()), t);
            }
            throw new IOException(Core.bundle.format("client.sonka.campaign.switch.fail", t.getMessage(), backup.name()), t);
        }

        //(5) копии B в его папке больше не нужны - живые файлы уже на месте и settings сохранены
        try{
            if(target.savesDir().exists()) for(Fi f : target.savesDir().list()) f.delete();
            if(target.previewsDir().exists()) for(Fi f : target.previewsDir().list()) f.delete();
            target.progressFile().delete();
        }catch(Throwable t){
            //не критично: при следующем переключении папка будет перезаписана снапшотом
            Log.err("[sonka-campaign] cleanup of " + target.id + " stored copies failed", t);
        }
        return backup;
    }

    static void rollback(Profile from, ObjectMap<String, Object> progressA, Seq<Fi[]> movedOut, Seq<Fi> copiedIn){
        for(Fi f : copiedIn){
            try{
                f.delete();
            }catch(Throwable t){
                Log.err("[sonka-campaign] rollback: delete " + f.name() + " failed", t);
            }
        }
        for(Fi[] pair : movedOut){
            if(pair[1].exists()) pair[1].moveTo(pair[0]);
        }
        CampaignInventory.clearProgress();
        CampaignInventory.putProgress(progressA);
        Core.settings.put(ACTIVE_KEY, from.id);
        writeMarker(from.id);
        Core.settings.forceSave();
    }

    //---- старт ----

    /** Вешается из Main.kt: сверка маркера с settings (см. javadoc класса, «Маркер»). */
    public static void init(){
        Events.on(ClientLoadEvent.class, e -> checkOnStartup());
    }

    static void checkOnStartup(){
        try{
            Fi marker = root().child(MARKER);
            if(!marker.exists()) return; //фича ещё не использовалась
            String onDisk = marker.readString().trim();
            String inSettings = activeId();
            if(onDisk.equals(inSettings)) return;
            Profile disk = load(root().child(onDisk)), set = load(root().child(inSettings));
            mismatchDiskId = onDisk;
            mismatchSettingsId = inSettings;
            startupWarning = Core.bundle.format("client.sonka.campaign.startup.mismatch",
                disk != null ? disk.name : onDisk, set != null ? set.name : inSettings, dataDirectory.child("backups").path());
            Log.warn("[sonka-campaign] @", startupWarning);
            Core.app.post(() -> ui.showCustomConfirm("@client.sonka.campaign.title", startupWarning,
                "@client.sonka.campaign.title", "@ok", () -> new CampaignProfilesDialog().show(), () -> {}));
        }catch(Throwable t){
            Log.err("[sonka-campaign] startup check failed", t);
        }
    }

    /** Имя профиля по id (или сам id, если папки нет) - для подписей кнопок разрешения расхождения. */
    public static String nameOf(@Nullable String id){
        if(id == null) return "?";
        Profile p = root().child(id).child(PROFILE_JSON).exists() ? load(root().child(id)) : null;
        return p != null ? p.name : id;
    }

    /**
     * Разрешение расхождения маркера и settings РЕШЕНИЕМ sonka: {@code trustSettings} - текущее
     * живое состояние объявляется профилем из settings (маркер переписывается), иначе - профилем из
     * маркера (переписывается ключ settings). Файлы не двигаются; если кампания реально смешана,
     * правильный путь - импорт бэкап-zip через dataio с перезаписью.
     */
    public static void resolveMismatch(boolean trustSettings){
        if(startupWarning == null) return;
        if(trustSettings){
            writeMarker(activeId());
        }else if(mismatchDiskId != null){
            Core.settings.put(ACTIVE_KEY, mismatchDiskId);
            Core.settings.forceSave();
        }
        ensureInit();
        startupWarning = null;
        mismatchDiskId = mismatchSettingsId = null;
    }
}
