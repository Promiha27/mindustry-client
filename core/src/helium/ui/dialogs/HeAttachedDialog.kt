package helium.ui.dialogs

import arc.Core
import arc.func.Boolp
import arc.scene.ui.Dialog
import mindustry.ui.dialogs.BaseDialog

/**
 * Минимальная замена universe.ui.dialogs.AttachableDialog из UniverseKit (вендорить его целиком
 * незачем): диалог, ПОДМЕНЯЮЩИЙ собой другой при показе. Пока [enabled] возвращает true, каждое
 * attached.show() тут же прячет attached и показывает этот диалог; [showAttached] открывает
 * оригинал разово, без обратной подмены (аналог кнопки-переключателя fillDefaultSwitch мода).
 */
open class HeAttachedDialog(val attached: Dialog, title: String, val enabled: Boolp) : BaseDialog(title){
    private var bypassOnce = false

    init{
        attached.shown{
            if(enabled.get() && !bypassOnce){
                //post: даём attached.show() штатно завершиться, потом мгновенно подменяем
                Core.app.post{
                    if(attached.isShown){
                        attached.hide(null)
                        show()
                    }
                }
            }
            bypassOnce = false
        }
    }

    /** Разовый показ оригинального диалога (кнопка "ванильный менеджер"). */
    fun showAttached(){
        bypassOnce = true
        hide()
        attached.show()
    }
}
