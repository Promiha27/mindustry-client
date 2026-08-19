package mindustrytool.features.chat.global.dto;

/** Порт: lombok @Data заменён на публичные поля + геттеры (getId нужен ChatOverlay). */
public class ChannelDto {
    public String id;
    public String name;
    public String lastMessageId;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getLastMessageId() { return lastMessageId; }
}
