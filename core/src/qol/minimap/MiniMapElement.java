package qol.minimap;

import arc.Core;
import arc.func.Floatc2;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Timer;
import mindustry.game.Teams.TeamData;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.ui.Fonts;

import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.renderer;
import static mindustry.Vars.state;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * The actual minimap square: draws the engine's own minimap texture ({@link renderer#minimap}, the
 * same one the vanilla full-map fragment uses - this widget hides itself whenever that fragment is open,
 * see {@link MiniMapFeature}, so the two never fight over the shared region/zoom state in the same
 * frame) plus live unit/player markers and the camera viewport box, ported from QoL Control's
 * {@code !cmap}.
 * <p>
 * Input is three-way: a right-click, or a left-press held {@link #LONG_PRESS}s without moving, pans the
 * camera continuously while held (via {@link mindustry.input.InputHandler#panCamera}, which on desktop
 * also flips {@code DesktopInput.panning} so the vanilla camera-follow doesn't fight it back);
 * left-drag past {@link #CLICK_SLOP} moves this whole widget instead; a plain left click (no movement)
 * toggles the vanilla full-screen map.
 */
class MiniMapElement extends Element{
    static final float LONG_PRESS = 0.4f;
    static final float CLICK_SLOP = 5f;
    static final float CACHE_INTERVAL = 30f; //ticks between unit-list rescans

    final Seq<Unit> unitCache = new Seq<>();
    float cacheTimer = CACHE_INTERVAL; //force an immediate first scan

    boolean unitOutline;
    boolean sortByHp = true;
    boolean showNames; //false = eye icon, true = player name
    float unitSize = 24f;

    boolean panning, moving;
    float pressX, pressY, dragOffsetX, dragOffsetY;
    Timer.Task pressTask;

    /** Fired with the new absolute stage position while the widget (the whole owning Table) is being dragged. */
    Floatc2 onMoved = (x, y) -> {
    };

    MiniMapElement(){
        addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float ex, float ey, int pointer, KeyCode button){
                if(button == KeyCode.mouseRight){
                    panning = true;
                    pan(ex, ey);
                    return true;
                }

                moving = false;
                panning = false;
                pressX = ex;
                pressY = ey;
                dragOffsetX = event.stageX - MiniMapElement.this.parent.x;
                dragOffsetY = event.stageY - MiniMapElement.this.parent.y;

                if(pressTask != null) pressTask.cancel();
                pressTask = Timer.schedule(() -> {
                    if(!moving){
                        panning = true;
                        pan(ex, ey);
                    }
                }, LONG_PRESS);
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float ex, float ey, int pointer){
                if(panning){
                    pan(ex, ey);
                    return;
                }

                if(!moving && (Math.abs(ex - pressX) > CLICK_SLOP || Math.abs(ey - pressY) > CLICK_SLOP)){
                    moving = true;
                    if(pressTask != null) pressTask.cancel();
                }

                if(moving) onMoved.get(event.stageX - dragOffsetX, event.stageY - dragOffsetY);
            }

            @Override
            public void touchUp(InputEvent event, float ex, float ey, int pointer, KeyCode button){
                if(pressTask != null) pressTask.cancel();

                if(panning){
                    panning = false;
                    return;
                }

                if(!moving && button == KeyCode.mouseLeft) ui.minimapfrag.toggle();
                moving = false;
            }
        });
    }

    void pan(float localX, float localY){
        float max = Math.max(world.width(), world.height()) * 8f;
        control.input.panCamera(new Vec2((localX / getWidth()) * max, (localY / getHeight()) * max));
    }

    void rescanUnits(float delta){
        cacheTimer += delta;
        if(cacheTimer < CACHE_INTERVAL) return;
        cacheTimer = 0f;

        unitCache.clear();
        for(TeamData data : state.teams.present){
            for(Unit u : data.units){
                if(!u.isPlayer() && u.type != null && u.type.drawMinimap) unitCache.add(u);
            }
        }
        if(sortByHp) unitCache.sort((a, b) -> Float.compare(a.maxHealth, b.maxHealth));
    }

    @Override
    public void draw(){
        TextureRegion region = renderer.minimap.getRegion();
        float w = world.width() * 8f, h = world.height() * 8f;
        if(region == null || w <= 0f || h <= 0f || !clipBegin()) return;

        float min = Math.min(w, h), max = Math.max(w, h);

        Draw.color();
        region.set(0f, 1f - w / min, h / min, 1f);
        Draw.rect(region, x + width / 2f, y + height / 2f, width, height);

        float scl = Scl.scl(1f);
        float scale = scl * unitSize;
        float off = scl * 1.5f;
        float pxScl = width / max, pyScl = height / max;

        if(unitOutline){
            Draw.color(Color.white);
            for(Unit u : unitCache) drawUnitIcon(u, pxScl, pyScl, scale, off, true);
        }
        for(Unit u : unitCache) drawUnitIcon(u, pxScl, pyScl, scale, off, false);

        Draw.color();
        float eyeScl = scl * 0.625f;
        for(Player p : Groups.player){
            if(p == player || p.unit() == null) continue;
            float px = x + p.x * pxScl, py = y + p.y * pyScl;
            if(!showNames){
                Draw.rect(Icon.eye.getRegion(), px, py, Icon.eye.getRegion().width * eyeScl, Icon.eye.getRegion().height * eyeScl);
            }else{
                Fonts.def.draw(p.name, px, py + scl * 4f, Align.center);
            }
        }

        Lines.stroke(scl * 3f);
        Draw.color(Color.white);
        var cam = Core.camera;
        Lines.rect(
            x + (cam.position.x - cam.width / 2f) * pxScl,
            y + (cam.position.y - cam.height / 2f) * pyScl,
            cam.width * pxScl, cam.height * pyScl
        );

        Draw.reset();
        clipEnd();
    }

    void drawUnitIcon(Unit u, float pxScl, float pyScl, float scale, float off, boolean outline){
        if(u == null || !u.isAdded() || u.dead()) return;

        TextureRegion icon = u.type.fullIcon;
        float px = x + u.x * pxScl, py = y + u.y * pyScl;
        float iw = scale, ih = scale * icon.height / icon.width;
        float rot = u.rotation - 90f;

        if(outline){
            Draw.rect(icon, px + off, py, iw, ih, rot);
            Draw.rect(icon, px - off, py, iw, ih, rot);
            Draw.rect(icon, px, py + off, iw, ih, rot);
            Draw.rect(icon, px, py - off, iw, ih, rot);
        }else{
            Draw.color(u.team.color);
            Draw.rect(icon, px, py, iw, ih, rot);
        }
    }
}
