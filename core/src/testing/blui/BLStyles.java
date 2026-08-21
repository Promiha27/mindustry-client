package testing.blui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import mindustry.ui.Styles;
import testing.blui.HoldImageButton.HoldImageButtonStyle;

public class BLStyles{
    public static ImageButtonStyle bluiImageStyle;
    public static HoldImageButtonStyle defaultHoldi, bluiHoldImageStyle;

    private BLStyles(){
    }

    public static void init(){
        if(bluiImageStyle != null) return;

        bluiImageStyle = new ImageButtonStyle(Styles.logici){{
            down = Styles.flatDown;
            over = Styles.flatOver;
            imageDisabledColor = Color.gray;
            imageUpColor = Color.white;
        }};

        defaultHoldi = new HoldImageButtonStyle(Styles.defaulti);
        Core.scene.addStyle(HoldImageButtonStyle.class, defaultHoldi);

        bluiHoldImageStyle = new HoldImageButtonStyle(bluiImageStyle);
    }
}
