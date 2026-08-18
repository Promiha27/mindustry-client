package eui.draw;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;
import arc.util.pooling.Pools;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;

/**
 * A small floating progress/percentage bar (block build progress, unit health/shield) plus the
 * centered floating-text label helper both that bar and several HUD widgets share. Ported from
 * utils/draw/bar-builder.js.
 */
public class BarBuilder{
    private static final float fontScale = 0.25f / arc.scene.ui.layout.Scl.scl(1f);
    private static final float borderSize = 1f;

    /**
     * @param drawX/drawY world-space center of the block/unit this bar hovers over
     * @param value 0-1 fill fraction; a falsy (0) value draws nothing, matching the source's {@code if(!value) return}
     * @param targetSizeInBlocks block footprint size in tiles (bar sits just left of it)
     * @param barSize bar thickness/height in pixels
     */
    public static void draw(float drawX, float drawY, float value, float targetSizeInBlocks, float barSize, String labelText, Color color, float alpha){
        if(value == 0) return;

        float blockPixelSize = targetSizeInBlocks * 8;
        float startX = drawX - blockPixelSize / 2 - barSize;
        float startY = drawY + blockPixelSize / 2;
        float endY = startY + barSize;

        float barLength = blockPixelSize + barSize * 2;
        float innerBarLength = barLength - borderSize * 2;
        float barHeight = barSize;
        float innerBarHeight = barHeight - borderSize * 2;

        float fillSize = innerBarLength * value;

        Draw.z(Layer.darkness + 1);

        Lines.stroke(borderSize, Pal.darkerGray);
        Draw.alpha(alpha);
        Lines.rect(startX, startY, barLength, barHeight);

        Draw.color(color, alpha);
        Fill.rect(drawX - (innerBarLength * (1 - value)) / 2, startY + barSize / 2, fillSize, innerBarHeight);

        if(labelText != null && !labelText.isEmpty()){
            drawLabel(labelText, drawX, endY + 4, Color.white);
        }

        Draw.reset();
    }

    public static String buildPercentLabel(float value){
        return Math.round(value * 100) + "%";
    }

    public static void drawLabel(String text, float x, float y, Color color){
        drawLabel(text, x, y, color, false);
    }

    public static void drawLabel(String text, float x, float y, Color color, boolean useIntegerPositions){
        GlyphLayout lay = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        Font font;

        //outline font renders badly at non-integer positions, so use the plain default font whenever
        //integer positions were requested instead
        if(useIntegerPositions){
            font = Fonts.def;
            font.setUseIntegerPositions(true);
        }else{
            font = Fonts.outline;
            font.setUseIntegerPositions(false);
        }

        font.getData().setScale(fontScale);

        lay.setText(font, text);

        font.setColor(color);
        font.draw(text, x - lay.width / 2, y + lay.height / 2);
        font.getData().setScale(1);

        Pools.free(lay);
    }
}
