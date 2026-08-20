package newconsole.io;

import arc.files.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;

/**
 * @author Mnemotechnician
 */
@SuppressWarnings("CanBeFinal")
public class ScriptsManager implements BaseManager{
    public final String name, nameSave, backupSave;
    public StringMap scripts = new StringMap();

    public ScriptsManager(String name){
        this.name = name;
        this.nameSave = Strings.format("newconsole-scripts-@.save", name);
        this.backupSave = Strings.format("newconsole-scripts-@.save.backup", name);
    }

    public void load(){
        Fi file = ManagerObjects.root.child(nameSave).exists() ? ManagerObjects.root.child(nameSave) : ManagerObjects.root.child(backupSave);

        if(!file.exists()){
            Log.warn("Autorun script save file for @ not found or inaccessible.", name);
            return;
        }

        int count = 0;

        //yeah, I did all the funny code just in case of unexpected modifications
        //also this thing will not break if I'll add something else. and I'll definitely do.
        try (var reads = file.reads()) {
            byte b;
            scripts:
            do {
                b = reads.b();
                if (b == ManagerObjects.startScript) {
                    String name = reads.str();
                    //find splitter
                    while ((b = reads.b()) != ManagerObjects.splitter) {
                        if (b == ManagerObjects.endScript) {
                            Log.warn("illegal EOS, skipping");
                            continue scripts;
                        } else if (b == ManagerObjects.eof) {
                            Log.warn("Illegal end of file: splitter and script body expected");
                            return;
                        }
                    }

                    String script = reads.str();
                    scripts.put(name, script);
                    count++;

                    //find end of script, just in case
                    while ((b = reads.b()) != ManagerObjects.endScript) {
                        if (b == ManagerObjects.eof) {
                            Log.warn("EOS expected, found eof. Ignoring.");
                            break scripts;
                        }
                    }
                }
            } while (b != ManagerObjects.eof);

            Log.info("NewConsole (@): Loaded @ script(s).", name, count);
        } catch (Exception e) {
            Log.warn("Failed to read existing save file. Illegal modification?");
        }
    }

    public void save() {
        //backup
        Fi savef = ManagerObjects.root.child(nameSave);
        if (savef.exists()) {
            savef.moveTo(ManagerObjects.root.child(backupSave));
        }
        //save
        var writes = ManagerObjects.root.child(nameSave).writes();
        scripts.each((name, script) -> {
            writes.b(ManagerObjects.startScript);
            writes.str(name);
            writes.b(ManagerObjects.splitter);
            writes.str(script);
            writes.b(ManagerObjects.endScript);
        });
        writes.b(ManagerObjects.eof);
        writes.close();
    }

    public void eachScript(Cons2<String, String> cons) {
        scripts.each(cons);
    }

    public void saveScript(String name, String script) {
        scripts.put(name, script);
        save();
    }

    public void deleteScript(String name) {
        scripts.remove(name);
        save();
    }

}
