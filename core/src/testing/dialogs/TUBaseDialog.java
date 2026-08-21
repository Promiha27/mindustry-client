package testing.dialogs;

import arc.Events;
import mindustry.game.EventType.GameOverEvent;
import mindustry.ui.dialogs.BaseDialog;
import testing.util.TUVars;

/** База диалогов мода: не ставит игру на паузу, пересобирается на shown/resize, закрывается по game over. */
public class TUBaseDialog extends BaseDialog{
    public TUBaseDialog(String title){
        super(title);

        shouldPause = false;
        addCloseButton();
        shown(this::rebuild);
        onResize(this::rebuild);
        shown(() -> TUVars.activeDialog = this);

        Events.on(GameOverEvent.class, e -> hide());
    }

    protected void rebuild(){
    }
}
