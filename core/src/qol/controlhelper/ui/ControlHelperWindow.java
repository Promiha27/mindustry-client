package qol.controlhelper.ui;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import mindustry.ai.UnitCommand;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.ui.Styles;
import qol.controlhelper.core.DisconnectedPowerHighlighter;
import qol.controlhelper.core.PowerNetworkReconnector;
import qol.controlhelper.core.buildingsdepowerer.BuildingsDepowerer;
import qol.controlhelper.core.buildingsdepowerer.FactoriesDepowerer;
import qol.controlhelper.core.buildingsdepowerer.ProducersDepowerer;
import qol.ui.QolWindow;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static arc.Core.graphics;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/**
 * Toggle buttons that cut/restore power to all factories or all producers on the map at once - handy
 * right before a wave when you want to stockpile instead of burning resources on production - plus a
 * toggle to outline any power building not on your main network, and an action button that reconnects
 * any such split-off network back to the main one (see {@link PowerNetworkReconnector}). Also carries an
 * assist-split on/off button and a poly-split action button (one-shot, not a toggle - see
 * {@link #dispatchPolySplit}) at sonka's request - this window rather than a separate one, since it's
 * already the "quick toggles while playing" spot. The assist-split button drives the exact same
 * {@link Core#settings} key its Settings-menu checkbox uses, same as every other TOGGLE in this window -
 * no separate tracked state to fall out of sync.
 */
public class ControlHelperWindow extends QolWindow{
    static final float BUTTON_SIZE = 42f;

    boolean factoriesDepowered = false;
    boolean producersDepowered = false;

    final FactoriesDepowerer factoriesDepowerer;
    final ProducersDepowerer producersDepowerer;
    final DisconnectedPowerHighlighter disconnectedPowerHighlighter;
    final PowerNetworkReconnector powerNetworkReconnector;

    public ControlHelperWindow(FactoriesDepowerer factoriesDepowerer, ProducersDepowerer producersDepowerer,
                                DisconnectedPowerHighlighter disconnectedPowerHighlighter, PowerNetworkReconnector powerNetworkReconnector){
        super("control-helper", "qol.feature.control-helper.title");
        this.factoriesDepowerer = factoriesDepowerer;
        this.producersDepowerer = producersDepowerer;
        this.disconnectedPowerHighlighter = disconnectedPowerHighlighter;
        this.powerNetworkReconnector = powerNetworkReconnector;
        //overrides QolWindow's own default (Vars.ui.hudfrag.shown alone) - needs state.isGame() too,
        //same as Hub - so keep both conditions here rather than just adding hudfrag.shown on top of
        //whatever the base class already installed.
        visible(() -> state.isGame() && ui.hudfrag.shown);
        rebuild();
    }

    @Override
    protected float defaultX(){
        return 250f;
    }

    @Override
    protected float defaultY(){
        return graphics.getHeight() - 260f;
    }

    @Override
    protected void setupCont(Table cont){
        cont.defaults().size(BUTTON_SIZE).pad(2f);
        addDepowerButton(cont, Blocks.groundFactory.uiIcon, "@control-helper-depower-factories", factoriesDepowerer,
            () -> factoriesDepowered, v -> factoriesDepowered = v);
        addDepowerButton(cont, Blocks.surgeSmelter.uiIcon, "@control-helper-depower-producers", producersDepowerer,
            () -> producersDepowered, v -> producersDepowered = v);
        addToggleButton(cont, Blocks.powerNode.uiIcon, "@control-helper-highlight-disconnected",
            disconnectedPowerHighlighter::IsEnabled, disconnectedPowerHighlighter::setEnabled);
        addActionButton(cont, Blocks.powerNodeLarge.uiIcon, "@control-helper-reconnect-power", powerNetworkReconnector::Reconnect);
        cont.row();
        addToggleButton(cont, Icon.players.getRegion(), "@control-helper-assist-share",
            () -> Core.settings.getBool("qol-assist-share-enabled", true),
            v -> Core.settings.put("qol-assist-share-enabled", v));
        addActionButton(cont, UnitTypes.poly.uiIcon, "@control-helper-poly-split", this::dispatchPolySplit);
        cont.row();
        addToggleButton(cont, Items.copper.uiIcon, "@control-helper-automine",
            () -> Core.settings.getBool("automineonpause"),
            v -> Core.settings.put("automineonpause", v));
    }

