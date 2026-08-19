package mindustrytool.features.browser;

import java.util.List;
import java.util.Objects;

import arc.struct.Seq;
import mindustrytool.Config;

import mindustrytool.dto.Sort;
import mindustrytool.dto.TagCategory;
import mindustrytool.dto.TagData;

public class SearchConfig {
    private Seq<SelectedTag> selectedTags = new Seq<>();
    private Seq<String> blocks = new Seq<>();
    private Sort sort = Config.sorts.get(0);
    private boolean changed = false;

    public void update() {
        changed = false;
    }

    public boolean isChanged() {
        return changed;
    }

    public void toggleBlock(String block) {
        if (blocks.contains(block)) {
            blocks.remove(block);
        } else {
            blocks.add(block);
        }
        changed = true;
    }

    public boolean containBlock(String block) {
        return blocks.contains(block);
    }

    public List<String> getBlocks() {
        return blocks.list();
    }

    public String getSelectedTagsString() {
        if (selectedTags.isEmpty()) {
            return "";
        }
        return String.join(",", selectedTags.map(s -> s.categoryName + "_" + s.name));
    }

    public Seq<SelectedTag> getSelectedTags() {
        return selectedTags;
    }

    public void setTag(TagCategory category, TagData value) {
        SelectedTag tag = new SelectedTag();

        tag.name = value.getName();
        tag.categoryName = category.getName();
        tag.icon = value.getIcon();

        if (selectedTags.contains(tag)) {
            this.selectedTags.remove(tag);
        } else {
            this.selectedTags.add(tag);
        }
        changed = true;
    }

    public boolean containTag(TagCategory category, TagData tag) {
        return selectedTags.contains(v -> v.name.equals(tag.getName()) && category.getName().equals(v.categoryName));
    }

    public Sort getSort() {
        return sort;
    }

    public void setSort(Sort sort) {
        this.sort = sort;
        changed = true;
    }

    /**
     * Порт: lombok @Data убран; equals/hashCode написаны руками — на них
     * держится toggle-логика setTag (contains/remove по значению).
     */
    public static class SelectedTag {
        String name;
        String categoryName;
        String icon;

        public String getName() { return name; }
        public String getCategoryName() { return categoryName; }
        public String getIcon() { return icon; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SelectedTag other)) return false;
            return Objects.equals(name, other.name)
                    && Objects.equals(categoryName, other.categoryName)
                    && Objects.equals(icon, other.icon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, categoryName, icon);
        }
    }
}
