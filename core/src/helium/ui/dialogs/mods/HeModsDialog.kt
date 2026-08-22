package helium.ui.dialogs.mods

import arc.Core
import arc.func.Boolp
import arc.func.Cons
import arc.graphics.Color
import arc.graphics.g2d.TextureRegion
import arc.math.Interp
import arc.scene.actions.Actions
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.Image
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import arc.struct.ObjectMap
import arc.util.Align
import arc.util.Http
import arc.util.Log
import arc.util.Scaling
import arc.util.serialization.Jval
import helium.HeVars
import helium.ui.ButtonEntry
import helium.ui.HeAssets
import helium.ui.HeCollapser
import helium.ui.UIUtils
import helium.ui.UIUtils.closeBut
import helium.ui.UIUtils.line
import helium.ui.dialogs.HeAttachedDialog
import helium.ui.dialogs.mods.ModsDialogHelper.addTip
import helium.ui.dialogs.mods.ModsDialogHelper.buildDescSelector
import helium.ui.dialogs.mods.ModsDialogHelper.buildErrorIcons
import helium.ui.dialogs.mods.ModsDialogHelper.buildLinkButton
import helium.ui.dialogs.mods.ModsDialogHelper.buildModAttrIcons
import helium.ui.dialogs.mods.ModsDialogHelper.buildModAttrList
import helium.ui.dialogs.mods.ModsDialogHelper.buildModBasicStatus
import helium.ui.dialogs.mods.ModsDialogHelper.buildModErrList
import helium.ui.dialogs.mods.ModsDialogHelper.buildStatus
import helium.ui.dialogs.mods.ModsDialogHelper.getModList
import helium.ui.dialogs.mods.ModsDialogHelper.markupTable
import helium.ui.dialogs.mods.ModsDialogHelper.setupContentsList
import helium.ui.dialogs.mods.ModsDialogHelper.showDownloadModDialog
import helium.ui.dialogs.mods.ModsDialogHelper.tryCompareVersion
import helium.util.LOCAL_FILE
import helium.util.ModStat
import helium.util.UP_TO_DATE
import helium.util.set
import helium.util.addEventBlocker
import mindustry.Vars
import mindustry.Vars.modGuideURL
import mindustry.ctype.UnlockableContent
import mindustry.gen.Icon
import mindustry.gen.Tex
import mindustry.graphics.Pal
import mindustry.mod.ModListing
import mindustry.mod.Mods
import mindustry.ui.Styles
import sonkaextras.packs.PackScan
import sonkaextras.packs.PackUi

/**
 * Порт helium.ui.dialogs.mods.HeModsDialog (Helium, EB-wilson): переработанный менеджер модов -
 * два столбца включённых/выключенных, раскрывающиеся карточки с иконками статуса, проверкой
 * обновлений по листингу, описанием/списком контента, шарингом ссылки на репозиторий.
 * Подменяет собой ванильный ModsDialog при включённой настройке (см. HeAttachedDialog);
 * кнопка «Ванильный менеджер» открывает оригинал разово (замена fillDefaultSwitch UniverseKit).
 *
 * Отличия от оригинала:
 * <ul>
 * <li>markdown-рендер описаний (universe.ui.markdown) заменён клиентским StupidMarkupParser
 *     с фоллбеком в plain text (вкладка "Raw Text" осталась);</li>
 * <li>вместо «перезапустите игру» - живой Vars.mods.reload() при закрытии, как у ванильного
 *     диалога ЭТОГО форка;</li>
 * <li>кнопка экспорта модпака убрана - ModPackerDialog не портирован (тянет шаблонный
 *     ModpackModel.jar мода);</li>
 * <li>спец-обработка удаления самого мода Helium убрана - мы не мод.</li>
 * </ul>
 */
class HeModsDialog : HeAttachedDialog(Vars.ui.mods, Core.bundle["mods"], Boolp{ HeVars.modsDialogEnabled }){
    val browser = HeModsBrowser()

    private val modTabs = ObjectMap<Mods.LoadedMod, Table>()
    private val updateChecked = ObjectMap<Mods.LoadedMod, UpdateEntry>()

