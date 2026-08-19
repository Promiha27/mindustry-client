package mindustrytool.dto;

import java.util.List;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class SchematicDetailData {
    String id;
    String itemId;
    String createdBy;
    String name;
    String description;
    int width;
    int height;
    Long likes = 0L;
    Long downloads = 0L;
    Long comments = 0L;
    List<TagData> tags;
    SchematicMetadata meta;

    public String getId() { return id; }
    public String getItemId() { return itemId; }
    public String getCreatedBy() { return createdBy; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Long getLikes() { return likes; }
    public Long getDownloads() { return downloads; }
    public Long getComments() { return comments; }
    public List<TagData> getTags() { return tags; }
    public SchematicMetadata getMeta() { return meta; }

    public static class SchematicMetadata {
        List<SchematicRequirement> requirements;

        public List<SchematicRequirement> getRequirements() { return requirements; }
    }

    public static class SchematicRequirement {
        String name;
        String color;
        Integer amount;

        public String getName() { return name; }
        public String getColor() { return color; }
        public Integer getAmount() { return amount; }
    }
}
