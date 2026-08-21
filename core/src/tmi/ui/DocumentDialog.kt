package tmi.ui

import arc.Core
import arc.func.Cons
import arc.func.Intc
import arc.input.KeyCode
import arc.math.Interp
import arc.scene.Element
import arc.scene.actions.Actions
import arc.scene.ui.ImageButton.ImageButtonStyle
import arc.scene.ui.layout.Cell
import arc.scene.ui.layout.Table
import mindustry.gen.Icon
import mindustry.graphics.Pal
import mindustry.ui.Styles
import mindustry.ui.dialogs.BaseDialog
import tmi.util.Consts

class DocumentDialog : BaseDialog("") {
  var rebuilder: Intc? = null
  var resize: Runnable? = null
  var lastPane: Table? = null

  private val docCont: Cell<Table> = cont.table().pad(20f).maxWidth(1280f).grow()
  private val doc: Table = docCont.get()

  init {
    addEventBlocker()

    addCloseButton()
    keyDown(KeyCode.escape) { hide() }
    resized { rebuilder?.get(0) }

    hidden { doc.clearChildren() }
  }

  fun contLayout(layout: Cons<Cell<Table>>): DocumentDialog {
    layout.get(docCont)
    doc.invalidateHierarchy()
    return this
  }

  /**
   * Markdown-страницы. UniverseKit-рендер (universe.ui.markdown) не вендорится - документ идёт
   * через свой мини-рендер [TmiMarkdown] (заголовки/абзацы/списки/цитаты/линии/инлайн-жирный).
   * Клиентский StupidMarkupParser здесь НЕ годится: он понимает только «# » и « * » и показывал
   * справку калькулятора пустой. Встроенные base64-картинки из документов мода вырезаны ещё на
   * этапе копирования ассетов. При любом сбое рендера - сырой текст одним Label.
   */
  fun showMarkdown(title: String, vararg markdowns: String) {
    val pages = markdowns.map { md ->
      try {
        TmiMarkdown.format(md)
      } catch (e: Throwable) {
        Table().also { t -> t.add(md).wrap().growX().labelAlign(arc.util.Align.topLeft) }
      }
    }

    showDocument(title, *pages.toTypedArray())
  }

  fun showDocument(title: String, vararg tableBuilder: Cons<Table>) {
    val pages = tableBuilder.map { builder -> Table(builder) }

    showDocument(title, *pages.toTypedArray())
  }

  fun showDocument(title: String, vararg documents: Element) {
    titleTable.clearChildren()
    titleTable.add(title).color(Pal.accent)

    var currPage = 0
    doc.clearChildren()
    if (documents.isNotEmpty()) {
      doc.top().table { table ->
        fun buildSwitchLeft(t: Table){
          if (documents.size > 1) {
            if (Core.graphics.isPortrait) {
              t.defaults().growX().height(45f)
            }
            else t.defaults().growY().width(40f)

            val bu = t.button(Icon.leftOpen, Styles.clearNonei) {
              currPage--
              rebuilder?.get(-1)
            }.disabled { currPage <= 0 }.get()
            bu.style.disabled = Consts.grayUIAlpha
            bu.style.up = bu.style.disabled
          }
        }
        val build = Runnable {
          table.table { clip ->
            table.top().defaults().top()
            clip.setClip(true)

            rebuilder = Intc { i ->
              if (i != 0 && lastPane != null) {
                lastPane?.actions(
                  Actions.parallel(
                    Actions.alpha(0f, 0.5f, Interp.pow3In),
                    Actions.moveBy(-clip.getWidth()/2*i, 0f, 0.5f, Interp.pow3In)
                  ),
                  Actions.run {
                    clip.removeChild(lastPane)
                    lastPane = clip.table(Consts.padGrayUIAlpha) { page ->
                      page.top().table().grow().get().pane(
                        Styles.smallPane, Table{ it.add(documents[currPage]).grow().pad(16f) }
                      ).grow().scrollX(false)
                    }.scrollX(false).grow().get()
                    lastPane?.color?.a = 0f

                    val w = clip.getWidth()
                    val h = clip.getHeight()
                    lastPane?.actions(
                      Actions.parallel(
                        Actions.alpha(1f, 0.5f, Interp.pow3Out),
                        Actions.moveTo(w/2*i, 0f),
                        Actions.sizeTo(w, h),
                        Actions.moveTo(0f, 0f, 0.5f, Interp.pow3Out)
                      )
                    )
                  }
                )
              }
              else {
                lastPane = clip.table(Consts.padGrayUIAlpha) { page ->
                  page.top().table().grow().get().pane(
                    Styles.smallPane, Table{ it.add(documents[currPage]).grow().pad(16f) }
                  ).grow().scrollX(false)
                }.grow().get()
              }
            }
            rebuilder?.get(0)
          }.grow()
        }

        fun buildSwitchRight(t: Table){
          if (documents.size > 1) {
            if (Core.graphics.isPortrait) {
              t.defaults().growX().height(45f)
            }
            else t.defaults().growY().width(40f)

            val bu = t.button(Icon.rightOpen, object : ImageButtonStyle(Styles.clearNonei) {
              init {
                up = Consts.grayUIAlpha
              }
            }) {
              currPage++
              rebuilder?.get(1)
            }.disabled { _ -> currPage >= documents.size - 1 }.get()
            bu.style.disabled = Consts.grayUIAlpha
            bu.style.up = bu.style.disabled
          }
        }

        resize = Runnable {
          table!!.clearChildren()
          if (Core.graphics.isPortrait) {
            build.run()
            table.row()
            table.table { b ->
              buildSwitchLeft(b)
              buildSwitchRight(b)
            }.fillY().growX()
          }
          else {
            buildSwitchLeft(table.table().growY().fillX().get())
            build.run()
            buildSwitchRight(table.table().growY().fillX().get())
          }
        }
        resize?.run()
        resized(resize)
      }.grow()
      doc.row()
      doc.add("").update { l ->
        l.setText(Core.bundle.format("dialog.recipes.pages", currPage + 1, documents.size))
      }
    }

    show()
  }
}