package eui.util;

import arc.util.Time;

/**
 * Smoothed per-episode delta tracker (e.g. "resources gained per second", averaged/blended over a
 * sliding window rather than jumping every sample) - used by the resource-rate HUD widget, one instance
 * per tracked item. Ported from utils/difference.js.
 */
public class Difference{
    private final float countTimerMs;
    private final float episodesAmount;
    private float prevDifference = 0;
    private float currentValue;
    private float currentTimeMs;

    public Difference(float countTimerMs, float startValue){
        this(countTimerMs, startValue, 1000);
    }

    public Difference(float countTimerMs, float startValue, float episodeDurationMs){
        this.countTimerMs = countTimerMs;
        this.episodesAmount = countTimerMs / episodeDurationMs;
        this.currentValue = startValue;
        this.currentTimeMs = Time.time / 60f * 1000f;
    }

    public float difference(float value){
        float timeMs = Time.time / 60f * 1000f;
        float currentDifference = value - currentValue;

        if(timeMs - currentTimeMs > countTimerMs){
            prevDifference = currentDifference;
            currentValue = value;
            currentTimeMs = timeMs;
            return prevDifference / episodesAmount;
        }else{
            float measurement = (timeMs - currentTimeMs) / countTimerMs;

            float countedDifference = currentDifference * measurement / episodesAmount;
            float countedPrevDifference = prevDifference * (1 - measurement) / episodesAmount;
            return countedDifference + countedPrevDifference;
        }
    }
}
