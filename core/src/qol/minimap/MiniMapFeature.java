package qol.minimap;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import mindustry.core.World;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import qol.core.Feature;
import qol.core.SafeSettings;

import static arc.Core.graphics;
import static arc.Core.scene;
import static mindustry.Vars.player;
import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * Draggable real-time minimap widget ported from QoL Control's {@code !cmap} - see
 * {@link MiniMapElement} for the actual drawing/input logic. Settings are re-read from
 * {@link Core#settings} every tick rather than pushed through change listeners (same idiom
 * {@link qol.minedefaults.MineDefaultsFeature}/{@link qol.coreheal.CoreHealFeature} already use for
 * their sliders) - cheap primitive reads, and it keeps this feature and its settings UI fully decoupled.
 */
public class MiniMapFeature implements Feature{
    static final int SIZE_MIN = 50, SIZE_MAX = 1000, SIZE_STEP = 10;
    static final int UNIT_SIZE_MIN = 10, UNIT_SIZE_MAX = 60;

    Table owner;
    MiniMapElement map;
    Label coordsLabel;
    int lastSize = -1;

    @Override
    public String id(){
        return "minimap";
    }

    @Override
    public String titleKey(){
        return "qol.feature.minimap.title";
    }

    @Override
    public void init(){
        map = new MiniMapElement();

        coordsLabel = new Label("");
        coordsLabel.setFontScale(0.75f);
        coordsLabel.setAlignment(Align.center);
        coordsLabel.update(() -> {
            if(player == null) return;
            int mx = World.toTile(Core.input.mouseWorld().x);
            int my = World.toTile(Core.input.mouseWorld().y);
            coordsLabel.setText("[lightgray]" + player.tileX() + "," + player.tileY() + " []" + mx + "," + my);
        });

        owner = new Table(Styles.black5);
        owner.setPosition(SafeSettings.getFloat("minimap-x", 15f), SafeSettings.getFloat("minimap-y", 400f));
        rebuildLayout();
        scene.add(owner);

        map.onMoved = (nx, ny) -> {
            nx = Mathf.clamp(nx, 0f, Math.max(0f, graphics.getWidth() - owner.getWidth()));
            ny = Mathf.clamp(ny, 0f, Math.max(0f, graphics.getHeight() - owner.getHeight()));
            owner.setPosition(nx, ny);
            Core.settings.put("minimap-x", nx);
            Core.settings.put("minimap-y", ny);
        };

        Events.on(WorldLoadEvent.class, e -> renderer.minimap.setZoom(Math.max(world.width(), world.height()) / 32f));

        Events.run(Trigger.update, () -> {
            boolean mapOpen = ui.minimapfrag != null && ui.minimapfrag.shown();
            boolean visible = isEnabled() && state.isGame() && ui.hudfrag.shown && !mapOpen;
            owner.visible = visible;
            if(!visible) return;

            int size = SafeSettings.getInt("minimap-size", 200);
            if(size != lastSize) rebuildLayout();

            map.unitOutline = SafeSettings.getBool("minimap-unit-outline", false);
            map.sortByHp = SafeSettings.getBool("minimap-sort-hp", true);
            map.showNames = SafeSettings.getBool("minimap-show-names", false);
            map.unitSize = SafeSettings.getInt("minimap-unit-size", 24);

            owner.x = Mathf.clamp(owner.x, 0f, Math.max(0f, graphics.getWidth() - owner.getWidth()));
            owner.y = Mathf.clamp(owner.y, 0f, Math.max(0f, graphics.getHeight() - owner.getHeight()));

            map.rescanUnits(Time.delta);
        });
    }

    void rebuildLayout(){
        lastSize = SafeSettings.getInt("minimap-size", 200);
        owner.clearChildren();
        owner.add(map).size(lastSize).row();
        owner.add(coordsLabel).width(lastSize).padTop(2f);
        owner.pack();
    }

    @Override
    public void buildSettings(SettingsTable table){
        table.sliderPref("minimap-size", 200, SIZE_MIN, SIZE_MAX, SIZE_STEP, v -> v + "px");
        table.sliderPref("minimap-unit-size", 24, UNIT_SIZE_MIN, UNIT_SIZE_MAX, 2, v -> v + "");
        table.checkPref("minimap-unit-outline", false);
        table.checkPref("minimap-sort-hp", true);
        table.checkPref("minimap-show-names", false);
    }
}
