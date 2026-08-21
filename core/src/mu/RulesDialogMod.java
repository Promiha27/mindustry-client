package mu;

import arc.Core;
import arc.func.Cons;
import arc.func.Prov;
import arc.scene.ui.CheckBox;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Collapser;
import arc.scene.ui.layout.Table;
import arc.util.Reflect;
import mindustry.content.Planets;
import mindustry.ctype.ContentType;
import mindustry.editor.BannedContentDialog;
import mindustry.game.Rules;
import mindustry.game.Rules.TeamRule;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.CustomRulesDialog;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;
import mindustry.world.meta.Env;
import mu.MappingUtilitiesMod.MUMod;

import static arc.Core.settings;
import static mindustry.Vars.ui;

/**
 * Расширение одного экземпляра {@link CustomRulesDialog}: через публичный хук
 * {@code additionalSetup} дописывает в существующие категории скрытые правила карты (те поля
 * {@link Rules}, что ваниль умеет читать/писать, но не показывает в редакторе), новую
 * категорию «Прочее» (флаги, имя режима, миссия, планетный фон), редактор правил произвольной
 * команды 0-255 и per-team «читы»/«корабли ядра» в коллапсеры базовых команд. Плюс подмена
 * приватных диалогов бана/раскрытия контента на {@link BetterBannedContentDialog}.
 * <p>
 * Логика поиска: {@code dialog.check/number/...} сами фильтруются по {@code ruleSearch},
 * сырые кнопки (цвета, вложенные диалоги, текстовые поля) фильтруются вручную по бандлу -
 * иначе они показывались бы при любом поисковом запросе.
 */
public class RulesDialogMod extends MUMod{
    public final CustomRulesDialog dialog;
    private final Runnable setupFunc = this::setup;

    private Rules rules;

    private final BetterBannedContentDialog<Block> betterRevealedBlocks;
    private final BetterBannedContentDialog<Block> betterBannedBlocks;
    private final BetterBannedContentDialog<UnitType> betterBannedUnits;
    private final PlanetBackgroundDialog planetBackgroundDialog;

    private final BannedContentDialog<Block> oldBannedBlocks;
    private final BannedContentDialog<UnitType> oldBannedUnits;
    private final BannedContentDialog<Block> oldRevealedBlocks;

    /** Выбранный id «нумерованной команды»; живёт между пересборками, чтобы поле не сбрасывалось. */
    private int currentNumbered = 0;

    public RulesDialogMod(CustomRulesDialog dialog){
        this.settingName = "mu_rules_mod";
        this.dialog = dialog;

        //предикаты - ванильные (у мода для revealed был b -> true: раскрывать и так видимые
        //блоки смысла нет, а список раздувался на все ~400 блоков)
        betterRevealedBlocks = new BetterBannedContentDialog<>("@revealedblocks", ContentType.block, b -> b.buildVisibility != BuildVisibility.shown);
        betterRevealedBlocks.isRevealed = true;
        betterBannedBlocks = new BetterBannedContentDialog<>("@bannedblocks", ContentType.block, Block::canBeBuilt);
        betterBannedUnits = new BetterBannedContentDialog<>("@bannedunits", ContentType.unit, u -> !u.isHidden());
        planetBackgroundDialog = new PlanetBackgroundDialog();

        oldBannedBlocks = Reflect.get(dialog, "bannedBlocks");
        oldBannedUnits = Reflect.get(dialog, "bannedUnits");
        oldRevealedBlocks = Reflect.get(dialog, "revealedBlocks");
    }

    @Override
    public void enable(){
        dialog.additionalSetup.add(setupFunc);
        if(settings.getBool("editor_better_content_dialogs", true)){
            Reflect.set(dialog, "bannedBlocks", betterBannedBlocks);
            Reflect.set(dialog, "bannedUnits", betterBannedUnits);
            Reflect.set(dialog, "revealedBlocks", betterRevealedBlocks);
        }else{
            restoreDialogs();
        }
    }

    @Override
    public void disable(){
        dialog.additionalSetup.remove(setupFunc);
        restoreDialogs();
    }

