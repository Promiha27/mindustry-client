package testing.blui;

import arc.Core;
import arc.func.Boolp;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.Image;
import arc.scene.ui.ImageButton;
import arc.util.Time;

/**
 * ImageButton с долгим нажатием (BLUI): {@link #held(Runnable)} срабатывает после
 * {@link BLVars#longPress} тиков удержания (с {@link #setRepeat} - каждый тик), обычный клик
 * при этом глотается. Стиль несёт отдельную картинку/цвет для состояния «зажата».
 */
public class HoldImageButton extends ImageButton{
    private Runnable held = () -> {};
    public Boolp canHold = () -> true;
    private boolean heldAct;
    private HoldImageButtonStyle style;
    private boolean repeat = false;
    private boolean hasReset;

    public HoldImageButton(){
        this(Core.scene.getStyle(HoldImageButtonStyle.class));
    }

    public HoldImageButton(Drawable icon, HoldImageButtonStyle stylen){
        this(stylen);
        HoldImageButtonStyle style = new HoldImageButtonStyle(stylen);
        style.imageUp = icon;
        setStyle(style);
    }

    public HoldImageButton(TextureRegion region){
        this(Core.scene.getStyle(HoldImageButtonStyle.class));
        HoldImageButtonStyle style = new HoldImageButtonStyle(Core.scene.getStyle(HoldImageButtonStyle.class));
        style.imageUp = new TextureRegionDrawable(region);
        setStyle(style);
    }

    public HoldImageButton(TextureRegion region, HoldImageButtonStyle stylen){
        this(stylen);
        HoldImageButtonStyle style = new HoldImageButtonStyle(stylen);
        style.imageUp = new TextureRegionDrawable(region);
        setStyle(style);
    }

    public HoldImageButton(HoldImageButtonStyle style){
        super(style);
        setStyle(style);
        setSize(getPrefWidth(), getPrefHeight());
    }

    public HoldImageButton(Drawable imageUp){
        this(new HoldImageButtonStyle(null, null, null, imageUp, null, null, null));
        HoldImageButtonStyle style = new HoldImageButtonStyle(Core.scene.getStyle(HoldImageButtonStyle.class));
        style.imageUp = imageUp;
        setStyle(style);
    }

    @Override
    public HoldImageButtonStyle getStyle(){
        return style;
    }

    @Override
    public void setStyle(Button.ButtonStyle style){
        if(!(style instanceof HoldImageButtonStyle s)){
            throw new IllegalArgumentException("style must be a HoldImageButtonStyle.");
        }
        super.setStyle(style);
        this.style = s;
        if(getImage() != null){
            updateImage();
        }
    }

    public Element held(Runnable r){
        held = r;
        return this;
    }

    public Element canHold(Boolp canHold){
        this.canHold = canHold;
        return this;
    }

    @Override
    public ClickListener clicked(Cons<ClickListener> tweaker, final Cons<ClickListener> runner){
        ClickListener click;
        addListener(click = new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(runner != null && !isDisabled() && !heldAct){
                    runner.get(this);
                }
            }
        });
        tweaker.get(click);
        addReset(); //сброс таймера должен висеть ПОСЛЕ клика, иначе клик увидит уже сброшенный heldAct
        return click;
    }

    @Override
    protected void updateImage(){
        Drawable drawable = null;
        if(isDisabled() && style.imageDisabled != null){
            drawable = style.imageDisabled;
        }else if(isHeld() && style.imageHeld != null){
            drawable = style.imageHeld;
        }else if(isPressed() && style.imageDown != null){
            drawable = style.imageDown;
        }else if(isChecked() && style.imageChecked != null){
            drawable = style.imageCheckedOver != null && isOver() ? style.imageCheckedOver : style.imageChecked;
        }else if(isOver() && style.imageOver != null){
            drawable = style.imageOver;
        }else if(style.imageUp != null){
            drawable = style.imageUp;
        }

        Color color = getImage().color;
        if(isDisabled() && style.imageDisabledColor != null){
            color = style.imageDisabledColor;
        }else if(isHeld() && style.imageHeldColor != null){
            color = style.imageHeldColor;
        }else if(isPressed() && style.imageDownColor != null){
            color = style.imageDownColor;
        }else if(isChecked() && style.imageCheckedColor != null){
            color = style.imageCheckedColor;
        }else if(isOver() && style.imageOverColor != null){
            color = style.imageOverColor;
        }else if(style.imageUpColor != null){
            color = style.imageUpColor;
        }

        Image image = getImage();
        image.setDrawable(drawable);
        image.setColor(color);
    }

    @Override
    public void act(float delta){
        super.act(delta);

        if(isPressed() && !isDisabled() && canHold.get()){
            BLVars.pressTimer += Time.delta;
            if(BLVars.pressTimer > BLVars.longPress && (repeat || !heldAct)){
                heldAct = true;
                held.run();
            }
        }
    }

    public boolean isHeld(){
        return isPressed() && BLVars.pressTimer > BLVars.longPress;
    }

    public void addReset(){
        if(hasReset) return;

        released(() -> {
            heldAct = false;
            BLVars.pressTimer = 0;
        });

        hasReset = true;
    }

    public boolean repeat(){
        return repeat;
    }

    public void setRepeat(boolean repeat){
        this.repeat = repeat;
    }

    public static class HoldImageButtonStyle extends ImageButtonStyle{
        public Drawable imageHeld;
        public Color imageHeldColor;

        public HoldImageButtonStyle(Drawable up, Drawable down, Drawable checked, Drawable imageUp, Drawable imageDown, Drawable imageChecked, Drawable imageHeld){
            super(up, down, checked, imageUp, imageDown, imageChecked);
            this.imageHeld = imageHeld;
        }

        public HoldImageButtonStyle(HoldImageButtonStyle style){
            super(style);
            this.imageHeld = style.imageHeld;
            this.imageHeldColor = style.imageHeldColor;
        }

        public HoldImageButtonStyle(ImageButtonStyle style){
            super(style);
        }
    }
}
