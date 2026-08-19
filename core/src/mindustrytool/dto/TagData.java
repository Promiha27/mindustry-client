package mindustrytool.dto;

import java.util.List;

import arc.graphics.Color;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class TagData {
    private String id;
    private String name;
    private Integer position = 0;
    private String categoryId;
    private String icon;
    private String fullTag;
    private String color;
    private Integer count = 0;
    private List<String> planetIds;

    public String getId() { return id; }
    public String getName() { return name; }
    public Integer getPosition() { return position; }
    public String getCategoryId() { return categoryId; }
    public String getIcon() { return icon; }
    public String getFullTag() { return fullTag; }
    public String getColor() { return color; }
    public Integer getCount() { return count; }
    public List<String> getPlanetIds() { return planetIds; }

    public Color color() {
        try {
            return Color.valueOf(color);
        } catch (Exception ex) {
            return Color.white;
        }
    }
}