    private val shouldReload get() = Vars.mods.requiresReload()
    private lateinit var tipTable: Table
    private lateinit var disabledMods: Table
    private lateinit var enabledMods: Table

    private var searchStr = ""
    /** sonka: фильтр «только текстурпаки» (ресурс-паки по составу файлов, см. sonkaextras.packs.PackScan). */
    private var packsOnly = false

    init{
        HeAssets.ensure()

        resizedShown(::rebuild)

        hidden{
            //как ванильный ModsDialog форка: живая перезагрузка модов вместо требования рестарта
            if(shouldReload){
                Vars.mods.reload()
            }
        }
    }

    fun rebuild(){
        getCell(buttons)?.padBottom(if(Core.graphics.isPortrait) 29f else 3f)

        cont.clearChildren()

        cont.table{ main ->
            if(Core.graphics.isPortrait){
                main.stack(
                    Table(HeAssets.grayUIAlpha){ list ->
                        list.top().margin(6f)

                        var coll: HeCollapser? = null
                        list.button(Core.bundle["dialog.mods.menu"], Icon.menuSmall, Styles.flatt){
                            coll?.toggle()
                        }.growX().height(38f).margin(8f).update{ b ->
                            b.find<Image>{ it is Image }?.setDrawable(if(coll?.collapse != false) Icon.menuSmall else Icon.upOpen)
                        }
                        list.row()
                        list.add(HeCollapser(collX = false, collY = true, collapsed = true){ collT ->
                            collT.pane(Styles.smallPane){ pane ->
                                pane.defaults().growX().fillY().pad(4f)
                                pane.add(Core.bundle["dialog.mods.importMod"]).color(Color.gray)
                                pane.row()
                                pane.line(Color.gray, true, 4f).padTop(6f).padBottom(6f)
                                pane.row()
                                pane.button(Core.bundle["mods.browser"], Icon.planet, Styles.grayt, 46f){
                                    browser.show()
                                }.margin(4f)
                                pane.row()
                                pane.button(Core.bundle["mod.import.file"], Icon.file, Styles.grayt, 46f){
                                    importFile()
                                }.margin(4f)
                                pane.row()
                                pane.button(Core.bundle["mod.import.github"], Icon.download, Styles.grayt, 46f){
                                    importGithub()
                                }.margin(4f)
                                pane.row()

                                pane.add(Core.bundle["dialog.mods.otherHandle"]).color(Color.gray)
                                pane.row()
                                pane.line(Color.gray, true, 4f).padTop(6f).padBottom(6f)
                                pane.row()
                                pane.button(Core.bundle["dialog.mods.vanillaDialog"], Icon.list, Styles.grayt, 46f)
                                { showAttached() }.margin(4f)
                                pane.row()
                                pane.button(Core.bundle["mods.openfolder"], Icon.save, Styles.grayt, 46f)
                                { openFolder() }.margin(4f)
                                pane.row()
                                pane.button(Core.bundle["mods.guide"], Icon.link, Styles.grayt, 46f)
                                { Core.app.openURI(modGuideURL) }.margin(4f)
                            }.growX().fillY().maxHeight(400f)
                        }.setDuration(0.3f, Interp.pow3Out).also{ coll = it }).growX().fillY()
                        list.row()
                        list.line(Pal.darkerGray, true, 4f)
                        list.row()
                        list.check(Core.bundle["client.sonka.packs.only"], packsOnly){
                            packsOnly = it
                            rebuildMods()
                        }.left().pad(4f)
                        list.row()
                        list.pane(Styles.smallPane){ pane ->
                            pane.table{ en ->
                                en.add(Core.bundle["dialog.mods.enabled"]).color(Pal.accent).left().growX().labelAlign(Align.left)
                                en.row()
                                en.line(Pal.accent, true, 4f).padTop(6f).padBottom(6f)
                                en.row()
                                en.top().table{ enabled ->
                                    this.enabledMods = enabled
                                }.growX().fillY().top()
                            }.margin(6f).growX().fillY()
                            pane.row()
                            pane.table{ di ->
                                di.add(Core.bundle["dialog.mods.disabled"]).color(Pal.accent).left().growX().labelAlign(Align.left)
                                di.row()
                                di.line(Pal.accent, true, 4f).padTop(6f).padBottom(6f)
                                di.row()
                                di.top().table{ disabled ->
                                    this.disabledMods = disabled
                                }.growX().fillY().top()
                            }.margin(6f).growX().fillY()
                        }.growX().fillY().scrollX(false).scrollY(true).get().setForceScroll(true, true)
                    },
                    Table{ tip ->
                        tip.bottom().table(HeAssets.grayUIAlpha){ t ->
                            tipTable = t
                            t.visible = false
                        }.fillY().growX().margin(8f)
                    }
                ).grow()
                main.row()
                main.line(Pal.gray, true, 4f).pad(-8f).padTop(6f).padBottom(6f)
                main.row()
                main.table{ but ->
                    but.button(Core.bundle["back"], Icon.leftOpen, Styles.grayt, 46f)
                    { hide() }.height(58f).pad(6f).growX()
                    but.button(Core.bundle["dialog.mods.refresh"], Icon.refresh, Styles.grayt, 46f)
                    { refresh() }.height(58f).pad(6f).growX()
                }.growX().fillY()
            }else{
                main.table{ search ->
                    search.image(Icon.zoom).size(64f).scaling(Scaling.fit)
                    search.field(""){
                        searchStr = it.lowercase()
                        rebuildMods()
                    }.growX()
                    search.check(Core.bundle["client.sonka.packs.only"], packsOnly){
                        packsOnly = it
                        rebuildMods()
                    }.padLeft(12f)
                }.growX().fillY().padLeft(16f).padRight(16f)
                main.row()

                main.stack(
                    Table{ mods ->
                        fun buildModsLayout(list: Table, reback: Cons<Table>){
                            list.line(Pal.accent, true, 4f).padTop(6f).padBottom(6f)
                            list.row()
                            list.top().pane(Styles.smallPane, reback)
                                .width(Core.graphics.width/2.8f/arc.scene.ui.layout.Scl.scl())
                                .fillY().top().get().setForceScroll(false, true)
                        }

                        mods.table{ left ->
                            left.table(HeAssets.grayUIAlpha){ list ->
                                list.add(Core.bundle["dialog.mods.enabled"]).color(Pal.accent)
                                list.row()
                                buildModsLayout(list){
                                    this.enabledMods = it
                                }
                            }.fillX().growY()
                        }.fillX().growY()
                        mods.line(Color.gray, false, 4f).padLeft(6f).padRight(6f)

                        mods.table{ right ->
                            right.table(HeAssets.grayUIAlpha){ list ->
                                list.add(Core.bundle["dialog.mods.disabled"]).color(Pal.accent)
                                list.row()
                                buildModsLayout(list){
                                    this.disabledMods = it
                                }
                            }.fillX().growY()
                        }.fillX().growY()
                    },
                    Table{ tip ->
                        tip.bottom().table(HeAssets.grayUIAlpha){ t ->
                            tipTable = t
                            t.visible = false
                        }.fillY().growX().margin(8f)
                    }
                ).grow()

                main.row()
                main.line(Pal.gray, true, 4f).pad(-8f).padTop(6f).padBottom(6f)
                main.row()
                main.table{ buttonsT ->
                    buttonsT.table{ top ->
                        top.defaults().growX().height(54f).pad(4f)
                        top.button(Core.bundle["mods.browser"], Icon.planet, Styles.flatBordert, 46f){
                            browser.show()
                        }.margin(8f)
                        top.button(Core.bundle["mod.import.file"], Icon.file, Styles.flatBordert, 46f){
                            importFile()
                        }.margin(8f)
                        top.button(Core.bundle["mod.import.github"], Icon.download, Styles.flatBordert, 46f){
                            importGithub()
                        }.margin(8f)
                    }.growX().fillY().padBottom(6f)
                    buttonsT.row()
                    buttonsT.table{ bot ->
                        bot.defaults().growX().height(62f).pad(4f)
                        bot.button(Core.bundle["back"], Icon.leftOpen, Styles.grayt, 46f)
                        { hide() }
                        bot.button(Core.bundle["dialog.mods.refresh"], Icon.refresh, Styles.grayt, 46f)
                        { refresh() }
                        bot.button(Core.bundle["dialog.mods.vanillaDialog"], Icon.list, Styles.grayt, 46f)
                        { showAttached() }
                        bot.button(Core.bundle["mods.openfolder"], Icon.save, Styles.grayt, 46f)
                        { openFolder() }
                        bot.button(Core.bundle["mods.guide"], Icon.link, Styles.grayt, 46f)
                        { Core.app.openURI(modGuideURL) }
                    }.growX().fillY()
                }.growX().fillY().colspan(3)
            }

            main.update{
                if(!tipTable.visible && shouldReload){
                    tipTable.visible = true
                    tipTable.color.a = 0f
                    tipTable.clearChildren()
                    tipTable.add(Core.bundle["dialog.mods.shouldReload"]).color(Color.crimson)
                    tipTable.actions(Actions.alpha(1f, 0.3f, Interp.pow3Out))
                }
            }
        }.also{
            if(Core.graphics.isPortrait) it.grow()
            else it.fillX().growY()
        }

        rebuildMods()
    }

