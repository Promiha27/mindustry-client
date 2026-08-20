package sonkaextras.cursors;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import mindustry.Vars;
import mindustry.ui.*;
import sonkaextras.cursors.CursorCustomizer.*;

import java.io.*;
import java.nio.charset.*;
import java.util.zip.*;

import static mindustry.Vars.ui;

/**
 * Импорт/экспорт паков курсоров (zip) - перенос всего набора между установками/машинами
 * (прецедент: eui SchematicsImportExport, тот же builder FileChooser + ZipOutputStream/ZipFi).
 * <p>
 * Формат пака (версия 1):
 * <pre>
 * cursors.json                - манифест:
 *   {
 *     "version": 1,
 *     "scale": 100,           - масштаб в процентах (20..200)
 *     "cursors": {
 *       "arrow": {"tint": "ff4455ff", "hotx": 2, "hoty": 3, "custom": true},
 *       "drill": {"custom": true},
 *       "target": {"tint": "00ff00ff"}
 *     }
 *   }
 * &lt;слот&gt;.png              - кастомная текстура для слотов с "custom": true
 * </pre>
 * tint - hex rrggbbaa (формат {@code Color.toString()}), hotx/hoty - хотспот в пикселях
 * ИСХОДНОЙ текстуры (отсутствуют = центр, как у ванили). Слоты, которых в манифесте нет,
 * при импорте сбрасываются к ванили: пак - полный снимок набора, а не дифф.
 * <p>
 * Импорт валидирует всё ДО каких-либо изменений (манифест парсится, каждый PNG декодируется и
 * проверяется лимитом {@link CursorCustomizer#MAX_SOURCE_SIZE}) и предварительно снимает бэкап
 * текущего набора в <data>/cursors-backup-&lt;ts&gt;.zip.
 */
public final class CursorPackIO{
    static final int VERSION = 1;

    /** Провалидированная запись одного слота из пака (собирается ДО применения). */
    static final class Entry{
        @Nullable byte[] png;
        @Nullable Integer tint;
        int hotspot = -1;
    }

    private CursorPackIO(){
    }

