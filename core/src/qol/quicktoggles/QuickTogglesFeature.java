package qol.quicktoggles;

import arc.Events;
import arc.graphics.Color;
import arc.math.geom.Vec2;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import qol.core.Feature;
import qol.ui.DragIconButton;

import static arc.Core.graphics;
import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/**
 * Two independent draggable HUD icon buttons ported from QoL Control.
 * <p>
 * Camera-lock zeroes the controlled unit's velocity every tick while locked, so it stays put wherever
 * it is - the engine's camera only ever recenters on select/possess, panning away from a unit is
 * already unrestricted, so freezing the unit is all "lock camera on this spot" needs.
 * <p>
 * Build-pause is a persistent on/off version of vanilla's hold-only "pause building" key
 * ({@link mindustry.input.Binding#pauseBuilding}) - the same {@code control.input.isBuilding} field
 * {@link qol.controlhelper.core.HandMiner} already flips temporarily for hand-mining.
 */
public class QuickTogglesFeature implements Feature{
    boolean cameraLocked;
    final Vec2 lockPos = new Vec2();

    DragIconButton lockBtn, pauseBtn;

    @Override
    public String id(){
        return "quick-toggles";
    }

    @Override
    public String titleKey(){
        return "qol.feature.quick-toggles.title";
    }

    @Override
    public void init(){
        lockBtn = new DragIconButton("qol-quicktoggles-lock", Icon.eye, 44f, 20f, graphics.getHeight() - 150f, () -> {
            cameraLocked = !cameraLocked;
            if(cameraLocked && player != null && player.unit() != null){
                lockPos.set(player.unit().x, player.unit().y);
            }
        });

        pauseBtn = new DragIconButton("qol-quicktoggles-pause", Icon.pause, 44f, 20f, graphics.getHeight() - 200f, () -> {
            if(control.input != null) control.input.isBuilding = !control.input.isBuilding;
        });

        lockBtn.visible(() -> isEnabled() && state.isGame() && ui.hudfrag.shown);
        pauseBtn.visible(() -> isEnabled() && state.isGame() && ui.hudfrag.shown);

        Events.on(WorldLoadEvent.class, e -> cameraLocked = false);

        Events.run(Trigger.update, () -> {
            if(!isEnabled() || !state.isGame()) return;

            if(player != null && player.unit() != null && cameraLocked){
                player.unit().vel().set(0f, 0f);
                lockBtn.btn.setColor(Color.scarlet);
            }else{
                lockBtn.btn.setColor(Color.white);
            }

            if(control.input != null){
                pauseBtn.btn.getImage().setDrawable(control.input.isBuilding ? Icon.pause : Icon.play);
                pauseBtn.btn.setColor(control.input.isBuilding ? Color.white : Color.scarlet);
            }
        });
    }

    @Override
    public void buildSettings(SettingsTable table){
    }
}
