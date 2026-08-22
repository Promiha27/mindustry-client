package helium.ui.dialogs.mods

import arc.Core
import arc.func.Cons
import arc.graphics.Color
import arc.math.Interp
import arc.math.geom.Rect
import arc.scene.Group
import arc.scene.style.Drawable
import arc.scene.ui.Image
import arc.scene.ui.ScrollPane
import arc.scene.ui.Tooltip
import arc.scene.ui.layout.Scl
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.util.Align
import arc.util.Log
import arc.util.Scaling
import arc.util.serialization.Jval
import helium.HeVars
import helium.ui.ButtonEntry
import helium.ui.HeAssets
import helium.ui.HeCollapser
import helium.ui.UIUtils
import helium.ui.UIUtils.line
import helium.ui.dialogs.mods.ModsDialogHelper.addTip
import helium.ui.dialogs.mods.ModsDialogHelper.buildModAttrIcons
import helium.ui.dialogs.mods.ModsDialogHelper.buildModAttrList
import helium.ui.dialogs.mods.ModsDialogHelper.buildStars
import helium.ui.dialogs.mods.ModsDialogHelper.buildStatus
import helium.ui.dialogs.mods.ModsDialogHelper.checkStatus
import helium.ui.dialogs.mods.ModsDialogHelper.getModList
import helium.ui.dialogs.mods.ModsDialogHelper.iconUrl
import helium.ui.dialogs.mods.ModsDialogHelper.markupTable
import helium.ui.dialogs.mods.ModsDialogHelper.shortDesc
import helium.ui.dialogs.mods.ModsDialogHelper.showDownloadModDialog
import helium.ui.dialogs.mods.ModsDialogHelper.switchBut
import helium.util.Downloader
import helium.util.ModStat
import helium.util.addEventBlocker
import mindustry.Vars
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.mod.ModListing
import mindustry.ui.Styles
import sonkaextras.packs.PackScan
import sonkaextras.packs.PackUi
import mindustry.ui.dialogs.BaseDialog
import kotlin.math.max
import kotlin.math.min

/**
 * Порт helium.ui.dialogs.mods.HeModsBrowser (Helium, EB-wilson): браузер модов карточками в
 * несколько колонок, с поиском, сортировкой по звёздам/дате, фильтром невалидных и локальным
 * избранным (звёздочка; хранится в mods/data/he/global_vars.bin - синхронизация избранного со
 * звёздами GitHub-аккаунта у самого мода закомментирована, тут её тоже нет).
 * Листинг - ванильный mods.json (Vars.modJsonURLs), иконки - Anuken/MindustryMods/icons.
 */
class HeModsBrowser : BaseDialog(Core.bundle["mods.browser"]){
    companion object{
        private fun Table.cullTable(background: Drawable? = null, build: Cons<Table>? = null) =
            add(CullTable(background).also{ t -> build?.get(t) })
    }

    /** Дёргается после установки/обновления мода из браузера - менеджер перечитывает свой список. */
    var onInstalledChanged: Runnable? = null

    private lateinit var rebuildList: () -> Unit

    private val browserTabs = ObjectMap<ModListing, Table>()
    private val favoritesMods = ObjectSet<Name>()

    private var search = ""
    private var orderDate = false
    private var reverse = false
    private var hideInvalid = true
    /** sonka: вкладка «Текстурпаки» - эвристика по описанию (PackScan.guess) или проверенный состав репозитория. */
    private var packsOnly = false
    /** Подфильтр внутри вкладки: маска PackScan.TEXTURES/MUSIC/SOUNDS. */
    private var packMask = PackScan.TEXTURES or PackScan.MUSIC or PackScan.SOUNDS

    init{
        HeAssets.ensure()
        addCloseListener() //ESC/Back
        shown(::rebuild)
        resized(::rebuild)
    }

    fun loadFavorites(){
        favoritesMods.clear()

        val listRaw = HeVars.global.getString("favorite-mods", "none")

        if(listRaw == "none" || listRaw.isNullOrBlank()) return
        try{
            val list = Jval.read(listRaw).asArray()
            list.forEach{
                val author = it.getString("author", "*")
                val name = it.getString("name", "")

                if(name.isNotBlank()) favoritesMods.add(Name(author, name))
            }
        }catch(e: Exception){
            Log.err("[helium] failed to read favorite mods", e)
        }
    }

