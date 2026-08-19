package helium.util

import mindustry.core.Version
import mindustry.mod.Mods
import mindustry.mod.Mods.ModState

//порт helium.util.ModStat (Helium, EB-wilson) как есть: битовые флаги состояния мода
//для иконок/списков статуса в менеджере и браузере модов
const val ENABLED              = 0b0000000000001
const val CLIENT_ONLY          = 0b0000000000010
const val JAR_MOD              = 0b0000000000100
const val JS_MOD               = 0b0000000001000
const val UP_TO_DATE           = 0b0000000010000
const val LOCAL_FILE           = 0b0000000100000
const val DEPRECATED           = 0b0000001000000
const val UNSUPPORTED          = 0b0000010000000
const val LIB_MISSING          = 0b0000100000000
const val LIB_INCOMPLETE       = 0b0001000000000
const val LIB_CIRCLE_DEPENDING = 0b0010000000000
const val ERROR                = 0b0100000000000
const val BLACKLIST            = 0b1000000000000

object ModStat{
    fun Int.isEnabled() = this and ENABLED != 0
    fun Int.isClientOnly() = this and CLIENT_ONLY != 0
    fun Int.isJAR() = this and JAR_MOD != 0
    fun Int.isJS() = this and JS_MOD != 0
    fun Int.isUpToDate() = this and UP_TO_DATE != 0
    fun Int.isLocalFile() = this and LOCAL_FILE != 0
    fun Int.isDeprecated() = this and DEPRECATED != 0
    fun Int.isUnsupported() = this and UNSUPPORTED != 0
    fun Int.isLibMissing() = this and LIB_MISSING != 0
    fun Int.isLibIncomplete() = this and LIB_INCOMPLETE != 0
    fun Int.isLibCircleDepending() = this and LIB_CIRCLE_DEPENDING != 0
    fun Int.isError() = this and ERROR != 0
    fun Int.isBlackListed() = this and BLACKLIST != 0

    fun checkModStat(mod: Mods.LoadedMod): Int{
        var res = 0b0

        if(mod.enabled()) res = res or ENABLED
        if(mod.meta.hidden) res = res or CLIENT_ONLY
        if(mod.isJava) res = res or JAR_MOD
        if(mod.root.child("scripts").exists()){
            val allScripts = mod.root.child("scripts").findAll{ f -> f.extEquals("js") }
            val main = if(allScripts.size == 1) allScripts.first() else mod.root.child("scripts").child("main.js")
            if(main.exists() && !main.isDirectory){
                res = res or JS_MOD
            }
        }
        if(mod.isOutdated) res = res or DEPRECATED
        if(!Version.isAtLeast(mod.meta.minGameVersion)) res = res or UNSUPPORTED
        if(mod.hasUnmetDependencies()) res = res or LIB_MISSING
        if(mod.state == ModState.incompleteDependencies) res = res or LIB_INCOMPLETE
        if(mod.state == ModState.circularDependencies) res = res or LIB_CIRCLE_DEPENDING
        if(mod.hasContentErrors()) res = res or ERROR
        if(mod.isBlacklisted) res = res or BLACKLIST

        return res
    }

    fun Int.isValid() = this and (
        DEPRECATED or
        UNSUPPORTED or
        LIB_MISSING or
        LIB_INCOMPLETE or
        LIB_CIRCLE_DEPENDING or
        ERROR or
        BLACKLIST
    ) == 0
}
