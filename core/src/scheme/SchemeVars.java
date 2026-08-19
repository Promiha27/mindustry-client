package scheme;

import mindustry.core.UI;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import scheme.tools.*;
import scheme.tools.admins.AdminsTools;
import scheme.ui.HudFragment;
import scheme.ui.dialogs.*;

import static arc.Core.*;
import static mindustry.Vars.*;

/**
 * Общие переменные порта Scheme Size (сильно урезанный SchemeVars мода: остались только
 * реально портированные инструменты - см. javadoc {@link SchemeSizeMod} за списком выпилов).
 */
public class SchemeVars{

    public static AdminsTools admins;
    public static RendererTools render;
    public static BuildingTools build;

    public static RuleSetterDialog rulesetter;
    public static AdminsConfigDialog adminscfg;
    public static RendererConfigDialog rendercfg;

    public static TeamSelectDialog team;
    public static TileSelectDialog tile;

    public static ContentSelectDialog<UnitType> unit;
    public static ContentSelectDialog<StatusEffect> effect;
    public static ContentSelectDialog<Item> item;

    public static WaveApproachingDialog approaching;
    public static HudFragment hudfrag;

    public static void load(){
        admins = AdminsConfigDialog.getTools();
        render = new RendererTools();
        build = new BuildingTools();

        rulesetter = new RuleSetterDialog();
        adminscfg = new AdminsConfigDialog();
        rendercfg = new RendererConfigDialog();

        team = new TeamSelectDialog();
        tile = new TileSelectDialog();

        unit = new ContentSelectDialog<>("@scheme.select.unit", content.units(), 0, 100, 1, value -> value == 0 ? "@scheme.select.unit.clear" : bundle.format("scheme.select.units", value));
        effect = new ContentSelectDialog<>("@scheme.select.effect", content.statusEffects(), 0, 500 * 3600, 60, value -> value == 0 ? "@scheme.select.effect.clear" : bundle.format("scheme.select.seconds", value / 60f));
        item = new ContentSelectDialog<>("@scheme.select.item", content.items(), -1000000, 1000000, 500, value -> value == 0 ? "@scheme.select.item.clear" : bundle.format("scheme.select.items", UI.formatAmount(value.longValue())));

        approaching = new WaveApproachingDialog();
        hudfrag = new HudFragment();
    }
}