    fun saveFavorites(){
        val list = Jval.newArray()
        favoritesMods.forEach{
            val mod = Jval.newObject()
            mod.put("author", it.author)
            mod.put("name", it.name)

            list.add(mod)
        }
        HeVars.global.put("favorite-mods", list.toString())
    }

    fun rebuild(){
        loadFavorites()

        cont.clearChildren()
        cont.table{ main ->
            main.top()
            main.table{ top ->
                top.image(Icon.zoom).size(64f).scaling(Scaling.fit)
                top.field(""){
                    search = it.lowercase()
                    rebuildList()
                }.growX()
                top.button(Icon.list, Styles.emptyi, 32f){
                    orderDate = !orderDate
                    rebuildList()
                }.update{ b -> b.style.imageUp = (if(orderDate) Icon.list else Icon.star) }
                    .size(48f).get()
                    .addListener(Tooltip{ tip ->
                        tip.label{ if(orderDate) "@mods.browser.sortdate" else "@mods.browser.sortstars" }.left()
                    })
                top.button(Icon.list, Styles.emptyi, 32f){
                    reverse = !reverse
                    rebuildList()
                }.update{ b -> b.style.imageUp = (if(reverse) Icon.upOpen else Icon.downOpen) }
                    .size(48f).get()
                    .addListener(Tooltip{ tip ->
                        tip.label{ if(reverse) "@misc.reverse" else "@misc.sequence" }.left()
                    })
                top.check(Core.bundle["dialog.mods.hideInvalid"], hideInvalid){
                    hideInvalid = it
                    rebuildList()
                }
                top.check(Core.bundle["client.sonka.packs.tab"], packsOnly){
                    packsOnly = it
                    rebuildList()
                }.padLeft(12f)
                //подфильтр текстуры/музыка/звуки, виден только во вкладке паков
                fun sub(icon: arc.scene.style.Drawable, bit: Int, tip: String){
                    top.button(icon, Styles.clearTogglei, 24f){
                        packMask = packMask xor bit
                        rebuildList()
                    }.size(40f).padLeft(4f).checked{ (packMask and bit) != 0 }.visible{ packsOnly }
                        .get().addListener(Tooltip{ t -> t.background(Styles.black6).margin(6f).add(tip) })
                }
                sub(Icon.imageSmall, PackScan.TEXTURES, Core.bundle["client.sonka.packs.sub.textures"])
                sub(Icon.musicSmall, PackScan.MUSIC, Core.bundle["client.sonka.packs.sub.music"])
                sub(Icon.playSmall, PackScan.SOUNDS, Core.bundle["client.sonka.packs.sub.sounds"])
            }.growX().padLeft(40f).padRight(40f)
            main.row()
            main.line(Pal.accent, true, 4f).padTop(4f)
            main.row()
            main.add(ScrollPane(CullTable{ list ->
                list.top().defaults().fill()

                val n = max((Core.graphics.width/Scl.scl(540f)).toInt(), 1)

                rebuildList = {
                    var favCols: Array<Table>? = null
                    var normCols: Array<Table>? = null

                    list.clearChildren()
                    list.add(" " + Core.bundle["dialog.mods.favorites"]).color(Pal.accent).padLeft(26f)
                    list.row()
                    list.line(Pal.accent, true, 4f).pad(6f).padLeft(20f).padRight(20f)
                    list.row()
                    list.cullTable{ fav ->
                        if(favoritesMods.isEmpty){
                            fav.table{ tab ->
                                tab.image(Icon.box).size(46f).color(Pal.accent)
                                tab.add(Core.bundle["dialog.mods.noFavorites"]).pad(36f).padLeft(12f)
                            }.fill().colspan(n)
                            fav.row()
                        }

                        fav.defaults().width(min(540f, (Core.graphics.width - 80f)/Scl.scl())).fillY().pad(6f)
                        favCols = Array(n){
                            fav.cullTable(HeAssets.grayUIAlpha){ it.top().defaults().growX().fillY() }.get()
                        }
                    }
                    list.row()
                    list.add(" " + Core.bundle["dialog.mods.mods"]).color(Pal.accent).padLeft(26f)
                    list.row()
                    list.line(Pal.accent, true, 4f).pad(6f).padLeft(20f).padRight(20f)
                    list.row()
                    list.cullTable{ norm ->
                        norm.top().defaults().width(min(540f, (Core.graphics.width - 80f)/Scl.scl())).fillY().pad(6f)
                        normCols = Array(n){
                            norm.cullTable(HeAssets.grayUIAlpha){ it.top().defaults().growX().fillY() }.get()
                        }
                    }
                    getModList(
                        errHandler = { e ->
                            Log.err(e)
                            list.clearChildren()
                            list.image(HeAssets.networkError).size(48f).pad(6f).color(Color.red)
                            list.add(Core.bundle["dialog.mods.checkFailed"], Styles.outlineLabel)
                        }
                    ){ ls ->
                        var favI = 0
                        var normI = 0

                        ls.values()
                            .filter{
                                search.isBlank()
                                || it.name.lowercase().contains(search)
                                || it.internalName.lowercase().contains(search)
                            }
                            .filter{ !hideInvalid || ModStat.run{ it.checkStatus().isValid() } }
                            .filter{ !packsOnly || packMatches(it) }
                            .let{ l ->
                                if(reverse){
                                    if(orderDate) l.reversed()
                                    else l.sortedBy{ it.stars }
                                }else{
                                    if(orderDate) l
                                    else l.sortedBy{ -it.stars }
                                }
                            }
                            .forEach{ m ->
                                val col =
                                    if(favoritesMods.contains(Name(m))) favCols!![favI++%n]
                                    else normCols!![normI++%n]
                                val tab = buildModTab(m)

                                col.add(tab).growX().fillY().pad(4f).row()
                            }
                    }
                }

                rebuildList()
            }, Styles.smallPane)).growY().fillX()
            main.row()
            main.line(Color.gray, true, 4f).padTop(6f).padBottom(6f)
            main.row()
            main.table{ bot ->
                bot.defaults().width(242f).height(62f).pad(6f)
                bot.button(Core.bundle["back"], Icon.leftOpen, Styles.grayt, 46f)
                { hide() }
                bot.button(Core.bundle["dialog.mods.refresh"], Icon.refresh, Styles.grayt, 46f){
                    ModsDialogHelper.resetModListCache()
                    browserTabs.clear()

                    rebuildList()
                }
                if(Core.graphics.isPortrait) bot.row()
                bot.button(Core.bundle["dialog.mods.importFav"], Icon.download, Styles.grayt, 46f){
                    importFavorites()
                }
                bot.button(Core.bundle["dialog.mods.exportFav"], Icon.export, Styles.grayt, 46f){
                    exportFavorites()
                }
            }.growX().fillY()
        }.grow()
    }

