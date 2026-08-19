package agzam4.utils;

import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import mindustry.content.*;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * Хаб "Утилиты" (бинд "open-utils", по умолчанию U): генератор процессоров, генератор артов,
 * спавнер юнитов/блоков. Пункт "FreeCam AI" (PlayerAI) из оригинала НЕ портирован - в клиенте
 * уже есть свои авто-режимы (MinePath/BuildPath, mi2u FullAI).
 */
public class PlayerUtils {

	private static BaseDialog utilsDialog;

	public static void build() {
		ProcessorGenerator.build();
		UnitSpawner.build();

		utilsDialog = new BaseDialog(Bungle.dialog("utils"));
		utilsDialog.title.setColor(Color.white);
		utilsDialog.titleTable.remove();
		utilsDialog.closeOnBack();

		utilsDialog.cont.pane(p -> {
			p.defaults().left();

			Table t = new Table();
			p.add(t).row();

			t.button(Blocks.microProcessor.emoji() + " " + Bungle.dialog("utils.processor-generator"), Styles.defaultt, () -> {
						ProcessorGenerator.show();
			}).growX().pad(10).padBottom(4).wrapLabel(false).row();

			t.button(Blocks.logicDisplay.emoji() + " " + Bungle.dialog("utils.display-generator"), Styles.defaultt, () -> {
				DisplayGenerator.show();
			}).growX().pad(10).padBottom(4).wrapLabel(false).row();

			t.button(Blocks.payloadSource.emoji() + " " + Bungle.dialog("utils.unit-spawn"), Styles.defaultt, () -> {
				UnitSpawner.show();
			}).growX().pad(10).padBottom(4).wrapLabel(false).disabled(b -> !UnitSpawner.avaliable()).row();

			t.button("@back", Styles.defaultt, () -> {
				hide();
			}).growX().pad(10).padBottom(4).wrapLabel(false).row();
		});
	}

	public static void show() {
		if(utilsDialog.isShown()) return;
		utilsDialog.show();
	}

	public static void hide() {
		utilsDialog.hide();
	}
}