    /**
     * Plain one-shot action, no on/off state: every commandable poly you currently own gets sent half to
     * mine, half to assist you - a fresh 50/50 split regardless of what they were doing before (mining
     * gets the extra one on an odd count).
     * <p>
     * Also force-disables {@code minedefaults-poly-split} first - that setting drives mine-defaults' OWN
     * continuous rebalancer ({@code MineDefaultsFeature.updatePolySplit}), which tracks "already
     * diverted to assist" through its own private bookkeeping ({@code UnitClaims} + a {@code diverted}
     * map). It has no way to know about units THIS dispatch just assist-commanded directly - so if left
     * on, its very next scan sees our freshly mine-commanded half as "eligible unclaimed miners" with
     * zero tracked assist count, and promptly diverts roughly half of THOSE too, skewing what was just a
     * clean 50/50 split within a second or two. Disabling it removes that fight entirely, so the split
     * this button issues is the one that actually sticks.
     */
    void dispatchPolySplit(){
        Core.settings.put("minedefaults-poly-split", false);

        if(player == null || player.team().data() == null) return;
        Seq<Unit> polys = player.team().data().getUnits(UnitTypes.poly);
        if(polys == null || polys.isEmpty()) return;

        IntSeq mineIds = new IntSeq(), assistIds = new IntSeq();
        int mineCount = polys.size - polys.size / 2;
        for(int i = 0; i < polys.size; i++){
            Unit u = polys.get(i);
            if(!u.isCommandable()) continue;
            (i < mineCount ? mineIds : assistIds).add(u.id());
        }

        if(mineIds.size > 0) Call.setUnitCommand(player, mineIds.toArray(), UnitCommand.mineCommand);
        if(assistIds.size > 0) Call.setUnitCommand(player, assistIds.toArray(), UnitCommand.assistCommand);
    }

    void addDepowerButton(Table buttons, TextureRegion icon, String tooltip, BuildingsDepowerer depowerer,
                           BooleanSupplier getDepowered, Consumer<Boolean> setDepowered){
        addToggleButton(buttons, icon, tooltip, getDepowered, next -> {
            setDepowered.accept(next);
            if(next) depowerer.DepowerBuilds(); else depowerer.PowerBuilds();
        });
    }

    /**
     * {@link arc.scene.ui.Button} already registers its own internal click listener that toggles
     * {@code isChecked} on every click - calling {@code setChecked(...)} again from a second, separately
     * added click listener races that internal toggle instead of cooperating with it. The click handler
     * here only ever mutates plain external state (via {@code onToggle}); a continuous {@code update()}
     * callback independently drives the visual checked state off of that state every frame, never
     * touching {@code setChecked} from the click handler itself.
     */
    void addToggleButton(Table buttons, TextureRegion icon, String tooltip, BooleanSupplier getChecked, Consumer<Boolean> onToggle){
        ImageButton btn = new ImageButton(new TextureRegionDrawable(icon), Styles.squareTogglei);
        btn.clicked(() -> onToggle.accept(!getChecked.getAsBoolean()));
        btn.update(() -> btn.setChecked(getChecked.getAsBoolean()));
        btn.addListener(new Tooltip(t -> t.background(null).add(tooltip)));
        buttons.add(btn);
    }

    /** Plain button, no checked/on-off state - fires {@code action} every click, {@link Styles#squarei} instead of the toggle style so it never looks pressed-in. */
    void addActionButton(Table buttons, TextureRegion icon, String tooltip, Runnable action){
        ImageButton btn = new ImageButton(new TextureRegionDrawable(icon), Styles.squarei);
        btn.clicked(action);
        btn.addListener(new Tooltip(t -> t.background(null).add(tooltip)));
        buttons.add(btn);
    }
}
