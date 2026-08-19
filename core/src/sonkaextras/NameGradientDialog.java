package sonkaextras;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

/**
 * Редактор градиентного ника: красит ник линейным градиентом между опорными цветами через
 * ваниль-разметку {@code [#rrggbb]} и пишет результат в настройку "name" - ровно так же, как
 * это делает поле ника в {@link JoinDialog}/{@link HostDialog} (рядом с которыми и живёт
 * кнопка-вход в этот диалог).
 * <p>
 * Лимит длины: сервер ({@code NetServer.fixName}) обрезает имя до {@link mindustry.Vars#maxNameLength}
 * (40) БАЙТ utf-8, причём цветовые метки считаются в эти байты. Одна метка "[#rrggbb]" = 9 байт,
 * так что по-буквенный градиент влезает только в очень короткие ники. Поэтому число меток
 * подбирается автоматически под остаток байтового бюджета (буквы группируются), а если ник сам
 * по себе длиннее лимита - диалог показывает красное предупреждение об обрезании.
 * <p>
 * Прецедент в этой кодовой базе: {@link agzam4.uiOverride.ChatGradient} красит градиентом
 * СООБЩЕНИЯ чата (дискретный выбор цвета по индексу); здесь интерполяция плавная (lerp по всем
 * опорным цветам) и с байтовым бюджетом, т.к. у ника, в отличие от сообщений, жёсткий лимит.
 */
public class NameGradientDialog extends BaseDialog{
    /** Настройка: опорные цвета градиента, 6-hex через пробел (тот же формат, что у ChatGradient). */
    private static final String colorsKey = "namegradient-colors";
    /** "[#rrggbb]" - цена одной цветовой метки в байтах utf-8. */
    private static final int stopCost = 9;

    /** Базовый ник без разметки. */
    private String base = "";
    private final Seq<Color> colors = new Seq<>();
    private Table colorsTable;

    /** Результат последней сборки: размеченный ник, его байты и фактическое число цветовых меток. */
    private String built = "";
    private int builtBytes, builtStops;

    public NameGradientDialog(){
        super("@client.namegradient.title");
        addCloseButton();
        shown(this::setup);
        onResize(this::setup);

        buttons.button("@client.namegradient.apply", Icon.ok, () -> {
            // применяем так же, как поле ника в JoinDialog: и игроку, и в настройку
            if(player != null) player.name(built);
            Core.settings.put("name", built);
            saveColors();
            hide();
        }).disabled(b -> built.isEmpty());
    }

    private void setup(){
        // каждый показ стартуем от текущего ника без старой разметки - так диалог
        // можно открывать повторно, не накапливая цветовые метки
        base = Strings.stripColors(Core.settings.getString("name", ""));
        loadColors();
        rebuildResult();

        cont.clear();
        cont.defaults().pad(4f);

        cont.table(t -> {
            t.add("@name").padRight(10f);
            t.field(base, text -> {
                base = text;
                rebuildResult();
            }).grow().pad(8f).maxTextLength(maxNameLength);
        }).width(460f).height(60f).row();

        colorsTable = cont.table().get();
        cont.row();
        rebuildColors();

        // живое превью: Label сам рендерит [#hex]-разметку
        cont.table(Tex.pane, t -> t.label(() -> built).pad(8f).get().setFontScale(1.3f)).minWidth(320f).row();

        // счётчик байт: красный, если сервер будет резать
        cont.label(() -> (builtBytes > maxNameLength ? "[scarlet]" : "[lightgray]")
            + Core.bundle.format("client.namegradient.bytes", builtBytes, maxNameLength)).row();
        cont.label(() -> {
            if(builtBytes > maxNameLength) return "[scarlet]" + Core.bundle.get("client.namegradient.toolong");
            if(base.isEmpty() || colors.isEmpty()) return "";
            if(builtStops == 0) return "[scarlet]" + Core.bundle.get("client.namegradient.nofit");
            if(builtStops < base.length()) return "[lightgray]" + Core.bundle.format("client.namegradient.grouped", builtStops);
            return "";
        }).growX().get().setWrap(true);
    }

