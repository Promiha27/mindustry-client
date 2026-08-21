package testing.util;

import arc.Core;
import arc.files.Fi;
import arc.graphics.Pixmap;
import arc.graphics.Texture.TextureFilter;
import arc.graphics.g2d.PixmapPacker;
import arc.scene.style.TextureRegionDrawable;
import arc.util.Log;
import mindustry.content.UnitTypes;

/**
 * Спрайты мода под оригинальными именами атласа ({@code test-utils-*}). У обычного мода их
 * пакует спрайт-пакер Mods; вшитая копия несёт png в {@code core/assets/testing/} и пакует
 * их в Core.atlas сама через PixmapPacker - в отличие от простого addRegion (паттерн
 * MI2UMod.loadSprites) это даёт и nine-patch'и ({@code *.9.png}: суффикс ".9" в имени региона
 * заставляет пакер вычислить splits/pads, а {@code atlas.drawable()} потом отдаёт NinePatch).
 * Страница одна, 256x256 - иконок 14 штук по 32px плюс 11 крошечных рамок.
 */
public class TUIcons{
    public static TextureRegionDrawable
    clone, seppuku,
    core, dump,
    survival, sandbox,
    heal, invincibility,
    weather,
    sounds, musics, stop,
    lightOff, lightOn,
    alpha;

    private TUIcons(){
    }

    public static void init(){
        loadSprites();

        clone = get("clone");
        seppuku = get("seppuku");
        core = get("core");
        dump = get("dump");
        survival = get("survival");
        sandbox = get("sandbox");
        heal = get("heal");
        invincibility = get("invincibility");
        weather = get("weather");
        sounds = get("sounds");
        musics = get("musics");
        stop = get("stop");
        lightOff = get("light-off");
        lightOn = get("light-on");
        alpha = new TextureRegionDrawable(UnitTypes.alpha.uiIcon);
    }

    private static final String[] ICONS = {
    "clone", "core", "dump", "heal", "invincibility", "light-off", "light-on", "musics",
    "sandbox", "seppuku", "sounds", "stop", "survival", "weather"
    };
    private static final String[] NINEPATCHES = {
    "button-center", "button-center-disabled", "button-center-down", "button-center-over",
    "button-left", "button-left-down", "button-left-over",
    "button-right", "button-right-down", "button-right-over",
    "pane-bottom"
    };

    private static void loadSprites(){
        if(Core.atlas.has("test-utils-clone")) return;

        //явные списки имён: Fi.list() по internal-папке внутри desktop-jar пуст
        PixmapPacker packer = new PixmapPacker(256, 256, 2, true);
        try{
            for(String name : ICONS){
                pack(packer, "test-utils-" + name, Core.files.internal("testing/icons/" + name + ".png"));
            }
            for(String name : NINEPATCHES){
                //"test-utils-button-left.9": суффикс .9 = nine-patch для пакера (splits/pads из рамки)
                pack(packer, "test-utils-" + name + ".9", Core.files.internal("testing/ui/" + name + ".9.png"));
            }
            packer.updateTextureAtlas(Core.atlas, TextureFilter.linear, TextureFilter.linear, false);
        }catch(Throwable t){
            Log.err("[testing] Failed to pack Testing Utilities sprites", t);
        }finally{
            packer.dispose();
        }
    }

    private static void pack(PixmapPacker packer, String name, Fi file){
        Pixmap pix = new Pixmap(file);
        packer.pack(name, pix);
        pix.dispose();
    }

    static TextureRegionDrawable get(String name){
        return new TextureRegionDrawable(Core.atlas.find("test-utils-" + name));
    }

    public static TextureRegionDrawable get(TextureRegionDrawable icon){
        return new TextureRegionDrawable(icon);
    }
}
