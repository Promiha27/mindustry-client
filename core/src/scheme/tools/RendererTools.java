package scheme.tools;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

import static arc.Core.*;
import static mindustry.Vars.*;

/**
 * Остаток рендер-тумблеров Scheme Size, которых нет ни в клиенте, ни во вшитых пакетах:
 * X-Ray (пол поверх зданий), сетка блоков и курсор-линейка. Всё остальное из RendererTools
 * мода (радиусы турелей/реакторов/овердрайва, хп-бары юнитов, скрытие юнитов, безрамочные
 * дисплеи) - уже есть нативно (Client.kt, настройки графики, бинды I/O) или в mi2u/eui.
 * <p>
 * Вместо квадродерева BuildsCache мода x-ray просто сканирует тайлы в границах камеры -
 * при вшивании в движок это дешевле, чем содержать кэш всех построек.
 */
public class RendererTools{

    public Rect bounds = new Rect();
    public boolean xray, grid, ruler;

    public void draw(){
        if(world.tiles == null) return;
        camera.bounds(bounds); // do NOT use Tmp.r1
        int xwidth = (int)(bounds.x + bounds.width), yheigth = (int)(bounds.y + bounds.height);

        if(xray){
            int sx = Math.max(0, (int)(bounds.x / tilesize) - 1), sy = Math.max(0, (int)(bounds.y / tilesize) - 1);
            int ex = Math.min(world.width() - 1, (xwidth + tilesize) / tilesize), ey = Math.min(world.height() - 1, (yheigth + tilesize) / tilesize);
            for(int x = sx; x <= ex; x++)
                for(int y = sy; y <= ey; y++){
                    var tile = world.rawTile(x, y);
                    if(tile.build != null) tile.floor().drawBase(tile);
                }
        }

        if(grid) Draw.draw(Layer.blockUnder, () -> {
            Lines.stroke(1f, Pal.darkMetal);

            int sx = Mathf.round(bounds.x, tilesize) + 4;
            int sy = Mathf.round(bounds.y, tilesize) + 4;

            for(int x = sx; x < xwidth; x += tilesize)
                for(int y = sy - 2; y < yheigth; y += tilesize)
                    Lines.line(x, y, x, y + 4);

            for(int y = sy; y < yheigth; y += tilesize)
                for(int x = sx - 2; x < xwidth; x += tilesize)
                    Lines.line(x, y, x + 4, y);
        });

        if(ruler) Draw.draw(Layer.legUnit, () -> {
            Lines.stroke(1f, Pal.accent);

            int x = Mathf.round(input.mouseWorldX() - 4, tilesize) + 4;
            int y = Mathf.round(input.mouseWorldY() - 4, tilesize) + 4;

            Lines.line(x, bounds.y, x, yheigth);
            Lines.line(x + tilesize, bounds.y, x + tilesize, yheigth);
            Lines.line(bounds.x, y, xwidth, y);
            Lines.line(bounds.x, y + tilesize, xwidth, y + tilesize);
        });

        // asynchrony requires sacrifice
        Draw.draw(Layer.blockUnder, Draw::reset);
        Draw.draw(Layer.legUnit, Draw::reset);
        Draw.reset();
    }
}
