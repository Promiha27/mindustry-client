package sonkaextras.dataio;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.client.utils.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import java.util.*;
import java.util.EnumSet;

import static mindustry.Vars.*;

/**
 * Диалоги гранулярного экспорта/импорта данных (см. {@link DataExport}/{@link DataImport}).
 * Экспорт: чекбоксы категорий со счётчиками локальных элементов → {@code FileChooser.save("zip")}.
 * Импорт: {@code FileChooser.open("zip")} → {@link DataImport#scan} → диалог только с теми
 * категориями, что реально есть в архиве (счётчики), тогл «перезаписывать существующие» (по
 * умолчанию ВЫКЛ) → применение → отчёт (импортировано/пропущено/заменено, бэкапы, ошибки) и,
 * если нужно, предложение перезапуска (моды, прогресс кампании, настройки).
 * <p>
 * Ванильные кнопки «экспорт/импорт данных» в настройках не трогаются - это дополнительный путь.
 */
public final class DataIODialog{
    private DataIODialog(){
    }

    public static void showExport(){
        new ExportDialog().show();
    }

    /** Диалог экспорта: набор категорий живёт в поле, чекбоксы пересобираются в setup(). */
    static class ExportDialog extends BaseDialog{
        final EnumSet<DataCategory> sel = EnumSet.of(DataCategory.schematics, DataCategory.maps);

        ExportDialog(){
            super("@client.sonka.dataio.export.title");
            addCloseButton();
            shown(this::setup);
            onResize(this::setup);
            buttons.button("@client.sonka.dataio.export.go", Icon.upload, this::export).size(230f, 64f);
        }

        void setup(){
            cont.clear();
            cont.add("@client.sonka.dataio.export.hint").width(560f).wrap().pad(6f).row();
            cont.pane(t -> {
                t.defaults().left().growX().pad(3f);
                for(DataCategory c : DataCategory.all){
                    int count = c.localCount();
                    CheckBox box = new CheckBox(c.title() + "  [lightgray](" + count + ")");
                    box.setChecked(sel.contains(c));
                    box.changed(() -> {
                        if(box.isChecked()) sel.add(c);
                        else sel.remove(c);
                    });
                    box.left();
                    t.add(box).row();
                    t.add(c.description()).color(Color.lightGray).wrap().width(520f).padLeft(34f).padBottom(6f).row();
                }
            }).growX().maxHeight(Core.graphics.getHeight() * 0.55f).row();

            cont.table(b -> {
                b.defaults().height(44f).pad(3f).growX();
                b.button("@client.sonka.linerotate.all", () -> {
                    sel.addAll(EnumSet.allOf(DataCategory.class));
                    setup();
                });
                b.button("@client.sonka.linerotate.none", () -> {
                    sel.clear();
                    setup();
                });
            }).growX().row();
        }

        void export(){
            if(sel.isEmpty()){
                ui.showInfoFade("@client.sonka.dataio.nothing");
                return;
            }
            EnumSet<DataCategory> copy = EnumSet.copyOf(sel);
            FileChooser.save("zip").name("mindustry-data-" + DataExport.fileDateFormat.format(new Date()) + ".zip").submit(file -> {
                ui.loadAnd(() -> {
                    try{
                        DataExport.export(file, copy);
                        Core.app.post(() -> {
                            hide();
                            ui.showInfoFade(Core.bundle.format("client.sonka.dataio.export.done", file.name()));
                        });
                    }catch(Throwable t){
                        Log.err("[sonka-dataio] export failed", t);
                        Core.app.post(() -> ui.showErrorMessage(Core.bundle.get("client.sonka.dataio.export.fail") + "\n" + t.getMessage()));
                    }
                });
            });
        }
    }

    public static void showImport(){
        FileChooser.open("zip").submit(file -> {
            DataImport.Archive archive;
            try{
                archive = DataImport.scan(file);
            }catch(Throwable t){
                Log.err("[sonka-dataio] scan failed", t);
                ui.showErrorMessage(Core.bundle.get("client.sonka.dataio.import.fail") + "\n" + t.getMessage());
                return;
            }
            if(!archive.any()){
                archive.close();
                ui.showErrorMessage("@client.sonka.dataio.import.empty");
                return;
            }
            showImportDialog(archive);
        });
    }

