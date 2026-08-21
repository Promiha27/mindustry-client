package testing.ui;

import arc.scene.style.Drawable;
import arc.scene.ui.Button.ButtonStyle;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.TextButton.TextButtonStyle;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import testing.blui.HoldImageButton.HoldImageButtonStyle;

import static arc.Core.atlas;
import static arc.graphics.Color.gray;
import static arc.graphics.Color.white;

/** Стили мода: скруглённые слева/по центру/справа кнопки (nine-patch'и из core/assets/testing/ui) + стили HoldImageButton. */
public class TUStyles{
    public static Drawable
    buttonLeft, buttonLeftDown, buttonLeftOver,
    buttonCenter, buttonCenterDown, buttonCenterOver, buttonCenterDisabled,
    buttonRight, buttonRightOver, buttonRightDown,
    paneBottom;
    public static ButtonStyle right;
    public static TextButtonStyle round, toggleCentert;
    public static ImageButtonStyle
    tuImageStyle,
    togglei,
    lefti, toggleLefti,
    righti, toggleRighti,
    centeri;

    public static HoldImageButtonStyle
    tuHoldImageStyle,
    teamChanger;

    private TUStyles(){
    }

    public static void init(){
        buttonLeft = atlas.drawable("test-utils-button-left");
        buttonLeftDown = atlas.drawable("test-utils-button-left-down");
        buttonLeftOver = atlas.drawable("test-utils-button-left-over");
        buttonCenter = atlas.drawable("test-utils-button-center");
        buttonCenterDown = atlas.drawable("test-utils-button-center-down");
        buttonCenterOver = atlas.drawable("test-utils-button-center-over");
        buttonCenterDisabled = atlas.drawable("test-utils-button-center-disabled");
        buttonRight = atlas.drawable("test-utils-button-right");
        buttonRightDown = atlas.drawable("test-utils-button-right-down");
        buttonRightOver = atlas.drawable("test-utils-button-right-over");
        paneBottom = atlas.drawable("test-utils-pane-bottom");

        right = new ButtonStyle(Styles.defaultb){{
            up = buttonRight;
            down = buttonRightDown;
            over = buttonRightOver;
        }};

        round = new TextButtonStyle(Styles.defaultt){{
            checked = up;
        }};

        toggleCentert = new TextButtonStyle(Styles.defaultt){{
            up = buttonCenter;
            down = buttonCenterDown;
            over = buttonCenterOver;
            checked = buttonCenterOver;
            disabled = buttonCenterDisabled;
        }};

        tuImageStyle = new ImageButtonStyle(Styles.logici){{
            down = Styles.flatDown;
            over = Styles.flatOver;
            imageDisabledColor = gray;
            imageUpColor = white;
        }};

        togglei = new ImageButtonStyle(Styles.defaulti){{
            checked = Tex.buttonOver;
        }};

        lefti = new ImageButtonStyle(Styles.defaulti){{
            up = buttonLeft;
            down = buttonLeftDown;
            over = buttonLeftOver;
        }};

        toggleLefti = new ImageButtonStyle(lefti){{
            checked = buttonLeftOver;
        }};

        righti = new ImageButtonStyle(Styles.defaulti){{
            up = buttonRight;
            down = buttonRightDown;
            over = buttonRightOver;
        }};

        toggleRighti = new ImageButtonStyle(righti){{
            checked = buttonRightOver;
        }};

        centeri = new ImageButtonStyle(Styles.defaulti){{
            up = buttonCenter;
            down = buttonCenterDown;
            over = buttonCenterOver;
        }};

        tuHoldImageStyle = new HoldImageButtonStyle(tuImageStyle);

        teamChanger = new HoldImageButtonStyle(Styles.clearNoneTogglei){{
            down = Tex.whiteui;
            checked = Tex.whiteui;
        }};
    }
}
