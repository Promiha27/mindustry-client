package newconsole.ui.dialogs;

import arc.math.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import newconsole.*;
import newconsole.io.*;
import newconsole.ui.*;

/**
 * Allows the user to run specific scripts upon specific game events.
 * @author Mnemotechnician
 */
public class AutorunDialog extends BaseDialog {
    
    public Class<?> lastEvent = EventType.ClientLoadEvent.class;
    
    public Table list;

    public AutorunDialog() {
        super("@newconsole.autorun-header");
        closeOnBack();

        cont.table(bar -> {
            bar.left();

            bar.button(Icon.exit, Styles.defaulti, this::hide).size(50f);
        }).growX().row();

        cont.stack(
                new Table(listRoot -> {
                    listRoot.top().left().setFillParent(true);

                    listRoot.add(new BetterPane(list -> {
                        list.setBackground(CStyles.scriptbg);

                        this.list = list;
                    })).grow();
                }),

                new Table(addAutorun -> {
                    addAutorun.top().left().setFillParent(true);

                    addAutorun.add(new Spinner("@newconsole.add-event", false, panel -> {
                        panel.setBackground(CStyles.scriptbg);

                        panel.label(() -> lastEvent.getSimpleName()).growX().get().setColor(Pal.accent);
                        panel.row();

                        BetterPane pane = new BetterPane(events -> {
                            AutorunManager.allEvents.each(event -> {
                                events.button(event.getSimpleName(), Styles.defaultt, () -> {
                                    lastEvent = event;
                                }).growX().row();
                            });
                        });

                        panel.add(pane).grow().marginBottom(10f).row();

                        panel.button("@newconsole.save", Styles.defaultt, () -> {
                            String code = ConsoleVars.consoles.get(ConsoleVars.selectConsole).area.getText();

                            if (!code.isEmpty()) {
                                ConsoleVars.getCurrentConsole().autorun.add(lastEvent, code);
                                ConsoleVars.getCurrentConsole().autorun.save();
                                rebuild();
                            } else {
                                Vars.ui.showInfo("@newconsole.empty-script");
                            }
                        }).growX();
                    })).margin(4f).width(350f).with(it -> {
                        it.setStyle(Styles.defaultt);
                        it.unique = false;
                    }).row();
                })
        ).grow();
    }

    @Override
    public Dialog show(Scene stage, Action action) {
        rebuild();
        return super.show(stage, action);
    }

    public void rebuild() {
        list.clearChildren();
        for (var entry : ConsoleVars.getCurrentConsole().autorun.events) addEntry(entry);
    }

    public void addEntry(AutorunManager.AutorunEntry<?> entry) {
        list.table(table -> {
            table.center().left().setBackground(CStyles.scriptbg);

            table.add("[accent]#" + list.getChildren().size).width(40f).padRight(10f);

            table.labelWrap("[gray]" + entry.event.getSimpleName() + " >").width(220f);

            table.add(new CodeSpinner(entry.script)).growX();

            table.table(actions -> {
                actions.defaults().size(50f);

                TextButton toggle = new TextButton(entry.enabled ? "@newconsole.enabled" : "@newconsole.disabled", Styles.defaultt);
                actions.add(toggle).width(80f);
                //fuck java lambdas
                toggle.clicked(() -> {
                    entry.enabled = !entry.enabled;

                    toggle.setText(entry.enabled ? "@newconsole.enabled" : "@newconsole.disabled");
                });

                actions.button(CStyles.editIcon, Styles.defaulti, () -> {
                    ConsoleVars.consoles.get(ConsoleVars.selectConsole).setCode(entry.script);
                    hide();
                });

                actions.button(CStyles.deleteIcon, Styles.defaulti, () -> Vars.ui.showConfirm("@newconsole.delete-confirm", () -> {
                    ConsoleVars.getCurrentConsole().autorun.remove(entry);
                    ConsoleVars.getCurrentConsole().autorun.save();
                    rebuild();
                }));
            }).padLeft(20f);
        }).growX().pad(4f).marginBottom(5f).row();
    }

}
