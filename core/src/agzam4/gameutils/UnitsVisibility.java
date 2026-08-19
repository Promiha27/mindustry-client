package agzam4.gameutils;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.geom.Rect;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.graphics.Layer;

/**
 * "Скрыть юнитов" (бинд "hide-units", по умолчанию H): вместо спрайтов юниты рисуются командными
 * кружками цвета команды - декластеризация экрана в больших боях, позиции юнитов при этом видны
 * (в отличие от mi2u disableUnit, который прячет юнитов совсем).
 * <p>
 * Адаптация: мод подменял Groups.draw собственной EntityGroup-обёрткой через рефлексию;
 * вшитая копия вместо этого дёргается напрямую из mindustry.core.Renderer.draw()
 * (см. хук "agzam4 UnitsVisibility" там) - без рефлексии и подмены групп.
 */
public class UnitsVisibility {

	public static boolean hide = false;

	private static final Rect bounds = new Rect();

	public static void toggle() {
		visibility(!hide);
	}

	public static void visibility(boolean b) {
		hide = b;
	}

	/**
	 * Вызывается из Renderer для каждого Drawc.
	 * @return true, если стандартную отрисовку юнита надо пропустить (вместо неё нарисован кружок).
	 */
	public static boolean skipDraw(Drawc d) {
		if(!hide) return false;
		if(!(d instanceof Unit u)) return false;

		float opacity = Vars.renderer.animateShields ? 1f : .25f;
		Core.camera.bounds(bounds);
		if(!bounds.overlaps(Tmp.r1.setCentered(u.x, u.y, u.clipSize()))) return true;

		Draw.reset();
		Draw.z(Layer.buildBeam);

		Tmp.c1.set(u.team().color);
		Tmp.c1.lerp(Color.black, .25f);

		Draw.color(Tmp.c1, opacity);
		Fill.circle(u.x, u.y, u.hitSize * Vars.unitCollisionRadiusScale-1);

		Draw.color(u.team().color, opacity);
		Lines.stroke(1f);
		Lines.circle(u.x, u.y, u.hitSize * Vars.unitCollisionRadiusScale-.5f);
		Draw.reset();
		return true;
	}
}