    private void restoreDialogs(){
        Reflect.set(dialog, "bannedBlocks", oldBannedBlocks);
        Reflect.set(dialog, "bannedUnits", oldBannedUnits);
        Reflect.set(dialog, "revealedBlocks", oldRevealedBlocks);
    }

    /** Зовётся из CustomRulesDialog.setupMain() после всех ванильных категорий. */
    private void setup(){
        rules = Reflect.get(dialog, "rules");

        //категория «Прочее» создаётся всегда - пустая в main не попадёт (addToMain проверяет hasChildren)
        category("miscellaneous");

        if(settings.getBool("editor_hidden_rules", true)) addHiddenRules();
        if(settings.getBool("editor_planet_background", true)) addPlanetBackground();
    }

    /** Переключиться на существующую категорию диалога или создать новую. */
    private void category(String name){
        int idx = dialog.categoryNames.indexOf(name);
        if(idx >= 0 && idx < dialog.categories.size){
            dialog.current = dialog.categories.get(idx);
        }else{
            dialog.category(name);
        }
    }

    private boolean matches(String key){
        return Core.bundle.get(key).toLowerCase().contains(dialog.ruleSearch);
    }

    private void addHiddenRules(){
        category("waves");
        dialog.check("@rules.hidespawns", b -> rules.hideSpawns = b, () -> rules.hideSpawns);

        category("resourcesbuilding");
        //logicUnitBuild и coreDestroyClear (Foo) ваниль уже показывает - не дублируем
        dialog.check("@rules.ghostblocks", b -> rules.ghostBlocks = b, () -> rules.ghostBlocks);

        category("unit");
        dialog.check("@rules.possessionallowed", b -> rules.possessionAllowed = b, () -> rules.possessionAllowed);
        dialog.check("@rules.unitpayloadupdate", b -> rules.unitPayloadUpdate = b, () -> rules.unitPayloadUpdate);

        category("enemy");
        dialog.check("@rules.pvpautopause", b -> rules.pvpAutoPause = b, () -> rules.pvpAutoPause);

        category("environment");
        dialog.check("@rules.borderdarkness", b -> rules.borderDarkness = b, () -> rules.borderDarkness);
        dialog.check("@rules.disableoutsidearea", b -> rules.disableOutsideArea = b, () -> rules.disableOutsideArea);
        dialog.check("@rules.staticfog", b -> rules.staticFog = b, () -> rules.staticFog);

        colorButton("@rules.staticfogcolor", () -> rules.staticColor);
        colorButton("@rules.dynamicfogcolor", () -> rules.dynamicColor);
        colorButton("@rules.cloudscolor", () -> rules.cloudColor);

        dialog.number("@rules.dragmultiplier", f -> rules.dragMultiplier = f, () -> rules.dragMultiplier);

        if(matches("rules.environmentsettings") && settings.getBool("editor_environment_settings", true)){
            dialog.current.button("@rules.environmentsettings", () -> environmentDialog(rules)).left().width(300f).fillX().row();
        }

        category("teams");
        Table numberedEdit = new Table();
        dialog.numberi("@rules.numberedteam", f -> {
            currentNumbered = f;
            updateNumberedEdit(numberedEdit, Team.get(f));
        }, () -> currentNumbered, 0, 255);
        updateNumberedEdit(numberedEdit, Team.get(currentNumbered));
        if(numberedEdit.hasChildren()){
            dialog.current.add(numberedEdit).row();
        }

        //ванильный team() даёт только 6 базовых команд кнопками - числовые поля открывают все 256
        dialog.numberi("@rules.playerteamid", f -> rules.defaultTeam = Team.get(f), () -> rules.defaultTeam.id, 0, 255);
        dialog.numberi("@rules.enemyteamid", f -> rules.waveTeam = Team.get(f), () -> rules.waveTeam.id, 0, 255);

        category("miscellaneous");
        dialog.check("@rules.cangameover", b -> rules.canGameOver = b, () -> rules.canGameOver);
        if(matches("rules.modename")){
            text(dialog.current, "@rules.modename", value -> rules.modeName = value.isEmpty() ? null : value, () -> rules.modeName == null ? "" : rules.modeName);
        }
        if(matches("rules.mission")){
            text(dialog.current, "@rules.mission", value -> rules.mission = value.isEmpty() ? null : value, () -> rules.mission == null ? "" : rules.mission);
        }

        addHiddenTeamRules();
    }

