package mu;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Reflect;
import mindustry.editor.MapEditorDialog;
import mindustry.editor.MapInfoDialog;
import mindustry.editor.MapResizeDialog;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.ui.dialogs.CustomRulesDialog;
import mindustry.ui.dialogs.MapPlayDialog;

import static mindustry.Vars.*;

/**
 * Порт мода "Mapping Utilities" (ApsZoldat, v1.9) как нативный пакет клиента: РАСШИРЕНИЯ
 * ДИАЛОГОВ редактора карт - скрытые правила карты (pvp-автопауза, возможность проигрыша,
 * вселение, обновление грузов, призраки построек, статический туман и его цвета, цвет облаков,
 * затемнение/отключение за границами карты, множитель сопротивления, битовые флаги окружения
 * Env, правила для ЛЮБОЙ команды 0-255, читы/корабли ядра per-team, имя режима, текст миссии),
 * редактор планетного фона карты (3D-превью с вращением/зумом), улучшенные диалоги бана и
 * раскрытия контента (фильтр по планете/вкладке базы данных + настраиваемый размер кнопок) и
 * снятие лимитов размера карты (1..Integer.MAX_VALUE вместо 50..2000).
 * <p>
 * Инвентаризация против уже вшитого Extra Editor ({@code core/src/extraeditor/}): пересечений
 * НЕТ - тот работает с холстом (буфер тайлов, кисти, ластик, сетка, undo-список), этот - с
 * диалогами правил/ресайза. Против ванильного v8 CustomRulesDialog: {@code logicUnitBuild},
 * {@code coreDestroyClear} (Foo) и кнопка «Revealed Blocks» уже есть - из скрытых правил они
 * выброшены, а ванильный диалог revealed-блоков при включённых «улучшенных диалогах» просто
 * подменяется улучшенным (с предикатом ванили: только блоки с нестандартной видимостью, а не
 * «все блоки» как у мода - раскрывать и так видимые блоки бессмысленно). SKIP: собственный
 * «новый редактор» мода (mu.editor.*, mu.ui.Window/EditorUI/UIExplorerDialog, ChunkedGridBits,
 * MUJson) - в v1.9 он выключен самим автором (EditorDialogMod закомментирован в MUMain, WIP);
 * UpdateChecker, SubtitleRandomizer (рандомный подзаголовок карточки мода), JSManager (импорт
 * пакетов мода в Rhino), MUFiles/MUReflect/MUAnnotations - обвязка мода, в движке не нужна.
 * <p>
 * Адаптация по образцу ExtraEditorMod/HeliumMod: без extends Mod, конструктор зовётся из
 * mindustry.client.Main.init() и только вешает ClientLoadEvent-слушатель; self-disable guard -
 * при установленном внешнем моде "mapping-utilities" вшитая копия молчит (иначе скрытые
 * правила задвоились бы в диалоге). Настройки - секция «Mapping Utilities» общей вкладки
 * «Моды» (ModsSettings) с оригинальными ключами (mu_rules_mod, mu_resize_mod, editor_*).
 * Reflection-доступ к приватным полям ванильных диалогов (infoDialog/ruleInfo, playtestDialog,
 * resizeDialog, bannedBlocks/bannedUnits/revealedBlocks) оставлен как в оригинале: расширять
 * API ванильных файлов ради этого - лишний merge-конфликт с upstream. Единственная правка
 * ванили - {@code categoryNames.clear()} в CustomRulesDialog.setupMain (баг ванили, который
 * мод обходил VisibilityListener'ом на shown; в движке чинится одной строкой).
 */
public class MappingUtilitiesMod{
    /** Все активные под-моды; нужны для перечитывания настроек на лету. */
    public static final Seq<MUMod> allMods = new Seq<>();

    /** Guard пройден и ClientLoadEvent отработал (строка в FeaturesDialog). */
    public static boolean enabled;

    public MappingUtilitiesMod(){
        //self-disable: рядом установлен настоящий Mapping Utilities - скрытые правила задвоились
        //бы в диалоге, а лимиты ресайза он и так снимет сам (общий паттерн всех вшитых пакетов)
        if(mods.locateMod("mapping-utilities") != null){
            Log.info("[mu] External Mapping Utilities mod is also loaded - baked-in copy is standing down.");
            return;
        }
        Events.on(ClientLoadEvent.class, e -> {
            try{
                init();
                enabled = true;
            }catch(Throwable t){
                //reflection-поля ванили могли переименоваться при мерже upstream - не ронять клиент
                Log.err("[mu] Failed to attach Mapping Utilities to the editor dialogs", t);
            }
        });
    }

    private void init(){
        //три живых экземпляра CustomRulesDialog: правила карты в редакторе, кастомная игра и
        //плейтест из редактора (четвёртый у мода - его собственный WIP-редактор, SKIP)
        CustomRulesDialog infoRules = Reflect.get(MapInfoDialog.class, Reflect.get(MapEditorDialog.class, ui.editor, "infoDialog"), "ruleInfo");
        CustomRulesDialog playRules = Reflect.get(MapPlayDialog.class, Reflect.get(ui.custom, "dialog"), "dialog");
        CustomRulesDialog playtestRules = Reflect.get(MapPlayDialog.class, Reflect.get(MapEditorDialog.class, ui.editor, "playtestDialog"), "dialog");

        allMods.add(new RulesDialogMod(infoRules));
        allMods.add(new RulesDialogMod(playRules));
        allMods.add(new RulesDialogMod(playtestRules));

        MapResizeDialog resize = Reflect.get(MapEditorDialog.class, ui.editor, "resizeDialog");
        allMods.add(new ResizeDialogMod(resize));

        registerSettings();
        updateMods(null);
    }

    private void registerSettings(){
        mindustry.client.ui.ModsSettings.section("modsec-mu", table -> {
            table.checkPref("mu_rules_mod", true, b -> updateMods(RulesDialogMod.class));
            table.checkPref("mu_resize_mod", true, b -> updateMods(ResizeDialogMod.class));
            //правила ниже читаются при каждом setup() диалога - перечитывать ничего не надо;
            //подмена диалогов бана - в enable(), поэтому тогл дёргает updateMods
            table.checkPref("editor_hidden_rules", true);
            table.checkPref("editor_planet_background", true);
            table.checkPref("editor_environment_settings", true);
            table.checkPref("editor_better_content_dialogs", true, b -> updateMods(RulesDialogMod.class));
            table.sliderPref("editor_content_buttons_size", 50, 30, 80, i -> i + "px");
        });
    }

    /** Перечитать настройку включённости под-модов данного класса (null - всех). */
    public static void updateMods(Class<?> cls){
        allMods.each(m -> {
            if(cls == null || cls.isInstance(m)) m.update();
        });
    }

    /** База под-мода: включается/выключается по своей настройке, умеет откатываться. */
    public abstract static class MUMod{
        public String settingName;
        private boolean active;

        public abstract void enable();

        public abstract void disable();

        public void update(){
            boolean want = Core.settings.getBool(settingName, true);
            if(active) disable();
            if(want) enable();
            active = want;
        }
    }
}
