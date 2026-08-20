package newconsole;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.geom.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;
import newconsole.game.*;
import newconsole.js.*;
import newconsole.ui.*;
import newconsole.ui.dialogs.*;

/**
 * Порт мода "New Console Hardline" (Mnemotechnician, порт SMOLKEYS, v2.3, Java) как нативный
 * пакет клиента: продвинутая JS-консоль - редактор кода с подсветкой синтаксиса и гайдами
 * отступов (CodeArea/JsCodeArea), панель логов с перехватом Log.logger, история выполнения
 * (сохраняется между сессиями), хранилище именованных скриптов (SavesDialog), скрипты по
 * игровым СОБЫТИЯМ (AutorunDialog/AutorunManager) и полноценный файл-браузер (FileBrowser,
 * открывается ТОЛЬКО кнопкой Files из консоли). Вход - плавающая перетаскиваемая кнопка
 * с иконкой терминала (FloatingWidget), позиция запоминается.
 *
 * НЕ заменяет нативную консоль форка (F8 consolefrag, команды !js): обе работают через один
 * и тот же Vars.mods.getScripts() (общий Rhino-scope - переменные, объявленные в одной,
 * видны в другой), New Console - независимая надстройка со своим UI. Плавающую кнопку можно
 * спрятать настройкой newconsole.floating-button (доступ тогда остаётся через справочник
 * фич - секция «Вшитые моды»).
 *
 * Адаптация по образцу HeliumMod/MI2UMod: без extends Mod, конструктор зовётся из
 * mindustry.client.Main.init() до ClientLoadEvent; self-disable guard по id мода
 * "newconsole-hardline" (плюс старый id "new-console" оригинала на всякий случай).
 * Вырезано: Vars.loadLogger() (клиент сам настраивает лог) и autoupdate-lib
 * (Updater.checkUpdates - вшитой копии самообновление не нужно, единственная внешняя
 * зависимость мода). Подпроект kts-eval (отдельный мод-расширение с Kotlin-scripting
 * консолью) НЕ портирован: тянет kotlin-scripting-compiler-embeddable и coroutines
 * целиком. Спрайты - в core/assets/newconsole/, регистрируются под оригинальными
 * атлас-именами "newconsole-hardline-*" (их ждёт CStyles); шрифт JetBrainsMono-medium.ttf -
 * в core/assets/fonts/ (CStyles ищет его через Vars.tree, путь совпадает с модовским).
 * Файлы скриптов/истории/autorun - те же saves/newconsole-*, что у мода: наработки
 * пользователя подхватываются как есть. startup.js (console/startup.js в Vars.tree)
 * переписан: оригинальный не парсился Rhino (redeclaration of formal parameter в
 * readString) и ссылался на несуществующий в hardline-порте JSInterface - т.е. фактически
 * не работал вовсе; наш вариант даёт те же хелперы через ConsoleVars.
 */
public class NewConsoleMod{

    public NewConsoleMod(){
        //self-disable: рядом установлен настоящий New Console - две копии продублировали бы
        //плавающую кнопку, перехват Log.logger и autorun-слушатели; вшитая уступает
        if(Vars.mods.locateMod("newconsole-hardline") != null || Vars.mods.locateMod("new-console") != null){
            Log.info("[newconsole] External New Console mod is also loaded - baked-in copy is standing down.");
            return;
        }

        Events.on(EventType.ClientLoadEvent.class, e -> {
            try{
                loadSprites();

                NCJSLink.importPackage(
                        "newconsole", "newconsole.game", "newconsole.io",
                        "newconsole.js", "newconsole.ui"
                );

                CStyles.loadSync();
                initConsole();

                Events.fire(new NewConsoleInitEvent());

                ConsoleVars.consoles.each(cons -> {
                    cons.scripts.load();
                    cons.autorun.load();
                });

                ConsoleSettings.init();
            }catch(Throwable t){
                Log.err("[newconsole] failed to initialize", t);
            }
        });
    }

    public static void executeStartup(){
        try{
            var file = Vars.tree.get(ConsoleVars.startup);
            if(!file.exists()){
                Log.warn("Startup script not found.");
                return;
            }

            Log.info("Executing startup script...");
            Time.mark();
            Vars.mods.getScripts().runConsole(file.readString());
            Log.info("Startup script executed in [blue]" + Time.elapsed() + "[] ms.");
        }catch(Throwable e){
            Log.err("Failed to execute startup script!", e);
        }
    }

