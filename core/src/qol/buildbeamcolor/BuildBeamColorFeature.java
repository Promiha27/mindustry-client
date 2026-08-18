package qol.buildbeamcolor;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;
import mindustry.world.Block;
import mindustry.world.blocks.defense.MendProjector;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.blocks.units.RepairTurret;
import qol.core.ButtonSetting;
import qol.core.Feature;
import qol.core.SafeSettings;
import qol.ui.QolWindow;

import static mindustry.Vars.content;
import static mindustry.Vars.ui;

/**
 * Recolors several in-world beam/aura colors, each to a custom color, a two-color gradient shimmer
 * (sine sweep between two picked colors; rainbow wins if both toggles are on), or a rainbow:
 * <ul>
 * <li><b>Build beam</b> - the beam units draw while building/repairing a block. Vanilla always uses
 * {@link Pal#accent}, the same shared "highlight" color the engine reuses for a handful of other
 * in-world overlays (shield auras, overdrive/regen auras), so those follow along.</li>
 * <li><b>Heal beams</b> - {@link Pal#heal} covers unit repair beams (poly/mega and friends reference
 * the very same Color instance through {@code RepairBeamWeapon.healColor}) and the green heal
 * effects. Mend projectors and repair turrets hold their OWN green Color instances instead of
 * Pal.heal, so those are covered separately by swapping the blocks' public color fields
 * ({@code MendProjector.baseColor}/{@code phaseColor}, {@code RepairTurret.laserColor}) for the
 * duration of the same draw pass.</li>
 * <li><b>Power lasers</b> - power node/surge tower connection lasers. These read the block's public
 * {@code laserColor1}/{@code laserColor2} fields (a white-to-{@link Pal#powerLight} gradient by
 * power satisfaction), swapped the same way; the starvation cue is kept by using a darkened copy of
 * the custom color as the second gradient stop.</li>
 * </ul>
 * There's no supported mod hook for any of these individually - e.g. {@code
 * BuilderComp.drawBuildingBeam()} is baked into the generated {@code Unit} class and can't be
 * overridden or wrapped. The narrowest available seam is bracketing the palette around the exact
 * world draw pass: {@link Trigger#drawOver} fires right before {@code blocks.drawBlocks()} +
 * {@code Groups.draw.draw(...)} (Renderer.java:416-419 - so BOTH block and unit drawing sit inside
 * the bracket), and {@link Trigger#postDraw} fires right after - the swap never bleeds into menus or
 * the settings UI (a separate Scene2D pass outside the bracket), nor into placement previews and
 * selection overlays (drawn at Layer.plans/overlayUI BEFORE drawOver fires). The mining beam can NOT
 * be recolored this way: it's tinted with the global {@code Color.lightGray}/{@code Color.white}
 * constants, which everything else in the world uses too.
 */
public class BuildBeamColorFeature implements Feature{
    static final float RAINBOW_SPEED = 1.2f; // degrees per tick -> full cycle roughly every 5s at normal speed
    static final float GRADIENT_SWING = 40f; // sine time scale for the two-color shimmer -> full A->B->A swing every ~4s

    final Color savedAccent = new Color();
    final Color savedHeal = new Color();
    /** Our owned instances the swapped block fields point at during the pass - mutating these never touches vanilla objects. */
    final Color healOwned = new Color();
    final Color powerC1 = new Color(), powerC2 = new Color();
    boolean overriding = false, healFieldsSwapped = false, powerSwapped = false;

    //blocks whose color lives in their own public fields rather than a Pal entry, with the original
    //Color references saved for restoring after each pass (laserColor2 in particular IS the shared
    //Pal.powerLight instance - restore must put back the reference, never mutate it)
    final Seq<PowerNode> powerBlocks = new Seq<>();
    final Seq<Color> powerOrig1 = new Seq<>(), powerOrig2 = new Seq<>();
    final Seq<MendProjector> mendBlocks = new Seq<>();
    final Seq<Color> mendOrigBase = new Seq<>(), mendOrigPhase = new Seq<>();
    final Seq<RepairTurret> repairBlocks = new Seq<>();
    final Seq<Color> repairOrig = new Seq<>();

    @Override
    public String id(){
        return "buildbeam-color";
    }

    @Override
    public String titleKey(){
        return "qol.feature.buildbeam-color.title";
    }

    @Override
    public boolean hasWindow(){
        return false;
    }

    @Override
    public QolWindow window(){
        return null;
    }

