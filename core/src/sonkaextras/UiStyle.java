package sonkaextras;

import arc.graphics.Color;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

/**
 * ЕДИНЫЙ STYLE-ГАЙД плавающих HUD-окон/панелей всех вшитых модов (qol, eui, mi2u, scheme,
 * mindustrytool, agzam4, sectorstats, qolc, sonkaextras). Одно место правды: и qol'ный
 * {@link qol.ui.QolWindow}, и mi2u'шный {@link mi2u.ui.elements.Mindow2}, и сырые Table-панели
 * (eui, Quick Access, UnitSpawner) берут фоны/цвета/размеры отсюда, а не хардкодят свои.
 * <p>
 * Канон выведен из того, что УЖЕ доминирует в нативных фрагментах форка
 * ({@code mindustry.ui.fragments.HudFragment} и клиентские окна):
 * <ul>
 * <li><b>Тело окна/панели</b> - {@link Styles#black3} (полупрозрачный чёрный 30%), как у нативных
 *     инфо-плашек (hudText, coreItems) и как у QolWindow/Mindow2 исторически.</li>
 * <li><b>Тайтл-бар / "хром"</b> (полоса заголовка, тулбары, бейджи) - {@link Styles#black6}
 *     (чёрный 60%), как у нативного верхнего меню/алертов. Красная/синяя тонировка тайтлов mi2u
 *     ({@code MI2UVars.mindowTitleBarBackground}) приведена сюда же.</li>
 * <li><b>Заголовок</b> - accent-цвет ({@link Pal#accent}), как у заголовков нативных диалогов;
 *     обычный шрифт, без outline.</li>
 * <li><b>Кнопки тайтл-бара</b> - плоские без собственного фона: {@link Styles#clearNonei} для
 *     действий, {@link Styles#clearTogglei} для переключателей; размер {@link #TITLE_BUTTON_SIZE}
 *     (32px - размер mi2u buttonSize и кнопок qol Hub, у нативного HUD те же ~32-40).</li>
 * <li><b>Отступ контента</b> - {@link #PANEL_MARGIN} (4px, как {@code margin(4)} нативных плашек).</li>
 * <li><b>Пристыкованные к нативным стекам панели</b> (units-table под "waves", scheme-панель у
 *     блок-меню) СОХРАНЯЮТ {@code Tex.buttonEdge*} - там канон "слиться с соседним нативным
 *     элементом", а не "выглядеть отдельным окном".</li>
 * </ul>
 * Геттеры-методы, а не static final поля: {@code Styles.black3/black6} заполняются в
 * {@code Styles.load()}, и жёсткая инициализация при загрузке класса могла бы поймать null,
 * если кто-то дёрнет класс до построения UI (то же соображение, что у ленивых фонов в Styles).
 */
public final class UiStyle{
    /** Размер кнопок тайтл-баров и тогглов Hub'а, px (до Scl-масштаба). */
    public static final float TITLE_BUTTON_SIZE = 32f;
    /** Внутренний отступ тела окна/панели, px. */
    public static final float PANEL_MARGIN = 4f;

    private UiStyle(){
    }

    /** Фон тела окна/панели. */
    public static Drawable windowBg(){
        return Styles.black3;
    }

    /** Фон тайтл-бара, тулбаров и плашек-бейджей. */
    public static Drawable titleBg(){
        return Styles.black6;
    }

    /** Цвет текста заголовка окна. */
    public static Color titleColor(){
        return Pal.accent;
    }

    /** Плоская кнопка-действие тайтл-бара (шестерёнка, закрыть...). */
    public static ImageButtonStyle titleButton(){
        return Styles.clearNonei;
    }

    /** Плоская кнопка-переключатель (показать/скрыть окно и т.п.). */
    public static ImageButtonStyle titleToggle(){
        return Styles.clearTogglei;
    }
}
