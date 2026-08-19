package mindustrytool.features.chat.global.dto;

/** Порт: lombok @Data убран, поля и так публичные. */
public class ChatMessage {
    public String id;
    public String createdBy;
    public String createdAt;
    public String content;
    public String replyTo;
    public String channelId;
}
