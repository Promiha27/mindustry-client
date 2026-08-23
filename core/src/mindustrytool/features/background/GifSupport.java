package mindustrytool.features.background;

/**
 * Изолированная проверка доступности GIF-декодера. Класс-верификатор JVM линкует ВЕСЬ класс
 * целиком при первом вызове любого его метода - если бы это был просто available() внутри
 * {@link GifBackgroundLoader}, сама попытка проверить "доступен ли awt/imageio" валила
 * NoClassDefFoundError на урезанных JRE без модуля java.desktop (например jlink-сборки
 * Windows-дистрибутива), потому что верификация задевает ДРУГИЕ методы того же класса
 * (load()/toTexture()), которые ссылаются на java.awt.Image через BufferedImage - краш происходил
 * ещё до того как guard успевал вернуть false. Здесь же нет ни единого упоминания java.awt/imageio
 * ни в полях, ни в сигнатурах методов - этот класс линкуется всегда, на любой JRE.
 */
public final class GifSupport{
    private GifSupport(){}

    public static boolean available(){
        return Package.getPackage("javax.imageio") != null;
    }
}
