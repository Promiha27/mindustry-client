package sonkaextras.cursors;

import arc.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.input.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import sonkaextras.cursors.CursorCustomizer.*;

import static mindustry.Vars.ui;

/**
 * Пиксельный редактор курсора («карандашиком»): сетка с шахматкой под прозрачность, инструменты
 * карандаш/ластик/заливка/пипетка/хотспот, цвет через ванильный ColorPicker, превью 1:1 и в
 * реальном размере (с тинтом и масштабом из настроек), выбор размера холста. Механика холста -
 * по ванильному прецеденту {@code CanvasEditDialog} (текстура поверх пиксмапы, попиксельный
 * {@code Pixmaps.drawPixel} при рисовании, Bresenham на драге, заливка стеком, рамка-ховер).
 * <p>
 * Размеры холста: 16/24/32/48 по запросу + 64 (родные спрайты курсоров как раз 64x64, без него
 * ванильный курсор в редакторе открывался бы ужатым). Открывается с текущей текстурой слота
 * (кастомной или встроенной); не-квадратный/нестандартный исходник приводится к ближайшему
 * разрешённому квадрату (nearest). «Сохранить» пишет PNG в файл пака (<data>/cursors/<слот>.png),
 * сохраняет хотспот и немедленно применяет курсор; закрытие без сохранения ничего не меняет.
 */
public class CursorEditorDialog extends BaseDialog{
    static final int[] SIZES = {16, 24, 32, 48, 64};

    final Slot slot;
    final @Nullable Runnable onSaved;

    Pixmap pix;
    Texture texture;
    final Color curColor = new Color(Color.white);
    Tool tool = Tool.pencil;
    /** Point2.pack хотспота в координатах холста; -1 = центр (ваниль). */
    int hotspot;

    enum Tool{
        pencil(Icon.pencil), eraser(Icon.eraser), fill(Icon.fill), pick(Icon.pick), hotspot(Icon.move);

        final TextureRegionDrawable icon;

        Tool(TextureRegionDrawable icon){
            this.icon = icon;
        }
    }

    public CursorEditorDialog(Slot slot, @Nullable Runnable onSaved){
        super("");
        this.slot = slot;
        this.onSaved = onSaved;
        title.setText(Core.bundle.format("client.sonka.cursors.editor.title", Core.bundle.get("client.sonka.cursors.slot." + slot.name)));

        pix = CursorCustomizer.basePixmap(slot);
        if(pix.width != pix.height || !allowedSize(pix.width)){
            int target = nearestSize(Math.max(pix.width, pix.height));
            Pixmap scaled = Pixmaps.scale(pix, target, target, false);
            pix.dispose();
            pix = scaled;
        }
        texture = newTexture();
        hotspot = Core.settings.getInt(CursorCustomizer.hotspotKey(slot), -1);
        if(hotspot != -1 && !pix.in(Point2.x(hotspot), Point2.y(hotspot))) hotspot = -1;

        addCloseButton();
        buttons.button("@client.sonka.cursors.editor.save", Icon.save, this::save);

        hidden(() -> {
            texture.dispose();
            pix.dispose();
        });
        shown(this::setup);
        onResize(this::setup);
    }

    static boolean allowedSize(int v){
        for(int s : SIZES) if(s == v) return true;
        return false;
    }

    static int nearestSize(int v){
        int best = SIZES[0];
        for(int s : SIZES) if(Math.abs(s - v) < Math.abs(best - v)) best = s;
        return best;
    }

    Texture newTexture(){
        Texture t = new Texture(pix);
        t.setFilter(TextureFilter.nearest);
        return t;
    }

