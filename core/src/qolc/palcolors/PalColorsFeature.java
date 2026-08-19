package qolc.palcolors;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Image;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Кастомизация всей палитры {@link Pal} (цвет команды-акцента, лазеров, щитов, хила...) с
 * сохранением между сессиями. Порт core/colors.js.
 * <p>
 * qol-suite'овский Beam Colors перекрашивает три конкретных луча скобочной подменой на время
 * отрисовки; здесь другой уровень - постоянная подмена ЗНАЧЕНИЯ любого поля Pal (сам объект Color
 * мутируется через {@code set()}, ссылки не трогаются, так что кеширующие ссылку потребители видят
 * новый цвет). Скобочная подмена Beam Colors захватывает и восстанавливает текущее значение
 * НА КАЖДОМ кадре, поэтому обе фичи сосуществуют: перекрашенный здесь Pal.heal просто станет для неё
 * новой "ванилью".
 * <p>
 * Ключи настроек {@code qol-pal-<имя>} - как у JS-оригинала, пользовательская палитра переживает порт.
 * Некоторые поля Pal ссылаются на ОДИН экземпляр Color (например пары свет/тень) - правка одного
 * меняет оба; оригинал вёл себя так же.
 */
public final class PalColorsFeature{
    private static final String prefix = "qol-pal-";
    private static final Seq<Entry> colors = new Seq<>();

    static class Entry{
        String name;
        Color color;
        String def; //hex-строка дефолта, снятая ДО применения сохранёнок - для кнопки сброса

        Entry(String name, Color color, String def){
            this.name = name;
            this.color = color;
            this.def = def;
        }
    }

    private PalColorsFeature(){
    }

    public static void init(){
        try{
            for(Field f : Pal.class.getDeclaredFields()){
                if(f.getType() != Color.class || !Modifier.isStatic(f.getModifiers())) continue;
                Color obj = (Color)f.get(null);
                if(obj == null) continue;
                Entry entry = new Entry(f.getName(), obj, obj.toString());

                String saved = Core.settings.getString(prefix + entry.name, null);
                if(saved != null && isHex(saved)){
                    try{
                        obj.set(Color.valueOf(saved));
                    }catch(Throwable ignored){
                    }
                }
                colors.add(entry);
            }
            colors.sort((a, b) -> a.name.compareTo(b.name));
        }catch(Throwable t){
            Log.err("[qol-control] failed to init Pal colors", t);
        }
    }

    private static boolean isHex(String s){
        if(s.length() != 6 && s.length() != 8) return false;
        for(int i = 0; i < s.length(); i++){
            char c = s.charAt(i);
            if(!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    public static void showDialog(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("qolc.palcolors.title"));
        dialog.addCloseButton();

        Table main = new Table();
        for(Entry entry : colors){
            Table row = new Table();

            Image swatch = new Image(Tex.whiteui);
            swatch.setColor(entry.color);

            TextField field = new TextField(entry.color.toString());
            field.setMaxLength(8);
            field.changed(() -> {
                String text = field.getText();
                if(isHex(text)){
                    try{
                        entry.color.set(Color.valueOf(text));
                        swatch.setColor(entry.color);
                        Core.settings.put(prefix + entry.name, text);
                    }catch(Throwable ignored){
                    }
                }
            });

            row.add(entry.name).width(200f).padRight(10f).left();
            row.add(field).width(130f).padRight(10f);
            row.add(swatch).size(32f).padRight(10f);
            row.button(Icon.cancel, Styles.clearNonei, () -> {
                entry.color.set(Color.valueOf(entry.def));
                swatch.setColor(entry.color);
                field.setText(entry.def);
                Core.settings.remove(prefix + entry.name);
            }).size(32f);

            main.add(row).left().row();
        }

        dialog.cont.add(new ScrollPane(main)).width(480f).height(Core.graphics.getHeight() * 0.65f).padTop(10f).padBottom(10f);
        dialog.show();
    }
}
