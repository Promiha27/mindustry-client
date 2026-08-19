package helium.ui

import arc.func.Boolp
import arc.func.Cons
import arc.graphics.g2d.Draw
import arc.math.Interp
import arc.scene.actions.TemporalAction
import arc.scene.event.Touchable
import arc.scene.style.Drawable
import arc.scene.ui.layout.Table
import arc.scene.ui.layout.WidgetGroup
import arc.util.ArcRuntimeException

/**
 * Вендорено из Helium (helium.ui.elements.HeCollapser) как есть: коллапсер, умеющий сворачивать
 * контент ПО ГОРИЗОНТАЛИ (ванильный arc Collapser только по вертикали), с клипом содержимого
 * во время анимации. Используется быстрой палитрой панели размещения и менеджером модов.
 */
open class HeCollapser(
    private var collTable: Table,
    var collapse: Boolean,
    private val collX: Boolean,
    private val collY: Boolean
) : WidgetGroup(){
    var collapsedFunc: Boolp? = null
    private val collapseAction = CollapseAction()
    var actionRunning = false
    var currentWidth = 0f
    var currentHeight = 0f

    constructor(
        collX: Boolean,
        collY: Boolean,
        collapsed: Boolean = false,
        background: Drawable? = null,
        cons: Cons<Table>
    ) : this(Table(background), collapsed, collX, collY){
        cons.get(collTable)
    }

    init{
        isTransform = true

        updateTouchable()
        addChild(collTable)
    }

    fun setDuration(seconds: Float, interp: Interp = Interp.linear): HeCollapser{
        this.collapseAction.duration = seconds
        this.collapseAction.interpolation = interp
        return this
    }

    fun setCollapsed(collapsed: Boolp): HeCollapser{
        this.collapsedFunc = collapsed
        return this
    }

    fun toggle(){
        setCollapsed(!collapse)
    }

    fun setCollapsed(collapse: Boolean){
        this.collapse = collapse
        updateTouchable()

        actionRunning = true

        addAction(collapseAction)
        collapseAction.restart()
    }

    private fun updateTouchable(){
        this.touchable = if(collapse) Touchable.disabled else Touchable.enabled
    }

    override fun draw(){
        if(currentWidth > 1 && currentHeight > 1){
            Draw.flush()
            if(clipBegin(x, y, currentWidth, currentHeight)){
                super.draw()
                Draw.flush()
                clipEnd()
            }
        }
    }

    override fun drawChildren(){
        if(collapse && !actionRunning) return
        super.drawChildren()
    }

    override fun act(delta: Float){
        super.act(delta)

        collapsedFunc?.also{
            val col = it.get()
            if(col != collapse) setCollapsed(col)
        }
    }

    override fun layout(){
        collTable.setBounds(0f, 0f, width, height)

        if(!actionRunning){
            currentWidth = if(collX){ if(collapse) 0f else collTable.prefWidth }else width
            currentHeight = if(collY){ if(collapse) 0f else collTable.prefHeight }else height
        }
    }

    override fun getPrefWidth(): Float{
        if(!collX) return collTable.prefWidth

        if(!actionRunning){
            return if(collapse) 0f else collTable.prefWidth
        }

        return currentWidth
    }

    override fun getPrefHeight(): Float{
        if(!collY) return collTable.prefHeight

        if(!actionRunning){
            return if(collapse) 0f else collTable.prefHeight
        }

        return currentHeight
    }

    fun setTable(table: Table){
        this.collTable = table
        clearChildren()
        addChild(table)
    }

    override fun getMinWidth(): Float = 0f

    override fun getMinHeight(): Float = 0f

    override fun childrenChanged(){
        super.childrenChanged()
        if(children.size > 1) throw ArcRuntimeException("Only one actor can be added to CollapsibleWidget")
    }

    private inner class CollapseAction : TemporalAction(){
        override fun act(delta: Float) = super.act(delta) || !actionRunning

        override fun begin(){
            actionRunning = true
        }

        override fun update(percent: Float){
            if(collX){
                currentWidth =
                    if(collapse) (1 - percent)*collTable.prefWidth
                    else percent*collTable.prefWidth
            }
            if(collY){
                currentHeight =
                    if(collapse) (1 - percent)*collTable.prefHeight
                    else percent*collTable.prefHeight
            }

            invalidateHierarchy()
        }

        override fun end(){
            actionRunning = false
        }
    }
}
