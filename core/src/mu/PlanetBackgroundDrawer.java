package mu;

import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.gl.FrameBuffer;
import arc.util.Nullable;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.graphics.g3d.PlanetRenderer;

import static arc.Core.graphics;

/**
 * Рендер планетного фона карты в квадратный FrameBuffer (сторона = больший размер экрана) для
 * превью в {@link PlanetBackgroundDialog}. Перерисовывается только по {@link #update()} или
 * при смене размера окна - PlanetRenderer дорогой, каждый кадр его гонять незачем. Свой
 * PlanetRenderer (а не {@code Vars.renderer.planets}) - как в оригинале: у игрового рендерера
 * своё состояние камеры и ui-слои, мешать их с превью нельзя.
 */
public class PlanetBackgroundDrawer{
    private static @Nullable FrameBuffer buffer;
    private static @Nullable PlanetRenderer planets;
    private static boolean changed = true;

    private PlanetBackgroundDrawer(){
    }

    /** Пометить превью устаревшим (после вращения камеры/зума/смены планеты). */
    public static void update(){
        changed = true;
    }

    public static Texture draw(PlanetParams params){
        int size = Math.max(graphics.getWidth(), graphics.getHeight());

        boolean fresh = false;
        if(buffer == null){
            fresh = true;
            buffer = new FrameBuffer(size, size);
        }
        if(planets == null) planets = new PlanetRenderer();

        if(changed || fresh || buffer.resizeCheck(size, size)){
            changed = false;

            buffer.begin(Color.clear);

            //override some values
            params.viewW = size;
            params.viewH = size;
            params.alwaysDrawAtmosphere = true;
            params.drawUi = false;

            planets.render(params);

            buffer.end();
        }

        return buffer.getTexture();
    }
}
