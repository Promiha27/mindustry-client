package helium.graphics

import arc.Core
import arc.Events
import arc.graphics.g2d.Draw
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import helium.HeVars
import mindustry.game.EventType
import mindustry.ui.Styles

/**
 * Blur-подложка диалогов - порт blur-части helium.ui.HeStyles: drawable BLUR_BACK, который
 * вместо сплошного чёрного размывает то, что за диалогом, плюс лёгкое чёрное затемнение сверху.
 * Механика "последнего диалога" оригинала: stageBackground рисуется у КАЖДОГО открытого диалога,
 * но дорогой blur выполняется только у верхнего - счётчик отрисовок за кадр сравнивается с
 * количеством из прошлого кадра.
 *
 * Подключение как у мода: подмена [Styles.defaultDialog].stageBackground на каждом апдейте
 * (см. [update]) - работает для всех BaseDialog-диалогов форка сразу, без правок самих диалогов.
 * fullDialog (планеты и т.п.) не трогаем, как и мод.
 */
object UiBlur{
    val blur = Blur()

    private var drawingCounter = 0
    private var lastDialogs = 0

    private lateinit var blurBack: Drawable
    private var originalBack: Drawable? = null

    fun load(){
        Events.run(EventType.Trigger.uiDrawBegin){ drawingCounter = 0 }
        Events.run(EventType.Trigger.uiDrawEnd){ lastDialogs = drawingCounter }

        originalBack = Styles.defaultDialog.stageBackground

        blurBack = object : TextureRegionDrawable(Core.atlas.white()){
            override fun draw(x: Float, y: Float, width: Float, height: Float){
                if(!ScreenSampler.isActive()){
                    //семплер не захватил кадр (первый кадр диалога) - обычная ванильная подложка
                    Styles.black9.draw(x, y, width, height)
                    return
                }

                drawingCounter++
                if(drawingCounter == lastDialogs) blur.drawBlur{
                    Draw.alpha(Draw.getColorAlpha())
                    Draw.rect(region, x + width/2f, y + height/2f, width, height)
                }

                Styles.black5.draw(x, y, width, height)
            }

            override fun draw(
                x: Float, y: Float, originX: Float, originY: Float,
                width: Float, height: Float,
                scaleX: Float, scaleY: Float,
                rotation: Float,
            ){
                if(!ScreenSampler.isActive()){
                    Styles.black9.draw(x, y, originX, originY, width, height, scaleX, scaleY, rotation)
                    return
                }

                drawingCounter++
                if(drawingCounter == lastDialogs) blur.drawBlur{
                    Draw.alpha(Draw.getColorAlpha())
                    Draw.rect(
                        region,
                        x + width/2f, y + height/2f,
                        width*scaleX, height*scaleY,
                        originX, originY,
                        rotation
                    )
                }

                Styles.black5.draw(x, y, originX, originY, width, height, scaleX, scaleY, rotation)
            }
        }
    }

    /** Каждый кадр из HeliumMod: живое применение слайдеров и вкл/выкл без пересборки UI. */
    fun update(){
        blur.blurScl = Core.settings.getInt(HeVars.BLUR_SCL, 2)
        blur.blurLevel = Core.settings.getInt(HeVars.BLUR_LEVEL, 2)
        blur.blurSpace = Core.settings.getInt(HeVars.BLUR_SPACE, 5)*0.25f
        Styles.defaultDialog.stageBackground = if(HeVars.blurEnabled) blurBack else originalBack
    }
}
