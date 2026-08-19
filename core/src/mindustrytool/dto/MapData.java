package mindustrytool.dto;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class MapData {
    String id;
    String itemId;
    String name;
    Long likes = 0L;
    Long downloads = 0L;
    Long comments = 0L;

    public String getId() { return id; }
    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public Long getLikes() { return likes; }
    public Long getDownloads() { return downloads; }
    public Long getComments() { return comments; }
}
