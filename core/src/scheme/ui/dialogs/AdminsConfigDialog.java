package scheme.ui.dialogs;

import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import mindustry.gen.Call;
import mindustry.gen.ClientSnapshotCallPacket;
import mindustry.ui.dialogs.BaseDialog;
import scheme.tools.admins.*;
import scheme.ui.TextSlider;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

/**
 * Конфиг админ-инструментов: главный рубильник, способ (auto/internal/slashjs),
 * "не проверять права" и строгий режим хоста (анти-телепорт чужих клиентов).
 * <p>
 * Отличие от мода: способ Mindurka не портирован (нужен их сервер + интеграция мода),
 * поэтому auto выбирает между Internal (хост/локалка) и SlashJs (клиент на сервере).
 * Индексы настройки "adminsway" сохранены: 0=internal, 1=slashjs, 3=auto (2, бывший
 * Mindurka, схлопывается в auto).
 */
public class AdminsConfigDialog extends BaseDialog{

    public static boolean enabled = settings.getBool("adminsenabled", false);
    public static boolean always = settings.getBool("adminsalways", false);
    public static boolean strict = settings.getBool("adminsstrict", false);
    public int way = settings.getInt("adminsway", 0);

    public AdminsConfigDialog(){
        super("@scheme.admins.name");
        addCloseButton();

        if(way == 2) way = 3; // Mindurka из старых конфигов -> auto

        hidden(() -> {
            settings.put("adminsenabled", enabled);
            settings.put("adminsalways", always);
            settings.put("adminsstrict", strict);
            settings.put("adminsway", way);
            admins = getTools();
        });

        new TextSlider(0, 1, 1, enabled ? 1 : 0, value -> bundle.format("scheme.admins.lever", bundle.get((enabled = value == 1) ? "scheme.admins.enabled" : "scheme.admins.disabled"))).build(cont).width(320f).row();

        cont.labelWrap("@scheme.admins.way").padTop(16f).width(320f).row();
        cont.table(table -> {
            var auto = table.check(bundle.format("scheme.admins.way.auto.name", detectToolsName()), value -> this.way = 3)
                .checked(t -> this.way == 3).disabled(t -> !enabled).tooltip("@scheme.admins.way.auto.desc").left().get();
            shown(() -> auto.setText(bundle.format("scheme.admins.way.auto.name", detectToolsName())));
            table.row();
            for(int i = 0; i < AdminsTools.implementations.length; i++)
                addCheck(table, "scheme.admins.way." + AdminsTools.implementations[i].keyName(), i);
        }).left().row();

        cont.labelWrap("@scheme.admins.always").padTop(16f).width(320f).row();
        new TextSlider(0, 1, 1, always ? 1 : 0, value -> (always = value == 1) ? "@yes" : "@no").update(slider -> slider.setDisabled(!enabled)).build(cont).width(320f).row();

        cont.labelWrap("@scheme.admins.strict").padTop(16f).width(320f).row();
        new TextSlider(0, 1, 1, strict ? 1 : 0, value -> (strict = value == 1) ? "@yes" : "@no").update(slider -> slider.setDisabled(net.client())).build(cont).width(320f).row();

        net.handleServer(ClientSnapshotCallPacket.class, (con, snapshot) -> {
            if(strict && con.player != null && !con.player.dead() && !con.kicked){
                var unit = con.player.unit();

                if(!snapshot.dead && unit.id == snapshot.unitID && !Mathf.within(snapshot.x, snapshot.y, unit.x, unit.y, 112f)){
                    Call.setPosition(con, unit.x, unit.y); // teleport and correct position when necessary
                    return;
                }
            }

            snapshot.handleServer(con); // built-in
        });
    }

    private void addCheck(Table table, String text, int way){
        table.check(bundle.get(text + ".name"), value -> this.way = way).checked(t -> this.way == way).disabled(t -> !enabled).tooltip("@" + text + ".desc").left().row();
    }

    /** Made static so that it can be accessed before the dialog is created. */
    public static AdminsTools getTools(){
        int way = settings.getInt("adminsway", 0);
        if(way >= 2) return detectTools();
        return AdminsTools.implementations[way];
    }

    public static String detectToolsName(){
        return bundle.get("scheme.admins.way." + detectTools().keyName() + ".name");
    }

    public static AdminsTools detectTools(){
        return net.client() ? AdminsTools.implementations[1] : AdminsTools.implementations[0];
    }
}
