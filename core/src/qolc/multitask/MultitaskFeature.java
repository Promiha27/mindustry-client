package qolc.multitask;

import arc.Core;
import arc.Events;
import arc.math.geom.Vec2;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Unit;
import mindustry.input.Binding;

/**
 * "Мультизадачность": стрельба, не прерывая стройку/добычу. Порт features/multitask.js.
 * <p>
 * Ванильный ввод (и нативный autotarget клиента - {@code AutoShoot.kt} явно выходит при
 * {@code activelyBuilding()}/{@code mining()}) не стреляет, пока юнит строит или копает. Эта фича
 * закрывает ровно эту дыру: пока зажата ЛКМ ({@code Binding.select}) и юнит занят стройкой/добычей,
 * оружие наводится на курсор и стреляет. Никакого авто-прицеливания: игрок целится сам, рукой -
 * поэтому это НЕ aimbot (мобильную ветку оригинала, целившуюся в ближайшего врага автоматически,
 * намеренно не переносим - это территория autotarget).
 * <p>
 * Хук на {@link Trigger#preDraw}, как в оригинале: он срабатывает после фазы ввода
 * ({@code Control.update()} -> {@code DesktopInput}), которая каждый кадр перезаписывает
 * {@code player.shooting}/aim - переопределять их можно только ПОСЛЕ неё.
 * Поворот через {@code unit.lookAt} - ручная арифметика оригинала с lastRotation была нужна ему лишь
 * потому, что main.js мода глобально ставил rotateSpeed=9999 всем юнитам (этот чит не портирован).
 */
public final class MultitaskFeature{

    private MultitaskFeature(){
    }

    public static void init(){
        Events.run(Trigger.preDraw, MultitaskFeature::update);
    }

    private static boolean enabled(){
        return Core.settings.getBool("qolc-multitask", false);
    }

    private static void update(){
        if(!enabled() || !Vars.state.isGame() || Vars.player == null) return;

        Unit unit = Vars.player.unit();
        if(unit == null || unit.type == null || !unit.type.hasWeapons()) return;
        if(!unit.activelyBuilding() && !unit.mining()) return;

        //летящий мех (boost) физически не может стрелять - не боремся с движком
        if(unit.isFlying() && unit.type.canBoost) return;

        if(!Core.input.keyDown(Binding.select) || Core.scene.hasMouse()) return;

        Vec2 mouse = Core.input.mouseWorld();
        Vars.player.shooting = true;
        Vars.player.mouseX = mouse.x;
        Vars.player.mouseY = mouse.y;
        unit.aim(mouse.x, mouse.y);
        if(unit.type.omniMovement && unit.type.faceTarget){
            unit.lookAt(mouse.x, mouse.y);
        }
        unit.controlWeapons(true, true);
    }
}
