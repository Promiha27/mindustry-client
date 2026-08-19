package agzam4.utils;

import agzam4.AgzamMod;
import arc.Core;
import arc.struct.ObjectMap;
import arc.util.Nullable;

/**
 * Обёртка настроек с оригинальным префиксом мода "agzam4mod.settings." - сохранённые значения
 * sonka из времён загрузки Agzam's Mod как обычного мода подхватываются без миграции.
 * Скопировано из мода как есть (v155.4.a).
 */
public class Prefs {

	public static final Prefs settings = new Prefs("settings");

	private final String prefix;
	/* перф: геттеры зовутся из draw/update-циклов (IndustryCalculator и др.) каждый кадр -
	 * кэшируем готовые "prefix.name" ключи вместо конкатенации на каждый вызов;
	 * имена настроек - фиксированный набор литералов, кэш ограничен */
	private final ObjectMap<String, String> keyCache = new ObjectMap<>();

	private Prefs(String prefix) {
		this.prefix = AgzamMod.name + "." + prefix + ".";
	}

	private String key(String string) {
		String k = keyCache.get(string);
		if (k == null) {
			k = prefix + string;
			keyCache.put(string, k);
		}
		return k;
	}

	public boolean bool(String string) {
		return Core.settings.getBool(key(string), true);
	}

	public boolean bool(String string, boolean def) {
		return Core.settings.getBool(key(string), def);
	}

	/** ВНИМАНИЕ: как и в оригинале - БЕЗ префикса (баг мода, сохранён для совместимости ключей). */
	public @Nullable String string(String string) {
		return Core.settings.getString(string, null);
	}

	public String string(String string, String def) {
		try {
			return Core.settings.getString(key(string), def);
		} catch (Exception e) {
			return def;
		}
	}

	public void put(String string, Object value) {
		Core.settings.put(key(string), value);
	}

	public int integer(String string, int def) {
		return Core.settings.getInt(key(string), def);
	}

	public float real(String string, float def) {
		return Core.settings.getFloat(key(string), def);
	}
}
