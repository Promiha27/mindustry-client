package qol.resourceforecast;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import qol.ui.QolWindow;

import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/**
 * The forecast's HUD panel: one label row per item, prebuilt by
 * {@link ResourceForecastFeature#buildRows} - the window itself only lays them out, and the feature
 * calls {@link #rebuild()} once per sample (1s), only while the window is attached and expanded.
 */
public class ResourceForecastWindow extends QolWindow{
    final ResourceForecastFeature feature;

    public ResourceForecastWindow(ResourceForecastFeature feature){
        super("resource-forecast", "qol.feature.resource-forecast.title");
        this.feature = feature;
        //same combined condition Hub and the other feature windows use (QolWindow's own default
        //only checks hudfrag.shown; visible() replaces rather than composes)
        visible(() -> state.isGame() && ui.hudfrag.shown);
        rebuild();
    }

    @Override
    protected float defaultX(){
        return 320f;
    }

    @Override
    protected float defaultY(){
        return Core.graphics.getHeight() - 460f;
    }

    @Override
    protected void setupCont(Table cont){
        cont.defaults().left().growX().pad(1f);
        if(feature.rows.isEmpty()){
            cont.add("[lightgray]" + Core.bundle.get("qol.resource-forecast.quiet", "No notable trends...")).left().minWidth(220f).labelAlign(Align.left).row();
        }else{
            for(String row : feature.rows){
                cont.add(row).left().minWidth(220f).labelAlign(Align.left).row();
            }
        }
    }
}
