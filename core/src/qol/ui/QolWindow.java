package qol.ui;

import arc.Core;
import arc.graphics.*;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.event.*;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.Vars;
import mindustry.gen.Iconc;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

import static arc.Core.graphics;
import static arc.Core.scene;

/**
 * Small draggable, collapsible HUD panel in the same visual language MI2 Utilities' "Mindow2" windows
 * use (Styles.black6/black3 panels, a drag handle that also toggles collapse on a plain click). Click
 * and release without moving the mouse more than {@link #CLICK_SLOP} pixels toggles {@link #minimized};
 * dragging moves the window instead. Position and collapsed state persist per {@link #prefId} via
 * {@link Core#settings}, and the window is clamped back onto the screen every frame so a game window
 * resize can never strand it off-screen.
 * <p>
 * Subclasses must call {@link #rebuild()} themselves at the end of their own constructor, once their
 * own fields are set - this constructor deliberately does NOT call it. {@link #rebuild()} dispatches
 * virtually to the subclass's {@link #setupCont}/{@link #setupTitleButtons} overrides, which typically
 * read subclass fields; those fields are still null while this constructor (the {@code super(...)} call)
 * is running, since a subclass's own field initializers and constructor body only run after
 * {@code super(...)} returns. Calling it from here crashed with an NPE the first time a window read one
 * of its own fields in {@code setupCont}.
 */
public class QolWindow extends Table{
    static final float CLICK_SLOP = 5f;
    static final int SIZE_MIN = 60, SIZE_MAX = 160, SIZE_STEP = 10;

    /** Every currently-attached QolWindow, so a drag in progress can push itself out of the others - see {@link #resolveCollisions}. */
    static final Seq<QolWindow> allWindows = new Seq<>();

    public final String prefId;
    public boolean hasTitle = true;
    public boolean minimized;
    /**
     * Uniform size multiplier, driven by a slider in Settings (see {@link #buildSizeSetting}), not
     * anything in the window itself. Applied through {@link #setScale} (needs {@link #setTransform} on,
     * see {@link #rebuild}) rather than resizing the table's own cells - {@code width}/{@code height}
     * stay the natural packed size and {@code scaleX}/{@code scaleY} scale on top of that at draw/hit-test
     * time, which is exactly what {@link #resolveCollisions} and the on-screen clamp below already
     * multiply by - both were written scale-aware from the start, so this needed no changes there.
     */
    public float sizeScale = 1f;

    protected final Table titleBar = new Table(Styles.black6);
    protected final Table titlePane = new Table();
    protected final Table titleExtras = new Table();
    protected final Table cont = new Table(Styles.black3);

    protected boolean dragging;
    protected float dragFromX, dragFromY;

    public QolWindow(String prefId, String titleKey){
        this.prefId = prefId;
        this.name = prefId;

        titlePane.left();
        titlePane.add(Iconc.move + " " + Core.bundle.get(titleKey, titleKey)).color(Color.lightGray).left().padLeft(4f).growX().labelAlign(Align.left);

        if(prefId != null && !prefId.isEmpty()) load();

        setupDrag();

        //Vanilla HUD elements (HudFragment.build) each tie their own .visible(...) to
        //HudFragment.shown, toggled by Binding.toggleMenus (default key: C) - nothing did that for
        //these windows, since they're added straight to Core.scene rather than through hudfrag, so
        //pressing C hid every vanilla panel but left these on screen untouched.
        visible(() -> Vars.ui.hudfrag.shown);

        update(() -> {
            float maxX = Math.max(0f, graphics.getWidth() - width * scaleX);
            float maxY = Math.max(0f, graphics.getHeight() - height * scaleY);
            x = Mathf.clamp(x, 0f, maxX);
            y = Mathf.clamp(y, 0f, maxY);
        });
    }

    /** Override to add content shown while the window isn't collapsed. */
    protected void setupCont(Table cont){
    }

    /**
     * Override to add extra buttons (settings gear, etc.) to the right of the title text. These live in
     * a table that is a SIBLING of the draggable title label, not a child of it - a child button would
     * still receive its own click, but the drag/collapse {@link InputListener} on the label also listens
     * for events bubbling up from anywhere inside it, so a button nested there would spuriously toggle
     * the window's collapsed state on every click. Bridge To Core's own Toolbar avoids the same trap by
     * keeping its drag handle and its mode buttons as separate rows; this keeps them as separate cells
     * in the same row instead, so they still share the title bar visually.
     */
    protected void setupTitleButtons(Table titleExtras){
    }

    public void rebuild(){
        setTransform(true);
        setScale(sizeScale);

        clear();
        titleBar.clear();
        titleExtras.clear();
        titleBar.add(titlePane).growX();
        setupTitleButtons(titleExtras);
        titleBar.add(titleExtras);
        if(hasTitle) add(titleBar).growX().row();
        if(!minimized){
            cont.clear();
            setupCont(cont);
            add(cont).growX();
        }
        pack();
    }

