package mindustrytool.features.playerconnect;

import java.util.List;

/**
 * Порт: lombok @Data и jackson @JsonProperty("isPrivate") убраны — Jval-маппер
 * Utils сопоставляет JSON-поле "isPrivate" с одноимённым Java-полем напрямую
 * (аннотация была нужна только из-за bean-конвенций jackson для boolean isX).
 */
public class PlayerConnectRoom {
    private String roomId;
    private String link;
    private String name;
    private String address;
    private PlayerConnectRoomData data;

    public String getRoomId() { return roomId; }
    public String getLink() { return link; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public PlayerConnectRoomData getData() { return data; }

    public static class PlayerConnectRoomData {
        private String name;
        private String status;
        private List<PlayerConnectRoomPlayer> players;
        private String mapName;
        private String gamemode;
        private List<String> mods;
        private String version;
        private String locale;
        private String protocolVersion;
        private boolean isPrivate;
        private boolean isSecured;

        public String getName() { return name; }
        public String getStatus() { return status; }
        public List<PlayerConnectRoomPlayer> getPlayers() { return players; }
        public String getMapName() { return mapName; }
        public String getGamemode() { return gamemode; }
        public List<String> getMods() { return mods; }
        public String getVersion() { return version; }
        public String getLocale() { return locale; }
        public String getProtocolVersion() { return protocolVersion; }
        public boolean isPrivate() { return isPrivate; }
        public boolean isSecured() { return isSecured; }
    }

    public static class PlayerConnectRoomPlayer {
        private String name;
        private String locale;

        public String getName() { return name; }
        public String getLocale() { return locale; }
    }
}