    private fun refresh(){
        ModsDialogHelper.resetModListCache()
        PackScan.clearCache()
        modTabs.clear()
        updateChecked.clear()
        rebuildMods()
    }

    fun rebuildMods(){
        enabledMods.clearChildren()
        disabledMods.clearChildren()

        Vars.mods.list()
            .filter{
                searchStr.isBlank()
                || it.name.lowercase().contains(searchStr)
                || it.meta.displayName.lowercase().contains(searchStr)
            }
            .filter{ !packsOnly || PackScan.of(it).isPack() }
            .forEach{ mod ->
                ModStat.apply{
                    val stat = checkModStat(mod)

                    val addToTarget = if(mod.enabled() && stat.isValid()) enabledMods else disabledMods
                    val modTab = buildModTab(mod)

                    addToTarget.add(modTab).growX().fillY().pad(4f)
                    addToTarget.row()
                }
            }
    }

    private fun buildModTab(mod: Mods.LoadedMod): Table{
        modTabs[mod]?.also{ return it }

        val res = Table()
        var stat = ModStat.checkModStat(mod)
        var updateEntry: UpdateEntry? = null
        var coll: HeCollapser? = null
        var setupContent = { _: Int -> }
        //sonka: состав ресурс-пака (кэшируется в PackScan по файлу мода)
        val pack = PackScan.of(mod)

        modTabs[mod] = res

        res.button({ top ->
            top.table(Tex.buttonSelect){ icon ->
                icon.image(mod.iconTexture?.let{ TextureRegionDrawable(TextureRegion(it)) } ?: Tex.nomap)
                    .scaling(Scaling.fit).size(80f)
            }.pad(10f).margin(4f).size(88f)
            top.stack(
                Table{ info ->
                    info.left().top().margin(12f).marginLeft(6f).defaults().left()
                    info.add(mod.meta.displayName).color(Pal.accent).grow().padRight(160f).wrap()
                    info.row()
                    info.add(mod.meta.version ?: "", 0.8f).color(Color.lightGray).grow().padRight(50f).wrap()
                    info.row()
                    info.add(mod.meta.shortDescription()).grow().padRight(50f).wrap()
                },
                Table{ over ->
                    over.right()

                    over.table{ status ->
                        status.top().defaults().size(26f).pad(4f)

                        var updateTip: Label? = null
                        val checkUpdate = status.image(HeAssets.loading).color(Pal.accent)
                            .tooltip{ t ->
                                t.table(HeAssets.padGrayUIAlpha){ tip ->
                                    updateTip = tip.add(Core.bundle["dialog.mods.checkUpdating"], Styles.outlineLabel).get()
                                }
                            }.get()

                        buildModAttrIcons(status, stat)
                        PackUi.badges(status, pack)

                        checkModUpdate(mod, {
                            checkUpdate.drawable = HeAssets.networkError
                            checkUpdate.setColor(Color.red)
                            updateTip?.setText(Core.bundle["dialog.mods.checkUpdateFailed"])
                        }){ res2 ->
                            ModStat.apply{
                                if(res2.latestMod != null && res2.updateValid) stat = stat or UP_TO_DATE
                                if(res2.latestMod == null) stat = stat or LOCAL_FILE

                                if(stat.isUpToDate()){
                                    checkUpdate.drawable = Icon.upSmall
                                    checkUpdate.setColor(HeAssets.lightBlue)

                                    updateEntry = res2
                                    updateTip?.setText(Core.bundle.format("dialog.mods.updateValid", res2.latestMod!!.version))
                                }

                                if(stat.isLocalFile()){
                                    status.image(Icon.fileSmall).scaling(Scaling.fit)
                                        .color(Color.white)
                                        .addTip(Core.bundle["dialog.mods.localFile"])
                                }

                                if(stat.isValid()){
                                    if(!stat.isUpToDate()){
                                        if(stat.isEnabled()){
                                            checkUpdate.drawable = Icon.okSmall
                                            checkUpdate.setColor(Pal.heal)

                                            updateTip?.setText(Core.bundle["dialog.mods.isLatest"])
                                        }else checkUpdate.visible = false
                                    }
                                }else{
                                    checkUpdate.visible = false

                                    buildErrorIcons(status, stat)
                                }
                            }
                        }
                    }.fill().pad(4f)

                    over.table{ side ->
                        side.line(Color.darkGray, false, 3f)
                        side.table{ buttons2 ->
                            buttons2.defaults().size(48f)

                            ModStat.apply{
                                buttons2.button(Icon.rightOpen, Styles.clearNonei, 32f){
                                    Vars.mods.setEnabled(mod, !mod.enabled())
                                    rebuildMods()
                                }.update{ m -> m.style.imageUp = if(mod.enabled()) Icon.rightOpen else Icon.leftOpen }
                                    .disabled{ !mod.enabled() && !stat.isValid() }
                            }

                            buttons2.row()
                            buttons2.button(Icon.exportSmall, Styles.clearNonei, 48f){ shareMod(mod) }
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
            col.stack(
                Table{ details ->
                    ModStat.apply{
                        details.left().defaults().growX().pad(4f).padLeft(12f).padRight(12f)

                        details.add(Core.bundle.format("dialog.mods.author", mod.meta.author ?: "???"))
                            .growX().padRight(50f).wrap().color(Pal.accent).labelAlign(Align.left)
                        details.row()
                        details.table{ link ->
                            buildLinkButton(link, mod)
                        }
                        details.row()
                        details.table{ status ->
                            status.left().defaults().left()

                            status.collapser(
                                { t ->
                                    t.left().defaults().left()
                                    buildStatus(t, Icon.upSmall, Core.bundle["dialog.mods.updateValidS"], HeAssets.lightBlue)
                                }, false
                            ){ stat.isUpToDate() }.fill().colspan(2)
                            status.row()

                            status.collapser(
                                { t ->
                                    t.left().defaults().left()
                                    buildStatus(t, Icon.fileSmall, Core.bundle["dialog.mods.localFile"], Color.white)
                                }, false
                            ){ stat.isLocalFile() }.fill().colspan(2)
                            status.row()

                            buildModBasicStatus(status, stat)
                            buildModAttrList(status, stat)
                            buildModErrList(status, stat)
                            status.table{ PackUi.statusLines(it, pack) }.growX().colspan(2)
                        }
                        details.row()
                        details.line(Color.gray, true, 4f).pad(6f).padLeft(-6f).padRight(-6f)
                        details.row()

                        val contents = if(stat.isEnabled()) Vars.content.contentMap.map{ it.toList() }
                            .flatten()
                            .filterIsInstance<UnlockableContent>()
                            .filter{ c -> c.minfo.mod === mod && !c.isHidden }
                        else listOf()

                        var current = -1
                        val hasFiles = pack.textures() || pack.audio() || pack.other.size > 0
                        buildDescSelector(details, { current }, { i -> setupContent(i) }, contents,
                            if(hasFiles) Core.bundle["client.sonka.packs.files"] else null)
                        details.row()
                        details.table(HeAssets.grayUI){ desc ->
                            desc.defaults().grow()
                            setupContent = a@{ i ->
                                if(i == current) return@a

                                desc.clearChildren()
                                current = i

                                when(i){
                                    0 -> desc.add(markupTable(mod.meta.description ?: ""))
                                    1 -> desc.add(mod.meta.description ?: "").wrap()
                                    2 -> setupContentsList(desc, contents)
                                    3 -> desc.add(Table().also{ f -> PackUi.filesTable(f, pack) })
                                }
                            }
                        }.grow().margin(12f).padTop(0f)
                    }
                },
                Table{ conf ->
                    ModStat.apply{
                        conf.top().right().table{ l ->
                            l.line(Color.darkGray, false, 3f)
                            l.table{ buttons2 ->
                                buttons2.collapser({
                                    it.button(Icon.upSmall, Styles.clearNonei, 48f){
                                        val latest = updateEntry?.latestMod
                                        if(latest != null){
                                            showDownloadModDialog(latest){
                                                refresh()
                                            }
                                        }else UIUtils.showError(Core.bundle["dialog.mods.noDownloadLink"])
                                    }.size(48f).visible{ stat.isUpToDate() }
                                }, false){ stat.isUpToDate() }.fill()
                                buttons2.row()
                                buttons2.button(Icon.trashSmall, Styles.clearNonei, 48f){ deleteMod(mod) }.size(48f)
                            }.fill()
                        }.fill()
                    }
                }
            ).grow()
        }.also{ it.setDuration(0.3f, Interp.pow3Out) }).growX().fillY().colspan(2).get()

        return res
    }

    private fun openFolder(){
        val path = Vars.modDirectory.absolutePath()

        if(Core.app.isMobile){
            UIUtils.showPane(
                Core.bundle["dialog.mods.openFolderFailed"],
                closeBut,
                ButtonEntry(Core.bundle["misc.copy"], Icon.copy){
                    Core.app.clipboardText = path
                    Vars.ui.showInfoFade(Core.bundle["infos.copied"])
                }
            ){ t ->
                t.add(Core.bundle["dialog.mods.cantOpenOnAndroid"]).growX().pad(6f).left()
                    .labelAlign(Align.left).color(Color.lightGray)
                t.row()
                t.table(HeAssets.darkGrayUIAlpha){ l ->
                    l.image(Icon.folder).scaling(Scaling.fit).pad(6f).size(36f)
                    l.add(path).pad(6f)
                }.margin(12f)
            }
        }else Core.app.openFolder(path)
    }

    private fun importGithub(){
        var tipLabel: Label? = null

        UIUtils.showInput(
            Core.bundle["mod.import.github"],
            Core.bundle["dialog.mods.inputGithubLink"],
            buildContent = { cont2 ->
                cont2.add(HeCollapser(collX = false, collY = true, collapsed = true){
                    tipLabel = it.add("").growX().labelAlign(Align.left).pad(8f).get()
                }.setDuration(0.3f, Interp.pow3Out).setCollapsed{ tipLabel?.text?.isBlank() ?: true }).fillY().growX()
            }
        ){ dialog, txt ->
            tipLabel?.setText(Core.bundle["dialog.mods.parsing"])
            tipLabel?.setColor(Pal.accent)
            val link = if(txt.startsWith("https://")) txt.substring(8) else txt
            if(link.startsWith("github.com/")){
                val repo = link.substring(11).trim('/')
                Http.get(
                    Vars.ghApi + "/repos/" + repo + "/releases/latest",
                    { res ->
                        if(res.status != Http.HttpStatus.OK) throw Exception("not found")
                        val jval = Jval.read(res.getResultAsString())
                        val tagLink = "https://raw.githubusercontent.com/${repo}/${jval.getString("tag_name")}"

                        val modJ = tryList(
                            "$tagLink/mod.json",
                            "$tagLink/mod.hjson",
                            "$tagLink/assets/mod.json",
                            "$tagLink/assets/mod.hjson",
                        ) ?: throw Exception("not found")

                        var repoMeta: Jval? = null
                        Http.get(Vars.ghApi + "/repos/" + repo)
                            .error{
                                tipLabel?.setText(Core.bundle["dialog.mods.parseFailed"])
                                tipLabel?.setColor(Color.crimson)
                            }
                            .block{ repoMeta = Jval.read(it.getResultAsString()) }

                        val meta = repoMeta ?: throw Exception("no repo meta")
                        val lang = meta.getString("language", "")

                        val modInfo = ModListing().also{
                            it.repo = repo
                            it.internalName = modJ.getString("name")
                            it.name = modJ.getString("displayName", modJ.getString("name"))
                            it.author = modJ.getString("author", "???")
                            it.version = modJ.getString("version", "???")
                            it.lastUpdated = meta.getString("pushed_at", "???")
                            it.stars = meta.getInt("stargazers_count", 0)
                            it.description = modJ.getString("description", "")
                            it.minGameVersion = modJ.getString("minGameVersion", "0")
                            it.hasScripts = lang == "JavaScript"
                            it.hasJava = modJ.getBool("java", false)
                                         || lang == "Java"
                                         || lang == "Kotlin"
                                         || lang == "Groovy"
                                         || lang == "Scala"
                        }

                        Core.app.post{
                            dialog.hide()
                            showDownloadModDialog(modInfo){
                                refresh()
                            }
                        }
                    }
                ){
                    Core.app.post{
                        if(it is IllegalArgumentException) tipLabel?.setText(Core.bundle["dialog.mods.parseFailed"])
                        else tipLabel?.setText(Core.bundle["dialog.mods.checkFailed"])
                        tipLabel?.setColor(Color.crimson)
                    }
                }
            }else{
                tipLabel?.setText(Core.bundle["dialog.mods.parseFailed"])
                tipLabel?.setColor(Color.crimson)
            }
        }
    }

    private fun tryList(vararg queries: String): Jval?{
        var result: Jval? = null
        for(str in queries){
            Http.get(str)
                .timeout(10000)
                .error{ /* пробуем следующий вариант пути */ }
                .block{ out -> result = try{ Jval.read(out.getResultAsString()) }catch(e: Exception){ null } }
            if(result != null) return result
        }
        return null
    }

    private fun importFile(){
        //file chooser форка переписан на builder-паттерн (mindustry.ui.FileChooser)
        mindustry.ui.FileChooser.open("zip", "jar").submitMulti{ files ->
            for(file in files){
                try{
                    Vars.mods.importMod(file)
                }catch(e: Exception){
                    Log.err(e)
                    UIUtils.showException(
                        e, if(e.message != null && e.message!!.lowercase().contains("writable dex")) Core.bundle["error.moddex"] else ""
                    )
                }
            }
            modTabs.clear()
            rebuildMods()
        }
    }

    private fun deleteMod(mod: Mods.LoadedMod){
        UIUtils.showConfirm(Core.bundle["dialog.mods.deleteMod"], Core.bundle["mod.remove.confirm"]){
            Vars.mods.removeMod(mod)
            modTabs.clear()
            rebuildMods()
        }
    }

    private fun shareMod(mod: Mods.LoadedMod){
        UIUtils.showPane(
            Core.bundle["dialog.mods.shareMod"],
            closeBut,
        ){ t ->
            val image = mod.iconTexture?.let{
                TextureRegionDrawable(TextureRegion(it))
            } ?: Tex.nomap
            val stat = ModStat.checkModStat(mod)

            t.table(HeAssets.darkGrayUI){ cont2 ->
                cont2.table(Tex.buttonSelect){ icon ->
                    icon.image(image).scaling(Scaling.fit).size(80f)
                }.pad(10f).margin(4f).size(88f)
                cont2.stack(
                    Table{ info ->
                        info.left().top().margin(12f).marginLeft(6f).defaults().left()
                        info.add(mod.meta.displayName).color(Pal.accent).grow().padRight(160f).wrap()
                        info.row()
                        info.add(mod.meta.version ?: "", 0.8f).color(Color.lightGray).grow().padRight(50f).wrap()
                        info.row()
                        info.add(mod.meta.shortDescription()).grow().padRight(50f).wrap()
                    },
                    Table{ info ->
                        info.top().right().defaults().right().top()
                        info.table{ status ->
                            status.top().right().defaults().size(26f).pad(4f)

                            buildModAttrIcons(status, stat)
                        }.grow()
                    }
                ).pad(12f).padLeft(4f).growX().fillY().minWidth(420f)
            }.margin(6f).growX().fillY()

            t.row()
            t.button(
                Core.bundle["dialog.mods.github"],
                Icon.githubSmall,
                Styles.flatt
            ){ openGithubRepo(mod) }
                .growX().fillY().margin(8f).marginTop(12f).marginBottom(12f).padTop(8f)

            t.row()
            t.button(
                Core.bundle["dialog.mods.openFolder"],
                Icon.exportSmall,
                Styles.flatt
            ){ Core.app.openFolder(mod.file.absolutePath()) }
                .growX().fillY().margin(8f).marginTop(12f).marginBottom(12f).padTop(8f)
        }
    }

    private fun openGithubRepo(mod: Mods.LoadedMod){
        fun showLink(repo: String){
            val link = "https://github.com/$repo"

            UIUtils.showPane(
                Core.bundle["dialog.mods.exportLink"],
                closeBut,
                ButtonEntry(Core.bundle["misc.open"], Icon.link){
                    Core.app.openURI(link)
                },
                ButtonEntry(Core.bundle["misc.copy"], Icon.copy){
                    Core.app.clipboardText = link
                    Vars.ui.showInfoFade(Core.bundle["infos.copied"])
                }
            ){ t ->
                t.add(Core.bundle["dialog.mods.githubLink"]).growX().pad(6f).left()
                    .labelAlign(Align.left).color(Color.lightGray)
                t.row()
                t.table(HeAssets.darkGrayUIAlpha){ l ->
                    l.image(Icon.github).scaling(Scaling.fit).pad(6f).size(36f)
                    l.add(link).pad(6f)
                }.margin(12f)
            }
        }

        //в форке репозиторий обычно есть прямо в meta
        val metaRepo = mod.getRepo()
        if(metaRepo != null){
            showLink(metaRepo)
            return
        }

        getModList(
            errHandler = { e ->
                Log.err(e)
                UIUtils.showException(e, Core.bundle["dialog.mods.checkFailed"])
            }
        ){ list ->
            val info = list.get(Name(mod))

            if(info != null){
                showLink(info.repo)
            }else{
                UIUtils.showTip(
                    Core.bundle["dialog.mods.noLink"],
                    Core.bundle["dialog.mods.noGithubRepo"]
                )
            }
        }
    }

    private fun checkModUpdate(
        mod: Mods.LoadedMod,
        errorHandler: Cons<Throwable>,
        callback: Cons<UpdateEntry>,
    ){
        val res = updateChecked.get(mod)
        if(res != null) callback.get(res)
        else{
            getModList(
                errHandler = { e ->
                    Log.err(e)
                    errorHandler.get(e)
                }
            ){ list ->
                val modInfo = list[Name(mod)]

                val entry = if(modInfo == null) UpdateEntry(mod, false, null)
                else{
                    val isUpdate = mod.meta.version != modInfo.version
                                   && tryCompareVersion(mod.meta.version, modInfo.version) < 0

                    UpdateEntry(mod, isUpdate, modInfo)
                }

                updateChecked[mod] = entry
                callback.get(entry)
            }
        }
    }

    private data class UpdateEntry(
        val mod: Mods.LoadedMod,
        val updateValid: Boolean,
        val latestMod: ModListing?,
    )
}