    /**
     * Регистрирует спрайты мода в атласе под оригинальными именами "newconsole-hardline-*"
     * (у обычного мода это делает спрайт-пакер Mods, префиксуя регионы id мода). Должно
     * отработать ДО CStyles.loadSync() - тот берёт регионы через atlas.find().
     */
    private static void loadSprites(){
        for(String name : new String[]{"folder", "file-unknown", "file-text", "file-js", "file-zip", "file-jar", "file-code", "file-image"}){
            String region = "newconsole-hardline-" + name;
            if(Core.atlas.has(region)) continue;
            Texture tex = new Texture(Core.files.internal("newconsole/" + name + ".png"));
            tex.setFilter(Texture.TextureFilter.linear);
            Core.atlas.addRegion(region, new TextureRegion(tex));
        }
    }

    public void initConsole(){
        ConsoleVars.group = new WidgetGroup();
        ConsoleVars.group.setFillParent(true);
        ConsoleVars.group.touchable = Touchable.childrenOnly;
        //наша настройка поверх модовского флага: кнопку можно спрятать, не теряя консоль
        //(открытие остаётся через секцию «Вшитые моды» справочника фич)
        ConsoleVars.group.visible(() -> ConsoleVars.consoleEnabled && Core.settings.getBool("newconsole.floating-button", true));
        Core.scene.add(ConsoleVars.group);

        ConsoleVars.consoles.add(new Console(new JsCodeArea("", CStyles.monoArea), "JS", code -> Vars.mods.getScripts().runConsole(code), (script, variable, eventObj) -> {
            Vars.mods.getScripts().scope.put(variable, Vars.mods.getScripts().scope, eventObj);

            String res = Vars.mods.getScripts().runConsole(script.replaceAll("_autorun_event", variable));

            Vars.mods.getScripts().scope.delete(variable);

            return res;
        }));

        ConsoleVars.saves = new SavesDialog();
        ConsoleVars.copypaste = new CopypasteDialog();
        ConsoleVars.fileBrowser = new FileBrowser();
        ConsoleVars.autorun = new AutorunDialog();

        ConsoleVars.floatingWidget = new FloatingWidget();

        ImageButton b = ConsoleVars.floatingWidget.button(Icon.terminal, Styles.defaulti, () ->
                ConsoleVars.getCurrentConsole().show()
        ).uniformX().uniformY().fill().get();

        ConsoleVars.floatingWidget.row();

        ConsoleVars.floatingWidget.button(Icon.left, Styles.defaulti, () -> {
            if(ConsoleVars.selectConsole > 0){
                ConsoleVars.selectConsole--;
                b.replaceImage(new Image(ConsoleVars.getCurrentConsole().buttonIcon));
            }
        }).uniformX().uniformY().fill().visible(() -> ConsoleVars.consoles.size > 1);

        ConsoleVars.floatingWidget.button(Icon.right, Styles.defaulti, () -> {
            if(ConsoleVars.selectConsole == ConsoleVars.consoles.size - 1) return;

            ConsoleVars.selectConsole++;
            b.replaceImage(new Image(ConsoleVars.getCurrentConsole().buttonIcon));
        }).uniformX().uniformY().fill().visible(() -> ConsoleVars.consoles.size > 1);

        ConsoleVars.group.addChild(ConsoleVars.floatingWidget);

        Time.run(10, () -> {
            //восстановление позиции кнопки - в оригинале настройка remember-button-position
            //объявлена, но нигде не читалась; здесь она реально гейтит восстановление
            var oldPosition = ConsoleSettings.rememberButtonPosition()
                    ? ConsoleSettings.getLastButtonPosition()
                    : Tmp.v1.set(-1, -1);

            ConsoleVars.floatingWidget.setPosition(
                    oldPosition.x != -1 ? oldPosition.x : ConsoleVars.group.getWidth() / 2f,
                    oldPosition.y != -1 ? oldPosition.y : ConsoleVars.group.getHeight() / 1.5f
            );
        });

        var lastSavedPosition = new Vec2(-1, -1);
        Timer.schedule(() -> {
            //периодическое сохранение позиции кнопки (и тут тоже уважаем настройку - см. выше)
            if(!ConsoleSettings.rememberButtonPosition()) return;

            var newPosition = Tmp.v1.set(
                    ConsoleVars.floatingWidget.x,
                    ConsoleVars.floatingWidget.y
            );
            if(newPosition.equals(lastSavedPosition)) return;

            lastSavedPosition.set(newPosition);
            ConsoleSettings.setLastButtonPosition(newPosition);
        }, 2f, 2f);

        executeStartup();
    }

    public static class NewConsoleInitEvent{}
}
