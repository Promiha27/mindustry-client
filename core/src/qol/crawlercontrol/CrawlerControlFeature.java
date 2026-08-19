package qol.crawlercontrol;

import arc.Events;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.ai.UnitStance;
import mindustry.ai.types.CommandAI;
import mindustry.content.UnitTypes;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import qol.core.Feature;

import static mindustry.Vars.player;
import static mindustry.Vars.state;

/**
 * Crawlers ({@link UnitTypes#crawler}) explode on whatever enemy is nearest the instant one's in range -
 * fine for their default {@code SuicideAI}, but a crawler you've commanded to travel somewhere detonates
 * on the first enemy it passes on the way, never reaching the target you actually sent it at.
 * <p>
 * Engine fact: {@link CommandAI#shouldFire} is {@code !hasStance(UnitStance.holdFire)} - a real vanilla
 * stance (same one the in-game stance UI toggles), not a hack, so holding it over the network via
 * {@link Call#setUnitStance} suppresses the self-destruct weapon exactly like it would for any other
 * unit's normal weapons, and releasing it lets the unit's own weapon logic take over again. This holds
 * the stance on every commanded crawler that's still travelling (has a pending {@code targetPos} or
 * {@code attackTarget} it isn't yet within engage range of) and releases it the instant it arrives, so
 * it detonates normally once close enough to blow something up. Crawlers running the vanilla
 * {@code SuicideAI} (i.e. never commanded) are left alone entirely.
 */
public class CrawlerControlFeature implements Feature{
    /* перф: сканировать всех краулеров каждый кадр незачем - троттлим по образцу соседних scan-фич
     * (CoreHeal/AssistShare), но короче (10 тиков, ~0.17с): смена stance чувствительна к задержке -
     * свежескомандованный краулер не должен успеть подорваться до первого скана */
    static final float SCAN_INTERVAL_TICKS = 10f;
    float scanTimer = 0f;

    final IntSeq toHold = new IntSeq();
    final IntSeq toRelease = new IntSeq();

    @Override
    public String id(){
        return "crawler-control";
    }

    @Override
    public String titleKey(){
        return "qol.feature.crawler-control.title";
    }

    @Override
    public void init(){
        Events.run(Trigger.update, this::update);
    }

    @Override
    public void buildSettings(SettingsTable table){
    }

    void update(){
        if(!isEnabled() || !state.isGame() || player == null || player.team().data() == null) return;

        scanTimer += Time.delta;
        if(scanTimer < SCAN_INTERVAL_TICKS) return;
        scanTimer = 0f;

        toHold.clear();
        toRelease.clear();

        //getUnits(type) returns null, not an empty Seq, for a type this team has never had a unit of
        Seq<Unit> crawlers = player.team().data().getUnits(UnitTypes.crawler);
        if(crawlers == null) return;

        for(Unit u : crawlers){
            if(!(u.controller() instanceof CommandAI ai)) continue;

            boolean traveling = ai.targetPos != null || ai.attackTarget != null;
            boolean nearAttackTarget = ai.attackTarget != null && u.within(ai.attackTarget, u.range());
            boolean shouldHold = traveling && !nearAttackTarget;

            boolean holding = ai.hasStance(UnitStance.holdFire);
            if(shouldHold && !holding) toHold.add(u.id());
            else if(!shouldHold && holding) toRelease.add(u.id());
        }

        if(toHold.size > 0) Call.setUnitStance(player, toHold.toArray(), UnitStance.holdFire, true);
        if(toRelease.size > 0) Call.setUnitStance(player, toRelease.toArray(), UnitStance.holdFire, false);
    }
}
