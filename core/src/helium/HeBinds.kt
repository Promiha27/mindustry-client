package helium

import arc.input.KeyBind
import arc.input.KeyCode

/**
 * Хоткеи порта Helium (оригинальные имена из helium.util.HeKeyBindings, категория "helium"
 * в диалоге управления). Дефолты мода сохранены: Tab (страница палитры) и Q (свернуть сетку) -
 * они ПЕРЕСЕКАЮТСЯ с ванильными player_list/chat_autocomplete и clear_building, ровно как у
 * самого мода; при желании перебиндиваются в Controls -> Helium.
 */
object HeBinds{
    @JvmField val switchFastPage: KeyBind = KeyBind.add("switchFastPageHotKey", KeyCode.tab, "helium")
    @JvmField val placementFold: KeyBind = KeyBind.add("placementFoldHotKey", KeyCode.q, "helium")

    /** Форс-инициализация + подтяжка сохранённых значений (Vars.loadSettings прогнал load()
     *  по KeyBind.all ещё до нашей регистрации - паттерн mi2u.MBinding). */
    fun load(){
        switchFastPage.load()
        placementFold.load()
    }
}
