package agzam4.uiOverride;

import agzam4.ModWork;
import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Strings;

/**
 * Градиентная покраска исходящих сообщений чата - единственный кусок кастомного чата Agzam's Mod,
 * который переехал в порт. Сам CustomChatFragment НЕ портирован: чат этого клиента и так тяжело
 * модифицирован (автодополнение команд/игроков/эмодзи, подпись сообщений, клиентские команды),
 * и его подмена потеряла бы эти фичи.
 * <p>
 * Хук: {@code mindustry.ui.fragments.ChatFragment.sendMessage()} прогоняет готовый текст через
 * {@link #apply(String)}. Настройки - оригинальные ключи мода ("agzam4mod.settings.messages-gradient"
 * = список цветов через пробел, "...messages-gradient-trigger" = префикс-триггер, пустой = красить всё).
 * Пока цвета не заданы (дефолт), фича полностью инертна.
 */
public class ChatGradient {

	public static final Seq<Color> messageColors = loadColors();
	public static String colorTrigger = ModWork.settingDef("messages-gradient-trigger", "");

	private static Seq<Color> loadColors() {
		Seq<Color> pal = new Seq<Color>();
		String[] colors = ModWork.settingDef("messages-gradient", "").split(" ");
		for (String color : colors) {
			if(color.isEmpty()) continue;
			try {
				pal.add(Color.valueOf(color));
			} catch (Exception ignored) {
			}
		}
		return pal;
	}

	public static void save() {
		ModWork.setting("messages-gradient", messageColors.toString(" "));
	}

	/**
	 * Красит сообщение градиентом, если фича настроена и сообщение подходит:
	 * не команда ("/", "!"), начинается с триггера, не содержит своих цветов/глифов.
	 */
	public static String apply(String message) {
		if(messageColors.size == 0) return message;
		if(message.isEmpty()) return message;
		if(message.startsWith("/") || message.startsWith("!")) return message;
		if(!message.startsWith(colorTrigger)) return message;
		if(message.length() != Strings.stripColors(message).length()) return message;

		message = message.substring(colorTrigger.length());
		if(message.isEmpty()) return message;
		StringBuilder msg = new StringBuilder();
		int lastColor = Color.white.rgb888();
		for (int i = 0; i < message.length(); i++) {
			Color color = messageColors.get(i*messageColors.size/message.length());
			if(color.rgb888() != lastColor) {
				lastColor = color.rgb888();
				msg.append("[#");
				msg.append(color.toString());
				msg.append("]");
			}
			msg.append(message.charAt(i));
		}
		return msg.toString();
	}
}
