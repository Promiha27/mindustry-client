package tmi.util

import arc.Core
import arc.files.Fi
import arc.struct.ObjectMap
import java.util.*

object TmiAssets {
  private val docCache: ObjectMap<Fi, String> = ObjectMap()

  private val SPRITES = arrayOf(
    "a_z", "tmi", "panner", "balance", "inbalance", "time", "clip", "show_grid", "hide_grid",
    "autolink_all", "autolink_inputs", "autolink_outputs", "autolink_off",
    "side_bottom", "side_top", "side_left", "side_right",
    "ammo_normal", "ammo_pierce", "ammo_missile", "ammo_spate", "ammo_canister", "ammo_laser",
    "ammo_canister_laser", "ammo_lightning", "ammo_flame"
  )

  /**
   * Спрайты мода под оригинальными именами атласа (tmi-*). У обычного мода их пакует
   * спрайт-пакер Mods; вшитая копия несёт png в core/assets/tmi/ui/ и пакует их в Core.atlas
   * через PixmapPacker (паттерн testing.util.TUIcons). Одна страница 512x512: 24 иконки
   * по 64px + логотип 256px.
   */
  fun loadSprites() {
    if (Core.atlas.has("tmi-tmi")) return

    val packer = arc.graphics.g2d.PixmapPacker(512, 512, 2, true)
    try {
      SPRITES.forEach { name ->
        val pix = arc.graphics.Pixmap(Core.files.internal("tmi/ui/$name.png"))
        packer.pack("tmi-$name", pix)
        pix.dispose()
      }
      packer.updateTextureAtlas(
        Core.atlas,
        arc.graphics.Texture.TextureFilter.linear,
        arc.graphics.Texture.TextureFilter.linear,
        false
      )
    } catch (e: Throwable) {
      arc.util.Log.err("[tmi] Failed to pack Too Many Items sprites", e)
    } finally {
      packer.dispose()
    }
  }

  /** Ассеты вшитой копии лежат в core/assets/tmi/ (у мода - в корне его jar). */
  fun getInternalFile(path: String): Fi = Core.files.internal("tmi/$path")

  fun getDocument(name: String, cache: Boolean = true): String {
    val fi = getDocumentFile(name)
    return if (cache) docCache.get(fi) { fi.readString() } else fi.readString()
  }
  fun getDocument(name: String, locale: Locale): String = getDocumentFile(name, locale).readString()

  fun getDocumentFile(name: String, locale: Locale = Core.bundle.locale): Fi {
    var docs = getInternalFile("documents").child(locale.toString())
    if (!docs.exists()) docs = getInternalFile("documents").child("en")
    return docs.child(name).also {
      if(!it.exists()) throw NoSuchFileException(it.file(), reason = "Cannot find this file in documents directory.")
    }
  }
}