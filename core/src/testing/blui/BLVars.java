package testing.blui;

/**
 * Вендоринг BottomLeftUILib (MEEPofFaith, v12) - крошечной UI-библиотеки, которую Testing
 * Utilities тянет как implementation-зависимость (в jar мода лежит её копия). Вшита целиком
 * в {@code testing.blui}: 5 классов, других потребителей в клиенте нет, в helium (UniverseKit)
 * аналогов нет. Константы размеров и общий таймер долгого нажатия {@link HoldImageButton}.
 */
public class BLVars{
    public static float pressTimer = 0;
    public static float longPress = 30;
    public static float iconSize = 40f, buttonSize = 24f, sliderWidth = 140f, fieldWidth = 80f;

    private BLVars(){
    }
}
