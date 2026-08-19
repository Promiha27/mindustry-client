package helium.ui

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.input.KeyBind
import arc.input.KeyCode
import arc.math.Interp
import arc.math.Mathf
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.event.ClickListener
import arc.scene.event.HandCursorListener
import arc.scene.event.InputEvent
import arc.scene.event.Touchable
import arc.scene.style.Drawable
import arc.scene.ui.ImageButton
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.Seq
import arc.util.Align
import arc.util.Time
import helium.HeBinds
import helium.HeVars
import mindustry.Vars
import mindustry.game.EventType.WorldLoadEvent
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.ui.Fonts
import mindustry.ui.Styles
import mindustry.world.Block

/**
 * Быстрая палитра блоков из Helium (часть HePlacementFrag мода): 8 слотов x 3 страницы,
 * хоткеи 1-8 (страница - Tab, свернуть сетку блоков - Q, см. HeBinds), запоминание наборов
 * ПЕР-СЕЙВ/ПЕР-ПЛАНЕТА в mods/data/he/global_vars.bin (тот же файл и те же ключи, что у мода -
 * наборы sonka времён игры с модом подхватываются).
 *
 * В отличие от мода (полная замена PlacementFragment) палитра ВСТРОЕНА колонкой слева в наш
 * PlacementFragment: у форка слишком много якорей и своих фич палитры (поиск, blockSelect-комбо,
 * PanelScale, scheme-док, mi2u-высота) - см. javadoc HeliumMod. Механика слотов оригинальная:
 * клик/цифра - взять блок слота (повторно - снять), двойной клик - очистить слот, при
 * РАЗВЁРНУТОЙ сетке выбранный слот перезаписывается любым выбранным блоком.
 *
 * Инстанс ОДИН на игру (создаётся лениво в PlacementFragment.build и переживает rebuild'ы -
 * слушатель WorldLoadEvent вешается один раз).
 */
class HeQuickInv{
    companion object{
        private val savePattern = Regex("^save#((\\w|-)+)-fast-slot-\\d+-\\d+$")

        private var currBlock: Block?
            get() = Vars.control.input.block
            set(value){ Vars.control.input.block = value }
    }

    private val invSlots = Seq<InvSlot>()
    private var currentSlot: InvSlot? = null
    private var invPage = 0
        set(value){
            field = value
            invAnimateActivating = true
            invAnimateProgress = 0f
        }

    private var invAnimateActivating = false
    private var invAnimateProgress = 0f

    private lateinit var foldIcon: ImageButton

    /** Обёртка-коллапсер: палитра уезжает влево при выключенной настройке и в командном режиме. */
    val wrapper: HeCollapser

    init{
        HeAssets.ensure()

        wrapper = HeCollapser(collX = true, collY = false, collapsed = true){ t ->
            t.top().table(Tex.pane){ inv ->
                inv.top().margin(4f)
                buildFastInventory(inv)
            }.growY().fillX()
        }.setDuration(0.3f, Interp.pow3Out)
            .setCollapsed{ !HeVars.placementEnabled || Vars.control.input.commandMode }

        wrapper.update{ update() }

        Events.on(WorldLoadEvent::class.java){
            Core.app.post{ loadSlots() }
        }

        cleanGlobal()
    }

    /** Свёрнута ли сетка блоков (fold-кнопка палитры). Публично: PlacementFragment вешает на это коллапсер сетки. */
    fun folded(): Boolean = HeVars.placementEnabled && Core.settings.getBool(HeVars.PLACEMENT_FOLD, false)

    private fun gridShown(): Boolean = HeVars.placementEnabled && !folded()

    private fun slotsActive(): Boolean =
        HeVars.placementEnabled && !Vars.control.input.commandMode && Vars.ui.hudfrag.shown && Vars.state.isGame

    private fun update(){
        //перезапись выбранного слота выбранным в сетке блоком - как в моде (только при открытой сетке)
        if(gridShown()){
            currentSlot?.also{ slot ->
                if(slot.block != currBlock && currBlock != null){
                    slot.block = currBlock
                    slot.save()
                }
            }
        }

        if(invAnimateActivating){
            invAnimateProgress = Mathf.approachDelta(invAnimateProgress, 1f, 0.06f)
            if(invAnimateProgress >= 1) invAnimateActivating = false
        }
    }

    fun loadSlots(){
        invSlots.forEach{ it.load() }
    }

