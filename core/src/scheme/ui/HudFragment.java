package scheme.ui;

import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.TextField;
import arc.scene.ui.TextField.TextFieldFilter;
import arc.scene.ui.TextField.TextFieldStyle;
import arc.scene.ui.layout.*;
import arc.util.Align;
import mindustry.game.EventType.*;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import scheme.tools.BuildingTools.Mode;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

/**
 * HUD-часть порта Scheme Size, урезанная до десктопа: панель строительных инструментов
 * (снизу справа, раскрывается шевроном) и плашка "волна на подходе". Мобильные панели,
 * шилд-бар, Gamma/Mono UI и кнопка шорткатов схем из мода не портированы (см. отчёт порта).
 */
public class HudFragment{
    /** перф: скретч для пульсации цвета плашки "волна на подходе" - setColor копирует значение, так что общий буфер безопасен (без Color.white.cpy() каждый кадр) */
    private static final Color approachPulse = new Color();


    public static final TextFieldStyle input = new TextFieldStyle(){{
        font = Fonts.def;
        fontColor = Color.white;
        selection = Tex.selection;
        cursor = Tex.cursor;
    }};

    public FlipButton building = new FlipButton();
    public FlipButton admin = new FlipButton();

    /** PlacementFragment - для выравнивания панели по левому краю блок-меню. */
    public Element block;
    /**
     * Весь top-left стек форка (name="waves"): statustable + панель режимов Foo's Client
     * (activemodesdisplay) + панель энергии/пейлоада. Якорь высоты админ-панели. Оригинальный мод
     * якорился на одну statustable - на этом форке под ней ещё две панели, и админ-панель их накрывала.
     */
    public Element statusTable;
    /** Панель редактора (name="editor") - якорь в режиме редактора. */
    public Element editorTable;
    public TextField size;

    public void build(Group parent){
        Events.run(WorldLoadEvent.class, this::updateBlocks);
        Events.run(UnlockEvent.class, this::updateBlocks);

        parent.fill(cont -> { // Building Tools
            cont.name = "scheme-buildingtools";
            cont.bottom().right();

            cont.visible(() -> ui.hudfrag.shown && !ui.minimapfrag.shown() && !control.input.commandMode);

            size = new TextField("8", input);
            size.setFilter(TextFieldFilter.digitsOnly);
            size.changed(() -> build.resize(size.getText()));

            //Tex.buttonEdge2 оставлен сознательно: панель пристыкована к нативному блок-меню и по
            //style-гайду (sonkaextras.UiStyle) докнутые панели сливаются с соседним нативным стеком,
            //а не выглядят отдельным плавающим окном
            cont.table(Tex.buttonEdge2, pad -> {
                partitionbt(pad, mode -> {
                    mode.button(Icon.cancel, Styles.clearNonei, () -> {
                        control.input.block = null;
                        build.plan.clear();
                    }).visible(build::isPlacing).row();
                    mode.add(size).row();
                    mode.button(Icon.up, Styles.clearNonei, () -> build.resize(1)).row();
                    mode.image(Icon.resize).row();
                    mode.button(Icon.down, Styles.clearNonei, () -> build.resize(-1)).row();
                });

                partitionbt(pad, mode -> {
                    mode.button(Icon.menu, Styles.clearNonei, tile::show).tooltip("@scheme.select.tile").padTop(46f).row();
                    setMode(mode, Icon.pick, Mode.pick);
                    setMode(mode, Icon.pencil, Mode.brush);
                    setMode(mode, Icon.editor, Mode.edit);
                });

                partitionbt(pad, mode -> {
                    mode.button(Icon.redo, Styles.clearNonei, () -> scheme.moded.SchemeInput.flushLastRemoved(control.input)).tooltip("@scheme.tooltip.return").padBottom(46f).row();
                    setMode(mode, Icon.fill, Mode.fill);
                    setMode(mode, Icon.grid, Mode.square);
                    setMode(mode, Icon.commandRally, Mode.circle);
                });

                partitionbt(pad, mode -> {
                    mode.add(building).row();
                    setMode(mode, Icon.upload, Mode.drop);
                    setMode(mode, Icon.link, Mode.replace);
                    setMode(mode, Icon.hammer, Mode.remove);
                    setMode(mode, Icon.power, Mode.connect);
                }).visible(() -> true).update(mode -> mode.setTranslation(Scl.scl(building.fliped ? 0f : -87f), 0f));
            }).height(254f).update(pad -> {
                if(block == null) return; // block is null before the world is loaded
                //sonka: блок-меню может быть масштабировано PanelScale-обёрткой - якорим по
                //ВИЗУАЛЬНОЙ ширине (локальная ширина * произведение scale предков)
                //helium: слева от блок-меню теперь может стоять колонка быстрой палитры (тот же
                //frame, тот же масштаб) - сдвигаемся и на её визуальную ширину
                float heQuick = ui.hudfrag.blockfrag.heQuickInv != null ? ui.hudfrag.blockfrag.heQuickInv.getWrapper().getWidth() : 0f;
                pad.setTranslation(Scl.scl(building.fliped ? 4f : 178f) - (block.getWidth() + heQuick) * sonkaextras.PanelScale.effectiveScale(block), 0f);
                pad.setWidth(Scl.scl(building.fliped ? 244f : 70f)); // more magic numbers to the god of magic numbers
            });
        });

        parent.fill(cont -> { // Wave Approaching
            cont.name = "scheme-waveapproaching";
            cont.bottom();

            //фон плашки-алерта - канонный black6 из единого style-гайда
            cont.table(sonkaextras.UiStyle.titleBg(), pad -> {
                pad.add("@scheme.approaching.info").labelAlign(Align.center, Align.center).update(label -> label.setColor(approachPulse.set(Color.white).lerp(Color.scarlet, Mathf.absin(10f, 1f)))).padRight(6f);
                pad.button(Icon.info, Styles.clearNonei, approaching::show).grow();
                pad.button(Icon.eyeOffSmall, Styles.clearNonei, () -> settings.put("approachenabled", false)).grow();
            }).margin(6f).padBottom(100f).update(pad -> {
                pad.color.a = Mathf.lerpDelta(pad.color.a, Mathf.num(
                    settings.getBool("approachenabled") && state.isGame() && state.rules.waves && state.wavetime > 600f && state.wavetime < 1800f
                ), .1f);
                pad.touchable = pad.color.a > .001f ? Touchable.childrenOnly : Touchable.disabled; // ingeniously
            }).get().color.a(0f); // hide on startup
        });

        parent.fill(cont -> { // Admin panel (в моде звалась "Mobile Buttons", но это именно админ-панель)
            cont.name = "scheme-adminpanel";
            cont.top().left();

            //ключ настройки оригинальный ("mobilebuttons") - сохранённое значение sonka из мода подхватится
            cont.visible(() -> ui.hudfrag.shown && !ui.minimapfrag.shown() && (settings.getBool("mobilebuttons", false) || mobile));

            cont.table(Tex.buttonEdge4, pad -> {
                //ряд 1 виден всегда (шеврон + быстрые действия), в свёрнутом виде панель подъезжает вверх
                partitionmb(pad, mode -> {
                    mode.add(admin);
                    setAction(mode, "blasted", admins::despawn);           //деспавн юнитов
                    setAction(mode, "overdrive", () -> admins.teleport()); //телепорт к курсору
                    setAction(mode, Icon.fileText, () -> { if(!admins.unusable()) rulesetter.show(); });
                }).visible(() -> true).update(mode -> mode.setTranslation(0f, Scl.scl(admin.fliped ? 0f : -63.2f))).row();

                partitionmb(pad, mode -> {
                    setAction(mode, Icon.effect, admins::placeCore);       //ядро под ногами
                    setAction(mode, "boss", admins::manageTeam);           //смена команды
                    setAction(mode, Icon.admin, adminscfg::show);
                    setAction(mode, Icon.image, rendercfg::show);
                }).row();

                partitionmb(pad, mode -> {
                    setAction(mode, Icon.units, admins::manageUnit);
                    setAction(mode, Icon.add, admins::spawnUnits);
                    setAction(mode, "corroded", admins::manageEffect);     //статус-эффекты
                    setAction(mode, Icon.production, admins::manageItem);  //предметы в ядро
                }).row();
                //кнопки GammaAI (ai.select) и lock-движений из мода не портированы - их подсистемы SKIP
            }).margin(0f).update(pad -> {
                Element anchor = state.rules.editor ? editorTable : statusTable;
                if(anchor == null) return;
                //sonka: стек "waves" может быть масштабирован PanelScale-обёрткой - высота якоря визуальная
                pad.setTranslation(0f, Scl.scl((admin.fliped ? 0f : 127f) - (mobile ? 69f : 0f)) - anchor.getHeight() * sonkaextras.PanelScale.effectiveScale(anchor));
                pad.setHeight(Scl.scl(admin.fliped ? 190.8f : 63.8f));
            });
        });
    }