    void setup(){
        cont.clear();

        cont.table(main -> {
            //инструменты + цвет слева
            main.table(Tex.button, tools -> {
                tools.defaults().size(48f).pad(2f);
                for(Tool t : Tool.values()){
                    tools.button(t.icon, Styles.clearTogglei, () -> tool = t)
                        .checked(b -> tool == t).tooltip("@client.sonka.cursors.editor.tool." + t.name());
                    tools.row();
                }
                ImageButton swatch = tools.button(Tex.whiteui, Styles.squarei, 32,
                    () -> ui.picker.show(curColor, false, curColor::set)).tooltip("@client.sonka.cursors.editor.color").get();
                //ImageButton копирует shared-стиль в конструкторе - мутировать getStyle() безопасно;
                //живая ссылка на curColor красит свотч без явных апдейтов
                swatch.getStyle().imageUpColor = curColor;
            }).top().padRight(6f);

            //холст по центру
            float side = Math.min(480f, Core.graphics.getHeight() / Scl.scl(1f) - 220f);
            main.stack(new Image(Tex.alphaBg), new PixelCanvas()).size(Math.max(side, 240f));

            //превью и размер холста справа
            main.table(right -> {
                right.top();
                right.add("@client.sonka.cursors.editor.preview").color(Color.gray).row();
                //1:1 и реальный размер (масштаб из настроек), тинт - цветом Image, как красит движок
                Color tint = CursorCustomizer.tint(slot);
                right.table(pv -> {
                    pv.stack(new Image(Tex.alphaBg), tinted(new Image(new TextureRegion(texture)), null)).size(pix.width).pad(4f);
                    pv.stack(new Image(Tex.alphaBg), tinted(new Image(new TextureRegion(texture)), tint))
                        .size(Math.min(CursorCustomizer.effectiveSize(pix.width), 160)).pad(4f);
                }).row();

                right.add("@client.sonka.cursors.editor.size").color(Color.gray).padTop(10f).row();
                right.table(sz -> {
                    for(int s : SIZES){
                        sz.button(s + "", Styles.togglet, () -> resizeCanvas(s))
                            .checked(b -> pix.width == s).size(44f, 40f).pad(2f);
                    }
                }).row();

                right.button("@client.sonka.cursors.editor.hotspot.reset", () -> hotspot = -1)
                    .disabled(b -> hotspot == -1).growX().height(40f).padTop(10f)
                    .tooltip("@client.sonka.cursors.editor.tool.hotspot");
            }).top().padLeft(6f).width(180f);
        }).row();

        cont.add("@client.sonka.cursors.editor.hint").width(700f).wrap().pad(6f).color(Color.gray);
    }

    Image tinted(Image img, @Nullable Color tint){
        if(tint != null) img.setColor(tint);
        return img;
    }

    /** Смена размера холста: содержимое пересэмплируется (nearest), хотспот едет пропорционально. */
    void resizeCanvas(int size){
        if(size == pix.width) return;
        Pixmap scaled = Pixmaps.scale(pix, size, size, false);
        float f = size / (float)pix.width;
        pix.dispose();
        pix = scaled;
        texture.dispose();
        texture = newTexture();
        if(hotspot != -1){
            hotspot = Point2.pack(
                Mathf.clamp(Math.round(Point2.x(hotspot) * f), 0, size - 1),
                Mathf.clamp(Math.round(Point2.y(hotspot) * f), 0, size - 1));
        }
        setup();
    }

    /** Сохранить в файл пака и применить сразу: PNG + хотспот -> rebuild всех курсоров. */
    void save(){
        try{
            CursorCustomizer.cursorsDir().mkdirs();
            CursorCustomizer.customFile(slot).writePng(pix);
            if(hotspot != -1){
                Core.settings.put(CursorCustomizer.hotspotKey(slot), hotspot);
            }else{
                Core.settings.remove(CursorCustomizer.hotspotKey(slot));
            }
            CursorCustomizer.rebuild();
            if(onSaved != null) onSaved.run();
            ui.showInfoFade("@client.sonka.cursors.editor.saved");
        }catch(Throwable t){
            Log.err("[sonka-cursors] editor save failed", t);
            ui.showErrorMessage(Core.bundle.get("client.sonka.cursors.editor.savefail") + "\n" + t.getMessage());
        }
    }

    /** Холст: механика ванильного CanvasEditDialog + шахматка снаружи (stack c Tex.alphaBg). */
    class PixelCanvas extends Element{
        int lastX, lastY;
        final IntSeq stack = new IntSeq();

        int convertX(float ex){
            return (int)(ex / (width / pix.width));
        }