    @Override
    public void init(){
        //content is final by ClientLoadEvent - covers modded PowerNode/MendProjector/RepairTurret subclasses too
        for(Block b : content.blocks()){
            if(b instanceof PowerNode pn){
                powerBlocks.add(pn);
                powerOrig1.add(pn.laserColor1);
                powerOrig2.add(pn.laserColor2);
            }else if(b instanceof MendProjector mp){
                mendBlocks.add(mp);
                mendOrigBase.add(mp.baseColor);
                mendOrigPhase.add(mp.phaseColor);
            }else if(b instanceof RepairTurret rt){
                repairBlocks.add(rt);
                repairOrig.add(rt.laserColor);
            }
        }
        Events.run(Trigger.drawOver, this::beginOverride);
        Events.run(Trigger.postDraw, this::endOverride);
    }

    @Override
    public void buildSettings(SettingsTable table){
        table.checkPref(rainbowKey(), false);
        table.checkPref(gradientKey(), false);
        table.pref(new ButtonSetting(pickKey(), () -> openPicker(colorKey(), defaultColorInt())));
        table.pref(new ButtonSetting(pick2Key(), () -> openPicker(color2Key(), defaultColor2Int())));
        table.checkPref(healRainbowKey(), false);
        table.checkPref(healGradientKey(), false);
        table.pref(new ButtonSetting(healPickKey(), () -> openPicker(healColorKey(), defaultHealInt())));
        table.pref(new ButtonSetting(healPick2Key(), () -> openPicker(healColor2Key(), defaultColor2Int())));
        table.checkPref(powerRainbowKey(), false);
        table.checkPref(powerGradientKey(), false);
        table.pref(new ButtonSetting(powerPickKey(), () -> openPicker(powerColorKey(), defaultPowerInt())));
        table.pref(new ButtonSetting(powerPick2Key(), () -> openPicker(powerColor2Key(), defaultColor2Int())));
    }

    void beginOverride(){
        if(!isEnabled()){
            overriding = false;
            return;
        }

        savedAccent.set(Pal.accent);
        Pal.accent.set(resolved(isRainbow(), isGradient(), colorKey(), defaultColorInt(), color2Key()));

        //heal: Pal.heal swap covers unit repair beams + heal effects; the block-field swap below
        //covers menders/repair turrets, which keep their own green instances. defaultHealInt() must
        //be read BEFORE the swap - it samples the live (still vanilla here) Pal.heal.
        int healDef = defaultHealInt();
        boolean healActive = isHealRainbow() || isHealGradient() || SafeSettings.getInt(healColorKey(), healDef) != healDef;
        savedHeal.set(Pal.heal);
        if(healActive){
            healOwned.set(resolved(isHealRainbow(), isHealGradient(), healColorKey(), healDef, healColor2Key()));
            Pal.heal.set(healOwned);

            healFieldsSwapped = true;
            for(int i = 0; i < mendBlocks.size; i++){
                mendBlocks.get(i).baseColor = healOwned;
                mendBlocks.get(i).phaseColor = healOwned;
            }
            for(int i = 0; i < repairBlocks.size; i++){
                repairBlocks.get(i).laserColor = healOwned;
            }
        }

        int powerDef = defaultPowerInt();
        boolean powerActive = isPowerRainbow() || isPowerGradient() || SafeSettings.getInt(powerColorKey(), powerDef) != powerDef;
        if(powerActive){
            powerC1.set(resolved(isPowerRainbow(), isPowerGradient(), powerColorKey(), powerDef, powerColor2Key()));
            //keep vanilla's "starved lasers look different" cue: gradient runs custom -> darker custom
            powerC2.set(powerC1).mul(0.55f);
            powerC2.a = 1f;

            powerSwapped = true;
            for(int i = 0; i < powerBlocks.size; i++){
                powerBlocks.get(i).laserColor1 = powerC1;
                powerBlocks.get(i).laserColor2 = powerC2;
            }
        }

        overriding = true;
    }

    void endOverride(){
        if(!overriding) return;
        Pal.accent.set(savedAccent);
        Pal.heal.set(savedHeal);
        if(healFieldsSwapped){
            for(int i = 0; i < mendBlocks.size; i++){
                mendBlocks.get(i).baseColor = mendOrigBase.get(i);
                mendBlocks.get(i).phaseColor = mendOrigPhase.get(i);
            }
            for(int i = 0; i < repairBlocks.size; i++){
                repairBlocks.get(i).laserColor = repairOrig.get(i);
            }
            healFieldsSwapped = false;
        }
        if(powerSwapped){
            for(int i = 0; i < powerBlocks.size; i++){
                powerBlocks.get(i).laserColor1 = powerOrig1.get(i);
                powerBlocks.get(i).laserColor2 = powerOrig2.get(i);
            }
            powerSwapped = false;
        }
        overriding = false;
    }

