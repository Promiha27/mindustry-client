package qolc.mlog;

import arc.Core;
import arc.Events;
import arc.files.Fi;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.client.CommandsKt;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Player;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;
import mindustry.world.blocks.logic.LogicBlock.LogicBuild;

/**
 * Библиотека mlog-программ: {@code !mlog} вставляет код из {@code <data>/qol/mlog/*.txt} в процессоры.
 * Порт ЯДРА features/mlog.js (список/вставка/вставка-по-клику/удаление файлов + комплект дефолтных
 * программ мода: deflag, novaheal, phase, thor - лежат в ассетах клиента {@code qolc/mlog/} и
 * докопируются в папку игрока, если их там нет).
 * <p>
 * Папка та же, что у JS-оригинала - накопленная библиотека игрока продолжает работать. Вставка -
 * штатный {@code build.configure(LogicBlock.compress(code, links))}, тот же путь, каким сохраняет код
 * ванильный редактор процессора, поэтому MP-безопасно (сервер применяет обычный config-пакет).
 * <p>
 * Режим {@code !mlog <файл> set} в оригинале требовал СТРЕЛЯТЬ по целевому процессору (начать и
 * прекратить стрельбу) - здесь заменено на простой клик по процессору: у нативного порта нет нужды в
 * этой акробатике, клик по конкретному зданию и так однозначен. {@code !mlog cancel} снимает режим.
 * <p>
 * НЕ портировано из mlog.js (2334 строки): расширения редактора логики (Copy with Labels /
 * Save-Load / Insert / Replace с конвертацией джампов в метки), окна трекера переменных процессоров и
 * редактор ячеек памяти - это отдельная IDE-подсистема, вопрос о её нужности оставлен sonka.
 */
public final class MlogLibrary{
    private static final String[] defaults = {"deflag", "novaheal", "phase", "thor"};

    /** Код, ждущий клика по процессору (режим {@code set}); null - режим неактивен. */
    private static String pending = null;
    private static String pendingName = null;

    private MlogLibrary(){
    }

    private static Fi dir(){
        return Vars.dataDirectory.child("qol").child("mlog");
    }

    public static void init(){
        seedDefaults();

        Events.on(WorldLoadEvent.class, e -> {
            pending = null;
            pendingName = null;
        });

        Events.run(Trigger.update, MlogLibrary::updatePending);

        CommandsKt.register("mlog [file] [mode]", Core.bundle.get("client.command.mlog.description"), MlogLibrary::runCommand);
    }

    /** Дефолтные программы мода из ассетов клиента - только недостающие, пользовательские файлы не трогаем. */
    private static void seedDefaults(){
        try{
            Fi dir = dir();
            dir.mkdirs();
            for(String name : defaults){
                Fi dst = dir.child(name + ".txt");
                if(dst.exists()) continue;
                Fi src = Core.files.internal("qolc/mlog/" + name + ".txt");
                if(src.exists()) src.copyTo(dst);
            }
        }catch(Throwable t){
            Log.err("[qol-control] failed to seed default mlog files", t);
        }
    }

    private static void runCommand(String[] args, Player player){
        if(args.length == 0){
            player.sendMessage(Core.bundle.get("qolc.mlog.usage"));
            return;
        }

        switch(args[0]){
            case "list" -> {
                Seq<Fi> files = Seq.with(dir().list()).select(f -> f.extension().equals("txt"));
                if(files.isEmpty()){
                    player.sendMessage(Core.bundle.get("qolc.mlog.no-files"));
                }else{
                    player.sendMessage(Core.bundle.format("qolc.mlog.list",
                        files.toString("\n- ", Fi::nameWithoutExtension)));
                }
            }
            case "remove" -> {
                if(args.length < 2){
                    player.sendMessage(Core.bundle.get("qolc.mlog.usage"));
                    return;
                }
                Fi f = dir().child(args[1] + ".txt");
                if(f.exists()){
                    f.delete();
                    player.sendMessage(Core.bundle.format("qolc.mlog.removed", args[1]));
                }else{
                    player.sendMessage(Core.bundle.format("qolc.mlog.not-found", args[1]));
                }
            }
            case "cancel" -> {
                pending = null;
                pendingName = null;
                player.sendMessage(Core.bundle.get("qolc.mlog.cancelled"));
            }
            default -> {
                Fi f = dir().child(args[0] + ".txt");
                if(!f.exists()){
                    player.sendMessage(Core.bundle.format("qolc.mlog.not-found", args[0]));
                    return;
                }
                String code = f.readString();

                if(args.length > 1 && args[1].equals("set")){
                    pending = code;
                    pendingName = args[0];
                    player.sendMessage(Core.bundle.format("qolc.mlog.click-target", pendingName));
                }else{
                    LogicBuild target = findEmptyProcessor();
                    if(target == null){
                        player.sendMessage(Core.bundle.get("qolc.mlog.no-empty"));
                    }else{
                        inject(target, code);
                    }
                }
            }
        }
    }

