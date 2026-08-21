package sonkaextras.campaign;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import sonkaextras.campaign.CampaignProfiles.*;
import sonkaextras.dataio.*;

import java.text.*;
import java.util.*;

import static mindustry.Vars.*;

/**
 * Диалог «Профили кампании»: список (имя, активный, число секторов, планеты, дата последней игры),
 * на строку - переключить / переименовать / дублировать / экспорт / удалить; внизу - новый пустой,
 * импорт из zip, папка профилей. Операции с живой кампанией (переключение, дублирование активного,
 * импорт) доступны только из главного меню ({@link CampaignProfiles#canSwitch()}). После
 * переключения - обязательный перезапуск (см. javadoc {@link CampaignProfiles}).
 */
public class CampaignProfilesDialog extends BaseDialog{
    static final DateFormat dateFormat = SimpleDateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    public CampaignProfilesDialog(){
        super("@client.sonka.campaign.title");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);
    }

    void setup(){
        cont.clear();
        boolean can = CampaignProfiles.canSwitch();

        cont.add("@client.sonka.campaign.hint").width(640f).wrap().pad(6f).row();
        if(!can){
            cont.add("[gold]" + Core.bundle.get("client.sonka.campaign.menuonly")).width(640f).wrap().pad(4f).row();
        }
        if(CampaignProfiles.startupWarning != null){
            cont.add("[scarlet]" + CampaignProfiles.startupWarning).width(640f).wrap().pad(4f).row();
            cont.table(b -> {
                b.defaults().height(48f).pad(3f).growX();
                b.button(Core.bundle.format("client.sonka.campaign.startup.trust", CampaignProfiles.nameOf(CampaignProfiles.mismatchSettingsId)), () -> {
                    CampaignProfiles.resolveMismatch(true);
                    setup();
                });
                b.button(Core.bundle.format("client.sonka.campaign.startup.trust", CampaignProfiles.nameOf(CampaignProfiles.mismatchDiskId)), () -> {
                    CampaignProfiles.resolveMismatch(false);
                    setup();
                });
            }).growX().row();
        }

        cont.pane(list -> {
            list.top().defaults().growX().pad(4f);
            for(Profile p : CampaignProfiles.list()){
                list.table(Tex.button, row -> buildRow(row, p, can)).row();
            }
        }).growX().maxHeight(Core.graphics.getHeight() * 0.55f).row();

        cont.table(b -> {
            b.defaults().height(48f).pad(3f).growX();
            b.button("@client.sonka.campaign.new", Icon.add, () -> ui.showTextInput("@client.sonka.campaign.new", "@client.sonka.campaign.name", 40, "", name -> {
                if(name.trim().isEmpty()) return;
                CampaignProfiles.create(name.trim());
                setup();
            })).disabled(bt -> !can);
            b.button("@client.sonka.campaign.import", Icon.download, () -> FileChooser.open("zip").submit(file ->
                ui.showTextInput("@client.sonka.campaign.import", "@client.sonka.campaign.name", 40, file.nameWithoutExtension(), name -> {
                    if(name.trim().isEmpty()) return;
                    ui.loadAnd(() -> {
                        try{
                            CampaignProfiles.importProfile(file, name.trim());
                            Core.app.post(() -> {
                                ui.showInfoFade("@client.sonka.campaign.import.done");
                                setup();
                            });
                        }catch(Throwable t){
                            Log.err("[sonka-campaign] import failed", t);
                            Core.app.post(() -> ui.showErrorMessage(Core.bundle.get("client.sonka.campaign.import.fail") + "\n" + t.getMessage()));
                        }
                    });
                }))).disabled(bt -> !can);
            if(!mobile){
                b.button("@client.sonka.campaign.folder", Icon.folder, () -> {
                    CampaignProfiles.ensureInit();
                    Core.app.openFolder(CampaignProfiles.root().absolutePath());
                });
            }
        }).growX().row();
    }

    void buildRow(Table row, Profile p, boolean can){
        boolean active = p.active();
        row.left().margin(8f);
        row.table(info -> {
            info.left().defaults().left();
            String title = (active ? "[accent]" : "") + p.name + (active ? "  [lightgray](" + Core.bundle.get("client.sonka.campaign.active") + ")" : "");
            info.add(title).row();
            String planets = p.planets().toString(", ");
            String details = p.empty() ? Core.bundle.get("client.sonka.campaign.empty")
                : Core.bundle.format("client.sonka.campaign.details", p.sectors(), planets.isEmpty() ? "-" : planets, dateFormat.format(new Date(p.lastPlayed())));
            info.add("[lightgray]" + details).row();
        }).growX().left();

        row.table(b -> {
            b.defaults().size(46f).pad(2f);
            b.button(Icon.play, Styles.clearNonei, () -> confirmSwitch(p)).disabled(bt -> active || !can)
                .tooltip("@client.sonka.campaign.switch");
            b.button(Icon.pencil, Styles.clearNonei, () -> ui.showTextInput("@client.sonka.campaign.rename", "@client.sonka.campaign.name", 40, p.name, name -> {
                if(name.trim().isEmpty()) return;
                CampaignProfiles.rename(p, name.trim());
                setup();
            })).tooltip("@client.sonka.campaign.rename");
            b.button(Icon.copy, Styles.clearNonei, () -> ui.showTextInput("@client.sonka.campaign.duplicate", "@client.sonka.campaign.name", 40,
                Core.bundle.format("client.sonka.campaign.copyname", p.name), name -> {
                if(name.trim().isEmpty()) return;
                ui.loadAnd(() -> {
                    try{
                        CampaignProfiles.duplicate(p, name.trim());
                        Core.app.post(this::setup);
                    }catch(Throwable t){
                        Log.err("[sonka-campaign] duplicate failed", t);
                        Core.app.post(() -> ui.showException(t));
                    }
                });
            })).disabled(bt -> active && !can).tooltip("@client.sonka.campaign.duplicate");
            b.button(Icon.upload, Styles.clearNonei, () -> FileChooser.save("zip").name("campaign-" + Strings.sanitizeFilename(p.name) + ".zip").submit(file -> ui.loadAnd(() -> {
                try{
                    CampaignProfiles.export(p, file);
                    Core.app.post(() -> ui.showInfoFade(Core.bundle.format("client.sonka.dataio.export.done", file.name())));
                }catch(Throwable t){
                    Log.err("[sonka-campaign] export failed", t);
                    Core.app.post(() -> ui.showException(t));
                }
            }))).tooltip("@client.sonka.campaign.export");
            b.button(Icon.trash, Styles.clearNonei, () -> ui.showConfirm("@confirm", Core.bundle.format("client.sonka.campaign.delete.confirm", p.name), () -> {
                try{
                    var backup = CampaignProfiles.delete(p);
                    ui.showInfoFade(Core.bundle.format("client.sonka.campaign.delete.done", backup.name()));
                }catch(Throwable t){
                    Log.err("[sonka-campaign] delete failed", t);
                    ui.showException(t);
                }
                setup();
            })).disabled(bt -> active).tooltip("@client.sonka.campaign.delete");
        }).right();
    }

    void confirmSwitch(Profile target){
        if(!CampaignProfiles.canSwitch()){
            ui.showErrorMessage("@client.sonka.campaign.menuonly");
            return;
        }
        Profile from = CampaignProfiles.active();
        String text = Core.bundle.format("client.sonka.campaign.switch.confirm", from == null ? "?" : from.name, target.name);
        ui.showConfirm("@client.sonka.campaign.switch", text, () -> ui.loadAnd(() -> {
            try{
                var backup = CampaignProfiles.switchTo(target);
                Core.app.post(() -> {
                    hide();
                    //перезапуск обязателен: кэш кампании в памяти теперь от прежнего профиля
                    ui.showInfoOnHidden(Core.bundle.format("client.sonka.campaign.switch.done", target.name, backup.name()), DataIODialog::restart);
                });
            }catch(Throwable t){
                Log.err("[sonka-campaign] switch failed", t);
                Core.app.post(() -> {
                    ui.showErrorMessage(t.getMessage());
                    setup();
                });
            }
        }));
    }
}
