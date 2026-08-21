package tmi.ui

import arc.graphics.Color
import arc.scene.ui.Label
import arc.scene.ui.layout.Table
import arc.util.Align
import mindustry.graphics.Pal
import mindustry.ui.Fonts

/**
 * Минимальный рендер markdown-документов TMI (core/assets/tmi/documents/) в Table из Label-ов.
 *
 * ЗАЧЕМ: UniverseKit-рендер мода не вендорится, а клиентский StupidMarkupParser понимает только
 * строки вида «# » и « * » и молча выбрасывает всё остальное - справка калькулятора
 * (##/###-заголовки, абзацы, «- »-списки, «> »-цитаты, «---») отрисовывалась ПУСТОЙ.
 * Здесь ровно то подмножество, которое встречается в документах мода: заголовки любого уровня,
 * абзацы (соседние строки склеиваются, как в markdown), маркированные списки, цитаты,
 * горизонтальные линии, инлайн **жирный** / `код` / [ссылка](url) / ![картинка](url).
 * Картинки (в оригинале base64 на 3 МБ) были вырезаны при копировании ассетов - ссылки на них
 * просто опускаются. Литеральные «[» экранируются в «[[», чтобы не ломать цветовую разметку Label.
 */
object TmiMarkdown {
  private val heading = Regex("^(#{1,6})\\s+(.*)$")
  private val listItem = Regex("^\\s*[-*+]\\s+(.*)$")
  private val rule = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$")
  private val image = Regex("!\\[[^]]*]\\([^)]*\\)")
  private val link = Regex("\\[([^]]*)]\\([^)]*\\)")
  private val bold = Regex("\\*\\*(.+?)\\*\\*")
  private val code = Regex("`([^`]+)`")

  fun format(md: String): Table {
    val table = Table().margin(10f)
    table.left().top().defaults().left().growX()

    val paragraph = StringBuilder()
    fun flushParagraph() {
      if (paragraph.isEmpty()) return
      addLabel(table, inline(paragraph.toString()), Color.white).padBottom(8f)
      paragraph.clear()
    }

    for (raw in md.lines()) {
      val line = raw.trimEnd()
      when {
        line.isBlank() -> flushParagraph()
        rule.matches(line) -> {
          flushParagraph()
          table.image().color(Color.darkGray).height(3f).growX().pad(6f, 0f, 10f, 0f).row()
        }
        heading.matches(line) -> {
          flushParagraph()
          val (hashes, text) = heading.find(line)!!.destructured
          val top = if (hashes.length <= 2) 14f else 8f
          addLabel(table, inline(text), Pal.accent, scale = if (hashes.length <= 2) 1.25f else 1f)
            .padTop(top).padBottom(6f)
        }
        line.startsWith(">") -> {
          flushParagraph()
          val text = line.removePrefix(">").trim()
          table.table { q ->
            q.left()
            q.image().color(Pal.accent).width(3f).growY().padRight(8f)
            addLabel(q, inline(text), Color.lightGray)
          }.growX().padBottom(8f).row()
        }
        listItem.matches(line) -> {
          flushParagraph()
          val text = listItem.find(line)!!.groupValues[1]
          addLabel(table, "• " + inline(text), Color.white).padLeft(12f).padBottom(2f)
        }
        else -> {
          if (paragraph.isNotEmpty()) paragraph.append(' ')
          paragraph.append(line.trim())
        }
      }
    }
    flushParagraph()

    return table
  }

  /** Инлайн-разметка -> цветовые теги Label; исходные «[» экранируются до подстановки тегов. */
  private fun inline(text: String): String {
    var s = text.replace(image, "")
    s = link.replace(s) { it.groupValues[1] }
    s = s.replace("[", "[[")
    s = bold.replace(s) { "[accent]${it.groupValues[1]}[]" }
    s = code.replace(s) { "[lightgray]${it.groupValues[1]}[]" }
    return s
  }

  private fun addLabel(table: Table, text: String, color: Color, scale: Float = 1f) =
    table.add(Label(text, Label.LabelStyle(Fonts.def, color)).also {
      it.setWrap(true)
      it.setAlignment(Align.topLeft)
      if (scale != 1f) it.setFontScale(scale)
    }).growX().also { table.row() }
}