    /** Первый пустой обычный (не world-) процессор своей команды. */
    private static LogicBuild findEmptyProcessor(){
        for(Building b : Vars.player.team().data().buildings){
            if(b instanceof LogicBuild lb && b.block instanceof LogicBlock block && !block.privileged
                && (lb.code == null || lb.code.isEmpty())){
                return lb;
            }
        }
        return null;
    }

    private static void updatePending(){
        if(pending == null) return;
        if(!Vars.state.isGame()){
            pending = null;
            pendingName = null;
            return;
        }
        if(!Core.input.justTouched() || Core.scene.hasMouse()) return;

        Tile tile = Vars.world.tileWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        Building b = tile == null ? null : tile.build;
        if(b instanceof LogicBuild lb && b.team == Vars.player.team() && b.block instanceof LogicBlock block && !block.privileged){
            inject(lb, pending);
            pending = null;
            pendingName = null;
        }
    }

    /**
     * Две строки в Edit-меню редактора логики: сохранить текущий код в библиотеку
     * ({@code qol/mlog/<имя>.txt}) и загрузить файл библиотеки в редактор. Минимальный полезный кусок
     * "редакторных расширений" mlog.js - без него библиотеку {@code !mlog} нельзя пополнять из игры.
     * Вызывается из {@link mindustry.logic.LogicDialog} (мы владеем исходником движка - не нужен
     * scene-watcher, которым оригинал впрыскивал кнопки в чужой диалог).
     */
    public static void buildEditMenuButtons(arc.scene.ui.layout.Table t, arc.scene.ui.TextButton.TextButtonStyle style, mindustry.logic.LCanvas canvas, Runnable closeMenu){
        t.button(Core.bundle.get("qolc.mlog.save-button"), mindustry.gen.Icon.save, style, () -> {
            closeMenu.run();
            Vars.ui.showTextInput(Core.bundle.get("qolc.mlog.save-title"), Core.bundle.get("qolc.mlog.save-name"), 64, "", name -> {
                if(name.isEmpty()) return;
                try{
                    Fi dir = dir();
                    dir.mkdirs();
                    dir.child(name + ".txt").writeString(canvas.save());
                    Vars.ui.showInfoFade(Core.bundle.format("qolc.mlog.saved", name));
                }catch(Throwable e){
                    Vars.ui.showException(e);
                }
            });
        }).marginLeft(12f).row();

        t.button(Core.bundle.get("qolc.mlog.load-button"), mindustry.gen.Icon.folder, style, () -> {
            closeMenu.run();
            mindustry.ui.dialogs.BaseDialog picker = new mindustry.ui.dialogs.BaseDialog(Core.bundle.get("qolc.mlog.load-button"));
            picker.addCloseButton();
            picker.cont.pane(p -> {
                p.margin(10f);
                Seq<Fi> files = Seq.with(dir().list()).select(f -> f.extension().equals("txt"));
                if(files.isEmpty()){
                    p.add(Core.bundle.get("qolc.mlog.no-files"));
                    return;
                }
                for(Fi f : files){
                    p.button(f.nameWithoutExtension(), () -> {
                        try{
                            canvas.load(f.readString().replace("\r\n", "\n"));
                            picker.hide();
                        }catch(Throwable e){
                            Vars.ui.showException(e);
                        }
                    }).size(280f, 50f).padBottom(4f).row();
                }
            });
            picker.show();
        }).marginLeft(12f).row();
    }

