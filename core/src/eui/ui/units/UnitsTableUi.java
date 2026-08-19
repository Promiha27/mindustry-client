package eui.ui.units;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.style.Drawable;
import arc.scene.ui.layout.Table;
import eui.draw.BarBuilder;
import eui.units.UnitsCounter;
import eui.units.UnitsCounter.TeamInfo;
import eui.units.UnitsCounter.UnitTypeInfo;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Styles;
import sonkaextras.UiStyle;

import static mindustry.Vars.player;
import static mindustry.Vars.ui;

/**
 * "eui-ShowUnitTable": a top-left HUD panel listing each team's top unit types by total
 * {@link UnitsCounter} value (icon + live count per type), with toggles to hide core/support units from
 * the ranking and to collapse the panel to just its show/hide button. Hovering or clicking a unit's icon
 * also draws a line + distance label from the player to one representative unit of that type/team.
 * Ported from ui/units/units-table-ui.js.
 */
public class UnitsTableUi{
    private static final int granularity = 6;
    private static final int maxToDisplay = 8;

    private boolean prevUnitsUiVisible = true;
    private boolean unitsUiVisible = true;
    private boolean hideCoreUnits = false;
    private boolean hideSupportUnits = false;
    private boolean built = false;
    private Unit holdedEntity;
    private Unit hoveredEntity;
    private int amountToDisplay = 0;
    private long updateTimerMs = System.currentTimeMillis();

    private Table overlayMarker;
    private Table contentTable;
    private Table unitTable;
    //перф: альфа панели читается из настроек раз в 500мс (в троттленом update), а не каждый кадр в act()
    private float tableAlpha = 1f;

    public UnitsTableUi(){
        Events.on(WorldLoadEvent.class, e -> holdedEntity = null);
        Events.run(Trigger.update, this::update);
        Events.run(Trigger.draw, this::draw);
    }

    void update(){
        if(!Core.settings.getBool("eui-ShowUnitTable", true)){
            if(built) clearTable();
            hoveredEntity = null;
            return;
        }

        if(hoveredEntity != null && contentTable != null && !contentTable.hasMouse()) hoveredEntity = null;

        if(overlayMarker == null) setMarker();

        //throttled - updating every frame makes it near-impossible to click a label before it's rebuilt out from under the cursor
        long now = System.currentTimeMillis();
        if(now - 500 < updateTimerMs) return;
        updateTimerMs = now;

        tableAlpha = Core.settings.getInt("eui-UnitsTableAlpha", 100) / 100f;

        var unitsValueTop = UnitsCounter.getUnitsValueTop(maxToDisplay, granularity, hideCoreUnits, hideSupportUnits);
        amountToDisplay = unitsValueTop.size;

        if(!built) rebuildTable();

        unitTable.clearChildren();
        for(TeamInfo teamInfo : unitsValueTop){
            Team team = teamInfo.team;

            for(UnitTypeInfo unitInfo : teamInfo.units){
                Unit entity = unitInfo.entity;
                int amount = unitInfo.amount;

                //перф: текст константен до следующей перестройки (раз в 500мс) - статичная строка вместо
                //супплаера, собирающего её заново каждый кадр
                unitTable.add(teamColor(team) + amount + "[white]").left();
                var image = unitTable.image(entity.icon()).left().padRight(5f).padBottom(2f).maxSize(24f).get();
                image.hovered(() -> hoveredEntity = entity);
                image.clicked(() -> {
                    if(holdedEntity == null || !isSameEntity(holdedEntity, entity)) holdedEntity = entity;
                    else holdedEntity = null;
                });

                if(holdedEntity != null && holdedEntity.dead && isSameEntity(holdedEntity, entity)) holdedEntity = entity;
            }
            unitTable.row();
        }
    }

