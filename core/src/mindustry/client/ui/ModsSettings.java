package mindustry.client.ui;

import arc.Core;
import arc.func.Cons;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

/**
 * Единая вкладка настроек «Моды» для всех вшитых модов вместо россыпи отдельных категорий
 * (раньше их было семь: QoL Suite, Extended UI++, Campaign Utils, QoL Control, Agzam's Mod,
 * Scheme Size, Mindustry Tool). Каждый мод вместо {@code ui.settings.addCategory(...)} зовёт
 * {@link #section} - первый вызов регистрирует саму категорию, дальше все секции складываются
 * в одну общую {@link SettingsTable}.
 * <p>
 * Секция открывается крупным сворачиваемым заголовком через {@link SettingsTable#category} -
 * это родной паттерн форка (та же механика, что у секций вкладки Client: акцентный заголовок
 * с разделительной линией и коллапсером; состояние свёрнутости запоминается per-секция в
 * {@code settingscategory-<имя>-enabled}). Category, как и {@link qol.core.LabelSetting}, идёт
 * через tracked-путь {@code pref(...)} - поисковая строка форка над таблицей продолжает работать
 * по всем модам сразу; сырые {@code table.add(...)} на верхнем уровне билдеров запрещены, иначе
 * SettingsTable отключит поиск для всей вкладки (см. javadoc {@link qol.core.LabelSetting}).
 * Заголовок секции берётся из бандл-ключа {@code client.setting.<имя>.category}.
 * <p>
 * Порядок секций НЕ задаётся здесь явно: он равен порядку вызовов section(), а те идут из
 * ClientLoadEvent-обработчиков модов, срабатывающих в порядке регистрации - т.е. в порядке
 * инстанцирования модов в {@code mindustry.client.Main.init()}: qol-suite (главный мод sonka)
 * первым, дальше по порядку вшивания - eui, campaignutils, qolc, agzam4, scheme, mindustrytool.
 * Переставишь конструкторы в Main.init() - переставятся и секции.
 * <p>
 * mi2u сюда сознательно НЕ входит: его категория - зеркало настроек его же Mindow-окон
 * (контекстные настройки на месте), а её билдер построен на сырых add'ах - слив его сюда
 * отключил бы поиск всей общей вкладки. См. комментарий в {@code mi2u.ModifyFuncs}.
 */
public class ModsSettings{

    /** Общая таблица вкладки «Моды»; создаётся при первом вызове {@link #section}. */
    private static SettingsTable table;

    private ModsSettings(){
    }

    /**
     * Добавляет секцию одного мода в общую вкладку. Если мод стоит в самоотключении (внешняя
     * копия установлена как обычный мод), он просто не зовёт этот метод - пустых секций и, при
     * всех отключённых модах, пустой вкладки не появляется.
     *
     * @param name    имя секции (например {@code modsec-qol}); даёт бандл-ключ заголовка
     *                {@code client.setting.<name>.category} и ключ состояния коллапсера
     * @param builder наполнение секции; ТОЛЬКО tracked-путь: pref()/checkPref()/sliderPref()/
     *                LabelSetting/ButtonSetting (qol.core или eui.core) или свой {@code Setting},
     *                рисующий сырой UI внутри {@code Setting.add(...)} (во время rebuild это
     *                безопасно) - но не сырой {@code table.add(...)} прямо из билдера
     */
    public static void section(String name, Cons<SettingsTable> builder){
        if(table == null){
            //addCategory строит SettingsTable синхронно (SettingsCategory-конструктор зовёт
            //builder.get сразу же), так что после этого вызова table гарантированно не null
            Vars.ui.settings.addCategory(Core.bundle.get("client.settings.mods", "Mods"), Icon.book, t -> table = t);
        }
        table.category(name);
        builder.get(table);
    }

    /**
     * Открывает Settings сразу на вкладке «Моды» - дополнительная точка входа из секции
     * «Вшитые моды» справочника {@link FeaturesDialog}. Индекс для {@code visible()}: пять
     * встроенных вкладок (game/graphics/sound/dev/client - см. {@code SettingsMenuDialog.
     * rebuildMenu()}/{@code visible()}) + позиция нашей категории среди кастомных; категорию
     * ищем по идентичности её SettingsTable, а не по локализуемому имени. {@code show()}
     * синхронно прогоняет shown-листенер (back() + rebuildMenu()), поэтому visible() ПОСЛЕ
     * show() не будет им перезатёрт. Если все вшитые моды в самоотключении, вкладки нет -
     * открываются просто Settings.
     */
    public static void openTab(){
        Vars.ui.settings.show();
        if(table == null) return;
        int idx = Vars.ui.settings.getCategories().indexOf(c -> c.table == table);
        if(idx >= 0) Vars.ui.settings.visible(5 + idx);
    }
}
