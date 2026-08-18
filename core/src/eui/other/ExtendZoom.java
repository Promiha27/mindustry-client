package eui.other;

import arc.Core;
import arc.Events;
import mindustry.game.EventType.Trigger;

import static mindustry.Vars.renderer;

/**
 * Widens the camera's minimum zoom limit (how far the player can zoom out) based on the "eui-maxZoom"
 * setting (0-10 slider, name inherited as-is from the source) mapped to a min-zoom of 2.5 down to 0.5;
 * max-zoom (25, how far in) is left fixed. Always active, no on/off toggle - matches the source. Ported
 * from other/extend-zoom.js.
 * <p>
 * COLLISION / mostly-inert in this fork: {@link mindustry.core.Renderer#minZoom}/{@code maxZoom} are the
 * fields the vanilla JS source (and this port) writes to, but {@code Renderer.minScale()}/{@code
 * maxScale()} - what actually clamps the player's manual zoom every frame - only reads them while
 * {@code control.input.logicCutscene} is true (a logic-processor scripted camera cutscene); the rest of
 * the time they read a *different*, already-configurable client setting instead (a "Min Zoom" slider
 * under Settings > Client, {@code SettingsMenuDialog} key {@code "minzoom"}, 0-100 on a log scale - see
 * {@code Renderer.minScale()}). That native slider already provides equivalent (and finer-grained)
 * "let me zoom out further" functionality. Ported anyway for behavioural parity with the source (and it
 * does still affect the logicCutscene zoom bounds), but expect this setting to have no visible effect on
 * ordinary manual zooming - the fields themselves also carry a repo comment warning not to repurpose them
 * ("don't change or vanilla compat breaks"), so this deliberately does not redirect into the native
 * "minzoom" setting instead.
 */
public class ExtendZoom{
    private static final float MAX_ZOOM = 25f;

    private float minZoom = 0.5f;

    public ExtendZoom(){
        setZoom(minZoom, MAX_ZOOM);
        Events.run(Trigger.update, this::update);
    }

    void update(){
        //TODO (source): convert to linear scale
        float newMinZoom = 2.5f - Core.settings.getInt("eui-maxZoom", 10) / 5f;
        if(newMinZoom != minZoom){
            setZoom(newMinZoom, MAX_ZOOM);
            minZoom = newMinZoom;
        }
    }

    void setZoom(float min, float max){
        renderer.minZoom = min;
        renderer.maxZoom = max;
    }
}
