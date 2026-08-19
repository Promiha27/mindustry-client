package mindustrytool.features.chat.global.dto;

import java.util.List;
import java.util.Optional;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class ChatUser {
    private String name;
    private String imageUrl;
    private List<SimpleRole> roles;
    private String state = "";

    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public List<SimpleRole> getRoles() { return roles; }
    public String getState() { return state; }

    public Optional<SimpleRole> getHighestRole() {
        if (roles == null || roles.isEmpty()) {
            return Optional.empty();
        }

        return getRoles().stream().max((a, b) -> Integer.compare(a.getLevel(), b.getLevel()));
    }

    public static class SimpleRole {
        String id;
        String color;
        String icon;
        int level;

        public String getId() { return id; }
        public String getColor() { return color; }
        public String getIcon() { return icon; }
        public int getLevel() { return level; }
    }
}
