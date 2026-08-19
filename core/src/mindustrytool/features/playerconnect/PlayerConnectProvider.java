package mindustrytool.features.playerconnect;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class PlayerConnectProvider {
    private String id;
    private String name;
    private String address;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
}
