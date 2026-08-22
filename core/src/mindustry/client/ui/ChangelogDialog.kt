package mindustry.client.ui

import arc.*
import arc.scene.ui.*
import mindustry.gen.*
import mindustry.ui.dialogs.*

object ChangelogDialog : BaseDialog("Changelog") {
    private var init = false
    override fun show(): Dialog {
        if (!init) {
            init = true
            cont.pane(StupidMarkupParser.format(Core.files.internal("changelog").readString("UTF-8"))).growX().scrollX(false)
            addCloseButton()
            // sonka: история релизов Monolith (заметки с GitHub, см. sonkaextras.WhatsNew) - ручной changelog
            // выше остаётся сводкой фич, а здесь по-релизная дельта
            buttons.button("@client.sonka.whatsnew.history", Icon.refresh) { sonkaextras.WhatsNew.showAll() }.size(210f, 64f)
        }
        return super.show()
    }
}