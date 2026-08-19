package helium.util

import arc.func.Boolf
import arc.func.Cons
import arc.func.Cons2
import arc.func.Prov
import arc.scene.Element
import arc.scene.event.SceneEvent
import arc.struct.ObjectMap
import arc.util.Strings

//мелкие Kotlin-удобства из helium.He / helium.util.Formatter - только реально используемые портом

operator fun <K, V> ObjectMap<K, V>.set(key: K, value: V): V? = put(key, value)

operator fun <P> Cons<P>.invoke(p: P) = get(p)
operator fun <P1, P2> Cons2<P1, P2>.invoke(p1: P1, p2: P2) = get(p1, p2)
operator fun <R> Prov<R>.invoke(): R = get()

/** Блокировка всплытия событий сцены (кнопки внутри кликабельной шапки мод-таба). */
fun Element.addEventBlocker(
    capture: Boolean = false,
    isCancel: Boolean = false,
    filter: Boolf<SceneEvent> = Boolf{ true }
){
    (this::addCaptureListener.takeIf{ capture } ?: this::addListener){ event ->
        if(event != null && filter.get(event)){
            if(isCancel) event.cancel()
            else event.stop()
        }
        false
    }
}

private val storeList = arrayOf(
    "B", "KB", "MB", "GB",
    "TB", "PB", "YB", "ZB"
)

/** 12345678f -> "11.77[lightgray]MB" - прогресс скачивания без известной длины. */
fun Float.toStoreSize(): String{
    var v = this
    var n = 0

    while(v > 1024){
        v /= 1024
        n++
    }

    return "${Strings.fixed(v, 2)}[lightgray]${storeList[n]}"
}
