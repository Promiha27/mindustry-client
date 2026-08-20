package newconsole.io;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import newconsole.game.*;

/**
 * @author Mnemotechnician
 */
@SuppressWarnings("unused")
public class AutorunManager implements BaseManager{
    public static final Seq<Class<?>> allEvents = new Seq<>();

    public final String name, nameSave, backupSave;
    public final Seq<AutorunEntry<?>> events = new Seq<>();

    //???
    public Func3<String, String, Object, String> runner;

    public AutorunManager(String name){
        this.name = name;
        this.nameSave = Strings.format("newconsole-autorun-@.save", name);
        this.backupSave = Strings.format("newconsole-autorun-@.save.backup", name);
    }

    static {
        findEvents();
    }

    /**
     * Loads events from the save file for this manager. Returns whether the load was successful.
     */
    public void load() {
        Fi file = ManagerObjects.root.child(nameSave).exists() ? ManagerObjects.root.child(nameSave) : ManagerObjects.root.child(backupSave);
        
        if(!file.exists()){
            Log.warn("Autorun script save file for @ not found or inaccessible.", name);
            return;
        }

        int count = 0;

        try (var reads = file.reads()) {
            int b;

            outer:
            while ((b = reads.b()) != ManagerObjects.eof) {
                //find sos
                if (b != ManagerObjects.startScript) {
                    if (b == ManagerObjects.splitter || b == ManagerObjects.endScript) {
                        Log.warn("Unexpected character " + b + ", ignoring.");
                    }
                    continue;
                }

                //read class
                String className = reads.str();
                Class<?> clazz;
                try {
                    clazz = Class.forName(className, true, Vars.mods.mainLoader());
                } catch (ClassNotFoundException e) {
                    Log.warn("Unknown class " + className + ". Skipping.");
                    continue;
                }

                //find ManagerObjects.splitter
                while ((b = reads.b()) != ManagerObjects.splitter) {
                    if (b == ManagerObjects.startScript || b == ManagerObjects.endScript) {
                        Log.warn("unexpected character " + b + ", ignoring this entry.");
                        continue;
                    }
                    if (b == ManagerObjects.eof) break outer;
                }

                //read script
                String script = reads.str();

                //construct the entry
                var entry = add(clazz, script);
                entry.enabled = false; //you wouldn't want mindustry to fall into a bootloop, would you?
                count++;

                //find eos, just in case
                while ((b = reads.b()) != ManagerObjects.endScript) {
                    if (b == ManagerObjects.eof) break outer;
                }
            }

            Log.info("NewConsole (@): Loaded @ autorun script(s).", name, count);
        } catch (Exception e) {
            Log.err("Couldn't read events file (" + file.absolutePath() + "). illegal modification?", e);
        }

    }

    /**
     * Saves the events into a file and creates a backup of the previous save
     */
    public void save() {
        if (ManagerObjects.root.child(nameSave).exists()) {
            ManagerObjects.root.child(nameSave).moveTo(ManagerObjects.root.child(backupSave));
        }

        var writes = ManagerObjects.root.child(nameSave).writes();
        events.each(entry -> {
            writes.b(ManagerObjects.startScript);
            writes.str(entry.event.getName());
            writes.b(ManagerObjects.splitter);
            writes.str(entry.script);
            writes.b(ManagerObjects.endScript);
        });
        writes.b(ManagerObjects.eof);
        writes.close();
    }

    public <T> AutorunEntry<T> add(Class<T> event, final String script) {
        var entry = new AutorunEntry<>(event, script);

        Cons<T> cons = it -> {
            if (entry.enabled) {
                //add temporary variable that references the event object
                String variable = "autorun_event_obj" + Mathf.random(999999);
                String res = runner.get(script, variable, it);

                if(ConsoleSettings.logAutorunOutput()) Log.info(res);
            }
        };

        entry.cons = cons;
        Events.on(event, cons);
        events.add(entry);

        return entry;
    }

    public void remove(String script) {
        remove(events.find(e -> e.script.equals(script)));
    }

    public <T> void remove(AutorunEntry<T> entry) {
        Events.remove(entry.event, entry.cons);
        events.remove(entry);
    }

    /**
     * Adds all default events to the seq
     */
    private static void findEvents() {
        var classes = EventType.class.getDeclaredClasses();
        for (var event : classes) {
            if (!event.isEnum()) {
                allEvents.add(event);
            }
        }
    }

    public static class AutorunEntry<T>{

        public final Class<T> event;
        public final String script;
        public Cons<T> cons;
        public boolean enabled = true;

        public AutorunEntry(Class<T> event, String script) {
            this.event = event;
            this.script = script;
        }

    }

}
