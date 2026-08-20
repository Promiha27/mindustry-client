package newconsole.ui.dialogs;

import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import newconsole.*;
import newconsole.io.*;
import newconsole.ui.*;

import java.util.*;

/**
 * @author Mnemotechnician
 */
public class SavesDialog extends BaseDialog {

    public Table scriptsTable;
    public TextField saveName;

    public SavesDialog() {
        super("@newconsole.scripts-header");
        closeOnBack();

        cont.table(save -> {
            save.button("@newconsole.save", Styles.defaultt, () -> {
                Console console = ConsoleVars.getCurrentConsole();

                String name = saveName.getText();
                if (name.replaceAll("\\s", "").isEmpty()) {
                    Vars.ui.showInfo("@newconsole.empty-name");
                    return;
                }

                String script = console.area.getText();
                if (script.replaceAll("\\s", "").isEmpty()) {
                    Vars.ui.showInfo("@newconsole.empty-script");
                    return;
                }

                if (console.scripts.scripts.containsKey(name)) {
                    //Overwrite, ask the player to confirm
                    Vars.ui.showConfirm("@newconsole.overwrite-confirm", () -> {
                        console.scripts.saveScript(name, script);
                        rebuild();
                    });
                } else {
                    console.scripts.saveScript(name, script);
                    rebuild();
                }
            }).width(90).get();

            saveName = save.field("", input -> {
            }).growX().get();
            saveName.setMessageText("@newconsole.input-name");
        }).growX().marginBottom(40).row();

        cont.add(new BetterPane(table -> scriptsTable = table)).grow().row();

        cont.button("@newconsole.close", Styles.defaultt, this::hide).growX();
    }

    public void rebuild() {
        scriptsTable.clearChildren();
        Console console = ConsoleVars.getCurrentConsole();

        // copy to a seq, then sort by name - fuck java.
        Seq<Pair<String, String>> seq = new Seq<>(console.scripts.scripts.size);
        console.scripts.eachScript((name, script) -> seq.add(new Pair<>(name, script)));

        seq.sort(new EntryComparator());

        seq.each(it -> add(it.first, it.second));
    }

    @Override
    public Dialog show(Scene stage, Action action) {
        rebuild();
        return super.show(stage, action);
    }

    public void add(String name, String script) {
        scriptsTable.table(entry -> {
            entry.center().left().setBackground(CStyles.scriptbg);
            entry.labelWrap(name).width(250).marginLeft(20);

            entry.add(new CodeSpinner(script)).growX();

            entry.table(actions -> {
                Console console = ConsoleVars.getCurrentConsole();

                actions.center().right().defaults().center().size(50);

                actions.button(CStyles.playIcon, Styles.defaulti, () -> ConsoleVars.getCurrentConsole().runConsole(script));

                actions.button(CStyles.editIcon, Styles.defaulti, () -> {
                    ConsoleVars.getCurrentConsole().setCode(script);
                    hide();
                });

                actions.button(CStyles.deleteIcon, Styles.defaulti, () -> Vars.ui.showConfirm("@newconsole.delete-confirm", () -> {
                    console.scripts.deleteScript(name);
                    scriptsTable.removeChild(entry);
                }));
            });
        }).growX().pad(2f).marginBottom(20).row();
    }

    /**
     * Same as kotlin.Pair.
     */
    public static class Pair<A, B> {
        final A first;
        final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }
    }

    public static class EntryComparator implements Comparator<Pair<String, String>> {
        @Override
        public int compare(Pair<String, String> o1, Pair<String, String> o2) {
            if (o1 == o2) return 0;

            var left = Strings.stripColors(o1.first);
            var right = Strings.stripColors(o2.first);

            for (int i = 0; i < Math.min(left.length(), right.length()); i++) {
                var diff = Character.toLowerCase(left.charAt(i)) - Character.toLowerCase(right.charAt(i));
                if (diff != 0) return diff;
            }

            if (left.length() > right.length()) return -1;
            return 1;
        }

        @Override
        public boolean equals(Object o) {
            return o == this;
        }
    }

}