    private fun importFavorites(){
        UIUtils.showInput(
            Core.bundle["dialog.mods.importFav"],
            Core.bundle["dialog.mods.inputFavText"],
            true
        ){ d, t ->
            val repos = t.split(";").map{ it.trim() }.filter{ it.isNotBlank() }.toSet()
            getModList{ list ->
                list.values()
                    .filter{ repos.contains(it.repo) }
                    .forEach{ m -> favoritesMods.add(Name(m)) }

                saveFavorites()
                rebuildList()
                d.hide()
            }
        }
    }

    private fun exportFavorites(){
        if(favoritesMods.isEmpty){
            UIUtils.showTip(
                null,
                Core.bundle["dialog.mods.noFavorites"]
            )
            return
        }

        //экспортируем в формате репозиториев (author/repo), который принимает importFavorites
        val mods = StringBuilder()
        getModList{ list ->
            list.values().forEach{ m ->
                if(favoritesMods.contains(Name(m))) mods.append(m.repo).append(";\n")
            }

            UIUtils.showPane(
                Core.bundle["dialog.mods.exportFav"],
                UIUtils.closeBut,
                ButtonEntry(Core.bundle["misc.copy"], Icon.copy){
                    Vars.ui.showInfoFade(Core.bundle["infos.copied"])
                    Core.app.clipboardText = mods.toString()
                },
                ButtonEntry(Core.bundle["misc.save"], Icon.file){
                    //file chooser форка переписан на builder-паттерн (mindustry.ui.FileChooser)
                    mindustry.ui.FileChooser.save("txt").submit{ f ->
                        f.writeString(mods.toString(), false)
                    }
                }
            ){ t ->
                t.add(Core.bundle["dialog.mods.favoritesText"]).growX().pad(6f).left()
                    .labelAlign(Align.left).color(Color.lightGray)
                t.row()
                t.table(HeAssets.darkGrayUIAlpha){ l ->
                    l.left().top().add(mods.toString()).pad(6f).wrap().growX()
                }.margin(12f).minWidth(420f).growX()
            }
        }
    }

