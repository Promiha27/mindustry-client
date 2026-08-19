package mi2u.game;

import arc.*;
import arc.math.*;
import arc.util.*;

import static mi2u.MI2UVars.*;
import static mindustry.Vars.*;

/**
 * DEDUPE-PASS NOTE (spec decision 11): this is NOT an fps cap of its own - it only reads the native
 * "fpscap" graphics setting (which remains the honest frame limiter) and, while toggled on, swaps
 * {@link Time#setDeltaProvider} so the SIMULATION slows down in step with real fps (fps below target =>
 * proportional slow-mo instead of dropped sim time; fpscap below 60 => deliberate slow motion). The
 * slowdown IS the whole feature, so there was nothing to split out; per the fallback in the spec it
 * stays as-is: default OFF (the {@code update} flag starts false and is never persisted - a restart
 * always comes back disabled). mindustrytool's TimeControlFeature is the primary/honest game-speed
 * control; both it and this class overwrite the same global delta provider, so whichever was toggled
 * last owns game speed - note that toggling this OFF resets to the standard client provider, wiping any
 * TimeControl multiplier (re-apply it from its own HUD strip if needed).
 */
public class FpsController{
    public static float scl = 1f, ratio = 1f, tgtFps = 120f, min = 3f;
    public static boolean update = false;
    public static float lastAuto = 0f;

    public static void update(){
        if(!update) return;
        if(!(Time.globalTime - lastAuto < 5f)){
            lastAuto = Time.globalTime;
            ratio = Core.graphics.getFramesPerSecond() / tgtFps;
            if(Mathf.zero(ratio - 1f, 0.05f)) ratio = 1f;
            scl = Mathf.lerp(scl, ratio, 0.4f);
            if(Mathf.zero(ratio - scl, 0.01f)) scl = ratio;
            tgtFps = Core.settings.getInt("fpscap", 120);
            //фикс бага оригинала: слайдер в настройках пишет ключ "fpsCtrl.cutoff",
            //а здесь читался несуществующий "speedctrl.cutoff" (всегда 0 => порог не работал)
            min = mi2ui.settings.getInt("fpsCtrl.cutoff") / tgtFps;
            scl = Math.max(scl, min);
        }
    }

    public static void reset(){
        scl = ratio = 1f;
    }

    public static void toggle(){
        update = !update;
        if(update){
            Time.setDeltaProvider(() -> Core.graphics.getDeltaTime() * Math.min(tgtFps, 60) * scl);
        }else{
            reset();
            Time.setDeltaProvider(() -> {
                float result = Core.graphics.getDeltaTime() * 60f;
                return (Float.isNaN(result) || Float.isInfinite(result)) ? 1f : Mathf.clamp(result, 0.0001f, maxDeltaClient);
            });
        }
    }
}
