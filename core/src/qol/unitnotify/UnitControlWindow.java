package qol.unitnotify;

import arc.Core;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import qol.ui.QolWindow;

import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/** Draggable log panel for {@link UnitNotifyFeature}'s "enemy is actively controlling units" alerts - a persistent, always-visible record instead of the fading toast, which is easy to miss if you're not looking at that exact moment. */
public class UnitControlWindow extends QolWindow{
    final UnitNotifyFeature feature;

    public UnitControlWindow(UnitNotifyFeature feature){
        super("unit-control", "qol.unit-control.title");
        this.feature = feature;
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
        return Core.graphics.getHeight() - 460f;
    }

    @Override
    protected void setupCont(Table cont){
        cont.defaults().left().growX().pad(1f);
        if(feature.logList.isEmpty()){
            cont.add("[lightgray]" + Core.bundle.get("qol.unit-control.quiet", "No hostile control detected")).left().minWidth(280f).labelAlign(Align.left).row();
        }else{
            for(UnitNotifyFeature.LogEntry log : feature.logList){
                Label label = cont.add(log.text).left().wrap().growX().minWidth(280f).labelAlign(Align.left).get();
                label.update(() -> label.setColor(feature.blinkColor()));
                cont.row();
            }
        }
    }
}
