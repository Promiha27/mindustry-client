package mindustrytool.dto;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class ModData {
    private String id;
    private String name;
    private String icon;
    private Integer position = 0;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public Integer getPosition() { return position; }
}
