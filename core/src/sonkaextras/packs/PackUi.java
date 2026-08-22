package sonkaextras.packs;

import arc.*;
import arc.graphics.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.ui.*;
import sonkaextras.packs.PackScan.*;

import static mindustry.Vars.*;

/**
 * Общие UI-кусочки вкладки «Текстурпаки» для обоих диалогов Helium (установленные моды и
 * браузер): бейджи в строке статус-иконок карточки, строки статуса в раскрытой карточке, список
 * подменяемых файлов и кнопка проверки состава репозитория. Держится в Java рядом с
 * классификатором, чтобы Kotlin-диалоги только звали 4 метода и не дублировали логику.
 */
public class PackUi{
    private PackUi(){
    }

    /** Бейджи в строке статус-иконок карточки установленного мода (ячейки 26px, как у Helium). */
    public static void badges(Table status, PackInfo info){
        if(!info.isPack()) return;
        if(info.textures()){
            badge(status, Icon.imageSmall, Pal.accent, texturesText(info));
        }
        if(info.music.size > 0 || info.newMusic > 0){
            badge(status, Icon.musicSmall, Pal.accent, musicText(info));
        }
        if(info.sounds.size > 0 || info.newSounds > 0){
            badge(status, Icon.playSmall, Pal.accent, soundsText(info));
        }
        if(info.legacySpritesOnly() || info.deadOverrides.size > 0){
            badge(status, Icon.warningSmall, Color.scarlet, info.legacySpritesOnly()
                ? Core.bundle.get("client.sonka.packs.legacy")
                : Core.bundle.format("client.sonka.packs.dead", info.deadOverrides.size));
        }
    }

    /** Бейджи карточки браузера: серые = догадка по описанию, цветные = проверенный состав. */
    public static void browserBadges(Table status, ModListing mod){
        PackInfo checked = PackScan.cachedRemote(mod);
        if(checked != null){
            if(checked.isPack()) badges(status, checked);
            return;
        }
        int mask = PackScan.guess(mod);
        if(mask == 0) return;
        String tip = Core.bundle.get("client.sonka.packs.guessed");
        if((mask & PackScan.TEXTURES) != 0) badge(status, Icon.imageSmall, Color.gray, tip);
        if((mask & PackScan.MUSIC) != 0) badge(status, Icon.musicSmall, Color.gray, tip);
        if((mask & PackScan.SOUNDS) != 0) badge(status, Icon.playSmall, Color.gray, tip);
    }

    private static void badge(Table status, Drawable icon, Color color, String tip){
        status.image(icon).scaling(Scaling.fit).color(color).get()
            .addListener(new Tooltip(t -> t.background(Styles.black6).margin(6f).add(tip)));
    }

    /** Строки статуса в раскрытой карточке (под списком атрибутов Helium). */
    public static void statusLines(Table t, PackInfo info){
        if(info.isPack()){
            line(t, Icon.boxSmall, Pal.accent, Core.bundle.get("client.sonka.packs.ispack"));
        }
        if(info.textures()) line(t, Icon.imageSmall, Color.white, texturesText(info));
        if(info.music.size > 0 || info.newMusic > 0) line(t, Icon.musicSmall, Color.white, musicText(info));
        if(info.sounds.size > 0 || info.newSounds > 0) line(t, Icon.playSmall, Color.white, soundsText(info));
        if(info.other.size > 0) line(t, Icon.fileSmall, Color.white, Core.bundle.format("client.sonka.packs.other", info.other.size));
        if(info.contentPatches.size > 0) line(t, Icon.pencilSmall, Color.white, Core.bundle.format("client.sonka.packs.patches", info.contentPatches.size));
        if(info.bundles > 0 && info.isPack()) line(t, Icon.bookSmall, Color.white, Core.bundle.format("client.sonka.packs.bundles", info.bundles));
        if(info.legacySpritesOnly()) line(t, Icon.warningSmall, Color.scarlet, Core.bundle.get("client.sonka.packs.legacy"));
        else if(info.deadOverrides.size > 0) line(t, Icon.warningSmall, Color.scarlet, Core.bundle.format("client.sonka.packs.dead", info.deadOverrides.size));
        if(info.truncated) line(t, Icon.warningSmall, Color.gray, Core.bundle.get("client.sonka.packs.truncated"));
    }