        int convertY(float ey){
            return pix.height - 1 - (int)(ey / (height / pix.height));
        }

        {
            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float ex, float ey, int pointer, KeyCode button){
                    int cx = convertX(ex), cy = convertY(ey);
                    if(!pix.in(cx, cy)) return false;
                    switch(tool){
                        case pencil -> {
                            drawPixel(cx, cy, curColor.rgba());
                            lastX = cx;
                            lastY = cy;
                            return true;
                        }
                        case eraser -> {
                            drawPixel(cx, cy, 0);
                            lastX = cx;
                            lastY = cy;
                            return true;
                        }
                        case fill -> flood(cx, cy, curColor.rgba());
                        case pick -> curColor.set(pix.getRaw(cx, cy));
                        case hotspot -> hotspot = Point2.pack(cx, cy);
                    }
                    return false;
                }

                @Override
                public void touchDragged(InputEvent event, float ex, float ey, int pointer){
                    int cx = convertX(ex), cy = convertY(ey);
                    int color = tool == Tool.eraser ? 0 : curColor.rgba();
                    Bresenham2.line(lastX, lastY, cx, cy, (x, y) -> drawPixel(x, y, color));
                    lastX = cx;
                    lastY = cy;
                }
            });
        }

        void drawPixel(int x, int y, int color){
            if(pix.in(x, y) && pix.getRaw(x, y) != color){
                pix.setRaw(x, y, color);
                Pixmaps.drawPixel(texture, x, y, color);
            }
        }

        /** Заливка стеком по 4-связности (паттерн ванили); текстура обновляется одним draw в конце. */
        void flood(int sx, int sy, int replacement){
            int target = pix.getRaw(sx, sy);
            if(target == replacement) return;
            stack.clear();
            stack.add(Point2.pack(sx, sy));
            while(!stack.isEmpty()){
                int cur = stack.pop();
                int x = Point2.x(cur), y = Point2.y(cur);
                if(!pix.in(x, y) || pix.getRaw(x, y) != target) continue;
                pix.setRaw(x, y, replacement);
                for(int i = 0; i < 4; i++){
                    stack.add(Point2.pack(x + Geometry.d4x(i), y + Geometry.d4y(i)));
                }
            }
            texture.draw(pix);
        }

        @Override
        public void draw(){
            int size = pix.width;
            float space = width / size;

            Tmp.tr1.set(texture);
            Draw.alpha(parentAlpha);
            Draw.rect(Tmp.tr1, x + width / 2f, y + height / 2f, width, height);

            //сетка
            Draw.color(Color.black, 0.35f * parentAlpha);
            for(int i = 0; i <= size; i++){
                Fill.crect(x + space * i - 0.5f, y, 1f, height);
                Fill.crect(x, y + space * i - 0.5f, width, 1f);
            }

            //хотспот: явный - рамкой, дефолтный центр - блёклым крестом
            Draw.color(Pal.remove, (hotspot != -1 ? 0.9f : 0.35f) * parentAlpha);
            int hx = hotspot != -1 ? Point2.x(hotspot) : size / 2;
            int hy = hotspot != -1 ? Point2.y(hotspot) : size / 2;
            float cxr = x + (hx + 0.5f) * space, cyr = y + (size - 1 - hy + 0.5f) * space;
            Lines.stroke(2f);
            Lines.line(cxr - space * 0.7f, cyr, cxr + space * 0.7f, cyr);
            Lines.line(cxr, cyr - space * 0.7f, cxr, cyr + space * 0.7f);
            Lines.rect(x + hx * space, y + (size - 1 - hy) * space, space, space);

            //рамка-ховер текущей клетки (как у ванильного canvas-редактора)
            Vec2 m = screenToLocalCoordinates(Core.input.mouse());
            if(m.x >= 0 && m.y >= 0 && m.x < width && m.y < height){
                float sx = (int)(m.x / space) * space, sy = (int)(m.y / space) * space;
                Lines.stroke(Scl.scl(4f));
                Draw.color(Pal.accent, parentAlpha);
                Lines.rect(sx + x, sy + y, space, space, Lines.getStroke() - 1f);
            }
            Draw.reset();
        }
    }
}
