package helium.ui.dialogs.mods

import arc.Core
import arc.func.Cons
import arc.func.Func
import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.math.Mathf
import arc.scene.Element
import arc.scene.style.Drawable
import arc.scene.ui.Button
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import arc.struct.OrderedMap
import arc.struct.Seq
import arc.util.Http
import arc.util.Log
import arc.util.Scaling
import arc.util.Strings
import arc.util.Threads
import arc.util.Time
import arc.util.serialization.Json
import arc.util.serialization.Jval
import helium.util.set
import helium.ui.ButtonEntry
import helium.ui.HeAssets
import helium.ui.UIUtils
import helium.util.CLIENT_ONLY
import helium.util.DEPRECATED
import helium.util.Downloader
import helium.util.JAR_MOD
import helium.util.JS_MOD
import helium.util.ModStat
import helium.util.UNSUPPORTED
import helium.util.toStoreSize
import mindustry.Vars
import mindustry.core.Version
import mindustry.ctype.UnlockableContent
import mindustry.gen.Icon
import mindustry.mod.ModListing
import mindustry.mod.Mods
import mindustry.ui.Bar
import mindustry.ui.Styles
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * Порт helium.ui.dialogs.mods.ModsDialogHelper (Helium, EB-wilson): общие кирпичики
 * менеджера и браузера модов - иконки/списки статусов, ссылки на GitHub, сравнение версий,
 * кэшируемый листинг модов и диалог скачивания.
 *
 * Отличия от оригинала:
 * <ul>
 * <li>класс листинга - ванильный [mindustry.mod.ModListing] (наш браузер и апдейт-чекер форка
 *     парсят тот же mods.json Anuken/MindustryMods из Vars.modJsonURLs; свой список модов
 *     EB-wilson'а с полями subtitle/hidden не используется, поэтому clientOnly для записей
 *     браузера неизвестен и его иконка там не показывается);</li>
 * <li>иконки модов берутся с Anuken/MindustryMods/icons (мод для диалога скачивания ходил в
 *     собственное зеркало EB-wilson'а).</li>
 * </ul>
 */
object ModsDialogHelper{
    private val exec: ExecutorService = Threads.unboundedExecutor("HTTP", 1)

    var modList: OrderedMap<Name, ModListing>? = null
        private set

    val switchBut: Button.ButtonStyle by lazy{
        Button.ButtonStyle().also{
            HeAssets.ensure()
            it.up = Styles.none
            it.over = HeAssets.grayUIAlpha
            it.down = HeAssets.grayUI
            it.checked = HeAssets.grayUI
        }
    }

    /** Короткое описание с обрезкой - в ванильном ModListing нет subtitle, берём description. */
    fun ModListing.shortDesc(): String = Strings.truncate(
        if(description == null || description.length > Vars.maxModSubtitleLength) "" else description,
        Vars.maxModSubtitleLength, "..."
    )

    /** Порт ModListing.checkStatus() мода; clientOnly из листинга не узнать - бит ставится всегда,
     *  чтобы иконка "работает на сервере" в браузере не врала. */
    fun ModListing.checkStatus(): Int{
        var res = CLIENT_ONLY

        if(hasJava) res = res or JAR_MOD
        if(hasScripts) res = res or JS_MOD

        if(minMajor() < (if(hasJava) Vars.minJavaModGameVersion else Vars.minModGameVersion)) res = res or DEPRECATED
        if(!Version.isAtLeast(minGameVersion ?: "0")) res = res or UNSUPPORTED

        return res
    }

    private fun ModListing.minMajor(): Int{
        val ver = minGameVersion ?: "0"
        val dot = ver.indexOf(".")
        return if(dot != -1) Strings.parseInt(ver.take(dot), 0)
        else Strings.parseInt(ver, 0)
    }

    fun iconUrl(repo: String): String =
        "https://raw.githubusercontent.com/Anuken/MindustryMods/master/icons/" + repo.replace("/", "_")

    fun buildDescSelector(
        details: Table,
        get: () -> Int,
        set: (Int) -> Unit,
        contents: List<UnlockableContent>,
    ){
        details.table{ switch ->
            switch.left().defaults().center()
            switch.button({ it.add(Core.bundle["dialog.mods.description"], 0.85f) }, switchBut){ set(0) }
                .margin(12f).checked{ get() == 0 }.disabled{ t -> t.isChecked }
            switch.button({ it.add(Core.bundle["dialog.mods.rawText"], 0.85f) }, switchBut){ set(1) }
                .margin(12f).checked{ get() == 1 }.disabled{ t -> t.isChecked }
            if(contents.any()){
                switch.button({ it.add(Core.bundle["dialog.mods.contents"], 0.85f) }, switchBut){ set(2) }
                    .margin(12f).checked{ get() == 2 }.disabled{ t -> t.isChecked }
            }
        }.grow().padBottom(0f)
    }

    fun buildErrorIcons(status: Table, stat: Int){
        ModStat.apply{
            if(stat.isLibMissing()) status.image(Icon.layersSmall).scaling(Scaling.fit).color(Color.crimson)
                .addTip(Core.bundle["dialog.mods.libMissing"])
            else if(stat.isLibIncomplete()) status.image(Icon.warningSmall).scaling(Scaling.fit)
                .color(Color.crimson)
                .addTip(Core.bundle["dialog.mods.libIncomplete"])
            else if(stat.isLibCircleDepending()) status.image(Icon.refresh).scaling(Scaling.fit)
                .color(Color.crimson)
                .addTip(Core.bundle["dialog.mods.libCircleDepending"])

            if(stat.isError()) status.image(Icon.cancelSmall).scaling(Scaling.fit).color(Color.crimson)
                .addTip(Core.bundle["dialog.mods.error"])
            if(stat.isBlackListed()) status.image(Icon.infoCircle).scaling(Scaling.fit).color(Color.crimson)
                .addTip(Core.bundle["dialog.mods.blackListed"])
        }
    }

    fun buildLinkButton(link: Table, mod: Mods.LoadedMod){
        link.left().image(Icon.githubSmall).scaling(Scaling.fit).size(24f).color(Color.lightGray)
        val linkButton = link.button("...", Styles.nonet){}
            .padLeft(4f).padRight(50f).wrapLabel(true)
            .growX().left().align(arc.util.Align.left).height(30f).disabled(true).get()

        linkButton.label.setAlignment(arc.util.Align.left)
        linkButton.label.setFontScale(0.9f)

        fun apply(repo: String?){
            if(repo == null){
                linkButton.isDisabled = true
                linkButton.setText(Core.bundle["dialog.mods.noGithubRepo"])
            }else{
                val url = "https://github.com/$repo"
                linkButton.isDisabled = false
                linkButton.setText(url)
                linkButton.clicked{ Core.app.openURI(url) }
            }
        }

        //у форка репозиторий часто лежит прямо в meta мода - без сети
        val metaRepo = mod.getRepo()
        if(metaRepo != null){
            apply(metaRepo)
            return
        }

        getModList(
            errHandler = {
                linkButton.isDisabled = true
                linkButton.setText(Core.bundle["dialog.mods.checkFailed"])
            }
        ){ list ->
            apply(list[Name(mod)]?.repo)
        }
    }

    fun buildModAttrIcons(status: Table, stat: Int){
        ModStat.apply{
            if(stat.isJAR()) status.image(HeAssets.java).scaling(Scaling.fit).color(mindustry.graphics.Pal.reactorPurple)
                .addTip(Core.bundle["dialog.mods.jarMod"])
            if(stat.isJS()) status.image(HeAssets.javascript).scaling(Scaling.fit).color(mindustry.graphics.Pal.accent)
                .addTip(Core.bundle["dialog.mods.jsMod"])
            if(!stat.isClientOnly()) status.image(Icon.hostSmall).scaling(Scaling.fit).color(mindustry.graphics.Pal.techBlue)
                .addTip(Core.bundle["dialog.mods.hostMod"])

            if(stat.isDeprecated()) status.image(Icon.warningSmall).scaling(Scaling.fit).color(Color.crimson)
                .addTip(
                    Core.bundle.format(
                        "dialog.mods.deprecated",
                        if(stat.isJAR()) Vars.minJavaModGameVersion else Vars.minModGameVersion
                    )
                )
            else if(stat.isUnsupported()) status.image(Icon.warningSmall).scaling(Scaling.fit).color(Color.crimson)
                .addTip(Core.bundle["dialog.mods.unsupported"])
        }
    }

    fun buildModBasicStatus(status: Table, stat: Int){
        ModStat.apply{
            if(stat.isValid()){
                buildStatus(status, Icon.okSmall, Core.bundle["dialog.mods.modStatCorrect"], mindustry.graphics.Pal.heal)
            }else{
                buildStatus(status, Icon.cancelSmall, Core.bundle["dialog.mods.modStatError"], Color.crimson)
            }
        }
    }

    fun buildModAttrList(status: Table, stat: Int){
        ModStat.apply{
            if(stat.isJAR()){
                buildStatus(status, HeAssets.java, Core.bundle["dialog.mods.jarMod"], mindustry.graphics.Pal.reactorPurple)
            }
            if(stat.isJS()){
                buildStatus(status, HeAssets.javascript, Core.bundle["dialog.mods.jsMod"], mindustry.graphics.Pal.accent)
            }
            if(!stat.isClientOnly()){
                buildStatus(status, Icon.hostSmall, Core.bundle["dialog.mods.hostMod"], mindustry.graphics.Pal.techBlue)
            }
        }
    }

    fun buildModErrList(status: Table, stat: Int){
        ModStat.apply{
            if(stat.isDeprecated()){
                buildStatus(
                    status,
                    Icon.warningSmall,
                    Core.bundle.format(
                        "dialog.mods.deprecated",
                        if(stat.isJAR()) Vars.minJavaModGameVersion else Vars.minModGameVersion
                    ),
                    Color.crimson
                )
            }else if(stat.isUnsupported()){
                buildStatus(status, Icon.warningSmall, Core.bundle["dialog.mods.unsupported"], Color.crimson)
            }

            if(stat.isLibMissing()){
                buildStatus(status, Icon.layersSmall, Core.bundle["dialog.mods.libMissing"], Color.crimson)
            }else if(stat.isLibIncomplete()){
                buildStatus(status, Icon.warningSmall, Core.bundle["dialog.mods.libIncomplete"], Color.crimson)
            }else if(stat.isLibCircleDepending()){
                buildStatus(status, Icon.rotateSmall, Core.bundle["dialog.mods.libCircleDepending"], Color.crimson)
            }

            if(stat.isError()){
                buildStatus(status, Icon.cancelSmall, Core.bundle["dialog.mods.error"], Color.crimson)
            }
            if(stat.isBlackListed()){
                buildStatus(status, Icon.infoCircleSmall, Core.bundle["dialog.mods.blackListed"], Color.crimson)
            }
        }
    }

    fun buildStars(stars: Table, modInfo: ModListing){
        stars.add(object : Element(){
            override fun draw(){
                validate()
                Draw.color(Color.darkGray)
                Icon.starSmall.draw(
                    x - width*0.2f, y - height*0.2f,
                    0f, 0f, width, height,
                    1.4f, 1.4f, 0f
                )
                Draw.color(Color.white)
                Icon.starSmall.draw(x, y, width, height)
            }
        }).size(60f).pad(-16f)
        stars.add(modInfo.stars.toString(), Styles.outlineLabel, 0.85f)
            .bottom().padBottom(4f).padLeft(-2f)
    }

    fun buildStatus(status: Table, icon: Drawable, information: String, color: Color){
        status.image(icon).scaling(Scaling.fit).color(color).size(26f).pad(4f)
        status.add(information, 0.85f).color(color)
        status.row()
    }

    fun <T : Element> Cell<T>.addTip(tipText: String): Cell<T>{
        tooltip{ t ->
            t.table(HeAssets.padGrayUIAlpha){ tip ->
                tip.add(tipText, Styles.outlineLabel)
            }
        }

        return this
    }

    /** GitHub-markdown описаний через клиентский StupidMarkupParser (замена universe.ui.markdown UniverseKit). */
    fun markupTable(text: String): Table{
        return try{
            mindustry.client.ui.StupidMarkupParser.format(text)
        }catch(e: Throwable){
            Table().also{ t -> t.add(text).wrap().growX().labelAlign(arc.util.Align.topLeft) }
        }
    }

    fun resetModListCache(){
        modList = null
    }

    private object Lock

    fun getModList(
        index: Int = 0,
        refresh: Boolean = false,
        errHandler: Cons<Throwable>? = null,
        listener: Cons<OrderedMap<Name, ModListing>>,
    ){
        if(index >= Vars.modJsonURLs.size) return
        if(refresh) modList = null

        modList?.also{
            listener.get(it)
            return
        }

        exec.submit{
            synchronized(Lock){
                modList?.also{ list ->
                    Core.app.post{ listener.get(list) }
                    return@synchronized
                }

                val req = Http.get(Vars.modJsonURLs[index])
                req.error{ err ->
                    if(index < Vars.modJsonURLs.size - 1){
                        getModList(index + 1, false, errHandler, listener)
                    }else{
                        Core.app.post{ errHandler?.get(err) }
                    }
                }
                req.block{ response ->
                    val strResult = response.resultAsString
                    try{
                        val result = OrderedMap<Name, ModListing>()
                        @Suppress("UNCHECKED_CAST")
                        val list = Json().fromJson(Seq::class.java, ModListing::class.java, strResult) as Seq<ModListing>
                        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
                        val parser = Func<String, Date>{ text ->
                            try{
                                d.parse(text)
                            }catch(_: Exception){
                                Date()
                            }
                        }

                        list.sortComparing{ m -> parser.get(m!!.lastUpdated) }.reverse()
                        list.forEach{ result[Name(it)] = it }

                        modList = result
                        Core.app.post{ listener.get(result) }
                    }catch(e: Exception){
                        Core.app.post{ errHandler?.get(e) }
                    }
                }
            }
        }
    }

    fun setupContentsList(
        desc: Table,
        contents: List<UnlockableContent>,
    ){
        val n = (desc.width/arc.scene.ui.layout.Scl.scl(50f)).toInt().coerceAtLeast(1)
        contents.forEachIndexed{ i, c ->
            if(i > 0 && i%n == 0) desc.row()

            desc.button(arc.scene.style.TextureRegionDrawable(c.uiIcon), Styles.flati, Vars.iconMed){
                Vars.ui.content.show(c)
            }.size(50f).with{ im ->
                val click = im.clickListener
                im.update{
                    im.image.color.lerp(
                        if(!click.isOver) Color.lightGray else Color.white,
                        0.4f*Time.delta
                    )
                }
            }.tooltip(c.localizedName)
        }
    }

    private val paragraphMatcher = "pre-alpha|alpha|beta|rc|ga|pre-release|release|stable|hotfix|build|\\d+|\\w".toRegex()
    private val testLevel = mapOf(
        "pre-alpha" to 0,
        "alpha" to 1,
        "beta" to 2,
        "rc" to 3,
        "ga" to 4,
        "pre-release" to 5,
        "blank" to 6,
        "release" to 7,
        "stable" to 8,
        "hotfix" to 9
    )

    /** Нестрогое сравнение версий (порт tryCompareVersion мода): понимает alpha/beta/rc/etc. */
    fun tryCompareVersion(aVer: String?, bVer: String?): Int{
        if(aVer == null || bVer == null) return 1
        val aParagraph = paragraphMatcher.findAll(aVer.lowercase().trimStart('v')).map{
            it.value
        }.toList().also{ if(it.isEmpty()) return 1 }.filter{ it != "build" }
        val bParagraph = paragraphMatcher.findAll(bVer.lowercase().trimStart('v')).map{
            it.value
        }.toList().also{ if(it.isEmpty()) return 1 }.filter{ it != "build" }

        val maxSize = maxOf(aParagraph.size, bParagraph.size)

        (0 until maxSize).forEach{ i ->
            val pa = if(i >= aParagraph.size) "blank" else aParagraph[i]
            val pb = if(i >= bParagraph.size) "blank" else bParagraph[i]

            if(testLevel.containsKey(pa)){
                if(testLevel.containsKey(pb)){
                    val res = testLevel.getValue(pa) - testLevel.getValue(pb)
                    if(res > 0) return 1
                    else if(res < 0) return -1
                }else return 1
            }else{
                val na = Strings.parseInt(pa, Int.MIN_VALUE)
                val nb = Strings.parseInt(pb, Int.MIN_VALUE)
                if(na != Int.MIN_VALUE && nb != Int.MIN_VALUE){
                    val res = na - nb
                    if(res > 0) return 1
                    else if(res < 0) return -1
                }else{
                    val res = pa.compareTo(pb)
                    if(res > 0) return 1
                    else if(res < 0) return -1
                }
            }
        }

        return 0
    }

    fun showDownloadModDialog(modInfo: ModListing, callback: Runnable){
        var progress = 0f
        var complete = false
        var downloading = false
        var task: Future<*>? = null

        val loaded = Vars.mods.getMod(modInfo.internalName)
        val isUpdate =
            loaded != null
            && loaded.meta.version != modInfo.version
            && tryCompareVersion(loaded.meta.version, modInfo.version) < 0

        fun buildContent(content: Table){
            val image = Downloader.downloadLazyDrawable(iconUrl(modInfo.repo), Core.atlas.find("nomap"))

            content.table(HeAssets.darkGrayUIAlpha){ cont ->
                cont.table(mindustry.gen.Tex.buttonSelect){ icon ->
                    icon.image(image).scaling(Scaling.fit).size(80f)
                }.pad(10f).margin(4f).size(88f)
                cont.stack(
                    Table{ info ->
                        info.left().top().defaults().left().pad(3f)
                        info.add(modInfo.name).color(mindustry.graphics.Pal.accent)
                        info.row()

                        if(loaded != null){
                            if(isUpdate){
                                info.add("[lightgray]${loaded.meta.version}  >>>  [accent]${modInfo.version}")
                            }else{
                                info.add("[lightgray]${loaded.meta.version}  >>>  ${modInfo.version}" + Core.bundle["dialog.mods.reinstall"])
                            }
                        }else info.add(modInfo.version)

                        info.row()
                        info.table{ b ->
                            b.add(
                                Bar(
                                    {
                                        if(complete) Core.bundle["dialog.mods.downloadComplete"]
                                        else Core.bundle.format(
                                            "dialog.mods.downloading",
                                            if(progress < 0) (-progress).toStoreSize()
                                            else "${Mathf.round(progress*100)}%"
                                        )
                                    },
                                    { mindustry.graphics.Pal.accent },
                                    { if(progress < 0) 1f else progress }
                                )).growX().pad(6f).height(22f).visible{ downloading }
                        }.grow()
                    },
                    Table{ info ->
                        info.top().right().defaults().right().top()
                        info.table{ status ->
                            status.top().right().defaults().size(26f).pad(4f)
                            buildModAttrIcons(status, modInfo.checkStatus())
                        }.fill()
                        info.row()
                        info.table{ stars ->
                            stars.bottom().right()
                            buildStars(stars, modInfo)
                        }
                    }
                ).pad(12f).padLeft(4f).growX().fillY().minWidth(420f)
            }.margin(6f).growX().fillY()
        }

        UIUtils.showPane(
            Core.bundle[if(isUpdate) "dialog.mods.updateMod" else "dialog.mods.downloadMod"],
            ButtonEntry(
                Core.bundle["cancel"],
                Icon.cancel
            ){
                task?.cancel(true)
                it.hide()
            },
            ButtonEntry(
                Core.bundle["misc.download"],
                Icon.download,
                disabled = { downloading }
            ){
                downloading = true

                //sonka: скачивание в файл + импорт - вынесено в общую лямбду, т.к. теперь два пути
                //к ней ведут (Java-релиз и script-архив ветки, см. ниже)
                fun finishDownload(url: String, suffix: String){
                    val fi = Vars.modDirectory.child("tmp").child(modInfo.internalName + suffix)
                    Downloader.downloadToFile(
                        url, fi, true,
                        { p -> progress = p },
                        { e ->
                            if(e is InterruptedException) return@downloadToFile
                            Log.err(e)
                            Core.app.post{
                                UIUtils.showException(e, Core.bundle["dialog.mods.downloadFailed"])
                            }
                        }
                    ){
                        Core.app.post{
                            try{
                                if(isUpdate && loaded != null){
                                    Vars.mods.removeMod(loaded)
                                }
                                Vars.mods.importMod(fi)
                                fi.delete()
                                complete = true
                                callback.run()

                                it.hide()
                                UIUtils.showPane(
                                    Core.bundle[if(isUpdate) "dialog.mods.updateMod" else "dialog.mods.downloadMod"],
                                    ButtonEntry(Core.bundle["confirm"], Icon.ok){ d ->
                                        callback.run()
                                        d.hide()
                                    }
                                ){ t -> buildContent(t) }
                            }catch(e: Exception){
                                Log.err(e)
                                UIUtils.showException(e, Core.bundle["dialog.mods.downloadFailed"])
                            }
                        }
                    }
                }

                task = exec.submit{
                    //sonka: узнаём язык репозитория ПЕРЕД выбором стратегии скачивания - ровно как
                    //ваниль-браузер (ModsDialog.githubImport/githubImportJavaMod/githubImportBranch).
                    //Порт Helium раньше ВСЕГДА бил в /releases/latest - для script-модов (JS, не
                    //JVM-язык) это 404, если автор не публиковал GitHub Release (частый случай, см.
                    //aazamitsu/anime-units-display - ни одного релиза и ни одного тега), и установка
                    //ЛЮБОГО такого мода падала с "Failed to check for updates"-подобной ошибкой.
                    Http.get(Vars.ghApi + "/repos/" + modInfo.repo)
                        .error{ e ->
                            downloading = false
                            if(e is InterruptedException) return@error
                            Log.err(e)
                            Core.app.post{
                                UIUtils.showException(e, Core.bundle["dialog.mods.checkFailed"])
                            }
                        }
                        .block{ repoRes ->
                            val repoJson = Jval.read(repoRes.resultAsString)
                            val language = repoJson.getString("language", "<none>")
                            val defaultBranch = repoJson.getString("default_branch")
                            val isJvm = language == "Java" || language == "Kotlin" || language == "Groovy" || language == "Scala"

                            if(!isJvm){
                                //ваниль-фоллбек: архив ветки по умолчанию, без releases/latest
                                Http.get(Vars.ghApi + "/repos/" + modInfo.repo + "/zipball/" + defaultBranch)
                                    .error{ e ->
                                        downloading = false
                                        if(e is InterruptedException) return@error
                                        Log.err(e)
                                        Core.app.post{
                                            UIUtils.showException(e, Core.bundle["dialog.mods.checkFailed"])
                                        }
                                    }
                                    .block{ loc ->
                                        val redirect = loc.getHeader("Location")
                                        if(redirect != null){
                                            //Downloader сам качает по готовому URL - переиспользуем его (ретраи+прогресс)
                                            finishDownload(redirect, ".zip")
                                        }else{
                                            //редиректа не было - тело уже финальное, сохраняем как есть
                                            val fi = Vars.modDirectory.child("tmp").child(modInfo.internalName + ".zip")
                                            fi.write(false).use{ out -> loc.resultAsStream.copyTo(out) }
                                            Core.app.post{
                                                try{
                                                    if(isUpdate && loaded != null) Vars.mods.removeMod(loaded)
                                                    Vars.mods.importMod(fi)
                                                    fi.delete()
                                                    complete = true
                                                    callback.run()
                                                    it.hide()
                                                    UIUtils.showPane(
                                                        Core.bundle[if(isUpdate) "dialog.mods.updateMod" else "dialog.mods.downloadMod"],
                                                        ButtonEntry(Core.bundle["confirm"], Icon.ok){ d -> callback.run(); d.hide() }
                                                    ){ t -> buildContent(t) }
                                                }catch(e: Exception){
                                                    Log.err(e)
                                                    UIUtils.showException(e, Core.bundle["dialog.mods.downloadFailed"])
                                                }
                                            }
                                        }
                                    }
                                return@block
                            }

                            Http.get(Vars.ghApi + "/repos/" + modInfo.repo + "/releases/latest")
                                .error{ e ->
                                    downloading = false
                                    if(e is InterruptedException) return@error
                                    Log.err(e)
                                    Core.app.post{
                                        UIUtils.showException(e, Core.bundle["dialog.mods.checkFailed"])
                                    }
                                }
                                .block{ result ->
                                    val json = Jval.read(result.resultAsString)
                                    val assets = json.get("assets").asArray()

                                    val dexedAsset = assets.find{ j ->
                                        j.getString("name").startsWith("dexed")
                                        && j.getString("name").endsWith(".jar")
                                    }
                                    val jarAssets = dexedAsset ?: assets.find{ j ->
                                        j.getString("name").endsWith(".jar")
                                    }
                                    val asset = jarAssets ?: assets.find{ j ->
                                        j.getString("name").endsWith(".zip")
                                    }

                                    val suffix = if(dexedAsset == null && jarAssets == null) ".zip" else ".jar"

                                    val url = if(asset != null){
                                        asset.getString("browser_download_url")
                                    }else{
                                        json.getString("zipball_url")
                                    }

                                    finishDownload(url, suffix)
                                }
                        }
                }
            }
        ){ t -> buildContent(t) }
    }
}

/**
 * Ключ мода "автор-имя" (порт Name из ModsDialogHelper мода): нечувствителен к регистру,
 * автор "*" (неизвестен) матчится с любым - для сопоставления установленных модов с листингом.
 */
class Name(
    author: String,
    name: String,
){
    val author = author.lowercase()
    val name = name.lowercase()

    private val hash = this.name.hashCode()

    constructor(loaded: Mods.LoadedMod) : this(loaded.meta.author ?: "*", loaded.name)
    constructor(loaded: ModListing) : this(loaded.author ?: "*", loaded.internalName)

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean{
        if(this === other) return true
        if(other !is Name) return false

        if(author != "*" && other.author != "*" && author != other.author) return false
        if(name != other.name) return false

        return true
    }

    override fun toString() = "$author-$name"
}
