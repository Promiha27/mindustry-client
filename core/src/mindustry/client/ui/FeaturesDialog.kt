package mindustry.client.ui

import arc.*
import arc.graphics.*
import arc.input.*
import arc.scene.ui.*
import arc.scene.ui.layout.*
import helium.HeliumMod
import mindustry.Vars
import mindustry.client.*
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Fonts
import mindustry.ui.dialogs.*
import mindustrytool.MindustryToolMod
import qol.QolSuiteMod
import qolc.mlog.MlogLibrary
import qolc.palcolors.PalColorsFeature
import scheme.SchemeSizeMod
import scheme.SchemeVars
import sectorstats.CampaignUtilsMod
import sonkaextras.MenuUnitDialog
import sonkaextras.NameGradientDialog

object FeaturesDialog : BaseDialog("@client.features") {
    /** Тултип кнопок самоотключившегося пакета (установлен внешний одноимённый мод). */
    private const val STANDDOWN = "@client.features.mods.standdown"

    /** Тот же стиль, что у заголовков StupidMarkupParser, - секция «Вшитые моды» выглядит родной частью справки. */
    private val headingStyle = Label.LabelStyle(Fonts.def, Pal.accent)

    override fun show(): Dialog {
        cont.clear()
        buttons.clear()
        clearListeners()

        var str = Core.files.internal("features").readString("UTF-8")
        str = str.replace("\\{\\w+}".toRegex()) { res ->
            val value = res.value.removeSurrounding("{", "}")
            if (value == "p") return@replace ClientVars.clientCommandHandler.prefix // {p} becomes the client command prefix
            KeyBind.all.find { it.name == value }?.value?.key?.toString() ?: res.value // Keybind if it exists, keep as is otherwise
        }
        // одна общая вертикальная прокрутка: сверху секция «Вшитые моды» (дискаверабилити + кнопки-действия),
        // ниже - прежняя справка по фичам клиента с хоткеями (наша секция дополняет её, не заменяет)
        cont.pane { p ->
            p.add(bakedMods()).growX().row()
            p.add(StupidMarkupParser.format(str)).growX()
        }.growX().get().setScrollingDisabled(true, false)
        addCloseButton()

        return super.show()
    }