    static void showImportDialog(DataImport.Archive archive){
        BaseDialog d = new BaseDialog("@client.sonka.dataio.import.title");
        boolean[] applied = {false};
        d.hidden(() -> {
            if(!applied[0]) archive.close();
        });
        d.addCloseButton();

        EnumSet<DataCategory> sel = EnumSet.noneOf(DataCategory.class);
        boolean[] overwrite = {false};

        String src = archive.isVanilla() ? Core.bundle.get("client.sonka.dataio.import.vanilla")
            : Core.bundle.format("client.sonka.dataio.import.manifest",
                archive.manifest.getString("date", "?"), archive.manifest.getInt("build", 0));
        d.cont.add("[lightgray]" + archive.source.name() + "\n" + src).width(560f).wrap().pad(6f).row();
        if(archive.rejected > 0){
            d.cont.add("[scarlet]" + Core.bundle.format("client.sonka.dataio.import.rejected", archive.rejected)).width(560f).wrap().pad(4f).row();
        }

        d.cont.pane(t -> {
            t.defaults().left().growX().pad(3f);
            for(DataCategory c : DataCategory.all){
                if(!archive.has(c)) continue;
                //моды и прогресс по умолчанию не выбраны - они требуют перезапуска/меняют кампанию
                boolean def = c != DataCategory.mods && c != DataCategory.campaignProgress && c != DataCategory.settings;
                if(def) sel.add(c);
                CheckBox box = new CheckBox(c.title() + "  [lightgray](" + archive.count(c) + ")");
                box.setChecked(def);
                box.changed(() -> {
                    if(box.isChecked()) sel.add(c);
                    else sel.remove(c);
                });
                box.left();
                t.add(box).row();
                t.add(c.description()).color(Color.lightGray).wrap().width(520f).padLeft(34f).padBottom(6f).row();
            }
        }).growX().maxHeight(Core.graphics.getHeight() * 0.45f).row();

        CheckBox ow = new CheckBox(Core.bundle.get("client.sonka.dataio.import.overwrite"));
        ow.changed(() -> overwrite[0] = ow.isChecked());
        ow.left();
        d.cont.add(ow).left().pad(6f).row();
        d.cont.add("@client.sonka.dataio.import.overwrite.desc").color(Color.lightGray).wrap().width(560f).padLeft(34f).row();

        d.buttons.button("@client.sonka.dataio.import.go", Icon.download, () -> {
            if(sel.isEmpty()){
                ui.showInfoFade("@client.sonka.dataio.nothing");
                return;
            }
            if(sel.contains(DataCategory.campaignProgress) && !state.isMenu()){
                ui.showErrorMessage("@client.sonka.dataio.menuonly");
                return;
            }
            Runnable run = () -> {
                applied[0] = true;
                EnumSet<DataCategory> copy = EnumSet.copyOf(sel);
                boolean ow2 = overwrite[0];
                d.hide();
                ui.loadAnd(() -> {
                    DataImport.Result r;
                    try{
                        r = DataImport.apply(archive, copy, ow2);
                    }finally{
                        archive.close();
                    }
                    Core.app.post(() -> showResult(r));
                });
            };
            if(overwrite[0]) ui.showConfirm("@confirm", "@client.sonka.dataio.import.overwrite.confirm", run);
            else run.run();
        }).size(230f, 64f);
        d.show();
    }

    static void showResult(DataImport.Result r){
        BaseDialog d = new BaseDialog("@client.sonka.dataio.result.title");
        d.addCloseButton();
        Table t = d.cont;
        t.defaults().left().pad(3f);
        t.add(Core.bundle.format("client.sonka.dataio.result.summary", r.total(r.imported), r.total(r.replaced), r.total(r.skipped))).row();
        for(DataCategory c : DataCategory.all){
            int i = r.imported.get(c, 0), rp = r.replaced.get(c, 0), s = r.skipped.get(c, 0);
            if(i + rp + s == 0) continue;
            t.add("  " + c.title() + ": [accent]+" + i + "[]  [orange]~" + rp + "[]  [gray]=" + s).row();
        }
        if(r.backups.any()){
            t.add("@client.sonka.dataio.result.backups").padTop(8f).row();
            for(var f : r.backups) t.add("  [lightgray]" + f.name()).row();
        }
        if(r.errors.any()){
            t.add("[scarlet]" + Core.bundle.get("client.sonka.dataio.result.errors")).padTop(8f).row();
            t.pane(p -> {
                p.defaults().left();
                for(String e : r.errors) p.add("  [scarlet]" + e).wrap().width(560f).row();
            }).maxHeight(200f).growX().row();
        }
        if(r.needsRestart){
            t.add("@client.sonka.dataio.result.restart").color(Color.gold).wrap().width(560f).padTop(8f).row();
            d.buttons.button("@client.sonka.dataio.restart.now", Icon.refresh, () -> {
                Core.settings.forceSave();
                restart();
            }).size(230f, 64f);
        }
        d.show();
    }

    /** Перезапуск как у mods.reload() этого форка; при выключенном autorestart - просто выход. */
    public static void restart(){
        if(Core.settings.getBool("autorestart", true)) ClientUtils.restartGame();
        else Core.app.exit();
    }
}
