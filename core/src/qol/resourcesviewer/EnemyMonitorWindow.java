package qol.resourcesviewer;

import arc.Core;
import arc.scene.ui.ImageButton;
import arc.scene.ui.TextField.TextFieldFilter;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.gen.Icon;
import mindustry.type.Item;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import qol.ui.QolWindow;

import static mindustry.Vars.content;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;

/** Log panel for {@link EnemyMonitorFeature}; the gear button opens a dialog to enable/disable and set alarm thresholds per tracked item. */
public class EnemyMonitorWindow extends QolWindow{
    final EnemyMonitorFeature feature;

    public EnemyMonitorWindow(EnemyMonitorFeature feature){
        super("resources-viewer", "qol.feature.resources-viewer.title");
        this.feature = feature;
        //overrides QolWindow's own default (Vars.ui.hudfrag.shown alone) - needs state.isGame() too,
        //same as Hub - so keep both conditions here rather than just adding hudfrag.shown on top of
        //whatever the base class already installed.
        visible(() -> state.isGame() && ui.hudfrag.shown);
        rebuild();
    }

    @Override
    protected float defaultX(){
        return 20f;
    }

    @Override
    protected float defaultY(){
        return Core.graphics.getHeight() - 460f;
    }

    @Override
    protected void setupTitleButtons(Table titleExtras){
        ImageButton settingsBtn = new ImageButton(Icon.settings, Styles.clearNonei);
        settingsBtn.clicked(this::showItemSettings);
        settingsBtn.addListener(new Tooltip(t -> t.background(null).add("@qol.resources-viewer.configure")));
        titleExtras.add(settingsBtn).size(32f).pad(2f);
    }

    @Override
    protected void setupCont(Table cont){
        cont.defaults().left().growX().pad(1f);
        if(feature.logList.isEmpty()){
            cont.add("[lightgray]" + Core.bundle.get("qol.resources-viewer.quiet", "All quiet...")).left().minWidth(280f).labelAlign(Align.left).row();
        }else{
            for(EnemyMonitorFeature.LogEntry log : feature.logList){
                cont.add(log.text).left().wrap().growX().minWidth(280f).labelAlign(Align.left).row();
            }
        }
    }

    void showItemSettings(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("qol.resources-viewer.configure", "Resource Monitor Settings"));
        dialog.addCloseButton();
        dialog.cont.pane(itemTable -> {
            itemTable.defaults().growX().pad(4f);
            for(Item item : content.items()){
                Table row = new Table();
                row.left();
                row.check(item.localizedName, feature.itemEnabled(item), checked -> feature.setItemEnabled(item, checked)).left().growX().colspan(2).row();
                row.add("[gray]" + Core.bundle.get("qol.resources-viewer.threshold", "Threshold") + ":[]").padLeft(34f).padRight(5f).left();
                row.field(String.valueOf(feature.itemThreshold(item)), TextFieldFilter.digitsOnly, text -> {
                    if(text.isEmpty()) return;
                    feature.setItemThreshold(item, Integer.parseInt(text));
                }).width(100f).left();
                itemTable.add(row).growX().row();
                itemTable.image().color(arc.graphics.Color.gray).height(1f).growX().pad(2f).row();
            }
        }).width(480f).height(340f);
        dialog.show();
    }
}
