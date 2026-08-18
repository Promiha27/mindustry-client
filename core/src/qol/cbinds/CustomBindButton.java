package qol.cbinds;

import arc.func.Boolp;
import arc.func.Cons;
import arc.func.Floatc2;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Button;
import arc.scene.ui.layout.Table;

import static arc.Core.graphics;
import static arc.Core.scene;

/**
 * One live custom-bind button on the HUD - see {@link CustomBindsFeature} for the config model. Drag
 * tracking lives in a plain {@link InputListener} kept separate from the {@link Button}'s own native
 * click handling (added via the {@code button(Cons, ButtonStyle, Runnable)} call below): both listeners
 * receive every touch event independently, so locking (disabling the drag listener's {@code touchDown})
 * never blocks the button's own click - only dragging.
 */
class CustomBindButton extends Table{
    static final float CLICK_SLOP = 5f;

    boolean dragging;
    float dragFromX, dragFromY;

    CustomBindButton(Cons<Button> content, Button.ButtonStyle style, float width, float height,
                      float startX, float startY, Runnable onClick, Boolp locked, Floatc2 onMoved){
        Button btn = button(content, style, () -> {
            if(!dragging) onClick.run();
        }).size(width, height).get();
        pack();

        x = startX;
        y = startY;

        btn.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float ex, float ey, int pointer, KeyCode button){
                if(locked.get()) return false;
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
                if(dragging) onMoved.get(x, y);
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
