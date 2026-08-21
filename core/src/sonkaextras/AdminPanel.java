package sonkaextras;

import arc.Core;

/**
 * Выбор админ-панели HUD (решение sonka): в клиент вшиты ДВЕ песочничные панели с пересекающимся
 * набором действий - админ-панель Scheme Size (top-left под стеком «waves», включается
 * настройкой scheme {@code mobilebuttons}) и панель Testing Utilities (bottom-left, BLUI).
 * Вместо вырезания дублей - настройка «Админ-панель: обе / Scheme Size / Testing Utilities»
 * ({@code sonka-admin-panel}, дефолт - обе, чтобы сравнить живьём). Выбор гейтит ТОЛЬКО
 * видимость HUD-панелей: диалоги обоих пакетов остаются доступны из секции «Вшитые моды»
 * FeaturesDialog независимо от выбора. Геометрически панели не пересекаются (разные углы),
 * так что «обе» - рабочий вариант, а не компромисс.
 * <p>
 * Слайдер живёт в секции Sonka Extras (билдер в {@link ChainWarn#init()}): это кросс-модовый
 * выбор, а не настройка одного из пакетов. Значение кэшируется - visible()-лямбды панелей
 * зовутся каждый кадр, в settings лазить незачем.
 */
public class AdminPanel{
    public static final String KEY = "sonka-admin-panel";
    public static final int BOTH = 0, SCHEME = 1, TESTING = 2;

    private static int cached = -1;

    private AdminPanel(){
    }

    public static int mode(){
        if(cached < 0) cached = Core.settings.getInt(KEY, BOTH);
        return cached;
    }

    /** Зовётся из changed-колбэка слайдера - применение вживую. */
    public static void invalidate(){
        cached = -1;
    }

    public static boolean schemeEnabled(){
        int m = mode();
        return m == BOTH || m == SCHEME;
    }

    public static boolean testingEnabled(){
        int m = mode();
        return m == BOTH || m == TESTING;
    }

    public static String label(int mode){
        return Core.bundle.get(mode == SCHEME ? "client.sonka.adminpanel.scheme" : mode == TESTING ? "client.sonka.adminpanel.testing" : "client.sonka.adminpanel.both");
    }
}