    /**
     * Обзорный диалог библиотеки {@code qol/mlog/}: список файлов с просмотром кода (+копирование в
     * буфер), удалением и вставкой-по-клику (та же pending-механика, что у {@code !mlog <файл> set}).
     * Добавлен как кнопка-действие секции «Вшитые моды» в {@link mindustry.client.ui.FeaturesDialog} -
     * ДОПОЛНИТЕЛЬНАЯ точка входа: команда {@code !mlog} и кнопки Edit-меню редактора логики остаются.
     */
    public static void showLibraryDialog(){
        mindustry.ui.dialogs.BaseDialog dialog = new mindustry.ui.dialogs.BaseDialog(Core.bundle.get("qolc.mlog.library-title"));
        dialog.addCloseButton();

        arc.scene.ui.layout.Table list = new arc.scene.ui.layout.Table();
        //массив-обёртка: лямбда перестройки списка ссылается на саму себя (кнопка удаления)
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.clear();
            Seq<Fi> files = Seq.with(dir().list()).select(f -> f.extension().equals("txt"));
            files.sort((a, b) -> a.name().compareTo(b.name()));
            if(files.isEmpty()){
                list.add(Core.bundle.get("qolc.mlog.no-files")).left();
                return;
            }
            for(Fi f : files){
                String name = f.nameWithoutExtension();
                list.add(name).left().growX().padRight(8f);
                list.button(mindustry.gen.Icon.zoom, mindustry.ui.Styles.clearNonei, () -> showCodeDialog(f))
                    .size(40f).tooltip("@qolc.mlog.view");
                list.button(mindustry.gen.Icon.paste, mindustry.ui.Styles.clearNonei, () -> {
                    //тот же режим, что "!mlog <файл> set": код ждёт клика по процессору своей команды
                    pending = f.readString();
                    pendingName = name;
                    dialog.hide();
                    //showInfoFade, а не sendMessage: чат может быть закрыт менюшками, из которых открыт диалог
                    Vars.ui.showInfoFade(Core.bundle.format("qolc.mlog.click-target", name));
                }).size(40f).disabled(b -> !Vars.state.isGame()).tooltip("@qolc.mlog.set-tooltip");
                list.button(mindustry.gen.Icon.trash, mindustry.ui.Styles.clearNonei, () -> Vars.ui.showConfirm("@confirm",
                    Core.bundle.format("qolc.mlog.delete-confirm", name), () -> {
                        f.delete();
                        rebuild[0].run();
                    })).size(40f).tooltip("@delete");
                list.row();
            }
        };
        rebuild[0].run();

        //подсказка по команде - тот же текст, что печатает "!mlog" без аргументов
        dialog.cont.add(Core.bundle.get("qolc.mlog.usage")).left().growX().wrap().padBottom(10f).row();
        dialog.cont.pane(list).growX().maxWidth(640f);
        dialog.show();
    }

    /** Просмотр кода одного файла библиотеки, с копированием в буфер обмена. */
    private static void showCodeDialog(Fi f){
        mindustry.ui.dialogs.BaseDialog d = new mindustry.ui.dialogs.BaseDialog(f.nameWithoutExtension());
        d.addCloseButton();
        String code = f.readString();
        d.buttons.button("@copy", mindustry.gen.Icon.copy, () -> {
            Core.app.setClipboardText(code);
            Vars.ui.showInfoFade("@copied");
        }).size(210f, 64f);
        //"[[" - экранирование цветовой разметки arc-Label: mlog-строки со скобками должны показываться как есть
        d.cont.pane(p -> p.add(new arc.scene.ui.Label(code.replace("[", "[["), mindustry.ui.Styles.monoLabel)).left().growX().wrap()).grow();
        d.show();
    }

    private static void inject(LogicBuild target, String code){
        try{
            target.configure(LogicBlock.compress(code, target.links));
            Vars.player.sendMessage(Core.bundle.format("qolc.mlog.injected", target.tileX(), target.tileY()));
        }catch(Throwable t){
            Vars.player.sendMessage(Core.bundle.format("qolc.mlog.inject-failed", t.getMessage()));
            Log.err("[qol-control] mlog inject failed", t);
        }
    }
}