    private static void line(Table t, Drawable icon, Color color, String text){
        t.table(row -> {
            row.left();
            row.image(icon).scaling(Scaling.fit).size(24f).color(color).padRight(6f);
            row.add(text).color(color).wrap().growX().left();
        }).growX().left().padTop(2f).row();
    }

    /** Вкладка «Замены»: какие именно ванильные файлы подменяет пак. */
    public static void filesTable(Table t, PackInfo info){
        t.top().left().defaults().left().growX();
        boolean any = false;
        any |= section(t, Core.bundle.get("client.sonka.packs.files.sprites"), info.overrides);
        any |= section(t, Core.bundle.get("client.sonka.packs.files.dead"), info.deadOverrides);
        any |= section(t, Core.bundle.get("client.sonka.packs.files.music"), info.music);
        any |= section(t, Core.bundle.get("client.sonka.packs.files.sounds"), info.sounds);
        any |= section(t, Core.bundle.get("client.sonka.packs.files.other"), info.other);
        any |= section(t, Core.bundle.get("client.sonka.packs.files.patches"), info.contentPatches);
        if(info.newSprites > 0 || info.newMusic > 0 || info.newSounds > 0){
            t.add(Core.bundle.format("client.sonka.packs.files.new", info.newSprites, info.newMusic, info.newSounds)).color(Color.gray).wrap().padTop(6f).row();
            any = true;
        }
        if(!any){
            t.add("@client.sonka.packs.files.none").color(Color.gray).row();
        }
    }

    private static boolean section(Table t, String title, Seq<String> items){
        if(items.isEmpty()) return false;
        t.add(title + " (" + items.size + ")").color(Pal.accent).padTop(6f).row();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for(String s : items){
            if(shown++ >= 400){
                sb.append("… +").append(items.size - 400);
                break;
            }
            if(sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        t.add(sb.toString()).color(Color.lightGray).fontScale(0.9f).wrap().padBottom(4f).row();
        return true;
    }

    /**
     * Блок в раскрытой карточке браузера: результат проверки состава репозитория (из кэша) или
     * кнопка «Проверить состав» (2 запроса к GitHub API, лимит 60/час без токена).
     */
    public static void remoteCheck(Table t, ModListing mod, Runnable onChanged){
        t.left().defaults().left().growX();
        PackInfo cached = PackScan.cachedRemote(mod);
        if(cached != null){
            if(cached.isPack()){
                statusLines(t, cached);
            }else{
                line(t, Icon.boxSmall, Color.gray, Core.bundle.get("client.sonka.packs.notpack"));
            }
            t.button("@client.sonka.packs.recheck", Icon.refresh, Styles.grayt, () -> check(t, mod, onChanged)).height(40f).left().padTop(4f).row();
            return;
        }
        int mask = PackScan.guess(mod);
        if(mask != 0){
            line(t, Icon.boxSmall, Color.gray, Core.bundle.get("client.sonka.packs.guessed.long"));
        }
        t.button("@client.sonka.packs.check", Icon.zoom, Styles.grayt, () -> check(t, mod, onChanged)).height(40f).left().padTop(4f).row();
    }

    private static void check(Table t, ModListing mod, Runnable onChanged){
        t.clearChildren();
        t.add("@client.sonka.packs.checking").color(Color.gray).row();
        PackScan.checkRemote(mod, info -> {
            t.clearChildren();
            remoteCheck(t, mod, onChanged);
            if(onChanged != null) onChanged.run();
        }, err -> {
            t.clearChildren();
            line(t, Icon.warningSmall, Color.scarlet, Core.bundle.format("client.sonka.packs.checkfailed", err));
            t.button("@client.sonka.packs.check", Icon.zoom, Styles.grayt, () -> check(t, mod, onChanged)).height(40f).left().padTop(4f).row();
        });
    }

    static String texturesText(PackInfo info){
        if(info.overrides.size > 0 || info.deadOverrides.size > 0){
            return Core.bundle.format("client.sonka.packs.textures", info.overrides.size, info.newSprites);
        }
        return Core.bundle.format("client.sonka.packs.textures.newonly", info.newSprites);
    }

    static String musicText(PackInfo info){
        return Core.bundle.format("client.sonka.packs.music", info.music.size, info.newMusic);
    }

    static String soundsText(PackInfo info){
        return Core.bundle.format("client.sonka.packs.sounds", info.sounds.size, info.newSounds);
    }
}
