package sonkaextras;

import arc.Core;
import arc.func.Floatp;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Align;

/**
 * Пер-панельный масштаб НАТИВНЫХ HUD-панелей: transform-обёртка, которая рисует содержимое с
 * множителем 50%..150% и честно сообщает родительскому layout'у уже МАСШТАБИРОВАННЫЙ pref-размер.
 * <p>
 * Почему так, а не локальный множитель к размерам при построении: панели (миникарта, стек волн,
 * блок-палитра) собраны из десятков ячеек с захардкоженными размерами и пересобираются по многу раз;
 * transform-обёртка масштабирует их целиком без единой правки внутренностей и применяется ВЖИВУЮ
 * при движении слайдера, без пересборки и рестарта. Механика:
 * <ul>
 * <li>рисование - {@code setTransform(true)} + scale на самой обёртке: {@code Group.draw} умножает
 *     матрицу батча, дети рисуются в своих локальных координатах как обычно (в т.ч. clip-скиссоры
 *     миникарты - они считаются через матрицу трансформа и режут правильно);</li>
 * <li>инпут - сцена конвертирует координаты в локальное пространство обёртки через
 *     {@code parentToLocalCoordinates}, который учитывает scale, поэтому клики/скролл/ховер попадают
 *     точно (масштабируется и хитбокс, и картинка);</li>
 * <li>layout - родитель видит {@code getPrefWidth/Height() * scale}, так что соседние элементы
 *     (тосты от prefHeight coreinfo, попапы от границ палитры и т.д.) сдвигаются сами. Если родитель
 *     растянул обёртку больше pref (Stack стека волн), содержимое прижимается к углу {@code align} -
 *     тому же, каким панель заякорена на экране, чтобы визуально ничего не уезжало.</li>
 * </ul>
 * Содержимое кладётся в (0,0) своим НЕмасштабированным pref-размером, а видимая область обёртки в
 * её локальном пространстве - {@code getWidth()/scale x getHeight()/scale}; выравнивание считается
 * в этих координатах.
 * <p>
 * Панели, которые заякорены на чужие размеры снаружи (scheme-панели на {@code blockCatTable} и стек
 * "waves"), читают ширину/высоту ВНУТРЕННЕГО контента - для них есть {@link #effectiveScale(Element)}:
 * произведение scale всех предков, превращающее локальный размер в визуальный.
 * <p>
 * Чат этой обёрткой НЕ масштабируется (см. {@code ChatFragment.draw}): он рисует сообщения руками в
 * экранных координатах и матчит их с {@code input.mouseX/Y} для кликабельных кнопок в тексте -
 * transform сломал бы этот маппинг, поэтому там вместо обёртки масштабируется сам шрифт (метрики и
 * хитбоксы считаются из одних и тех же масштабированных величин и остаются согласованными).
 */
public class PanelScale extends WidgetGroup{
    public static final int MIN = 50, MAX = 150;

    public static final String CHAT_KEY = "sonka-scale-chat";
    public static final String MINIMAP_KEY = "sonka-scale-minimap";
    public static final String WAVES_KEY = "sonka-scale-waves";
    public static final String COREITEMS_KEY = "sonka-scale-coreitems";
    public static final String PALETTE_KEY = "sonka-scale-palette";

    public final Element content;
    final Floatp scaleGetter;
    final int align;
    float lastScale;

    public PanelScale(Element content, int align, Floatp scale){
        this.content = content;
        this.align = align;
        this.scaleGetter = scale;
        touchable = Touchable.childrenOnly;
        setTransform(true);
        addChild(content);
        lastScale = scale.get();
        setScale(lastScale);
    }

    /* перф: scl() зовётся из act() каждой обёртки (5 панелей) и из ChatFragment.draw каждый кадр -
     * кэшируем значение и сбрасываем из changed-колбэка слайдера (ChainWarn.buildSettings), так
     * что движение слайдера по-прежнему применяется вживую */
    private static final ObjectMap<String, Float> sclCache = new ObjectMap<>();

    /** Текущий множитель настройки key (проценты в settings -> 0.5..1.5). */
    public static float scl(String key){
        Float v = sclCache.get(key);
        if(v == null){
            v = Mathf.clamp(Core.settings.getInt(key, 100), MIN, MAX) / 100f;
            sclCache.put(key, v);
        }
        return v;
    }

    /** Сброс кэша множителя - зовётся из changed-колбэка слайдера настройки. */
    public static void invalidate(String key){
        sclCache.remove(key);
    }

    /** Живой геттер множителя для конструктора - слайдер применяется без пересборки панели. */
    public static Floatp setting(String key){
        return () -> scl(key);
    }

    /**
     * Произведение scale всех предков элемента - визуальный множитель его локальных размеров.
     * Для внешних якорей, которые складывают позицию из {@code getWidth()/getHeight()} контента,
     * лежащего внутри {@link PanelScale} (scheme-панели).
     */
    public static float effectiveScale(Element e){
        float s = 1f;
        for(Group g = e.parent; g != null; g = g.parent){
            s *= g.scaleX;
        }
        return s;
    }

    @Override
    public void act(float delta){
        float s = scaleGetter.get();
        if(!Mathf.equal(s, lastScale)){
            lastScale = s;
            setScale(s);
            invalidateHierarchy();
        }
        super.act(delta);
    }

    @Override
    public float getPrefWidth(){
        return content.getPrefWidth() * lastScale;
    }

    @Override
    public float getPrefHeight(){
        return content.getPrefHeight() * lastScale;
    }

    @Override
    public void layout(){
        float s = lastScale;
        float pw = content.getPrefWidth(), ph = content.getPrefHeight();
        content.setSize(pw, ph);
        //видимая область обёртки в её локальном (немасштабированном) пространстве
        float availW = getWidth() / s, availH = getHeight() / s;
        float cx = (align & Align.left) != 0 ? 0f : (align & Align.right) != 0 ? availW - pw : (availW - pw) / 2f;
        float cy = (align & Align.bottom) != 0 ? 0f : (align & Align.top) != 0 ? availH - ph : (availH - ph) / 2f;
        content.setPosition(cx, cy);
        content.validate();
    }
}
