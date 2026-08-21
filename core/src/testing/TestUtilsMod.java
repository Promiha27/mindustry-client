package testing;

import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.util.Log;
import arc.util.Time;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import testing.buttons.Spawn;
import testing.content.TUFx;
import testing.content.TUFx.TPData;
import testing.ui.TUStyles;
import testing.util.Setup;
import testing.util.TUIcons;
import testing.util.TUSettings;
import testing.util.TUVars;

import static arc.Core.*;
import static mindustry.Vars.*;
import static testing.ui.TUDialogs.*;

/**
 * Порт мода "Testing Utilities" (MEEPofFaith, v69.10) ЦЕЛИКОМ как нативный пакет клиента -
 * песочничная панель в левом нижнем углу HUD (BLUI): спавнер юнитов (кол-во/радиус/команда/
 * точка, трансформация, выбор волны с превью состава), размещение любого блока, террейн-
 * пейнтер прямо в живой карте (карандаш/линия/ластик/заливка/спрей/пипетка, режимы, данные
 * тайлов, обрывы, undo/redo), меню статус-эффектов, меню мира (окружение планеты, погода),
 * переключатель песочницы, заполнить/опустошить ядро, смена команды (любая из 256), лечение/
 * неуязвимость, клон, самоуничтожение, выключатель освещения (+цвет), телепорт Alt+клик
 * (sk7725/whynotteleport), мировые координаты/инфо о тайле под миникартой; вне игры -
 * визуализатор интерполяций Interp и «звуковая комната» (звуки/музыка/аудиофильтры).
 * <p>
 * ИНВЕНТАРИЗАЦИЯ (по решению sonka дубли НЕ вырезаются - вместо этого выбор панели, см.
 * {@link sonkaextras.AdminPanel}): спавн/деспавн юнитов, смена команды, статус-эффекты,
 * предметы в ядро, телепорт, правила - пересекаются со scheme admin tools; спавн юнитов/
 * блоков - с agzam4 UnitSpawner; God Mode mindustrytool. Уникальное здесь: террейн-пейнтер
 * в живой игре, выбор волны с превью, трансформация в юнита, погода, окружение планеты,
 * выключатель света, интерполяции, звуковая комната, заполнение ядра, клон/самоуничтожение.
 * SKIP: расширение диапазона зума (у форка зум и так настраиваемый: minzoom/maxScale=300),
 * интеграция с модом Time Control (в клиенте его нет, тайм-контрол есть у mindustrytool),
 * tu-mobile-test (хак форса мобильного UI на десктопе), раскраска названия мода в списке модов.
 * Телепорт оригинал глушил на Foo's Client (Version.foos) - здесь тогл tu-teleport, по
 * умолчанию выкл.
 * <p>
 * Адаптация по образцу остальных вшитых пакетов: без extends Mod, конструктор зовётся из
 * mindustry.client.Main.init() и только вешает ClientLoadEvent-слушатель; self-disable guard
 * по id мода "test-utils" (две панели дрались бы за угол). BLUI завендорен в testing.blui.
 * Спрайты - core/assets/testing/ (см. TUIcons). Настройки - секция «Testing Utilities»
 * вкладки «Моды», оригинальные ключи tu-*. Бандлы en+ru (ru оригинала неполный - дописан).
 */
public class TestUtilsMod{
    private static boolean teleport;

    /** Guard пройден и ClientLoadEvent отработал (строка в FeaturesDialog). */
    public static boolean enabled;

    public TestUtilsMod(){
        if(mods.locateMod("test-utils") != null){
            Log.info("[testing] External Testing Utilities mod is also loaded - baked-in copy is standing down.");
            return;
        }
        Events.on(ClientLoadEvent.class, e -> {
            try{
                init();
                enabled = true;
            }catch(Throwable t){
                Log.err("[testing] Failed to initialize Testing Utilities", t);
            }
        });
    }

    private void init(){
        TUIcons.init();
        TUStyles.init();
        testing.blui.BLStyles.init(); //дефолтный стиль HoldImageButton в scene до первого new HoldImageButton(Drawable)
        TUVars.init();
        Setup.init();
        TUSettings.init();

        //отрисовка точек спавна и sk7725/whynotteleport (мобильной поддержки ниже нет)
        if(mobile) return;
        Events.on(WorldLoadEvent.class, e -> {
            Spawn.spawnHover = Spawn.blockHover = false;
        });
        Events.run(Trigger.draw, () -> {
            Draw.z(Layer.overlayUI + 0.04f);
            unitDialog.drawPos();
            blockDialog.drawPos();
            if(!teleport && canTeleport()){
                Draw.z(Layer.effect);
                Lines.stroke(2f, Pal.accent);
                float x1 = player.x, y1 = player.y,
                x2 = input.mouseWorldX(), y2 = input.mouseWorldY();

                Lines.line(x1, y1, x2, y2, false);
                Fill.circle(x1, y1, 1f);
                Fill.circle(x2, y2, 1f);

                for(int j = 0; j < 4; j++){
                    float rot = j * 90f + 45f + (-Time.time) % 360f;
                    float length = 8f;
                    Draw.rect("select-arrow", x2 + Angles.trnsx(rot, length), y2 + Angles.trnsy(rot, length), length / 1.9f, length / 1.9f, rot - 135f);
                }
            }
            Draw.reset();
        });
        Events.run(Trigger.update, () -> {
            if(state.isGame()){
                //sk7725/whynotteleport
                if(canTeleport() && click()){
                    player.shooting(false);
                    if(teleport) return;
                    teleport = true;

                    float oldX = player.x, oldY = player.y;

                    player.unit().set(input.mouseWorld());
                    player.snapInterpolation();

                    TUFx.teleport.at(
                    input.mouseWorldX(), input.mouseWorldY(),
                    player.unit().rotation - 90f, player.team().color,
                    new TPData(player.unit().type, oldX, oldY)
                    );
                }else{
                    teleport = false;
                }
            }
        });
    }

    public static boolean disableTeleport(){
        return !settings.getBool("tu-teleport", false) || net.client() || disableCampaign();
    }

    public static boolean canTeleport(){
        return !mobile && !disableTeleport() && player.unit() != null && !player.unit().type.internal && input.alt();
    }

    public static boolean disableCampaign(){
        return state.isCampaign() && !settings.getBool("tu-cheating", false);
    }

    public static boolean click(){
        return mobile ? input.isTouched() : input.keyDown(KeyCode.mouseLeft);
    }

    public static boolean anyClick(){
        return mobile ? input.isTouched() : (input.keyDown(KeyCode.mouseLeft) || input.keyDown(KeyCode.mouseRight) || input.keyDown(KeyCode.mouseMiddle));
    }

    public static KeyCode getClick(){
        if(input.keyDown(KeyCode.mouseLeft)) return KeyCode.mouseLeft;
        if(input.keyDown(KeyCode.mouseRight)) return KeyCode.mouseRight;
        if(input.keyDown(KeyCode.mouseMiddle)) return KeyCode.mouseLeft;
        return null;
    }
}