    /**
     * Секция «Вшитые моды»: по строке на каждый вшитый пакет - имя, описание одной строкой и кнопки-
     * действия там, где у пакета есть свои диалоги/окна; порядок строк = порядок инстанцирования в
     * [mindustry.client.Main.init]. Тогл-фичи без диалогов здесь сознательно не дублируются - на них
     * одна кнопка перехода в Settings на вкладку «Моды» ([ModsSettings.openTab]).
     *
     * Любой пакет мог самоотключиться (guard при установленном внешнем одноимённом моде) - его кнопки
     * тогда заблокированы с тултипом-объяснением (образец поведения - qol.ui.Hub с окнами чужих
     * пакетов), имя помечено, описание потушено. Состояние вычисляется один раз на show(): guard'ы
     * отрабатывают в Main.init()/ClientLoadEvent и до перезапуска игры не меняются.
     */
    private fun bakedMods(): Table {
        val t = Table()
        t.margin(10f)
        t.left()

        t.table { head ->
            head.add(Label(Core.bundle["client.features.mods"], headingStyle)).left().growX()
            head.button("@client.features.mods.settings", Icon.settings) { ModsSettings.openTab() }
                .height(44f).minWidth(220f).right()
        }.growX().row()
        t.add("@client.features.mods.hint").left().growX().wrap().color(Color.lightGray).padTop(4f).padBottom(12f).row()

        t.table { list ->
            list.left()
            list.defaults().left().top().padBottom(10f)

            // qol-suite: hub != null <=> guard пройден и ClientLoadEvent отработал
            val qolOk = QolSuiteMod.hub != null
            modRow(list, Core.bundle["client.setting.modsec-qol.category"], Core.bundle["client.features.mod.qol.desc"], qolOk) { btns ->
                btns.button("@client.features.qol.hub", Icon.list) {
                    QolSuiteMod.hub?.attach()
                    hide() // прячем справочник, чтобы окно было видно (меню паузы под ним остаётся)
                }.disabled { !qolOk || !Vars.state.isGame() }
                    .tooltip(if (qolOk) "@client.features.ingame" else STANDDOWN)
            }

            modRow(list, Core.bundle["client.setting.modsec-eui.category"], Core.bundle["client.features.mod.eui.desc"],
                Vars.mods.locateMod("extended-ui") == null)

            val stats = CampaignUtilsMod.statsDialog()
            modRow(list, Core.bundle["client.setting.modsec-campaignutils.category"], Core.bundle["client.features.mod.campaignutils.desc"], stats != null) { btns ->
                btns.button("@campaignutils.sector-production-button", Icon.chartBar) { stats?.show() }
                    .disabled { stats == null }.standdownTooltip(stats != null)
            }

            val qolcOk = Vars.mods.locateMod("qol-control") == null
            modRow(list, Core.bundle["client.setting.modsec-qolc.category"], Core.bundle["client.features.mod.qolc.desc"], qolcOk) { btns ->
                btns.button("@qolc.palcolors.title", Icon.pencil) { PalColorsFeature.showDialog() }
                    .disabled { !qolcOk }.standdownTooltip(qolcOk)
                btns.button("@qolc.mlog.library-title", Icon.logic) { MlogLibrary.showLibraryDialog() }
                    .disabled { !qolcOk }.standdownTooltip(qolcOk)
            }

            modRow(list, Core.bundle["client.features.mod.mi2u.name"], Core.bundle["client.features.mod.mi2u.desc"],
                Vars.mods.locateMod("mi2-utilities-java") == null)

            // текущее значение бинда хаба утилит agzam4 - через общий реестр KeyBind, а не через класс
            // ModWork.KeyBinds: его загрузка как side-effect регистрирует бинды, что при self-disable не нужно
            val utilsKey = KeyBind.all.find { it.name == "open-utils" }?.value?.key?.toString() ?: "U"
            modRow(list, Core.bundle["client.setting.modsec-agzam4.category"], Core.bundle.format("client.features.mod.agzam4.desc", utilsKey),
                Vars.mods.locateMod("agzam4mod") == null)

            val schemeOk = SchemeSizeMod.enabled()
            modRow(list, Core.bundle["client.setting.modsec-scheme.category"], Core.bundle["client.features.mod.scheme.desc"], schemeOk) { btns ->
                btns.button("@scheme.admins.name", Icon.admin) { SchemeVars.adminscfg.show() }
                    .disabled { !schemeOk }.standdownTooltip(schemeOk)
                btns.button("@scheme.rulesetter.name", Icon.fileText) {
                    // как одноимённая кнопка в настройках: unusable() сам объясняет игроку, чего не хватает
                    if (!SchemeVars.admins.unusable()) SchemeVars.rulesetter.show()
                }.disabled { !schemeOk }.standdownTooltip(schemeOk)
                btns.button("@scheme.render.name", Icon.image) { SchemeVars.rendercfg.show() }
                    .disabled { !schemeOk }.standdownTooltip(schemeOk)
            }

            val mdt = MindustryToolMod.featureSettingDialog
            modRow(list, Core.bundle["client.setting.modsec-mindustrytool.category"], Core.bundle["client.features.mod.mindustrytool.desc"], mdt != null) { btns ->
                btns.button("@mindustrytool.settings.open", Icon.planet) { mdt?.show() }
                    .disabled { mdt == null }.standdownTooltip(mdt != null)
            }

            // sonkaextras: нативные фичи клиента, guard'а нет - всегда доступны
            modRow(list, Core.bundle["client.setting.modsec-sonkaextras.category"], Core.bundle["client.features.mod.sonkaextras.desc"], true) { btns ->
                btns.button("@client.namegradient.title", Icon.pencil) { NameGradientDialog().show() }
                btns.button("@client.menuunit.title", Icon.units) { MenuUnitDialog().show() }
            }

            val he = HeliumMod.heModsDialog
            modRow(list, Core.bundle["client.setting.modsec-helium.category"], Core.bundle["client.features.mod.helium.desc"], he != null) { btns ->
                btns.button("@client.features.helium.mods", Icon.book) { he?.show() }
                    .disabled { he == null }.standdownTooltip(he != null)
            }

            // extraeditor: тулбар живёт только внутри редактора карт, диалогов нет - строка без кнопок
            modRow(list, Core.bundle["client.setting.modsec-extraeditor.category"], Core.bundle["client.features.mod.extraeditor.desc"],
                Vars.mods.locateMod("extra-editor") == null)

            // newconsole: consoles непусто <=> guard пройден и ClientLoadEvent отработал; кнопка -
            // запасной вход в консоль на случай спрятанной настройкой плавающей кнопки
            val ncOk = !newconsole.ConsoleVars.consoles.isEmpty
            modRow(list, Core.bundle["client.setting.modsec-newconsole.category"], Core.bundle["client.features.mod.newconsole.desc"], ncOk) { btns ->
                btns.button("@client.features.newconsole.open", Icon.terminal) {
                    if (ncOk) newconsole.ConsoleVars.getCurrentConsole().show()
                }.disabled { !ncOk }.standdownTooltip(ncOk)
            }
        }.growX().row()

        return t
    }

    /**
     * Строка одного вшитого пакета: слева имя + однострочное описание, справа кнопки-действия.
     * ok=false - пакет самоотключился: имя получает пометку, описание тускнеет (кнопки блокирует
     * вызывающий - у некоторых свои дополнительные условия, например «только в игре» у Hub).
     */
    private fun modRow(list: Table, name: String, desc: String, ok: Boolean, buttons: ((Table) -> Unit)? = null) {
        list.table { info ->
            info.left().top()
            info.add(if (ok) name else "$name [scarlet]${Core.bundle["client.features.mods.off"]}").left().row()
            info.add(desc).left().growX().wrap().color(if (ok) Color.lightGray else Color.darkGray).padTop(2f)
        }.growX()
        list.table { btns ->
            btns.right().top()
            btns.defaults().height(44f).minWidth(140f).pad(2f)
            buttons?.invoke(btns)
        }.right().row()
    }

    /** Тултип-объяснение на кнопке самоотключившегося пакета; у живых пакетов кнопки без тултипа. */
    private fun <T : Button> Cell<T>.standdownTooltip(ok: Boolean): Cell<T> {
        if (!ok) tooltip(STANDDOWN)
        return this
    }
}
