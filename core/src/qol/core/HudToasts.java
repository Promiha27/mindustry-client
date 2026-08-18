package qol.core;

import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import mindustry.ui.Styles;

import static mindustry.Vars.ui;

/**
 * Shared top-center "toast" stack for small transient HUD readouts (current zoom, team DPS, ...) - one
 * Table added once to {@code Vars.ui.hudGroup}, roughly under vanilla's own core-items bar (itself
 * top-aligned only, no left()/right(), so it sits horizontally centered at the very top of the screen).
 * Each caller's label becomes just another row instead of independently centering itself and overlapping
 * every other toast at the same spot.
 */
public final class HudToasts{
    static final float TOP_PAD = 165f;

    static Table stack;

    private HudToasts(){
    }

    /** A fresh, empty, non-interactive label appended as the next row - callers own its text/color from here on. */
    public static Label addToast(){
        ensureStack();
        Label label = new Label("");
        label.setStyle(Styles.outlineLabel);
        label.touchable = Touchable.disabled;
        stack.add(label).padTop(4f).row();
        return label;
    }

    static void ensureStack(){
        if(stack != null) return;
        stack = new Table();
        stack.top();
        ui.hudGroup.fill(t -> {
            t.top();
            t.add(stack).padTop(TOP_PAD);
        });
    }
}