    /**
     * Сборка мусора: слоты удалённых сейвов выкидываются из global_vars.bin (порт cleanGlobal мода).
     * Отличия от мода: имена сейвов берутся ПРЯМО из файлов saves-папки, а не из control.saves -
     * этот форк грузит сейвы лениво, и saveSlots на старте либо пуст (мод бы снёс ЖИВЫЕ данные),
     * либо форсирует медленную синхронную загрузку всех сейвов. Плюс имена сверяются и в сыром,
     * и в underscore-виде: slotName() заменяет "-" на "_" (у мода из-за этого дырка - слоты
     * сейвов с дефисом в имени он считал мусором).
     */
    private fun cleanGlobal(){
        try{
            val saves = Vars.saveDirectory.list()
                .filter{ it.extension() == Vars.saveExtension }
                .flatMap{ listOf(it.nameWithoutExtension(), it.nameWithoutExtension().replace("-", "_")) }
                .toSet()

            HeVars.global.keys()
                .filter{ savePattern.matches(it) }
                .associateBy{ savePattern.find(it)?.groupValues?.get(1) }
                .filter{ it.key !in saves }
                .forEach{ HeVars.global.remove(it.value) }
        }catch(e: Throwable){
            arc.util.Log.err("[helium] fast-slot GC failed", e)
        }
    }

    private fun buildFastInventory(table: Table){
        table.defaults().size(48f)
        for(i in 0 until 8){
            if(i > 0 && i%2 == 0) table.row()
            val slot = InvSlot(i)
            table.add(slot)

            invSlots.add(slot)
        }

        table.row()

        fun listen(event: InputEvent, b: ImageButton, key: KeyBind){
            if(Core.scene.hasKeyboard() || !slotsActive()) return

            if(Core.input.keyDown(key)){
                for(listener in b.listeners){
                    if(listener is ClickListener) listener.touchDown(event, 0f, 0f, 0, listener.button)
                }
            }
            if(Core.input.keyRelease(key)){
                for(listener in b.listeners){
                    if(listener is ClickListener) listener.touchUp(event, 0f, 0f, 0, listener.button)
                }
            }
        }

        val pageEvent = InputEvent()
        val pageBtn = ImageButton(Icon.refresh, Styles.clearNonei).also{
            it.resizeImage(24f)
            it.clicked{
                if(!invAnimateActivating){
                    invPage = (invPage + 1)%3
                    currBlock = currentSlot?.block
                }
            }
            it.update{ listen(pageEvent, it, HeBinds.switchFastPage) }
        }
        pageEvent.listenerActor = pageBtn
        table.stack(pageBtn, hintElement(HeBinds.switchFastPage))

        val foldEvent = InputEvent()
        foldIcon = ImageButton(Icon.leftOpen, Styles.clearNonei).also{
            it.resizeImage(28f)
            it.clicked{ toggleFold() }
            it.update{ listen(foldEvent, it, HeBinds.placementFold) }
            //начальное направление стрелки: сетка открыта -> стрелка "закрыть" (зеркалим, как в моде)
            it.setOrigin(Align.center)
            it.isTransform = true
            it.setScale(if(folded()) 1f else -1f, 1f)
        }
        foldEvent.listenerActor = foldIcon
        table.stack(foldIcon, hintElement(HeBinds.placementFold))
    }

    private fun hintElement(bind: KeyBind): Element = object : Element(){
        override fun draw(){
            validate()
            if(Core.app.isDesktop) drawKeyHint(bind, x, y, height)
        }
    }.also{ it.touchable = Touchable.disabled }

    private fun drawKeyHint(bind: KeyBind, x: Float, y: Float, h: Float){
        val key = bind.value?.key ?: return
        Fonts.outline.draw(
            key.value,
            x, y + h - Fonts.outline.capHeight*0.65f,
            Color.white, 0.65f, true,
            Align.left
        )
    }

    private fun toggleFold(){
        val fold = !Core.settings.getBool(HeVars.PLACEMENT_FOLD, false)
        Core.settings.put(HeVars.PLACEMENT_FOLD, fold)

        //открыли сетку - снимаем выбор слота, чтобы случайно не перезаписать его первым же кликом
        if(!fold) currentSlot = null

        foldIcon.clearActions()
        foldIcon.setOrigin(Align.center)
        foldIcon.isTransform = true
        foldIcon.actions(Actions.scaleTo(if(fold) 1f else -1f, 1f, 0.3f))
    }