    public static void export(Fi out) throws IOException{
        Jval root = Jval.newObject();
        root.put("version", VERSION);
        root.put("scale", CursorCustomizer.scalePercent());

        Jval cursors = Jval.newObject();
        Seq<Slot> customSlots = new Seq<>();
        for(Slot s : CursorCustomizer.slots){
            Jval e = Jval.newObject();
            Color tint = CursorCustomizer.tint(s);
            if(tint != null) e.put("tint", tint.toString());
            int hs = Core.settings.getInt(CursorCustomizer.hotspotKey(s), -1);
            if(hs != -1){
                e.put("hotx", (int)Point2.x(hs));
                e.put("hoty", (int)Point2.y(hs));
            }
            if(CursorCustomizer.customFile(s).exists()){
                e.put("custom", true);
                customSlots.add(s);
            }
            cursors.put(s.name, e);
        }
        root.put("cursors", cursors);

        try(OutputStream os = out.write(false); ZipOutputStream zos = new ZipOutputStream(os)){
            zos.putNextEntry(new ZipEntry("cursors.json"));
            zos.write(root.toString(Jval.Jformat.formatted).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            for(Slot s : customSlots){
                zos.putNextEntry(new ZipEntry(s.name + ".png"));
                zos.write(CursorCustomizer.customFile(s).readBytes());
                zos.closeEntry();
            }
        }
    }

    public static void importPack(Fi in, boolean backup) throws IOException{
        //ZipFi нужен реальный файл на диске - копия во временную папку игры (паттерн eui)
        Fi tmp = Vars.tmpDirectory.child("cursor-pack-import.zip");
        in.copyTo(tmp);
        try{
            ZipFi zip = new ZipFi(tmp);

            Fi json = zip.child("cursors.json");
            if(!json.exists()) throw new IOException("cursors.json missing - not a cursor pack");
            Jval root;
            try{
                root = Jval.read(json.readString());
            }catch(Throwable t){
                throw new IOException("broken cursors.json: " + t.getMessage());
            }
            Jval cursors = root.get("cursors");
            if(cursors == null || !cursors.isObject()) throw new IOException("invalid cursors.json: no \"cursors\" object");

            //валидация ВСЕГО (png + tint + hotspot из json) строго до каких-либо изменений:
            //битый пак либо отклоняется целиком, либо набор применяется целиком - полуимпорта нет
            ObjectMap<String, Entry> entries = new ObjectMap<>();
            int scale = -1;
            try{
                if(root.has("scale")) scale = Mathf.clamp(root.getInt("scale", 100), CursorCustomizer.MIN_PERCENT, CursorCustomizer.MAX_PERCENT);
            }catch(Throwable t){
                throw new IOException("invalid \"scale\" in cursors.json");
            }
            for(Slot s : CursorCustomizer.slots){
                Jval e = cursors.get(s.name);
                if(e == null || !e.isObject()) continue;
                Entry entry = new Entry();
                try{
                    if(e.getBool("custom", false)){
                        Fi png = zip.child(s.name + ".png");
                        if(!png.exists()) throw new IOException("missing " + s.name + ".png declared in cursors.json");
                        byte[] bytes = png.readBytes();
                        Pixmap p;
                        try{
                            p = new Pixmap(bytes);
                        }catch(Throwable t){
                            throw new IOException(s.name + ".png is not a valid PNG");
                        }
                        boolean ok = p.width > 0 && p.height > 0
                            && p.width <= CursorCustomizer.MAX_SOURCE_SIZE && p.height <= CursorCustomizer.MAX_SOURCE_SIZE;
                        p.dispose();
                        if(!ok) throw new IOException(s.name + ".png has unsupported size (max " + CursorCustomizer.MAX_SOURCE_SIZE + "px per side)");
                        entry.png = bytes;
                    }
                    String tint = e.getString("tint", null);
                    if(tint != null) entry.tint = Color.valueOf(tint).rgba();
                    int hx = e.getInt("hotx", -1), hy = e.getInt("hoty", -1);
                    if(hx >= 0 && hy >= 0 && hx <= CursorCustomizer.MAX_SOURCE_SIZE && hy <= CursorCustomizer.MAX_SOURCE_SIZE){
                        entry.hotspot = Point2.pack(hx, hy);
                    }
                }catch(IOException ioe){
                    throw ioe;
                }catch(Throwable t){
                    throw new IOException("invalid entry for cursor \"" + s.name + "\": " + t.getMessage());
                }
                entries.put(s.name, entry);
            }

            if(backup){
                try{
                    export(Vars.dataDirectory.child("cursors-backup-" + System.currentTimeMillis() + ".zip"));
                }catch(Exception e){
                    Log.err("[sonka-cursors] backup before import failed, importing anyway", e);
                }
            }

            //применение: пак - полный снимок; слоты без записи в манифесте сбрасываются к ванили
            CursorCustomizer.cursorsDir().mkdirs();
            if(scale != -1) Core.settings.put(CursorCustomizer.scaleKey, scale);
            for(Slot s : CursorCustomizer.slots){
                Entry e = entries.get(s.name);
                Fi dst = CursorCustomizer.customFile(s);
                if(e != null && e.png != null) dst.writeBytes(e.png);
                else dst.delete();
                if(e != null && e.tint != null) Core.settings.put(CursorCustomizer.tintKey(s), (int)e.tint);
                else Core.settings.remove(CursorCustomizer.tintKey(s));
                if(e != null && e.hotspot != -1) Core.settings.put(CursorCustomizer.hotspotKey(s), e.hotspot);
                else Core.settings.remove(CursorCustomizer.hotspotKey(s));
            }

            CursorCustomizer.rebuild();
        }finally{
            tmp.delete();
        }
    }

    public static void showExportDialog(){
        FileChooser.save("zip").name("cursor-pack.zip").submit(file -> {
            try{
                export(file);
                ui.showInfoFade("@client.sonka.cursors.export.success");
            }catch(Exception e){
                Log.err("[sonka-cursors] export error", e);
                ui.showErrorMessage(Core.bundle.get("client.sonka.cursors.export.fail") + "\n" + e.getMessage());
            }
        });
    }

    public static void showImportDialog(Runnable done){
        ui.showConfirm("@confirm", "@client.sonka.cursors.import.confirm", () ->
            FileChooser.open("zip").submit(file -> {
                try{
                    importPack(file, true);
                    ui.showInfoFade("@client.sonka.cursors.import.success");
                    if(done != null) done.run();
                }catch(Exception e){
                    Log.err("[sonka-cursors] import error", e);
                    ui.showErrorMessage(Core.bundle.get("client.sonka.cursors.import.fail") + "\n" + e.getMessage());
                }
            })
        );
    }
}
