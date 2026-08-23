package mindustry.client;

import arc.Core;
import arc.Graphics.*;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Pixmaps;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.ui.Fonts;

import static mindustry.Vars.mobile;

/**
 * Recolors the default mouse cursor to a custom color, a two-color gradient shimmer, or a rainbow
 * cycle - the same three modes {@code BuildBeamColorFeature} in the QoL Suite mod offers for build
 * beams, applied to the cursor instead. There's no tintable in-engine cursor draw call to hook (the
 * cursor is a native OS cursor built once from {@code cursors/cursor.png}), so this rebuilds that
 * native cursor from a recolored copy of the pixmap on demand.
 */
public class CursorColor{
    static final float RAINBOW_SPEED = 1.2f; // degrees per tick, matches qol-suite's build beam rainbow pace
    static final float GRADIENT_SWING = 40f; // sine time scale -> full A->B->A swing every ~4s
    static final int REGEN_INTERVAL = 6; // frames between native cursor rebuilds while animating; every frame is unnecessary churn

    static Cursor generated;
    static int lastPacked = Integer.MIN_VALUE;
    static int frame;

    private CursorColor(){}

    /** @return the recolored cursor to show in place of {@code fallback}, or {@code fallback} itself if the feature is off. */
    public static Cursor resolve(Cursor fallback){
        if(mobile || !active()){
            if(generated != null) reset();
            return fallback;
        }

        boolean animated = isRainbow() || isGradient();
        int packed = animated ? -1 : Core.settings.getInt("cursorcolor", defaultColorInt());

        if(generated == null || (animated && frame++ % REGEN_INTERVAL == 0) || packed != lastPacked){
            regen();
            lastPacked = packed;
        }

        return generated;
    }

    static void regen(){
        Pixmap pix = new Pixmap(Core.files.internal("cursors/cursor.png"));
        Color c = resolved();
        pix.replace(raw -> tint(raw, c));

        int scale = Fonts.cursorScale();
        if(scale != 1){
            Pixmap scaled = Pixmaps.scale(pix, pix.width * scale, pix.height * scale);
            pix.dispose();
            pix = scaled;
        }

        Cursor old = generated;
        generated = Core.graphics.newCursor(pix, pix.width / 2, pix.height / 2);
        pix.dispose();
        if(old != null) old.dispose();
    }

    /** Multiplies the pixel's RGB by the tint color, preserving alpha - identical in spirit to Draw.color() tinting a white sprite. */
    static int tint(int raw, Color c){
        int a = raw & 0xff;
        if(a == 0) return raw;
        int r = (int)(((raw >>> 24) & 0xff) * c.r);
        int g = (int)(((raw >>> 16) & 0xff) * c.g);
        int b = (int)(((raw >>> 8) & 0xff) * c.b);
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    static Color resolved(){
        Color out = new Color();
        if(isRainbow()){
            out.fromHsv((Time.time * RAINBOW_SPEED) % 360f, 1f, 1f);
        }else if(isGradient()){
            out.set(Core.settings.getInt("cursorcolor", defaultColorInt()))
                .lerp(new Color().set(Core.settings.getInt("cursorcolor2", defaultColor2Int())), 0.5f + 0.5f * Mathf.sin(Time.time, GRADIENT_SWING, 1f));
        }else{
            out.set(Core.settings.getInt("cursorcolor", defaultColorInt()));
        }
        out.a = 1f;
        return out;
    }

    public static boolean active(){
        return isRainbow() || isGradient() || Core.settings.getInt("cursorcolor", defaultColorInt()) != defaultColorInt();
    }

    static boolean isRainbow(){
        return Core.settings.getBool("cursorrainbow", false);
    }

    static boolean isGradient(){
        return Core.settings.getBool("cursorgradient", false);
    }

    /** White - a no-op tint, so the feature does nothing until the player actually picks a color or turns on rainbow/gradient. */
    public static int defaultColorInt(){
        return Color.rgba8888(1f, 1f, 1f, 1f);
    }

    public static int defaultColor2Int(){
        return Color.rgba8888(1f, 1f, 1f, 1f);
    }

    /** Disposes the generated cursor and forces a rebuild; call after a relevant setting changes. */
    public static void reset(){
        if(generated != null){
            generated.dispose();
            generated = null;
        }
        lastPacked = Integer.MIN_VALUE;
    }
}