    private void colorButton(String key, Prov<arc.graphics.Color> colorProv){
        if(!matches(key.substring(1))) return;
        var cell = dialog.current.button(b -> {
            b.left();
            b.table(Tex.pane, in -> in.stack(new Image(Tex.alphaBg), new Image(Tex.whiteui){{
                update(() -> setColor(colorProv.get()));
            }}).grow()).margin(4).size(50f).padRight(10);
            b.add(key);
        }, () -> ui.picker.show(colorProv.get(), colorProv.get()::set)).left().width(300f);
        dialog.ruleInfo(cell, key);
        dialog.current.row();
    }

    private void addPlanetBackground(){
        category("miscellaneous");
        if(matches("rules.planetbackground")){
            dialog.ruleInfo(dialog.current.table(table -> {
                table.button("@rules.planetbackground", () -> planetBackgroundDialog.show(rules)).width(300f).left();
                table.left().row();
            }).fillX(), "@rules.planetbackground");
            dialog.current.row();
        }
    }

    /** Коллапсер с правилами произвольной команды (копия ванильного per-team набора + читы/корабли). */
    private void updateNumberedEdit(Table edit, Team team){
        edit.clear();
        boolean[] shown = {false};
        boolean[] empty = {false};
        Table wasCurrent = dialog.current;

        edit.button(team.coloredName(), Icon.downOpen, Styles.togglet, () -> shown[0] = !shown[0])
        .marginLeft(14f).width(260f).height(55f).update(t -> {
            ((Image)t.getChildren().get(1)).setDrawable(shown[0] ? Icon.upOpen : Icon.downOpen);
            t.setChecked(shown[0]);
        }).left().padBottom(2f).row();

        edit.collapser(c -> {
            c.left().defaults().fillX().left().pad(5);
            dialog.current = c;
            TeamRule teams = rules.teams.get(team);

            dialog.number("@rules.blockhealthmultiplier", f -> teams.blockHealthMultiplier = f, () -> teams.blockHealthMultiplier);
            dialog.number("@rules.blockdamagemultiplier", f -> teams.blockDamageMultiplier = f, () -> teams.blockDamageMultiplier);

            dialog.check("@rules.rtsai", b -> teams.rtsAi = b, () -> teams.rtsAi, () -> team != rules.defaultTeam);
            dialog.numberi("@rules.rtsminsquadsize", f -> teams.rtsMinSquad = f, () -> teams.rtsMinSquad, () -> teams.rtsAi, 0, 100);
            dialog.numberi("@rules.rtsmaxsquadsize", f -> teams.rtsMaxSquad = f, () -> teams.rtsMaxSquad, () -> teams.rtsAi, 1, 1000);
            dialog.number("@rules.rtsminattackweight", f -> teams.rtsMinWeight = f, () -> teams.rtsMinWeight, () -> teams.rtsAi);

            dialog.check("@rules.buildai", b -> teams.buildAi = b, () -> teams.buildAi, () -> team != rules.defaultTeam && rules.env != Planets.erekir.defaultEnv && !rules.pvp);
            dialog.number("@rules.buildaitier", false, f -> teams.buildAiTier = f, () -> teams.buildAiTier, () -> teams.buildAi && rules.env != Planets.erekir.defaultEnv && !rules.pvp, 0, 1);

            dialog.check("@rules.infiniteresources", b -> teams.infiniteResources = b, () -> teams.infiniteResources);
            dialog.check("@rules.fillitems", b -> teams.fillItems = b, () -> teams.fillItems);
            dialog.number("@rules.buildspeedmultiplier", f -> teams.buildSpeedMultiplier = f, () -> teams.buildSpeedMultiplier, 0.001f, 50f);

            dialog.number("@rules.unitdamagemultiplier", f -> teams.unitDamageMultiplier = f, () -> teams.unitDamageMultiplier);
            dialog.number("@rules.unitcrashdamagemultiplier", f -> teams.unitCrashDamageMultiplier = f, () -> teams.unitCrashDamageMultiplier);
            dialog.number("@rules.unitbuildspeedmultiplier", f -> teams.unitBuildSpeedMultiplier = f, () -> teams.unitBuildSpeedMultiplier, 0.001f, 50f);
            dialog.number("@rules.unitcostmultiplier", f -> teams.unitCostMultiplier = f, () -> teams.unitCostMultiplier);
            dialog.number("@rules.unithealthmultiplier", f -> teams.unitHealthMultiplier = f, () -> teams.unitHealthMultiplier);

            dialog.check("@rules.cheat", value -> teams.cheat = value, () -> teams.cheat);
            dialog.check("@rules.coresspawnships", value -> teams.aiCoreSpawn = value, () -> teams.aiCoreSpawn);

            empty[0] = !dialog.current.hasChildren();
            dialog.current = wasCurrent;
        }, () -> shown[0]).left().growX().row();

        //поиск отфильтровал все правила команды - убрать и кнопку, и пустой коллапсер
        //(в оригинале clear() звался изнутри билдера, и коллапсер всё равно добавлялся следом)
        if(empty[0]) edit.clear();
    }

