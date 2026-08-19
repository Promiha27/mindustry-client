package scheme.ui.dialogs;

import arc.func.Boolc;
import arc.func.Boolp;
import arc.func.Cons;
import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import mindustry.ui.dialogs.BaseDialog;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

/**
 * Рендер-конфиг Scheme Size, урезанный до уникального: X-Ray, сетка, линейка,
 * тьма у краёв карты (Vars.enableDarkness - переменная есть, а UI у клиента не было)
 * и локальное отключение тумана войны. Остальные тумблеры мода (лазеры энергии, статусы
 * блоков, освещение, радиусы, хп-бары, скрытие юнитов, безрамочные дисплеи) не дублируем -
 * они уже есть в настройках графики клиента, на биндах I/O/`/F9 и в mi2u.
 */
public class RendererConfigDialog extends BaseDialog{

    public RendererConfigDialog(){
        super("@scheme.render.name");
        addCloseButton();

        partition("scheme.category.general", part -> {
            check(part, "dark", value -> enableDarkness = value, () -> enableDarkness);
            check(part, "fog", value -> state.rules.fog = value, () -> state.rules.fog);
        });

        partition("scheme.category.add", part -> {
            check(part, "xray", value -> render.xray = value, () -> render.xray);
            check(part, "grid", value -> render.grid = value, () -> render.grid);
            check(part, "ruler", value -> render.ruler = value, () -> render.ruler);
        });

        cont.labelWrap("@scheme.render.desc").labelAlign(2, 8).padTop(16f).width(320f).get().getStyle().fontColor = Color.lightGray;
    }

    private void partition(String title, Cons<Table> cons){
        cont.add("@" + title).padTop(16f).row();
        cont.table(cons).left().row();
    }

    private void check(Table table, String name, Boolc listener, Boolp checked){
        table.check("@scheme.render." + name, listener).left().with(check ->
            check.update(() -> check.setChecked(checked.get()))
        ).row();
    }
}
