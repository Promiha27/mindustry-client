package mindustry.client.navigation

import arc.*
import arc.math.*
import arc.math.geom.*
import mindustry.Vars.*
import mindustry.type.*
import mindustry.world.*

/** sonka: авто-добыча по приоритету, пока юнит игрока вообще ничего не строит (см.
 * DesktopInput.pollInputPlayer, activelyBuilding()). Специально не MinePath - без goTo
 * (та ходит через Navigation.goTo, использует A-star, и телепортирует юнита при
 * пересчёте пути) и без скарсити-выбора: строго по списку приоритета руды из настроек
 * (automineonpausepriority). Юнит просто идёт напрямую к ближайшей подходящей руде и
 * копает, без похода к ядру - как ванильный MinerAI.moveTo, но без A-star. */
class PriorityMinePath : Path() {
    private val moveVec = Vec2()

    override fun setShow(show: Boolean) = Unit
    override fun getShow() = false

    override fun follow() {
        val unit = player.unit() ?: return

        var target: Tile? = null
        for (name in Core.settings.getString("automineonpausepriority").trim().split(Regex("\\s+"))) {
            val item = content.item(name.trim().lowercase()) ?: continue
            if (!unit.canMine(item)) continue
            target = indexer.findClosestMineableOre(unit, item) ?: continue
            break
        }

        if (target == null) {
            unit.mineTile = null
            return
        }

        if (unit.within(target, unit.type.mineRange)) {
            unit.mineTile = target
        } else {
            unit.mineTile = null
            val circleLength = unit.type.mineRange / 2f
            val len = Mathf.clamp((unit.dst(target) - circleLength) / 20f, -1f, 1f)
            moveVec.set(target).sub(unit)
            moveVec.setLength(unit.speed() * len)
            if (len < 0f) moveVec.setZero()
            unit.movePref(moveVec)
        }
    }

    @Synchronized
    override fun draw() = Unit

    override fun progress() = 0f
    override fun reset() = Unit
    override fun next(): Position? = null
}
