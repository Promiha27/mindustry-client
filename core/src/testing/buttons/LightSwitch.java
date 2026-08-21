package testing.buttons;

import arc.graphics.*;
import arc.scene.ui.layout.*;
import testing.blui.*;
import testing.ui.*;
import testing.util.*;

import static mindustry.Vars.*;

public class LightSwitch{
    public static void lightButton(Table t){
        HoldImageButton b = new HoldImageButton(TUIcons.lightOff, TUStyles.tuHoldImageStyle);
        b.clicked(() -> state.rules.lighting = !state.rules.lighting);
        b.held(() -> ui.picker.show(state.rules.ambientLight, true, res -> state.rules.ambientLight.set(res)));
        b.resizeImage(BLVars.iconSize);

        BLElements.boxTooltip(b, "@tu-tooltip.button-light");
        b.getStyle().imageChecked = TUIcons.lightOn;
        b.getStyle().imageCheckedColor = new Color();

        b.update(() -> {
            b.setChecked(state.rules.lighting);
            if(b.isChecked()){
                b.getStyle().imageCheckedColor.set(state.rules.ambientLight).a(1);
            }
        });

        t.add(b);
    }
}