    /**
     * Попадает ли мод во вкладку паков: проверенный состав репозитория (кэш) имеет приоритет над
     * догадкой по описанию; подфильтр сверяется с реальными категориями у проверенных и с маской
     * ключевых слов у остальных.
     */
    private fun packMatches(mod: ModListing): Boolean{
        val checked = PackScan.cachedRemote(mod)
        if(checked != null){
            if(!checked.isPack()) return false
            var mask = 0
            if(checked.textures()) mask = mask or PackScan.TEXTURES
            if(checked.music.size > 0 || checked.newMusic > 0) mask = mask or PackScan.MUSIC
            if(checked.sounds.size > 0 || checked.newSounds > 0) mask = mask or PackScan.SOUNDS
            if(mask == 0) mask = PackScan.TEXTURES //прочие подмены (шейдеры и т.п.) - показываем вместе с текстурами
            return (mask and packMask) != 0
        }
        return (PackScan.guess(mod) and packMask) != 0
    }

    private fun buildModTab(mod: ModListing): Table{
        browserTabs[mod]?.also{ return it }

        val modName = Name(mod)
        val res = Table()
        val stat = mod.checkStatus()
        var coll: HeCollapser? = null
        var setupContent = { _: Int -> }

        browserTabs.put(mod, res)

        val image = Downloader.downloadLazyDrawable(iconUrl(mod.repo), Core.atlas.find("nomap"))
        val loaded = Vars.mods.getMod(mod.internalName)

        res.button(
            { top ->
                top.table(Tex.buttonSelect){ icon ->
                    icon.stack(
                        Image(image).setScaling(Scaling.fit),
                        Table{ stars ->
                            stars.bottom().left()
                            buildStars(stars, mod)
                        }
                    ).size(80f)
                }.pad(10f).margin(4f).size(88f)
                top.stack(
                    Table{ info ->
                        info.left().top().margin(12f).marginLeft(6f).defaults().left()
                        info.add(mod.name).color(Pal.accent).growX().labelAlign(Align.left).padRight(160f).wrap()
                        info.row()
                        info.add(mod.version ?: "", 0.8f).color(Color.lightGray).growX().padRight(50f).wrap()
                        info.row()
                        info.add(mod.shortDesc()).growY().growX().padRight(50f).wrap()
                    },
                    Table{ over ->
                        over.right()

                        over.table{ status ->
                            status.top().defaults().size(26f).pad(4f)

                            loaded?.also{ l ->
                                if(l.meta.version != mod.version){
                                    status.image(Icon.starSmall).scaling(Scaling.fit).color(HeAssets.lightBlue)
                                        .addTip(Core.bundle["dialog.mods.newVersion"])
                                }else{
                                    status.image(Icon.okSmall).scaling(Scaling.fit).color(Pal.heal)
                                        .addTip(Core.bundle["dialog.mods.installed"])
                                }
                            }

                            buildModAttrIcons(status, stat)
                            PackUi.browserBadges(status, mod)
                        }.fill().pad(4f)

                        over.table{ side ->
                            side.line(Color.darkGray, false, 3f)
                            side.table{ buttons2 ->
                                buttons2.defaults().size(48f)
                                buttons2.button(Icon.star, Styles.clearNonei, 24f){
                                    if(!favoritesMods.add(modName)) favoritesMods.remove(modName)
                                    saveFavorites()

                                    rebuildList()
                                }.update{ b ->
                                    b.image.setScale(0.9f)
                                    b.style.imageUpColor = if(favoritesMods.contains(modName)) Pal.accent else Color.white
                                }

                                buttons2.row()
                                buttons2.button(Icon.downloadSmall, Styles.clearNonei, 48f){
                                    showDownloadModDialog(mod){
                                        browserTabs.clear()
                                        onInstalledChanged?.run()
                                        rebuildList()
                                    }
                                }
                                buttons2.row()

                                buttons2.addEventBlocker()
                            }.fill()
                        }.fill()
                    }
                ).grow()
            }, Styles.grayt){
            coll!!.toggle()
            if(!coll!!.collapse){
                setupContent(0)
            }
        }.growX().fillY()

        res.row()
        coll = res.add(HeCollapser(collX = false, collY = true, collapsed = true, Styles.grayPanel){ col ->
            col.table{ details ->
                details.left().defaults().growX().pad(4f).padLeft(12f).padRight(12f)

                details.add(Core.bundle.format("dialog.mods.author", mod.author ?: "???"))
                    .growX().padRight(50f).wrap().color(Pal.accent).labelAlign(Align.left)
                details.row()
                details.table{ link ->
                    link.left().image(Icon.githubSmall).scaling(Scaling.fit).size(24f).color(Color.lightGray)
                    val linkButton = link.button("...", Styles.nonet){}
                        .padLeft(4f).wrapLabel(true)
                        .growX().left().align(Align.left).height(30f).disabled(true).get()

                    linkButton.label.setAlignment(Align.left)
                    linkButton.label.setFontScale(0.9f)

                    val url = "https://github.com/${mod.repo}"
                    linkButton.isDisabled = false
                    linkButton.setText(url)
                    linkButton.clicked{ Core.app.openURI(url) }
                }
                details.row()
                details.table{ status ->
                    status.left().defaults().left()

                    loaded?.also{ l ->
                        if(l.meta.version != mod.version){
                            buildStatus(status, Icon.starSmall, Core.bundle["dialog.mods.newVersion"], HeAssets.lightBlue)
                        }else{
                            buildStatus(status, Icon.okSmall, Core.bundle["dialog.mods.installed"], Pal.heal)
                        }
                    }

                    buildModAttrList(status, stat)
                }
                details.row()
                //sonka: состав репозитория (текстуры/музыка/звуки) - из кэша или по кнопке проверки;
                //после проверки карточка пересобирается, чтобы бейджи стали «проверенными»
                details.table{ packT ->
                    PackUi.remoteCheck(packT, mod){
                        Core.app.post{
                            browserTabs.remove(mod)
                            rebuildList()
                        }
                    }
                }
                details.row()
                details.line(Color.gray, true, 4f).pad(6f).padLeft(-6f).padRight(-6f)
                details.row()

                var current = -1
                details.table{ switch ->
                    switch.left().defaults().center()
                    switch.button({ it.add(Core.bundle["dialog.mods.description"], 0.85f) }, switchBut){ setupContent(0) }
                        .margin(12f).checked{ current == 0 }.disabled{ t -> t.isChecked }
                    switch.button({ it.add(Core.bundle["dialog.mods.rawText"], 0.85f) }, switchBut){ setupContent(1) }
                        .margin(12f).checked{ current == 1 }.disabled{ t -> t.isChecked }
                }.grow().padBottom(0f)
                details.row()
                details.table(HeAssets.grayUI){ desc ->
                    desc.defaults().grow()
                    setupContent = a@{ i ->
                        if(i == current) return@a

                        desc.clearChildren()
                        current = i

                        when(i){
                            0 -> desc.add(markupTable(mod.description ?: ""))
                            1 -> desc.add(mod.description ?: "").wrap()
                        }
                    }
                }.grow().margin(12f).padTop(0f)
            }.grow()
        }.also{ it.setDuration(0.3f, Interp.pow3Out) }).growX().fillY().colspan(2).get()

        return res
    }

    private class CullTable : Table{
        constructor(background: Drawable?) : super(background)
        constructor(build: Cons<Table>) : super(build)
        constructor(background: Drawable?, build: Cons<Table>) : super(background, build)

        override fun drawChildren(){
            cullingArea?.also{ widgetAreaBounds ->
                children.forEach{ widget ->
                    if(widget is Group){
                        val set = widget.cullingArea ?: Rect()
                        set.set(widgetAreaBounds)
                        set.x -= widget.x
                        set.y -= widget.y
                        widget.setCullingArea(set)
                    }
                }
            }
            super.drawChildren()
        }
    }
}
