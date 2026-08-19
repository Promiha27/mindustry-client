package mindustrytool.dto;

import java.util.List;
import java.util.Optional;

import mindustrytool.features.chat.global.dto.ChatUser.SimpleRole;

/** Порт: lombok @Data заменён на обычные поля + геттеры. */
public class UserData {
    private String id;
    private String name;
    private String imageUrl;
    private List<SimpleRole> roles;

    public String getId() { return id; }
    public String getName() { return name; }
    public String getImageUrl() { return imageUrl; }
    public List<SimpleRole> getRoles() { return roles; }

    public Optional<SimpleRole> getHighestRole() {
        if (roles == null || roles.isEmpty()) {
            return Optional.empty();
        }

        return getRoles().stream().max((a, b) -> Integer.compare(a.getLevel(), b.getLevel()));
    }
}