    /**
     * Дописывает читы/корабли ядра в ванильные коллапсеры базовых команд. Ваниль строит их в
     * setupMain до additionalSetup, поэтому ищем коллапсеры по порядку среди ячеек категории
     * «Команды»: Collapser держит свою таблицу единственным ребёнком (поле table пакетное).
     */
    private void addHiddenTeamRules(){
        category("teams");
        Table teamsCat = dialog.current;

        int i = 0;
        for(Cell<?> cell : teamsCat.getCells()){
            if(i >= Team.baseTeams.length) break;
            if(!(cell.get() instanceof Table t)) continue;
            Collapser col = null;
            for(Cell<?> inner : t.getCells()){
                if(inner.get() instanceof Collapser c){
                    col = c;
                    break;
                }
            }
            if(col == null || col.getChildren().isEmpty() || !(col.getChildren().first() instanceof Table body)) continue;

            TeamRule teams = rules.teams.get(Team.baseTeams[i++]);
            dialog.current = body;
            dialog.check("@rules.cheat", value -> teams.cheat = value, () -> teams.cheat);
            dialog.check("@rules.coresspawnships", value -> teams.aiCoreSpawn = value, () -> teams.aiCoreSpawn);
        }
        dialog.current = teamsCat;
    }

    private void environmentDialog(Rules rules){
        BaseDialog dialog = new BaseDialog("@rules.title.environment");
        dialog.cont.add("@rules.env.warning").color(Pal.accent).center().padBottom(20f).row();
        dialog.cont.pane(table -> {
            table.left().defaults().growX().left().pad(5);
            table.row();

            envCheck(table, "@rules.env.terrestrial", Env.terrestrial, rules);
            envCheck(table, "@rules.env.space", Env.space, rules);
            envCheck(table, "@rules.env.underwater", Env.underwater, rules);
            envCheck(table, "@rules.env.spores", Env.spores, rules);
            envCheck(table, "@rules.env.scorching", Env.scorching, rules);
            envCheck(table, "@rules.env.groundOil", Env.groundOil, rules);
            envCheck(table, "@rules.env.groundWater", Env.groundWater, rules);
            envCheck(table, "@rules.env.oxygen", Env.oxygen, rules);
        }).fillX();

        dialog.addCloseButton();
        dialog.show();
    }

    private void envCheck(Table tb, String text, int envVar, Rules rules){
        CheckBox check = new CheckBox(text);
        check.changed(() -> {
            if(check.isChecked()){
                rules.env |= envVar;
            }else{
                rules.env &= ~envVar;
            }
        });
        check.setChecked((rules.env & envVar) != 0);
        check.left();
        tb.add(check);
        tb.row();

        Cell<Label> desc = tb.add(text + ".description");
        desc.get().setWidth(600f);
        desc.get().setWrap(true);
        tb.row();
    }

    private void text(Table table, String labelText, Cons<String> cons, Prov<String> prov){
        Cell<Table> cell = table.table(t -> {
            t.left();
            t.add(labelText).left().padRight(5);
            t.field(String.valueOf(prov.get()), cons).padRight(100f);
        }).padTop(0);
        dialog.ruleInfo(cell, labelText);
        table.row();
    }
}
