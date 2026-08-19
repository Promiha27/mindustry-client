package mindustrytool.features;

import arc.struct.Seq;

public class WebFeature {
    private final String name;
    private final String description;
    private final String url;

    public WebFeature(String name, String description, String url) {
        this.name = name;
        this.description = description;
        this.url = url;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String url() {
        return url;
    }

    public static final Seq<WebFeature> defaults = Seq.with(
            new WebFeature(
                    "@content-patches",
                    "@content-patches.description",
                    "https://mindustry-tool.com/content-patches?size=100"),
            new WebFeature(
                    "@logic-editor",
                    "@logic-editor.description",
                    "https://mindustry-tool.com/tools/logic"),
            new WebFeature(
                    "@logic-display-generator",
                    "@logic-display-generator.description",
                    "https://mindustry-tool.com/tools/logic-display-generator"),
            new WebFeature(
                    "@sorter-image-generator",
                    "@sorter-image-generator.description",
                    "https://mindustry-tool.com/tools/sorter-generator"),
            new WebFeature(
                    "@canvas-image-generator",
                    "@canvas-image-generator.description",
                    "https://mindustry-tool.com/tools/canvas-generator"),
            new WebFeature(
                    "@wiki",
                    "@wiki.description",
                    "https://mindustry-tool.com/wiki"),
            new WebFeature(
                    "@post",
                    "@post.description",
                    "https://mindustry-tool.com/posts"),
            new WebFeature(
                    "@free-mindustry-server",
                    "@free-mindustry-server.description",
                    "https://mindustry-tool.com/@me/servers"));
}
