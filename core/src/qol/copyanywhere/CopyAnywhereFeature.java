package qol.copyanywhere;

import arc.Core;
import arc.Events;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import mindustry.core.World;
import mindustry.game.EventType.SchematicCreateEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.Schematic;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.input.Binding;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import qol.core.Feature;

import static mindustry.Vars.player;
import static mindustry.Vars.schematics;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;

/**
 * Lets the "hold a key, drag over built tiles, release to save as a schematic" gesture work while
 * spectating too. Engine fact (verified against {@code DesktopInput.java}): vanilla's own version of
 * this lives entirely inside {@code pollInputPlayer()}, which {@code DesktopInput.update()} only calls
 * when {@code !player.dead()} - while dead/spectating it calls {@code pollInputNoPlayer()} instead,
 * which has no schematic-selection code at all (only tile-tap and command-rect selection), so the
 * gesture is simply unavailable there, not merely disabled.
 * <p>
 * This is a fully independent re-implementation, gated to exactly the opposite condition
 * ({@code player.dead()}) - it only ever fires in the gap vanilla leaves open, and never touches
 * vanilla's own selection/placement state ({@code selectPlans}, {@code mode}, {@code schemX/Y}), all of
 * which {@code pollInputNoPlayer()} already leaves alone anyway. Reuses the vanilla, rebindable
 * {@link Binding#schematicSelect} key (default F) rather than a new bind, so the gesture feels identical
 * to the one already used while alive.
 * <p>
 * Unlike the in-hand vanilla version (which also arms the selection for immediate re-placement via
 * {@code useSchematic}), this only offers save-to-library - placing anything requires a controllable
 * unit, which is exactly what's missing while spectating. {@code Schematics.create()} itself only needs
 * {@link mindustry.Vars#player}'s team (for the fog/discovery check on each tile), not a live unit, so
 * it produces an identical schematic whether you're spectating or not.
 */
public class CopyAnywhereFeature implements Feature{
    boolean dragging = false;
    int dragStartX, dragStartY;

    @Override
    public String id(){
        return "copy-anywhere";
    }

    @Override
    public String titleKey(){
        return "qol.feature.copy-anywhere.title";
    }

    @Override
    public void init(){
        Events.run(Trigger.update, this::update);
        Events.run(Trigger.draw, this::draw);
    }

    @Override
    public void buildSettings(SettingsTable table){
        //nothing beyond the enable toggle - reuses the vanilla schematicSelect keybind, no new one needed
    }

    void update(){
        if(!state.isGame() || !isEnabled() || player == null || !player.dead()){
            //!player.dead(): vanilla's own DesktopInput.pollInputPlayer() already covers this case -
            //don't fight it, and don't leave a drag hanging if the player respawns mid-drag
            dragging = false;
            return;
        }

        if(!dragging){
            if(Core.input.keyTap(Binding.schematicSelect) && !Core.scene.hasKeyboard() && !Core.scene.hasDialog()){
                dragging = true;
                dragStartX = World.toTile(Core.input.mouseWorld().x);
                dragStartY = World.toTile(Core.input.mouseWorld().y);
            }
            return;
        }

        if(Core.input.keyRelease(Binding.schematicSelect)){
            dragging = false;
            int endX = World.toTile(Core.input.mouseWorld().x);
            int endY = World.toTile(Core.input.mouseWorld().y);
            finishCopy(dragStartX, dragStartY, endX, endY);
        }
    }

    void draw(){
        if(!dragging) return;

        int endX = World.toTile(Core.input.mouseWorld().x);
        int endY = World.toTile(Core.input.mouseWorld().y);
        float x = Math.min(dragStartX, endX) * tilesize - tilesize / 2f;
        float y = Math.min(dragStartY, endY) * tilesize - tilesize / 2f;
        float w = (Math.abs(endX - dragStartX) + 1) * tilesize;
        float h = (Math.abs(endY - dragStartY) + 1) * tilesize;

        //same Layer.overlayUI slot vanilla's own selection box draws at (Renderer.draw() wraps
        //control.input.drawTop() in Draw.draw(Layer.overlayUI, ...)) - keeps this above blocks/units
        Draw.draw(Layer.overlayUI, () -> {
            Draw.color(Pal.accent);
            Lines.stroke(1f);
            Lines.rect(x, y, w, h);
            Draw.reset();
        });
    }

    void finishCopy(int x1, int y1, int x2, int y2){
        Schematic schem = schematics.create(x1, y1, x2, y2);
        //nothing built in the dragged area - matches vanilla's own quiet no-op for an empty selection
        //(DesktopInput discards lastSchematic the same way when selectPlans ends up empty)
        if(schem.tiles.isEmpty()) return;

        saveSchematic(schem);
    }

    /**
     * Re-implements {@code InputHandler.showSchematicSave()} - that method is {@code protected} and
     * this feature lives in a different package, so it isn't reachable directly; its body is short
     * enough to copy rather than fight visibility with reflection. Same dialog, same overwrite-confirm
     * behavior, same {@link SchematicCreateEvent} fired on save, so it's indistinguishable from a
     * vanilla-saved schematic afterwards.
     */
    void saveSchematic(Schematic schem){
        ui.showTextInput("@schematic.add", "@name", 1000, "", text -> {
            Schematic replacement = schematics.all().find(s -> s.name().equals(text));
            if(replacement != null){
                ui.showConfirm("@confirm", "@schematic.replace", () -> {
                    schematics.overwrite(replacement, schem);
                    ui.showInfoFade("@schematic.saved");
                    ui.schematics.showInfo(replacement);
                });
            }else{
                schem.tags.put("name", text);
                schem.tags.put("description", "");
                schematics.add(schem);
                ui.showInfoFade("@schematic.saved");
                ui.schematics.showInfo(schem);
                Events.fire(new SchematicCreateEvent(schem));
            }
        });
    }
}
