package mu;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.scene.Element;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.ScrollPane;
import arc.util.Tmp;
import mindustry.game.Rules;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.type.Planet;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static arc.Core.input;
import static arc.Core.scene;
import static mindustry.Vars.content;
import static mindustry.Vars.ui;

/**
 * Редактор планетного фона карты ({@code Rules.planetBackground}): диалог без затемняющего
 * фона, вместо него - живое 3D-превью планеты из {@link PlanetBackgroundDrawer}. Перетаскивание
 * вращает камеру (по горизонтали с замедлением у полюсов, по вертикали с запасом в 1°, чтобы
 * не перекрутить через полюс), колесо/щипок - зум (ограничен minZoom планеты..50). Кнопки:
 * удалить фон, выбрать планету. Сам фон в игре рисуется поверх тайлов «Empty» - задумано
 * под космические карты.
 */
public class PlanetBackgroundDialog extends BaseDialog{
    private Rules rules;
    private float zoom = 1f;

    public PlanetBackgroundDialog(){
        super("@rules.planetbackground", new DialogStyle(){{
            stageBackground = Styles.none;
            titleFont = Fonts.def;
            titleFontColor = Pal.accent;
            //фон не задаётся специально - иначе он затемнит планету
        }});

        dragged((cx, cy) -> {
            if(rules.planetBackground == null) return;
            //мультитач-драг не вращает (это зум-жест)
            if(Core.input.getTouches() > 1) return;

            Vec3 pos = rules.planetBackground.camPos;

            float upV = pos.angle(Vec3.Y);
            float xScale = 9f, yScale = 10f;
            float margin = 1;

            //горизонтальная скорость зависит от полярного угла
            float speed = 1f - Math.abs(upV - 90) / 90f;

            pos.rotate(rules.planetBackground.camUp, cx / xScale * speed);

            //не давать докрутить до самого полюса - камера там сходит с ума
            float amount = cy / yScale;
            amount = Mathf.clamp(upV + amount, margin, 180f - margin) - upV;

            pos.rotate(Tmp.v31.set(rules.planetBackground.camUp).rotate(rules.planetBackground.camDir, 90), amount);
            PlanetBackgroundDrawer.update();
        });

        addListener(new InputListener(){
            @Override
            public boolean scrolled(InputEvent event, float x, float y, float amountX, float amountY){
                if(rules.planetBackground == null) return false;
                if(event.targetActor == PlanetBackgroundDialog.this){
                    zoom = Mathf.clamp(zoom + amountY / 10f, rules.planetBackground.planet.minZoom, 50f);
                }
                PlanetBackgroundDrawer.update();
                return true;
            }
        });

        addCaptureListener(new ElementGestureListener(){
            float lastZoom = -1f;

            @Override
            public void zoom(InputEvent event, float initialDistance, float distance){
                if(rules.planetBackground == null) return;
                if(lastZoom < 0){
                    lastZoom = zoom;
                }

                zoom = Mathf.clamp(initialDistance / distance * lastZoom, rules.planetBackground.planet.minZoom, 50f);
                PlanetBackgroundDrawer.update();
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                lastZoom = zoom;
            }
        });

        addCloseButton();
        buttons.button("@remove_background", Icon.none, () -> ui.showConfirm("@confirm", () -> {
            rules.planetBackground = null;
            build();
        })).get().setDisabled(() -> rules.planetBackground == null);
        buttons.button("@rules.title.planet", Icon.planet, () -> {
            BaseDialog dialog = new BaseDialog("@rules.title.planet");
            dialog.cont.pane(table -> {
                int i = 0;
                for(Planet planet : content.planets()){
                    table.button(planet.localizedName, Icon.icons.get(planet.icon, Icon.commandRally), Styles.togglet, () -> {
                        rules.planetBackground.planet = planet;
                        PlanetBackgroundDrawer.update();
                        dialog.hide();
                    }).marginLeft(14f).padBottom(5f).width(220f).height(55f).checked(rules.planetBackground.planet == planet)
                    .update(b -> b.setChecked(rules.planetBackground.planet == planet)).get().getChildren().get(1).setColor(planet.iconColor);
                    i += 1;
                    if(i % 3 == 0){
                        table.row();
                    }
                }
            });

            dialog.addCloseButton();
            dialog.show();
        }).get().setDisabled(() -> rules.planetBackground == null);

        shown(this::build);
    }

    public void show(Rules rules){
        this.rules = rules;
        zoom = rules.planetBackground == null ? 1f : rules.planetBackground.zoom;
        show();
    }

    private void build(){
        cont.clear();
        if(rules.planetBackground == null){
            cont.add("@empty").row();
            cont.button("@add", Icon.add, () -> {
                rules.planetBackground = new PlanetParams();
                zoom = rules.planetBackground.zoom;
                build();
            }).width(100f);
        }
        PlanetBackgroundDrawer.update();
    }

    @Override
    public void draw(){
        if(rules.planetBackground != null){
            if(scene.getDialog() == PlanetBackgroundDialog.this){
                //скролл-фокус на диалог, если курсор не над ScrollPane'ом - иначе колесо не зумит
                Element hit = scene.hit(input.mouseX(), input.mouseY(), true);
                if(hit == null || !hit.isDescendantOf(e -> e instanceof ScrollPane)){
                    scene.setScrollFocus(PlanetBackgroundDialog.this);
                }
            }
            //плавный зум: пока lerp не доехал - перерисовывать (в оригинале превью обновлялось
            //лишь на первом кадре после колеса, и зум «застревал» на 40% пути)
            if(!Mathf.equal(rules.planetBackground.zoom, zoom, 0.0005f)){
                rules.planetBackground.zoom = Mathf.lerpDelta(rules.planetBackground.zoom, zoom, 0.4f);
                PlanetBackgroundDrawer.update();
            }
            float drawSize = Math.max(Core.graphics.getWidth(), Core.graphics.getHeight());
            Draw.rect(Draw.wrap(PlanetBackgroundDrawer.draw(rules.planetBackground)), Core.graphics.getWidth() / 2f, Core.graphics.getHeight() / 2f, drawSize, -drawSize);
            Draw.flush();
        }else{
            Draw.color(color.r, color.g, color.b, color.a * parentAlpha);
            Styles.black9.draw(x, y, width, height);
        }

        super.draw();
    }
}
