package mindustrytool;

import arc.Core;
import arc.Events;
import arc.input.KeyBind;
import arc.input.KeyCode;
import arc.struct.Seq;
import mindustry.game.EventType.Trigger;

public class MdtKeybinds {

    public static KeyBind mapBrowserKb = KeyBind.add("mapBrowser", KeyCode.unset, "MindustryTool"),
            schematicBrowserKb = KeyBind.add("schematicBrowser", KeyCode.unset, "MindustryTool"),
            chatKb = KeyBind.add("chatOverlay", KeyCode.unset, "MindustryTool");

    /**
     * Порт: вшитый код регистрирует бинды ПОСЛЕ Vars.loadSettings() (который уже
     * прогнал KeyBind.load() по существующим), поэтому сохранённые значения
     * перечитываем вручную — та же оговорка, что у mi2u/agzam4/scheme.
     */
    public static void load() {
        mapBrowserKb.load();
        schematicBrowserKb.load();
        chatKb.load();
    }

    public static void addFeatureKeyBind(Feature feature, KeyBind keyBind) {
        Events.run(Trigger.update, () -> {
            boolean noInputFocused = !Core.scene.hasField();

            if (noInputFocused && Core.input.keyRelease(keyBind)) {
                Core.app.post(() -> FeatureManager.getInstance().toggle(feature));
            }
        });
    }
}
