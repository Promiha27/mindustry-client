package eui.core;

import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

/**
 * A settings-screen row that's just a button opening a dialog (e.g. the auto-fill priority editor), for
 * when a feature needs more than checkPref/sliderPref can express. See {@link LabelSetting}'s javadoc for
 * why this must go through {@link SettingsTable#pref} rather than a raw {@code table.button(...)} call.
 */
public class ButtonSetting extends SettingsTable.Setting{
    final Runnable action;

    public ButtonSetting(String name, Runnable action){
        super(name);
        this.action = action;
    }

    @Override
    public void add(SettingsTable table){
        table.button(title, action).growX().height(50f).pad(4f);
        table.row();
    }
}