    private Cell<Table> partitionmb(Table table, Cons<Table> cons){
        return table.table(cont -> {
            cont.defaults().size(63.5f).left();
            cons.get(cont);
        }).visible(() -> admin.fliped);
    }

    private void setAction(Table table, Object icon, Runnable listener){
        //строковые иконки - ванильные спрайты статус-эффектов (status-blasted и т.п.), как в моде
        table.button(icon instanceof String name ? atlas.drawable("status-" + name) : (Drawable)icon, Styles.clearNonei, 37f, listener);
    }

    private Cell<Table> partitionbt(Table table, Cons<Table> cons){
        if(table.hasChildren()) table.image().color(Pal.gray).fillY().width(4f).pad(4f).visible(() -> building.fliped);
        return table.table(cont -> {
            cont.defaults().size(46f).bottom().right();
            cons.get(cont);
        }).visible(() -> building.fliped);
    }

    private void setMode(Table table, Drawable icon, Mode mode){
        table.button(icon, Styles.clearNoneTogglei, () -> build.setMode(mode)).checked(t -> build.mode == mode).tooltip("@scheme.tooltip." + mode).row();
    }

    private void updateBlocks(){
        app.post(() -> { // waiting for blockfrag rebuild
            // sonka: багфикс "панель не видно". Оригинальный мод восстанавливал контейнер блок-меню
            // хрупкой цепочкой find("inputTable").parent.parent.parent - в ЭТОМ форке вложенность
            // PlacementFragment другая, цепочка попадала во внутреннюю таблицу шириной ~150px вместо
            // всего меню, из-за чего сдвиг панели (178 - width) выходил ПОЛОЖИТЕЛЬНЫМ и панель
            // уезжала за правый край экрана. У форка контейнер доступен напрямую как публичное поле.
            block = ui.hudfrag.blockfrag.blockCatTable;
            statusTable = ui.hudGroup.find("waves");
            editorTable = ui.hudGroup.find("editor");
        });
    }
}
