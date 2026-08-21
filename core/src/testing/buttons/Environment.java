package testing.buttons;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import testing.blui.*;
import testing.ui.*;
import testing.util.*;

import static testing.ui.TUDialogs.*;

public class Environment{
    public static void worldButton(Table t){
        ImageButton b = new ImageButton(TUIcons.weather, TUStyles.tuImageStyle);
        BLElements.boxTooltip(b, "@tu-tooltip.button-world");
        b.clicked(worldDialog::show);
        b.resizeImage(BLVars.iconSize);

        t.add(b);
    }
}
