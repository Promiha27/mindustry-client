package mindustrytool.features;

import java.util.Optional;

import arc.Core;
import arc.scene.ui.Dialog;
import arc.struct.ObjectMap;

public interface Feature {
    /* перф: ключ настройки неизменяем, а isEnabled() дёргают каждый кадр (visible-колбэки и т.п.) -
     * мемоизация по инстансу вместо конкатенации строки на каждый вызов */
    ObjectMap<Feature, String> SETTING_KEY_CACHE = new ObjectMap<>();

    FeatureMetadata getMetadata();

    default void init() {
    };

    default void onEnable() {
    };

    default void onDisable() {
    };

    default void onEnableChange(boolean enabled) {

    }

    default Optional<Dialog> setting() {
        return Optional.empty();
    }

    default Optional<Dialog> dialog() {
        return Optional.empty();
    }

    default String getSettingKey() {
        String key = SETTING_KEY_CACHE.get(this);
        if (key == null) {
            key = "mindustrytool." + getMetadata().name() + ".enabled";
            SETTING_KEY_CACHE.put(this, key);
        }
        return key;
    }

    default boolean isEnabled() {
        var metadata = getMetadata();

        return Core.settings.getBool(getSettingKey(), metadata.enabledByDefault());
    }
}
