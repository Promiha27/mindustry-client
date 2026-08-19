package mi2u.input;

import arc.input.*;

public class MBinding{
    public static final KeyBind

    ctrlBuildBypass = KeyBind.add("ctrl_build_bypass", KeyCode.controlLeft, "MI2U"),
    autoCompleteLogic = KeyBind.add("auto_complete_logic", KeyCode.tab, "MI2U"),
    ctrlUI = KeyBind.add("ctrl_ui", KeyCode.controlLeft, "MI2U"),
    uiPopWorldFinder = KeyBind.add("ui_pop_world_finder", KeyCode.f, "MI2U")
        ;

    /**
     * Вшитая копия регистрирует бинды позже, чем Vars.loadSettings() прогоняет
     * KeyBind.all[].load(), поэтому сохранённые пользователем значения подтягиваем сами.
     */
    public static void load(){
        ctrlBuildBypass.load();
        autoCompleteLogic.load();
        ctrlUI.load();
        uiPopWorldFinder.load();
    }
}
