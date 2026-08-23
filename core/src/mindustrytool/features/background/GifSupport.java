package mindustrytool.features.background;

/**
 * Изолированная проверка доступности GIF-декодера. Класс-верификатор JVM линкует ВЕСЬ класс
 * целиком при первом вызове любого его метода - если бы это был просто available() внутри
 * {@link GifBackgroundLoader}, сама попытка проверить "доступен ли awt/imageio" валила
 * NoClassDefFoundError на урезанных JRE без модуля java.desktop (например jlink-сборки
 * Windows-дистрибутива), потому что верификация задевает ДРУГИЕ методы того же класса
 * (load()/toTexture()), которые ссылаются на java.awt.Image через BufferedImage - краш происходил
 * ещё до того как guard успевал вернуть false. Здесь же нет ни единого упоминания java.awt/imageio
 * ни в полях, ни в сигнатурах методов (только строковый литерал в Class.forName) - этот класс
 * линкуется всегда, на любой JRE.
 *
 * Раньше проверка была через Package.getPackage("javax.imageio") != null - но это НЕ попытка
 * загрузки пакета, а просто взгляд в список уже загруженных пакетов текущей JVM. На свежем
 * запуске, до того как что-либо в javax.imageio реально использовалось, это почти всегда null
 * даже когда java.desktop полностью доступен - фича молча считала GIF недоступным и откатывалась
 * на статичный Texture(file) (тот декодирует только первый кадр gif через stb_image), отсюда баг
 * "гифка загружается, но выглядит статичной". Class.forName - настоящая попытка загрузки, а
 * ClassNotFoundException при реально отсутствующем java.desktop ловится нормальным catch, а не
 * валит верификацию этого класса (в отличие от прежнего бага).
 */
public final class GifSupport{
    private GifSupport(){}

    public static boolean available(){
        try{
            Class.forName("javax.imageio.ImageIO");
            return true;
        }catch(Throwable t){
            return false;
        }
    }
}
