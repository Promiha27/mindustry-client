package qol.core;

import arc.Core;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import qol.ui.QolWindow;

/**
 * One of the merged mods (Bridge To Core, Control Helper, ...). Each feature hooks its own event
 * listeners once from {@link #init()} - same as the standalone mods did - and gates its own behaviour
 * every tick with {@link #isEnabled()}, rather than being dynamically attached/detached. A feature that
 * has a HUD panel exposes it through {@link #window()} so the {@link qol.ui.Hub} can offer a show/hide
 * toggle for it.
 */
public interface Feature{
    /** Stable id used as the settings-key prefix; keep equal to the original standalone mod's key prefix so existing settings carry over. */
    String id();

    /** Bundle key for the display name shown in the hub and settings category. */
    String titleKey();

    /** Hooks events; called once on client load regardless of {@link #isEnabled()}. */
    void init();

    /** Adds this feature's preferences to the shared settings category. */
    void buildSettings(SettingsTable table);

    default String settingsKey(){
        return "qol-" + id() + "-enabled";
    }

    default boolean isEnabled(){
        return Core.settings.getBool(settingsKey(), true);
    }

    /**
     * Disabling a feature also hides its window (if it has one) - a disabled module shouldn't leave
     * its controls sitting on screen still clickable - and re-enabling restores it to whatever
     * shown/hidden state the player last left it in via the hub's own toggle
     * ({@link QolWindow#restoreShown}), not necessarily forcing it back open.
     */
    default void setEnabled(boolean value){
        Core.settings.put(settingsKey(), value);
        if(hasWindow() && window() != null){
            if(value) window().restoreShown(); else window().detach();
        }
    }

    default boolean hasWindow(){
        return false;
    }

    default QolWindow window(){
        return null;
    }
}
