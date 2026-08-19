package helium.graphics;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.GL30;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.gl.GLFrameBuffer;
import arc.graphics.gl.Shader;
import helium.HeVars;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.graphics.Layer;

import java.lang.reflect.Field;

/**
 * Точечно завендоренный {@code universe.graphic.ScreenSampler} из UniverseKit (у Helium есть его
 * Java-копия ScreenSamplerJ - порт сделан с неё): перенаправляет отрисовку мира и UI через
 * фреймбуферы, чтобы текстуру "экрана на данный момент" можно было сэмплировать посреди кадра -
 * это источник картинки для gauss-blur подложки диалогов ({@link Blur}).
 * <p>
 * Отличия от оригинала:
 * <ul>
 * <li>выброшен межмодовый реестр "sampler.setup" в Core.settings (координация нескольких модов
 *     с UniverseKit на одном семплере) - вшитая копия единственная, а при установленном настоящем
 *     Helium весь порт отключается guard'ом в HeliumMod;</li>
 * <li>захват НЕ постоянный: буферная переадресация включается только когда blur реально может
 *     понадобиться - настройка включена И на сцене есть диалог ({@code scene.hasDialog()}). Без
 *     диалогов рендер идёт напрямую, как без порта (мод платил за 2-3 фуллскрин-блита каждый кадр
 *     всегда). Цена: в первый кадр открытия диалога подложка рисуется обычным чёрным - blur
 *     подхватывается со следующего кадра, под fade-in диалога это не видно;</li>
 * <li>буфер пикселятора берётся через движковый геттер {@code Pixelator.getBuffer()}, а не
 *     рефлексией (мы движок и можем себе позволить); рефлексия осталась только для
 *     {@code GLFrameBuffer.lastBoundFramebuffer} arc'а;</li>
 * <li>у {@code toBuffer} появился gl20-фоллбек (fullscreen-блит шейдером) - оригинал требовал
 *     GL30 без проверки.</li>
 * </ul>
 */
public class ScreenSampler{
    private static final Field lastBoundFramebufferField;

    static{
        try{
            lastBoundFramebufferField = GLFrameBuffer.class.getDeclaredField("lastBoundFramebuffer");
            lastBoundFramebufferField.setAccessible(true);
        }catch(NoSuchFieldException e){
            throw new RuntimeException("Failed to initialize reflection fields", e);
        }
    }

    private static final FrameBuffer worldBuffer = new FrameBuffer(), uiBuffer = new FrameBuffer();

    /** gl20-фоллбек-блит: screenspace.vert + hedist_base.frag (посыл цвета 1:1, альфа=1) */
    private static Shader baseScreen;

    private static FrameBuffer currBuffer;
    private static boolean worldCaptured, uiActive;

    private ScreenSampler(){
    }

    /** Вызывается один раз из HeliumMod ДО ClientLoadEvent - только вешает слушатели. */
    public static void setup(){
        Events.run(EventType.Trigger.draw, () -> {
            worldCaptured = false;
            if(!shouldCapture()) return;
            //в очередь сортированного батча Renderer.draw: обёртываем слои min..end в worldBuffer
            Draw.draw(Layer.min - 0.001f, ScreenSampler::beginWorld);
            Draw.draw(Layer.end + 0.001f, ScreenSampler::endWorld);
        });
        Events.run(EventType.Trigger.uiDrawBegin, ScreenSampler::beginUI);
        Events.run(EventType.Trigger.uiDrawEnd, ScreenSampler::endUI);
    }

    private static boolean shouldCapture(){
        return HeVars.getBlurEnabled() && Core.scene != null && Core.scene.hasDialog();
    }

    /** true, пока идёт захваченная отрисовка UI - только тогда blur может сэмплировать экран. */
    public static boolean isActive(){
        return uiActive && currBuffer != null;
    }

    private static void beginWorld(){
        worldCaptured = true;
        if(Vars.renderer.pixelate){
            //пикселятор уже рендерит мир в свой буфер - просто помечаем его источником
            currBuffer = Vars.renderer.pixelator.getBuffer();
        }else{
            currBuffer = worldBuffer;

            if(worldBuffer.isBound()) return;

            worldBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
            worldBuffer.begin(Color.clear);
        }
    }

    private static void endWorld(){
        if(!Vars.renderer.pixelate){
            worldBuffer.end();
            blitBuffer(worldBuffer, null);
        }
        currBuffer = null;
    }

    private static void beginUI(){
        //в игре blur валиден только если мир в этом кадре реально захвачен (настройку могли
        //включить между отрисовкой мира и UI) - иначе кадр без blur'а, чтобы не мигать чёрным
        uiActive = shouldCapture() && (Vars.state.isMenu() || worldCaptured);
        if(!uiActive){
            currBuffer = null;
            return;
        }

        currBuffer = uiBuffer;

        if(uiBuffer.isBound()) return;

        uiBuffer.resize(Core.graphics.getWidth(), Core.graphics.getHeight());
        uiBuffer.begin(Color.clear);

        if(worldCaptured){
            blitBuffer(Vars.renderer.pixelate ? Vars.renderer.pixelator.getBuffer() : worldBuffer, uiBuffer);
        }
    }

    private static void endUI(){
        if(!uiActive) return;
        uiActive = false;
        currBuffer = null;
        uiBuffer.end();
        blitBuffer(uiBuffer, null);
    }

    /**
     * Копирует текущее содержимое "экрана" (активного буфера семплера) в целевой буфер.
     * Цвет копируется, stencil-аттачмент цели не трогается - Blur полагается на это.
     */
    public static void toBuffer(FrameBuffer target){
        if(currBuffer == null) throw new IllegalStateException("currently no buffer bound");

        if(Core.gl30 != null){
            target.begin();
            Gl.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, currBuffer.getFramebufferHandle());
            Gl.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.getFramebufferHandle());
            Core.gl30.glBlitFramebuffer(
                0, 0, currBuffer.getWidth(), currBuffer.getHeight(),
                0, 0, target.getWidth(), target.getHeight(),
                Gl.colorBufferBit, Gl.nearest
            );
            target.end();
        }else{
            target.begin();
            currBuffer.blit(baseScreen());
            target.end();
        }
    }

    private static Shader baseScreen(){
        if(baseScreen == null){
            baseScreen = new Shader(
                Core.files.internal("shaders/screenspace.vert"),
                Core.files.internal("shaders/hedist_base.frag")
            );
        }
        return baseScreen;
    }

    private static void blitBuffer(FrameBuffer from, FrameBuffer to){
        if(Core.gl30 == null){
            //gl20: цель либо уже забegin'ена (to == uiBuffer сразу после begin), либо экран после end()
            from.blit(baseScreen());
        }else{
            GLFrameBuffer<?> target = to != null ? to : getLastBoundFramebuffer(from);
            Gl.bindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.getFramebufferHandle());
            Gl.bindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target != null ? target.getFramebufferHandle() : 0);
            Core.gl30.glBlitFramebuffer(
                0, 0, from.getWidth(), from.getHeight(),
                0, 0,
                target != null ? target.getWidth() : Core.graphics.getWidth(),
                target != null ? target.getHeight() : Core.graphics.getHeight(),
                Gl.colorBufferBit, Gl.nearest
            );
        }
    }

    private static GLFrameBuffer<?> getLastBoundFramebuffer(GLFrameBuffer<?> buffer){
        try{
            return (GLFrameBuffer<?>)lastBoundFramebufferField.get(buffer);
        }catch(IllegalAccessException e){
            throw new RuntimeException("Failed to access lastBoundFramebuffer field", e);
        }
    }
}
