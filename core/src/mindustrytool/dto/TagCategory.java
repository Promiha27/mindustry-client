package mindustrytool.dto;

import java.util.List;

import arc.graphics.Color;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class TagCategory {
    private String id;
    private String name;
    private String color;
    private int position;
    private boolean duplicate;
    private String createdBy;
    private String updatedBy;
    private List<TagData> tags;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public int getPosition() { return position; }
    public boolean isDuplicate() { return duplicate; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }
    public List<TagData> getTags() { return tags; }

    public Color color() {
        try {
            return Color.valueOf(color);
        } catch (Exception ex) {
            return Color.white;
        }
    }
}
