package mindustrytool.features.auth.dto;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class UserSession {
    private String id;
    private String name;
    private String imageUrl;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
}
