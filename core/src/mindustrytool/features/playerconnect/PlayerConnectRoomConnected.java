package mindustrytool.features.playerconnect;

/** Порт: lombok @Data заменён явным конструктором. */
public class PlayerConnectRoomConnected {
    public final PlayerConnectLink link;

    public PlayerConnectRoomConnected(PlayerConnectLink link) {
        this.link = link;
    }
}
