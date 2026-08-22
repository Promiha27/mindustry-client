package sonkaextras.hotkeys;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.input.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.util.*;

import static mindustry.Vars.*;

/**
 * Единый список горячих клавиш клиента и всех вшитых модов. Зачем, если есть «Управление»: там
 * только зарегистрированные KeyBind'ы, без хардкод-комбо (Ctrl+буква для типов юнитов, аккорд
 * таблицы схем, слоты Helium, Ctrl+S калькулятора TMI, пользовательские чат-бинды QoL Control),
 * без подсветки конфликтов, и оно спрятано в настройках - а с 17 пакетами главный вопрос «какая
 * клавиша что делает» должен открываться одной кнопкой ({@link Binding#monolithHotkeys}, F2).
 * <p>
 * Конфликты: записи с одинаковой сигнатурой «модификаторы+клавиша» помечаются оранжевым чипом с
 * тултипом «также: ...». Это информационно - часть совпадений штатная (Tab у списка игроков и
 * автодополнения чата срабатывает в разных контекстах), решает пользователь. Фильтр «только
 * конфликты» показывает все такие группы разом.
 * <p>
 * Переназначение не дублируется: для KeyBind кнопка открывает штатное «Управление» уже
 * отфильтрованным на этот бинд ({@code KeybindDialog.showFor}), для ручных записей - диалог
 * пакета-владельца, если он есть.
 */
public class HotkeysDialog extends BaseDialog{
    private static HotkeysDialog instance;

    private String search = "";
    private boolean onlyConflicts = false;
    private TextField field;
    private Table list;

    private HotkeysDialog(){
        super("@client.sonka.hotkeys.title");
        addCloseButton();
        buttons.button("@client.sonka.hotkeys.controls", Icon.settings, () -> ui.controls.show()).size(210f, 64f);

        cont.table(top -> {
            top.left();
            top.image(Icon.zoom).padRight(6f);
            field = top.field(search, s -> {
                search = s;
                rebuild();
            }).growX().get();
            field.setMessageText(Core.bundle.get("client.sonka.hotkeys.search"));
            top.check("@client.sonka.hotkeys.onlyconflicts", onlyConflicts, v -> {
                onlyConflicts = v;
                rebuild();
            }).padLeft(12f);
        }).growX().padBottom(6f).row();

        cont.labelWrap("@client.sonka.hotkeys.hint").color(Color.lightGray).growX().padBottom(6f).row();

        list = new Table();
        list.top().left();
        ScrollPane pane = cont.pane(list).grow().get();
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);

        shown(() -> {
            field.setText(search = "");
            onlyConflicts = false;
            rebuild();
            Core.app.post(field::requestKeyboard);
        });
        onResize(this::rebuild);
    }

    public static HotkeysDialog get(){
        if(instance == null) instance = new HotkeysDialog();
        return instance;
    }

    /** Открыть (статический вход для меню/настроек; имя не show - нельзя перекрыть Dialog.show статиком). */
    public static void open(){
        get().show();
    }

    /** Тогл по {@link Binding#monolithHotkeys}: открыт - закрыть, иначе открыть. Из Main.kt. */
    public static void init(){
        Events.run(Trigger.update, () -> {
            if(Core.scene == null || Core.scene.hasField()) return;
            if(!Core.input.keyTap(Binding.monolithHotkeys)) return;
            HotkeysDialog d = get();
            if(d.isShown()) d.hide();
            else if(!Core.scene.hasDialog() || state.isMenu()) d.show();
        });
    }

    private void rebuild(){
        list.clear();
        Seq<Hotkey> all = HotkeyCatalog.all();

        //конфликты: сигнатура -> все записи с ней
        ObjectMap<String, Seq<Hotkey>> bySig = new ObjectMap<>();
        for(Hotkey h : all){
            String sig = h.signature();
            if(sig != null) bySig.get(sig, Seq::new).add(h);
        }

        String q = search.trim().toLowerCase(Locale.ROOT);
        float keyWidth = Math.min(260f, Core.graphics.getWidth() / Scl.scl(1f) * 0.22f);
        float width = Math.max(300f, Math.min(900f, Core.graphics.getWidth() / Scl.scl(1f) - 80f));

        String lastCategory = null;
        int shown = 0;
        for(Hotkey h : all){
            String sig = h.signature();
            Seq<Hotkey> same = sig == null ? null : bySig.get(sig);
            boolean conflict = same != null && same.size > 1;
            if(onlyConflicts && !conflict) continue;
            if(!q.isEmpty() && !matches(h, q)) continue;

            if(!h.category.equals(lastCategory)){
                lastCategory = h.category;
                list.add(h.category).color(Pal.accent).left().padTop(shown == 0 ? 0f : 14f).padBottom(2f).row();
                list.image().color(Pal.accent).height(2f).growX().padBottom(6f).row();
            }

            final Seq<Hotkey> others = same;
            list.table(row -> {
                row.left();
                //чип клавиши
                String key = h.key();
                boolean unset = key.isEmpty();
                Table chip = new Table(Tex.button);
                chip.margin(6f, 10f, 6f, 10f);
                Label kl = chip.add(unset ? Core.bundle.get("client.sonka.hotkeys.unset") : key).get();
                kl.setColor(unset ? Color.darkGray : conflict ? Pal.accent : Color.white);
                kl.setAlignment(Align.center);
                if(conflict){
                    chip.setColor(Color.valueOf("ffb347"));
                    StringBuilder sb = new StringBuilder(Core.bundle.get("client.sonka.hotkeys.conflict"));
                    for(Hotkey o : others){
                        if(o == h) continue;
                        sb.append("\n • ").append(o.category).append(": ").append(o.name);
                    }
                    chip.addListener(new Tooltip(t -> t.background(Styles.black6).margin(6f).add(sb.toString()).left()));
                }
                row.add(chip).minWidth(keyWidth).left().padRight(10f);

                //имя + описание
                row.table(text -> {
                    text.left().top();
                    text.add(h.name).left().wrap().growX().row();
                    if(h.desc != null && !h.desc.isEmpty()){
                        text.add(h.desc).color(Color.lightGray).fontScale(0.85f).left().wrap().growX();
                    }
                }).growX().left().padRight(10f);

                //действие
                if(h.bind != null){
                    KeyBind bind = h.bind;
                    row.button("@client.sonka.hotkeys.rebind", Styles.grayt, () -> ui.controls.showFor(bind)).size(140f, 40f).right();
                }else if(h.configure != null){
                    row.button("@client.sonka.hotkeys.configure", Styles.grayt, h.configure).size(140f, 40f).right();
                }else{
                    row.add().size(140f, 40f);
                }
            }).growX().width(width).padBottom(4f).row();
            shown++;
        }

        if(shown == 0){
            list.add("@client.sonka.hotkeys.empty").color(Color.gray).padTop(20f);
        }
    }

    private static boolean matches(Hotkey h, String q){
        return h.name.toLowerCase(Locale.ROOT).contains(q)
            || h.category.toLowerCase(Locale.ROOT).contains(q)
            || h.key().toLowerCase(Locale.ROOT).contains(q)
            || (h.desc != null && h.desc.toLowerCase(Locale.ROOT).contains(q))
            || (h.bind != null && h.bind.name.toLowerCase(Locale.ROOT).contains(q));
    }
}
