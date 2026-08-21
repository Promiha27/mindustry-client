package mu;

import arc.scene.event.VisibilityListener;
import arc.scene.ui.layout.Table;
import mindustry.editor.MapResizeDialog;
import mu.MappingUtilitiesMod.MUMod;

/**
 * Снятие лимитов размера карты в диалоге ресайза редактора: 1..Integer.MAX_VALUE вместо
 * ванильных 50..2000 (поля статические, поэтому правятся на месте и откатываются при
 * выключении). Поля ввода ванили ограничены 4 символами - на shown растягиваем до 10, чтобы
 * большие значения вообще можно было набрать. Автор честно предупреждает: «I don't care about
 * you crashing your game» - гигантские карты кладут клиент по памяти, это осознанный bypass.
 */
public class ResizeDialogMod extends MUMod{
    public final MapResizeDialog dialog;
    private final VisibilityListener shownListener;
    private final int oldMinSize, oldMaxSize;

    public ResizeDialogMod(MapResizeDialog dialog){
        this.settingName = "mu_resize_mod";
        this.dialog = dialog;
        oldMinSize = MapResizeDialog.minSize;
        oldMaxSize = MapResizeDialog.maxSize;

        shownListener = new VisibilityListener(){
            @Override
            public boolean shown(){
                setup();
                return false;
            }
        };
    }

    @Override
    public void enable(){
        MapResizeDialog.minSize = 1;
        MapResizeDialog.maxSize = Integer.MAX_VALUE;
        dialog.addListener(shownListener);
    }

    @Override
    public void disable(){
        MapResizeDialog.minSize = oldMinSize;
        MapResizeDialog.maxSize = oldMaxSize;
        dialog.removeListener(shownListener);
    }

    /** Ванильный shown-листенер пересобирает cont раньше нашего (он добавлен первым) - поля уже на месте. */
    private void setup(){
        if(dialog.cont.getChildren().isEmpty()) return;
        if(dialog.cont.getChildren().get(0) instanceof Table t){
            t.getCells().each(c -> c.maxTextLength(10));
        }
    }
}
