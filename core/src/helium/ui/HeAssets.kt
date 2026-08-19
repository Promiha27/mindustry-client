package helium.ui

import arc.Core
import arc.graphics.Color
import arc.graphics.Texture
import arc.graphics.g2d.Lines
import arc.graphics.g2d.TextureRegion
import arc.scene.style.BaseDrawable
import arc.scene.style.Drawable
import arc.scene.style.TextureRegionDrawable
import arc.scene.ui.layout.Scl
import arc.util.Time
import arc.util.Tmp
import mindustry.gen.Tex
import mindustry.graphics.Pal

/**
 * Урезанный порт helium.ui.HeAssets: только то, что нужно портированным фичам (палитра + мод-менеджер).
 * Спрайты мода лежат в core/assets/helium/ и регистрируются в атлас вручную под префиксом "he-"
 * (паттерн MI2UMod.loadSprites); тинт-drawable'ы строятся из ванильного whiteui.
 * Спиннер загрузки перерисован на ванильном Lines.arc вместо DrawUtils UniverseKit (не вендорим 500 строк).
 */
object HeAssets{
    val lightBlue: Color = Color.valueOf("D3FDFF")

    lateinit var heIcon: Drawable
    lateinit var java: Drawable
    lateinit var javascript: Drawable
    lateinit var networkError: Drawable
    lateinit var slotsBack: Drawable

    lateinit var loading: Drawable

    lateinit var transparent: Drawable
    lateinit var grayUI: Drawable
    lateinit var grayUIAlpha: Drawable
    lateinit var darkGrayUI: Drawable
    lateinit var darkGrayUIAlpha: Drawable
    lateinit var padGrayUIAlpha: Drawable

    private var loaded = false

    /** Идемпотентная ленивая загрузка: PlacementFragment строится ДО ClientLoadEvent, поэтому
     *  первым может позвать кто угодно - лишь бы атлас уже существовал (UI.init и позже). */
    fun ensure(){
        if(loaded) return
        loaded = true

        for(name in arrayOf("helium", "java", "javascript", "network-error", "slots-back")){
            val region = "he-$name"
            if(Core.atlas.has(region)) continue
            val tex = Texture(Core.files.internal("helium/$name.png"))
            tex.setFilter(Texture.TextureFilter.linear)
            Core.atlas.addRegion(region, TextureRegion(tex))
        }

        heIcon = Core.atlas.drawable("he-helium")
        java = Core.atlas.drawable("he-java")
        javascript = Core.atlas.drawable("he-javascript")
        networkError = Core.atlas.drawable("he-network-error")
        slotsBack = Core.atlas.drawable("he-slots-back")

        loading = object : BaseDrawable(){
            override fun draw(x: Float, y: Float, width: Float, height: Float){
                //крутящаяся дуга; оригинал рисовал её DrawUtils.arc из UniverseKit
                Lines.stroke(Scl.scl(4f))
                Lines.arc(x + width/2, y + height/2, width/2.3f, 0.75f, Time.globalTime*2f)
                Lines.stroke(1f)
            }
        }

        val white = Tex.whiteui as TextureRegionDrawable
        transparent = white.tint(Color.clear)
        grayUI = white.tint(Pal.darkerGray)
        grayUIAlpha = white.tint(Tmp.c1.set(Pal.darkerGray).a(0.7f))
        darkGrayUI = white.tint(Pal.darkestGray)
        darkGrayUIAlpha = white.tint(Tmp.c1.set(Pal.darkestGray).a(0.7f))
        padGrayUIAlpha = white.tint(Tmp.c1.set(Pal.darkerGray).a(0.7f)).also{
            it.leftWidth = 8f
            it.rightWidth = 8f
            it.topHeight = 8f
            it.bottomHeight = 8f
        }
    }
}