    private inner class InvSlot(val id: Int, val background: Drawable? = HeAssets.slotsBack) : Element(){
        private val blocks: Array<Block?> = arrayOfNulls(3)

        var block: Block?
            get() = blocks[invPage]
            set(value){ blocks[invPage] = value }
        private val selected: Boolean get() = currentSlot == this

        private val numKey = KeyCode.byOrdinal(KeyCode.num1.ordinal + id)

        private var lastClick = 0f

        init{
            clicked{
                if(Time.globalTime - lastClick < 15f){
                    currBlock = null
                    block = null
                    save()
                    return@clicked
                }
                lastClick = Time.globalTime
                if(currentSlot == this){
                    currentSlot = null
                    currBlock = null
                }else{
                    currentSlot = this
                    currBlock = block
                }
            }

            addListener(HandCursorListener())
            addListener(object : ClickListener(){
                override fun enter(event: InputEvent, x: Float, y: Float, pointer: Int, fromActor: Element?){
                    super.enter(event, x, y, pointer, fromActor)
                    if(pointer != -1) return
                    Vars.ui.hudfrag.blockfrag.menuHoverBlock = block
                }

                override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Element?){
                    super.exit(event, x, y, pointer, toActor)
                    if(pointer == -1 && Vars.ui.hudfrag.blockfrag.menuHoverBlock === block){
                        Vars.ui.hudfrag.blockfrag.menuHoverBlock = null
                    }
                }
            })
        }

        private fun slotName(): String?{
            return if(!Vars.net.active() || Vars.net.server()){
                if(Vars.state.isCampaign) Vars.state.planet?.let{ "planet#${it.name}}" } ?: "#unknow"
                else if(Vars.control.saves.current == null) null //без сейв-слота не запоминаем
                else "save#${Vars.control.saves.current.file.nameWithoutExtension().replace("-", "_")}"
            }
            else null //на чужих серверах не запоминаем (как в моде)
        }

        fun save(){
            val currPlanet = slotName() ?: return

            blocks.forEachIndexed{ i, block ->
                HeVars.global.put("$currPlanet-fast-slot-$id-$i", block?.name ?: "!empty")
            }
        }

        fun load(){
            val currPlanet = slotName() ?: return

            for(i in blocks.indices){
                val name = HeVars.global.getString("$currPlanet-fast-slot-$id-$i", "!empty")
                blocks[i] = Vars.content.block(name)
            }
        }

        override fun act(delta: Float){
            super.act(delta)
            if(block != null && selected && currBlock != block) currentSlot = null

            if(slotsActive() && !Core.scene.hasKeyboard() && Core.input.keyTap(numKey)) fireClick()
        }

        override fun draw(){
            super.draw()

            background?.draw(x, y, width, height)

            val fullSize = Scl.scl(32f)
            val smallSize = Scl.scl(14f)
            val prog = Interp.pow3Out.apply(invAnimateProgress)
            if(invAnimateActivating){
                val last = blocks[(invPage + 2)%3]
                val center = blocks[invPage%3]
                val next = blocks[(invPage + 1)%3]

                last?.also{
                    val s = Mathf.lerp(fullSize, smallSize, prog)
                    Draw.rect(
                        it.uiIcon,
                        Mathf.lerp(x + width/2, x + smallSize/2, prog),
                        Mathf.lerp(y + height/2, y + smallSize/2, prog),
                        s, s
                    )
                }
                center?.also{
                    val s = Mathf.lerp(smallSize, fullSize, prog)
                    Draw.rect(
                        it.uiIcon,
                        Mathf.lerp(x + width - smallSize/2, x + width/2, prog),
                        Mathf.lerp(y + smallSize/2, y + height/2, prog),
                        s, s
                    )
                }
                next?.also{
                    Draw.rect(
                        it.uiIcon,
                        Mathf.lerp(x + smallSize/2, x + width - smallSize/2, prog),
                        y + smallSize/2,
                        smallSize, smallSize
                    )
                }
            }else{
                val left = blocks[(invPage + 2)%3]
                val center = blocks[invPage%3]
                val right = blocks[(invPage + 1)%3]

                left?.also{ Draw.rect(it.uiIcon, x + smallSize/2, y + smallSize/2, smallSize, smallSize) }
                center?.also{ Draw.rect(it.uiIcon, x + width/2, y + height/2, fullSize, fullSize) }
                right?.also{ Draw.rect(it.uiIcon, x + width - smallSize/2, y + smallSize/2, smallSize, smallSize) }
            }

            if(selected){
                Lines.stroke(Scl.scl(4f), Pal.accent)
                Lines.rect(x, y, width, height)
            }

            if(Core.app.isDesktop){
                Fonts.outline.draw(
                    numKey.value,
                    x, y + height - Fonts.outline.capHeight*0.65f,
                    Color.white, 0.65f, true,
                    Align.left
                )
            }

            Draw.reset()
        }
    }
}
