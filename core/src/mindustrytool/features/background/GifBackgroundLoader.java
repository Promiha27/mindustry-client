package mindustrytool.features.background;

import arc.files.Fi;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.struct.Seq;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Iterator;

/**
 * Декодирует анимированный GIF в набор кадров-текстур для {@link BackgroundFeature.GifMenuRenderer}.
 * javax.imageio's read(i) отдаёт только суб-изображение конкретного кадра со своим left/top из
 * ImageDescriptor, а не готовый композит поверх предыдущих кадров - поэтому кадры последовательно
 * накладываются на общий холст (упрощение: считаем disposal method "не очищать", это подавляющее
 * большинство реальных gif; полный набор disposal-режимов не поддержан). MAX_FRAMES/MAX_DIMENSION -
 * страховка от gif на сотни МБ видеопамяти при разовой синхронной декодировке на GL-потоке (этот
 * метод, как и Texture(file) для статичного фона рядом, всегда вызывается синхронно из UI-колбэка).
 * Проверка доступности (java.desktop/javax.imageio) живёт отдельно, в {@link GifSupport} - см. его
 * javadoc за тем, почему вызывать её отсюда же нельзя.
 */
public final class GifBackgroundLoader{
    private static final int MAX_FRAMES = 240;
    private static final int MAX_DIMENSION = 1920;
    private static final int DEFAULT_DELAY_MS = 100;

    private GifBackgroundLoader(){}

    public static class Result{
        public final Texture[] frames;
        public final int[] delaysMs;
        public final boolean truncated;

        Result(Texture[] frames, int[] delaysMs, boolean truncated){
            this.frames = frames;
            this.delaysMs = delaysMs;
            this.truncated = truncated;
        }

        public int totalDurationMs(){
            int total = 0;
            for(int d : delaysMs) total += d;
            return Math.max(total, 1);
        }

        public void dispose(){
            for(Texture t : frames) t.dispose();
        }
    }

    public static Result load(Fi file) throws Exception{
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if(!readers.hasNext()) throw new IllegalStateException("no GIF reader available on this JVM");
        ImageReader reader = readers.next();

        try(ImageInputStream stream = ImageIO.createImageInputStream(file.file())){
            reader.setInput(stream, false);

            int totalFrames = reader.getNumImages(true);
            int numFrames = Math.min(totalFrames, MAX_FRAMES);
            boolean truncated = totalFrames > MAX_FRAMES;

            int canvasW = reader.getWidth(0), canvasH = reader.getHeight(0);
            float scale = Math.min(1f, MAX_DIMENSION / (float)Math.max(canvasW, canvasH));
            int outW = Math.max(1, Math.round(canvasW * scale));
            int outH = Math.max(1, Math.round(canvasH * scale));

            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();

            Seq<Texture> frames = new Seq<>();
            Seq<Integer> delays = new Seq<>();

            for(int i = 0; i < numFrames; i++){
                BufferedImage frame = reader.read(i);
                IIOMetadata metadata = reader.getImageMetadata(i);
                Node root = metadata.getAsTree(metadata.getNativeMetadataFormatName());

                int left = 0, top = 0;
                Node descriptor = findNode(root, "ImageDescriptor");
                if(descriptor != null){
                    left = intAttr(descriptor, "imageLeftPosition", 0);
                    top = intAttr(descriptor, "imageTopPosition", 0);
                }
                g.drawImage(frame, left, top, null);

                int delayCs = 0;
                Node control = findNode(root, "GraphicControlExtension");
                if(control != null) delayCs = intAttr(control, "delayTime", 0);
                delays.add(delayCs <= 1 ? DEFAULT_DELAY_MS : delayCs * 10);

                frames.add(toTexture(canvas, outW, outH, scale));
            }
            g.dispose();

            int[] delaysArr = new int[delays.size];
            for(int i = 0; i < delays.size; i++) delaysArr[i] = delays.get(i);
            return new Result(frames.toArray(Texture.class), delaysArr, truncated);
        }finally{
            reader.dispose();
        }
    }

    private static Texture toTexture(BufferedImage canvas, int outW, int outH, float scale){
        BufferedImage source = canvas;
        if(scale < 1f){
            BufferedImage scaled = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = scaled.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            sg.drawImage(canvas, 0, 0, outW, outH, null);
            sg.dispose();
            source = scaled;
        }

        //arc Pixmaps are bottom-up (GL origin bottom-left) - flip while copying from top-down BufferedImage rows
        Pixmap pixmap = new Pixmap(outW, outH);
        for(int y = 0; y < outH; y++){
            for(int x = 0; x < outW; x++){
                int argb = source.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, gr = (argb >> 8) & 0xFF, b = argb & 0xFF;
                pixmap.set(x, outH - y - 1, Color.rgba8888(r / 255f, gr / 255f, b / 255f, a / 255f));
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static int intAttr(Node node, String name, int def){
        Node attr = node.getAttributes().getNamedItem(name);
        return attr != null ? Integer.parseInt(attr.getNodeValue()) : def;
    }

    private static Node findNode(Node root, String name){
        Node node = root.getFirstChild();
        while(node != null){
            if(node.getNodeName().equals(name)) return node;
            Node child = findNode(node, name);
            if(child != null) return child;
            node = node.getNextSibling();
        }
        return null;
    }
}