    void draw(){
        if(!Core.settings.getBool("eui-ShowUnitTable", true)) return;

        Unit entity;
        if(hoveredEntity != null && !hoveredEntity.dead) entity = hoveredEntity;
        else if(holdedEntity != null && !holdedEntity.dead) entity = holdedEntity;
        else return;

        Unit tracked = entity;
        Draw.draw(Layer.overlayUI + 0.01f, () -> {
            float x, y;
            if(player.unit() == null){
                x = Core.camera.position.x;
                y = Core.camera.position.y;
            }else{
                x = player.unit().x;
                y = player.unit().y;
            }

            float distance = Mathf.dst(x, y, tracked.x, tracked.y);
            String text = String.valueOf(Math.round(distance / 8f));

            Draw.color(tracked.team.color);
            Lines.line(x, y, tracked.x, tracked.y);
            if(distance > 80) BarBuilder.drawLabel(text, x, y + 20, Color.white, true);
        });
    }

    void rebuildTable(){
        clearTable();
        build();
    }

    void clearTable(){
        if(!built) return;
        contentTable.clearChildren();
        built = false;
    }

    void build(){
        float buttonSizePx = 40;

        Table unitTableButtons = contentTable.table().top().left().margin(3f).get();
        unitTableButtons.update(() -> unitTableButtons.color.a = tableAlpha);

        //скиновая унификация: серые "приподнятые" Styles.defaulti заменены плоскими clear-стилями
        //из единого style-гайда (UiStyle) - как у кнопок тайтл-баров qol/mi2u-окон и нативного HUD;
        //тогглы - clearTogglei, чтобы включённое состояние читалось подсветкой, а не вдавленностью
        unitTableButtons.button(Icon.play, UiStyle.titleButton(), () -> unitsUiVisible = !unitsUiVisible)
            .width(buttonSizePx).height(buttonSizePx).pad(1f).name("show").tooltip(Core.bundle.get("units-table.button.hide.tooltip"));

        var coreUnitsButton = unitTableButtons.button(Icon.players, UiStyle.titleToggle(), () -> hideCoreUnits = !hideCoreUnits)
            .update(b -> b.setChecked(hideCoreUnits)).width(buttonSizePx).height(buttonSizePx).pad(1f)
            .name("core-units").tooltip(Core.bundle.get("units-table.button.core-units.tooltip")).get();
        coreUnitsButton.visibility = () -> unitsUiVisible;
        coreUnitsButton.resizeImage(buttonSizePx * 0.6f);

        var supportUnitsButton = unitTableButtons.button(Icon.github, UiStyle.titleToggle(), () -> hideSupportUnits = !hideSupportUnits)
            .update(b -> b.setChecked(hideSupportUnits)).width(buttonSizePx).height(buttonSizePx).pad(1f)
            .name("support-units").tooltip(Core.bundle.get("units-table.button.support-units.tooltip")).get();
        supportUnitsButton.visibility = () -> unitsUiVisible;
        supportUnitsButton.resizeImage(buttonSizePx * 0.6f);

        contentTable.row();

        unitTable = contentTable.table().margin(3f).get();
        unitTable.visibility = () -> unitsUiVisible;

        built = true;
    }

    void setMarker(){
        Drawable contentTableStyle = Tex.buttonEdge4;

        overlayMarker = ui.hudGroup.find("waves");
        if(overlayMarker == null) return;

        overlayMarker.row();
        Table t = overlayMarker.table(contentTableStyle).update(table -> {
            if(prevUnitsUiVisible != unitsUiVisible){
                table.setBackground(unitsUiVisible ? contentTableStyle : Styles.none);
                prevUnitsUiVisible = unitsUiVisible;
            }
        }).name("unit-table").top().left().marginBottom(2f).marginTop(2f).get();

        contentTable = t;
        contentTable.visibility = () -> built && amountToDisplay != 0;
    }

    static boolean isSameEntity(Unit a, Unit b){
        return a != null && b != null && a.team == b.team && a.type == b.type;
    }

    static String teamColor(Team team){
        return "[#" + team.color + "]";
    }
}