    /**
     * The configured color for one beam right now: rainbow wins, then the two-color gradient
     * shimmer (a sine sweep between the main and the second picked color), then the flat pick.
     * Fresh locals rather than reused fields on purpose: fromHsv() only writes r/g/b, so a shared
     * instance would silently keep a stale alpha (or stale channels) from its last use on another
     * branch - a prior custom pick's near-zero alpha would render the rainbow as an almost
     * transparent sliver blending into the (often gray/rocky) terrain, reading as "the rainbow
     * turned gray" even though the hue never did.
     */
    static Color resolved(boolean rainbow, boolean gradient, String valueKey, int def, String value2Key){
        Color out = new Color();
        if(rainbow){
            out.fromHsv((Time.time * RAINBOW_SPEED) % 360f, 1f, 1f);
        }else if(gradient){
            out.set(SafeSettings.getInt(valueKey, def))
                .lerp(new Color().set(SafeSettings.getInt(value2Key, defaultColor2Int())),
                    0.5f + 0.5f * Mathf.sin(Time.time, GRADIENT_SWING, 1f));
        }else{
            out.set(SafeSettings.getInt(valueKey, def));
        }
        out.a = 1f;
        return out;
    }

    void openPicker(String valueKey, int def){
        Color current = new Color().set(SafeSettings.getInt(valueKey, def));
        ui.picker.show(current, false, picked -> Core.settings.put(valueKey, Color.rgba8888(picked.r, picked.g, picked.b, picked.a)));
    }

    /** Vanilla {@link Pal#accent}, packed - the "no custom color picked yet" default, so the feature is a no-op until the player actually opens the picker or turns on rainbow. */
    static int defaultColorInt(){
        return Color.rgba8888(Pal.accent.r, Pal.accent.g, Pal.accent.b, Pal.accent.a);
    }

    static int defaultHealInt(){
        return Color.rgba8888(Pal.heal.r, Pal.heal.g, Pal.heal.b, Pal.heal.a);
    }

    static int defaultPowerInt(){
        return Color.rgba8888(Pal.powerLight.r, Pal.powerLight.g, Pal.powerLight.b, Pal.powerLight.a);
    }

    /** White - the "no second gradient color picked yet" default, so a fresh gradient visibly pulses toward white instead of doing nothing. */
    static int defaultColor2Int(){
        return Color.rgba8888(1f, 1f, 1f, 1f);
    }

    boolean isRainbow(){
        return SafeSettings.getBool(rainbowKey(), false);
    }

    boolean isGradient(){
        return SafeSettings.getBool(gradientKey(), false);
    }

    boolean isHealRainbow(){
        return SafeSettings.getBool(healRainbowKey(), false);
    }

    boolean isHealGradient(){
        return SafeSettings.getBool(healGradientKey(), false);
    }

    boolean isPowerRainbow(){
        return SafeSettings.getBool(powerRainbowKey(), false);
    }

    boolean isPowerGradient(){
        return SafeSettings.getBool(powerGradientKey(), false);
    }

    String rainbowKey(){
        return "buildbeam-color-rainbow";
    }

    String pickKey(){
        return "buildbeam-color-pick";
    }

    String colorKey(){
        return "buildbeam-color-value";
    }

    String healRainbowKey(){
        return "buildbeam-color-heal-rainbow";
    }

    String healPickKey(){
        return "buildbeam-color-heal-pick";
    }

    String healColorKey(){
        return "buildbeam-color-heal-value";
    }

    String powerRainbowKey(){
        return "buildbeam-color-power-rainbow";
    }

    String powerPickKey(){
        return "buildbeam-color-power-pick";
    }

    String powerColorKey(){
        return "buildbeam-color-power-value";
    }

    String gradientKey(){
        return "buildbeam-color-gradient";
    }

    String pick2Key(){
        return "buildbeam-color-pick2";
    }

    String color2Key(){
        return "buildbeam-color-value2";
    }

    String healGradientKey(){
        return "buildbeam-color-heal-gradient";
    }

    String healPick2Key(){
        return "buildbeam-color-heal-pick2";
    }

    String healColor2Key(){
        return "buildbeam-color-heal-value2";
    }

    String powerGradientKey(){
        return "buildbeam-color-power-gradient";
    }

    String powerPick2Key(){
        return "buildbeam-color-power-pick2";
    }

    String powerColor2Key(){
        return "buildbeam-color-power-value2";
    }
}
