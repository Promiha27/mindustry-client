package qol.ui;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;

import static arc.Core.graphics;
import static arc.Core.scene;

/**
 * Small draggable HUD icon button - the shape QoL Control's camera-lock/build-pause/quick-chat buttons
 * all share: a single icon that IS its own drag handle (no title bar, unlike {@link QolWindow}), where a
 * click (touch up without moving past {@link #CLICK_SLOP}) fires the action instead of a collapse
 * toggle. Position persists per {@code posId} via {@link Core#settings} and is clamped back onto screen
 * every frame. Uses a private copy of {@code style} (not a shared {@code Styles.*} instance) so callers
 * can freely repaint {@link #btn} (tint, swap the icon via {@code btn.getImage().setDrawable(...)})
 * without corrupting every other button in the game that happens to share the vanilla style.
 */
public class DragIconButton extends Table{
    static final float CLICK_SLOP = 5f;

    public final ImageButton btn;
    final String posId;

    boolean dragging;
    float dragFromX, dragFromY;

    public DragIconButton(String posId, Drawable icon, float size, float defaultX, float defaultY, Runnable onClick){
        this.posId = posId;

        btn = new ImageButton(icon, new ImageButton.ImageButtonStyle(Styles.clearNonei));
        add(btn).size(size);
        pack();

        x = Core.settings.getFloat(posId + ".x", defaultX);
        y = Core.settings.getFloat(posId + ".y", defaultY);

        btn.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float ex, float ey, int pointer, KeyCode button){
                dragFromX = ex;
                dragFromY = ey;
                dragging = false;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float ex, float ey, int pointer){
                float dx = ex - dragFromX, dy = ey - dragFromY;
                if(!dragging && Math.abs(dx) < CLICK_SLOP && Math.abs(dy) < CLICK_SLOP) return;
                dragging = true;
                x = Mathf.clamp(x + dx, 0f, Math.max(0f, graphics.getWidth() - width));
                y = Mathf.clamp(y + dy, 0f, Math.max(0f, graphics.getHeight() - height));
            }

            @Override
            public void touchUp(InputEvent event, float ex, float ey, int pointer, KeyCode button){
                if(dragging){
                    Core.settings.put(posId + ".x", x);
                    Core.settings.put(posId + ".y", y);
                }else{
                    onClick.run();
                }
                dragging = false;
            }
        });

        update(() -> {
            x = Mathf.clamp(x, 0f, Math.max(0f, graphics.getWidth() - width));
            y = Mathf.clamp(y, 0f, Math.max(0f, graphics.getHeight() - height));
        });

        scene.add(this);
    }
}
