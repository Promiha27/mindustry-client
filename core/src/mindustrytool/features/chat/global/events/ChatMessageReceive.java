package mindustrytool.features.chat.global.events;

import arc.struct.Seq;
import mindustrytool.features.chat.global.dto.ChatMessage;

/** Порт: lombok @RequiredArgsConstructor заменён явным конструктором. */
public class ChatMessageReceive {
    public final Seq<ChatMessage> messages;

    public ChatMessageReceive(Seq<ChatMessage> messages) {
        this.messages = messages;
    }
}