    void setupDrag(){
        titlePane.addListener(new InputListener(){
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
                resolveCollisions(x + dx, y + dy);
            }

            @Override
            public void touchUp(InputEvent event, float ex, float ey, int pointer, KeyCode button){
                if(dragging){
                    toFront();
                }else{
                    minimized = !minimized;
                    rebuild();
                }
                save();
                dragging = false;
            }
        });
    }

    public void attach(){
        scene.add(this);
        toFront();
        if(!allWindows.contains(this)) allWindows.add(this);
        if(prefId != null && !prefId.isEmpty()) Core.settings.put(prefId + ".qolwin2.shown", true);
    }

    public boolean attached(){
        return hasParent();
    }

    public void detach(){
        remove();
        allWindows.remove(this);
        if(prefId != null && !prefId.isEmpty()) Core.settings.put(prefId + ".qolwin2.shown", false);
    }

    /**
     * Moves this window toward (nx, ny), like MI2 Utilities' Mindow2 windows do, but pushed just far
     * enough out of any other attached window's bounding box that the two no longer overlap - dragging
     * one panel onto another shoves it aside instead of stacking on top, which is exactly the situation
     * that made Control Helper's panel look unresponsive after the fix for the "all windows spawn on
     * top of each other" bug: nothing stopped a window from being dragged right back into another one's
     * way. Resolves against every other window in a few passes (not just one) so a nudge away from one
     * neighbor that lands inside a second neighbor's bounds gets corrected too, instead of only ever
     * considering the first overlap found.
     */
    void resolveCollisions(float nx, float ny){
        float w = width * scaleX, h = height * scaleY;

        for(int pass = 0; pass < 3; pass++){
            boolean anyOverlap = false;

            for(QolWindow other : allWindows){
                if(other == this || !other.attached()) continue;

                float ow = other.width * other.scaleX, oh = other.height * other.scaleY;
                boolean overlapX = nx < other.x + ow && nx + w > other.x;
                boolean overlapY = ny < other.y + oh && ny + h > other.y;
                if(!overlapX || !overlapY) continue;

                anyOverlap = true;

                //shortest distance to clear the overlap by moving out along each side of each axis
                float pushRight = other.x + ow - nx;
                float pushLeft = nx + w - other.x;
                float pushUp = other.y + oh - ny;
                float pushDown = ny + h - other.y;
                float minXPush = Math.min(pushRight, pushLeft);
                float minYPush = Math.min(pushUp, pushDown);

                //resolve along whichever axis needs the smaller nudge, same idea as a standard AABB
                //separation - moving along that axis alone is enough to clear this pair, and disturbs
                //the window's position less than pushing along both axes at once would
                if(minXPush < minYPush){
                    nx += (pushRight < pushLeft) ? pushRight : -pushLeft;
                }else{
                    ny += (pushUp < pushDown) ? pushUp : -pushDown;
                }
            }

            if(!anyOverlap) break;
        }

        x = nx;
        y = ny;
    }

    /** Attaches this window if it was shown last session (default: shown). Call once after construction. */
    public void restoreShown(){
        if(prefId == null || prefId.isEmpty() || Core.settings.getBool(prefId + ".qolwin2.shown", true)) attach();
    }

    void save(){
        if(prefId == null || prefId.isEmpty()) return;
        Core.settings.put(prefId + ".qolwin2.x", x);
        Core.settings.put(prefId + ".qolwin2.y", y);
        Core.settings.put(prefId + ".qolwin2.min", minimized);
    }

    void load(){
        x = Core.settings.getFloat(prefId + ".qolwin2.x", defaultX());
        y = Core.settings.getFloat(prefId + ".qolwin2.y", defaultY());
        minimized = Core.settings.getBool(prefId + ".qolwin2.min", false);
        sizeScale = Core.settings.getInt(sizeSettingKey(), 100) / 100f;
    }

    String sizeSettingKey(){
        return "qolwin-size-" + prefId;
    }

    /**
     * A size slider for this window, meant to be added to the shared QoL Suite settings category (see
     * {@code QolSuiteMod.buildSettings}) rather than living inside the window itself - a +/- stepper in
     * the title bar was the first version of this, but a slider in Settings is both more precise and
     * doesn't clutter every single window's title bar with extra buttons.
     */
    public void buildSizeSetting(SettingsTable table){
        table.sliderPref(sizeSettingKey(), 100, SIZE_MIN, SIZE_MAX, SIZE_STEP, v -> v + "%", v -> {
            sizeScale = v / 100f;
            setScale(sizeScale);
        });
    }

    /**
     * Where this window spawns before it's ever been dragged. Every window used the same hardcoded
     * (20, height - 80) here originally - since nothing had a saved position yet on first launch, the
     * hub and every feature window all spawned stacked exactly on top of each other. Whichever ended up
     * frontmost (last attached) ate every click for that whole screen region; the windows underneath
     * looked crooked/jumbled and never received input at all - that's what looked like "buttons don't
     * work". Subclasses should override this pair so each window gets a distinct starting spot; the
     * user can still drag them anywhere afterward, same as before.
     */
    protected float defaultX(){
        return 20f;
    }

    protected float defaultY(){
        return graphics.getHeight() - 80f;
    }
}
