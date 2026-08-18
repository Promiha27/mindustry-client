package qol.unitnotify;

import arc.util.Time;

/** Throttles how often {@link UnitNotifyFeature#takeItemForSelectedTurret} can fire a fetch request. */
class AmmoFetchTimer{
    private float readyAt = 0f;

    void addTime(){
        readyAt = Time.time + 15.01f;
    }

    boolean canInteract(){
        return Time.time >= readyAt;
    }

    void reset(){
        readyAt = Time.time;
    }
}
