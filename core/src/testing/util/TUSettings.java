package testing.util;

import arc.func.Cons;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextField;
import arc.scene.ui.TextField.TextFieldFilter;
import arc.scene.ui.layout.Table;
import arc.scene.utils.Elem;
import arc.util.Strings;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable.Setting;
import mindustry.world.meta.StatUnit;
import testing.blui.BLVars;

import static arc.Core.*;
import static mindustry.Vars.mobile;
import static testing.ui.TUDialogs.*;

/**
 * Настройки мода - секция «Testing Utilities» общей вкладки «Моды» (ModsSettings) вместо
 * отдельной категории с баннером. Ключи оригинальные (tu-*). Кастомные Setting'и идут через
 * tracked-путь pref() - поиск по настройкам работает. SKIP: Banner (картинка-шапка, декор),
 * Separator (пустые строки), tu-mobile-test (форсирует мобильный UI на десктопе - тестовый хак
 * самого автора, на этом форке поломал бы десктопные фичи клиента).
 * Добавлено: tu-cheating (в оригинале читался, но в настройках не регистрировался - включает
 * панель в кампании) и tu-teleport (оригинал глушил телепорт на Foo's Client, здесь - тогл,
 * по умолчанию выключен: у scheme admin tools уже есть телепорт к курсору).
 */
public class TUSettings{
    private TUSettings(){
    }

    public static void init(){
        mindustry.client.ui.ModsSettings.section("modsec-testing", t -> {
            t.checkPref("tu-vertical", mobile);
            t.pref(new FloatTextSetting("tu-offset-x", Setup::setOffsetX));
            t.pref(new FloatTextSetting("tu-offset-y", Setup::setOffsetY));
            t.checkPref("tu-cheating", false);
            t.checkPref("tu-teleport", false);
            t.checkPref("tu-load-vanilla", false);
            t.checkPref("tu-instakill", true);
            t.checkPref("tu-death-effect", true);
            t.checkPref("tu-despawns", true);
            t.checkPref("tu-permanent", false);
            t.checkPref("tu-show-hidden", false);
            t.checkPref("tu-fill-all", false);
            t.checkPref("tu-wu-coords", false);
            t.checkPref("tu-tile-info", false);
            t.pref(new TeamSetting("tu-default-team"));
            t.pref(new ButtonSetting("tu-interp", TUIcons.get(Icon.line), () -> interpDialog.show()));
            t.sliderPref("tu-lerp-time", 8, 0, 40, s -> Strings.autoFixed(s / 4f, 2) + " " + StatUnit.seconds.localized());
            t.pref(new ButtonSetting("tu-sounds", TUIcons.get(Icon.effect), () -> soundDialog.show()));
            t.checkPref("tu-music-enabled", false);
            t.checkPref("tu-allow-filters", false);
        });
    }

    /** Кнопка в настройках (открывает диалог). */
    static class ButtonSetting extends Setting{
        Drawable icon;
        Runnable listener;

        public ButtonSetting(String name, Drawable icon, Runnable listener){
            super(name);
            this.icon = icon;
            this.listener = listener;
        }

        @Override
        public void add(SettingsTable table){
            ImageButton b = Elem.newImageButton(icon, listener);
            b.resizeImage(BLVars.iconSize);
            b.label(() -> title).padLeft(6).growX();
            b.left();

            addDesc(table.add(b).left().padTop(3f).get());
            table.row();
        }
    }

    static class TeamSetting extends Setting{
        public TeamSetting(String name){
            super(name);
        }

        @Override
        public void add(SettingsTable table){
            ImageButton b = table.button(
            TUIcons.get(Icon.defense), BLVars.iconSize,
            () -> teamDialog.show(getTeam(), team -> settings.put("tu-default-team", team.id))
            ).left().padTop(3f).get();
            b.label(() -> bundle.format("setting." + name + ".name", "[#" + getTeam().color + "]" + teamDialog.teamName(getTeam()) + "[]")).padLeft(6).growX();
            table.row();

            addDesc(b);
        }

        public Team getTeam(){
            return Team.get(settings.getInt("tu-default-team", Team.sharded.id));
        }
    }

    /** TextSetting, но заголовок перед полем и только float. */
    public static class FloatTextSetting extends Setting{
        Cons<Float> changed;

        public FloatTextSetting(String name, Cons<Float> changed){
            super(name);
            this.changed = changed;
        }

        @Override
        public void add(SettingsTable table){
            TextField field = new TextField();
            field.setFilter(TextFieldFilter.floatsOnly);
            field.setValidator(Strings::canParseFloat);

            field.update(() -> {
                if(field.getScene() != null && field.getScene().getKeyboardFocus() == field) return;
                field.setText(String.valueOf(settings.getFloat(name, 0f)));
            });

            field.changed(() -> {
                if(field.isValid()){
                    float val = Strings.parseFloat(field.getText());
                    settings.put(name, val);
                    if(changed != null){
                        changed.get(val);
                    }
                }
            });

            Table prefTable = table.table().left().padTop(3f).get();
            prefTable.label(() -> title);
            prefTable.add(field);
            addDesc(prefTable);
            table.row();
        }
    }
}
