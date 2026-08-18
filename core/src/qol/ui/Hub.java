package qol.ui;

import arc.Core;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import qol.core.Feature;

import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/**
 * Central always-present {@link QolWindow}: title bar holds one toggle button per feature that has its
 * own window (shows/hides it), plus a settings-gear button that opens the merged mod's settings
 * category. Enabling/disabling features themselves lives only in Settings, not here too - the hub used
 * to also carry an enable checkbox per feature in its body, but having the same on/off control in two
 * separate windows was confusing (e.g. a feature's window-visibility toggle here would still let you
 * re-open a disabled feature's window, since it didn't know about the OTHER checkbox's state). One
 * control surface for enabling, this one for which of the currently-enabled features' windows are
 * showing.
 */
public class Hub extends QolWindow{
    static final float BUTTON_SIZE = 32f;

    final Seq<Feature> features;

    public Hub(Seq<Feature> features){
        super("qol-hub", "qol.hub.title");
        this.features = features;
        visible(() -> state.isGame() && ui.hudfrag.shown);
        rebuild();
    }

    @Override
    protected void setupTitleButtons(Table titleExtras){
        titleExtras.defaults().size(BUTTON_SIZE).pad(2f);
        for(Feature f : features){
            if(!f.hasWindow()) continue;
            QolWindow win = f.window();
            ImageButton btn = new ImageButton(Icon.list, Styles.clearTogglei);
            btn.clicked(() -> {
                if(win.attached()) win.detach(); else win.attach();
            });
            btn.update(() -> btn.setChecked(win.attached()));
            //a disabled feature's window is force-closed by Feature.setEnabled - don't let this button
            //re-open it; the icon greys out via the style's own disabled look
            btn.setDisabled(() -> !f.isEnabled());
            btn.addListener(new Tooltip(t -> t.background(null).add(Core.bundle.get(f.titleKey(), f.titleKey()))));
            titleExtras.add(btn);
        }
        ImageButton settingsBtn = new ImageButton(Icon.settings, Styles.clearNonei);
        settingsBtn.clicked(() -> {
            ui.settings.show();
        });
        titleExtras.add(settingsBtn).size(BUTTON_SIZE).pad(2f);
    }
}
