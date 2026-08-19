package mindustrytool.features.chat.global.dto;

import arc.struct.ObjectMap;

/** Порт: lombok @Data убран, поле и так публичное (сериализуется arc Settings.putJson). */
public class LastReadMessageStore {
    public ObjectMap<String, String> lastReadMessageIds = new ObjectMap<>();
}
