package eui.input;

import arc.input.KeyBind;
import arc.input.KeyCode;

/**
 * KeyBind'ы Extended UI++ - отдельная категория "extended-ui" в диалоге управления (прецеденты:
 * {@code mi2u.input.MBinding}, {@code qol.controlhelper.core.UnitSplitter}). Регистрация происходит в
 * статик-инициализаторе, поэтому {@link eui.EUIMod} зовёт {@link #init()} для форс-инициализации
 * класса до открытия диалога биндов (тот же приём, что у MI2UMod).
 */
public class EuiBinding{
    /** Лидер цепочки "G + две цифры" таблицы схем: G, затем ряд/колонна (порядок настраивается). */
    public static final KeyBind schemTableLeader = KeyBind.add("schem_table_leader", KeyCode.g, "extended-ui");

    public static void init(){}
}
