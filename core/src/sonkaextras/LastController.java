package sonkaextras;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.scene.ui.layout.Scl;
import arc.struct.IntMap;
import arc.struct.IntSeq;
import arc.util.Align;
import arc.util.pooling.Pools;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.ui.Fonts;

import static mindustry.Vars.*;

/**
 * Метка над юнитом с ником игрока, который управлял им ПОСЛЕДНИМ (идея sonka): кто угнал/переставил
 * юнита - видно и после того, как игрок его отпустил. Пока юнит под прямым управлением, ваниль сама
 * рисует живой ник ({@code PlayerComp.drawName}) - наша метка показывается только после отпускания.
 * <p>
 * Источник данных - {@link UnitControlEvent}: его стреляет {@code InputHandler.unitControl} (remote,
 * forward=true), т.е. событие приходит и на клиентах в мультиплеере для ЛЮБОГО игрока, не только
 * локального. Респавн из ядра через это событие не идёт - и правильно: свой свежий юнит никто не
 * "угонял". Ник и цвет копируются в момент захвата (игрок может выйти с сервера - метка останется).
 * <p>
 * Настройки: {@link #settingKey} (вкл/выкл), {@link #timeoutKey} - через сколько секунд метка гаснет
 * (0 = пока юнит жив). Рисуется в стиле ванильного ника (Fonts.outline, scale 0.25, Layer.playerName),
 * приглушённо, с камера-куллингом и без аллокаций в кадре (строка метки собрана при захвате,
 * GlyphLayout из пула, как у ванили).
 */
public final class LastController{
    public static final String settingKey = "sonka-lastcontroller";
    public static final String timeoutKey = "sonka-lastcontroller-timeout";

    static class Entry{
        String label;
        final Color color = new Color();
        long since;
    }

    static final IntMap<Entry> byUnit = new IntMap<>();
    static final IntSeq toRemove = new IntSeq();

    private LastController(){
    }

    /** Вызывается из Main.kt до ClientLoadEvent - только вешает слушатели. */
    public static void init(){
        Events.on(UnitControlEvent.class, e -> {
            if(e.player == null || e.unit == null || e.player.name == null) return;
            Entry entry = byUnit.get(e.unit.id);
            if(entry == null){
                entry = new Entry();
                byUnit.put(e.unit.id, entry);
            }
            //"↺ Ник" - стрелка-возврат как знак "здесь побывал"
            entry.label = "↺ " + e.player.name;
            entry.color.set(e.player.color);
            entry.since = Time.millis();
        });

        Events.on(UnitDestroyEvent.class, e -> {
            if(e.unit != null) byUnit.remove(e.unit.id);
        });
        Events.on(WorldLoadEvent.class, e -> byUnit.clear());

        Events.run(Trigger.drawOver, LastController::draw);
    }

    static void draw(){
        if(byUnit.isEmpty() || state.isMenu() || !Core.settings.getBool(settingKey, true)) return;

        long timeoutMs = Core.settings.getInt(timeoutKey, 0) * 1000L;
        long now = Time.millis();
        Core.camera.bounds(Tmp.r1);

        Font font = Fonts.outline;
        GlyphLayout layout = Pools.obtain(GlyphLayout.class, GlyphLayout::new);
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.25f / Scl.scl(1f));

        Draw.z(Layer.playerName);
        float z = Drawf.text();

        toRemove.clear();
        for(IntMap.Entry<Entry> kv : byUnit){
            Entry entry = kv.value;
            Unit unit = Groups.unit.getByID(kv.key);
            if(unit == null || !unit.isValid()){
                toRemove.add(kv.key);
                continue;
            }
            if(timeoutMs > 0 && now - entry.since > timeoutMs){
                toRemove.add(kv.key);
                continue;
            }
            //пока юнитом управляет игрок - ваниль рисует живой ник, наша метка не нужна
            if(unit.isPlayer()) continue;

            float clip = unit.type.hitSize * 2f;
            if(!Tmp.r1.overlaps(unit.x - clip / 2f, unit.y - clip / 2f, clip, clip)) continue;
            if(unit.inFogTo(player.team())) continue;

            final float nameHeight = 11f;
            layout.setText(font, entry.label);

            Draw.color(0f, 0f, 0f, 0.25f);
            Fill.rect(unit.x, unit.y + nameHeight - layout.height / 2, layout.width + 2, layout.height + 3);
            Draw.color();
            //приглушённо: это след, а не живой игрок
            font.setColor(entry.color.r, entry.color.g, entry.color.b, 0.7f);
            font.draw(entry.label, unit.x, unit.y + nameHeight, 0, Align.center, false);
        }

        Draw.reset();
        Pools.free(layout);
        font.getData().setScale(1f);
        font.setColor(Color.white);
        font.setUseIntegerPositions(ints);
        Draw.z(z);

        for(int i = 0; i < toRemove.size; i++) byUnit.remove(toRemove.get(i));
    }
}
