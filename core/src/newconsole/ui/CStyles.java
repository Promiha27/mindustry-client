package newconsole.ui;

import arc.freetype.*;
import arc.freetype.FreeTypeFontGenerator.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.style.*;
import arc.scene.ui.Label.*;
import arc.scene.ui.TextField.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.ui.*;
import newconsole.game.*;

import static arc.Core.*;

/**
 * @author Mnemotechnician
 */
public class CStyles {
    public static Drawable
            scriptbg, filebg,
            playIcon, editIcon, deleteIcon;

    public static TextureRegion
            directory,
            fileAny,
            fileText, fileJs, fileCode, fileImage,
            fileZip, fileJar;

    public static Font mono;
    public static LabelStyle monoLabel;
    public static TextFieldStyle monoArea;

    @SuppressWarnings("CanBeFinal")
    public static Color accent = Color.valueOf("2244ff");

    public static void loadSync() {
        mono = new FreeTypeFontGenerator(Vars.tree.get("fonts/JetBrainsMono-medium.ttf")).generateFont(new FreeTypeFontParameter() {{
            size = ConsoleSettings.fontSize();
            incremental = true;
        }});
        mono.getData().markupEnabled = true;

        scriptbg = Tex.button;

        playIcon = Icon.play.tint(Color.green);
        editIcon = Icon.edit.tint(Color.yellow);
        deleteIcon = Icon.trash.tint(Color.red);

        filebg = ((ScaledNinePatchDrawable) Styles.flatDown).tint(accent);

        directory = atlas.find("newconsole-hardline-folder");
        fileAny = atlas.find("newconsole-hardline-file-unknown");
        fileText = atlas.find("newconsole-hardline-file-text");
        fileJs = atlas.find("newconsole-hardline-file-js");
        fileZip = atlas.find("newconsole-hardline-file-zip");
        fileJar = atlas.find("newconsole-hardline-file-jar");
        fileCode = atlas.find("newconsole-hardline-file-code");
        fileImage = atlas.find("newconsole-hardline-file-image");

        monoLabel = new LabelStyle(Styles.defaultLabel) {{
            font = mono;
        }};
        monoArea = new TextFieldStyle(Styles.defaultField) {{
            font = mono;
            messageFont = mono;
        }};
    }
}