    /** Полоса опорных цветов: клик по образцу - пикер, крестик - удалить, плюс - добавить. */
    private void rebuildColors(){
        colorsTable.clearChildren();
        for(int i = 0; i < colors.size; i++){
            int fi = i;
            Color color = colors.get(fi);
            colorsTable.table(t -> {
                ImageButton swatch = t.button(Tex.whiteui, Styles.squarei, 34, () ->
                    ui.picker.show(color, false, c -> {
                        color.set(c);
                        changed();
                    })).size(48f).tooltip("@client.namegradient.editcolor").get();
                swatch.update(() -> swatch.getStyle().imageUpColor = color);
                t.row();
                t.button(Icon.cancelSmall, Styles.clearNonei, () -> {
                    colors.remove(fi);
                    changed();
                }).size(48f, 28f).tooltip("@client.namegradient.removecolor");
            }).pad(2f);
        }
        colorsTable.button(Icon.add, Styles.squarei, () ->
            ui.picker.show(colors.isEmpty() ? Color.sky : colors.peek(), false, c -> {
                colors.add(new Color(c));
                changed();
            })).size(48f).padLeft(8f).tooltip("@client.namegradient.addcolor");
    }

    private void changed(){
        rebuildColors();
        rebuildResult();
        saveColors();
    }

    /** Пересобирает размеченный ник, укладывая число цветовых меток в байтовый бюджет сервера. */
    private void rebuildResult(){
        // '[' в нике экранируем как "[[", иначе он сам начнёт цветовую метку
        String escaped = base.replace("[", "[[");
        int stops = 0;
        String result = escaped;

        if(!base.isEmpty() && !colors.isEmpty()){
            int budget = maxNameLength - escaped.getBytes(Strings.utf8).length;
            stops = Math.min(base.length(), Math.max(budget / stopCost, 0));
            if(stops > 0){
                StringBuilder sb = new StringBuilder();
                int lastGroup = -1, lastRgb = -1;
                for(int i = 0; i < base.length(); i++){
                    int group = i * stops / base.length(); // равномерная группировка букв по меткам
                    if(group != lastGroup){
                        lastGroup = group;
                        lerp(colors, stops == 1 ? 0.5f : (float)group / (stops - 1), Tmp.c1);
                        int rgb = Tmp.c1.rgb888();
                        if(rgb != lastRgb){ // подряд одинаковые метки не пишем - экономия байтов
                            lastRgb = rgb;
                            sb.append("[#").append(String.format("%06x", rgb)).append(']');
                        }
                    }
                    char c = base.charAt(i);
                    sb.append(c);
                    if(c == '[') sb.append('[');
                }
                result = sb.toString();
            }
        }

        built = result;
        builtBytes = built.getBytes(Strings.utf8).length;
        builtStops = stops;
    }

    /** Плавная интерполяция по всем опорным цветам: t=0 - первый, t=1 - последний. */
    private static Color lerp(Seq<Color> colors, float t, Color out){
        if(colors.size == 1) return out.set(colors.first());
        float seg = t * (colors.size - 1);
        int i = Math.min((int)seg, colors.size - 2);
        return out.set(colors.get(i)).lerp(colors.get(i + 1), seg - i);
    }

    private void loadColors(){
        colors.clear();
        for(String s : Core.settings.getString(colorsKey, "").split(" ")){
            if(s.isEmpty()) continue;
            try{
                colors.add(Color.valueOf(s));
            }catch(Exception ignored){
            }
        }
        // первый запуск (или всё удалили): пара цветов по умолчанию, чтобы сразу было видно эффект
        if(colors.isEmpty()){
            colors.add(Color.sky.cpy());
            colors.add(Color.pink.cpy());
        }
    }

    private void saveColors(){
        StringBuilder sb = new StringBuilder();
        for(Color c : colors){
            if(sb.length() > 0) sb.append(' ');
            sb.append(String.format("%06x", c.rgb888()));
        }
        Core.settings.put(colorsKey, sb.toString());
    }
}
