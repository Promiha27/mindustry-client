package mindustrytool.dto;

import java.util.List;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class MapDetailData {
    String id;
    String itemId;
    String createdBy;
    String name;
    String description;
    int width;
    int height;
    List<TagData> tags;
    Long likes = 0L;
    Long downloads = 0L;
    Long comments = 0L;

    public String getId() { return id; }
    public String getItemId() { return itemId; }
    public String getCreatedBy() { return createdBy; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public List<TagData> getTags() { return tags; }
    public Long getLikes() { return likes; }
    public Long getDownloads() { return downloads; }
    public Long getComments() { return comments; }
}
