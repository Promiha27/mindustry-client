package sonkaextras.hotkeys;

import arc.func.*;
import arc.input.*;
import arc.input.KeyBind.*;
import arc.struct.*;
import arc.util.*;

import java.util.*;

/**
 * Одна строка единого списка хоткеев ({@link HotkeysDialog}). Две природы записей:
 * <ul>
 *   <li>{@link #bind} != null - зарегистрированный {@link KeyBind} (ваниль, клиент, вшитые моды):
 *   текст клавиши читается вживую из {@code bind.value}, переназначение - через штатное
 *   «Управление» ({@code KeybindDialog.showFor});</li>
 *   <li>{@link #bind} == null - комбо, которых нет в реестре KeyBind (хардкод вроде Ctrl+буква
 *   для типов юнитов, аккорд G-цифра-цифра таблицы схем, пользовательские чат-бинды QoL Control):
 *   текст клавиши даёт {@link #keyText}, а {@link #configure} (если есть) открывает диалог
 *   владельца.</li>
 * </ul>
 * {@link #signature()} - нормализованная «модификаторы+клавиша» для поиска конфликтов; null =
 * запись в конфликтах не участвует (не назначена или это описательное комбо без точной клавиши).
 */
public class Hotkey{
    /** Отображаемое имя группы (уже через бандл). */
    public final String category;
    public final String name;
    public final @Nullable String desc;
    public final @Nullable KeyBind bind;
    /** Текст клавиши для записей без KeyBind. */
    public final @Nullable Prov<String> keyText;
    /** Сигнатура для конфликтов у записей без KeyBind (например {@code "F5"}); null = не участвует. */
    public final @Nullable String manualSignature;
    public final @Nullable Runnable configure;

    public Hotkey(String category, KeyBind bind, @Nullable String desc){
        this.category = category;
        this.bind = bind;
        this.name = HotkeyCatalog.bindName(bind);
        this.desc = desc;
        this.keyText = null;
        this.manualSignature = null;
        this.configure = null;
    }

    public Hotkey(String category, String name, @Nullable String desc, Prov<String> keyText, @Nullable String manualSignature, @Nullable Runnable configure){
        this.category = category;
        this.bind = null;
        this.name = name;
        this.desc = desc;
        this.keyText = keyText;
        this.manualSignature = manualSignature;
        this.configure = configure;
    }

    public boolean unset(){
        return bind != null && bind.isUnset();
    }

    /** Текущий текст клавиши: «Shift + F», «A / D» для осей, пусто - не назначена. */
    public String key(){
        if(bind == null) return keyText == null ? "" : keyText.get();
        return keyString(bind);
    }

    public @Nullable String signature(){
        if(bind == null) return manualSignature;
        Axis axis = bind.value;
        if(axis == null || bind.isUnset() || axis.key == null) return null;
        //бинды-модификаторы (control, diagonal_placement, create_control_group... - все на LCtrl)
        //совпадают по природе, а не по ошибке: в конфликтах не участвуют, иначе фильтр «только
        //конфликты» тонет в девяти строках «Ctrl»
        if(isModifier(axis.key)) return null;
        return signature(axis.modifiers, axis.key);
    }

    static boolean isModifier(KeyCode key){
        return key == KeyCode.controlLeft || key == KeyCode.controlRight
            || key == KeyCode.shiftLeft || key == KeyCode.shiftRight
            || key == KeyCode.altLeft || key == KeyCode.altRight;
    }

    public static String keyString(KeyBind bind){
        Axis axis = bind.value;
        if(axis == null) return "";
        String mods = axis.modifiers == null ? "" : Seq.with(axis.modifiers).toString("", m -> m.getModifierName() + " + ");
        if(axis.key != null){
            return axis.key == KeyCode.unset ? "" : mods + axis.key.getName();
        }
        if(axis.min == null || axis.max == null) return "";
        return mods + axis.min.getName() + " / " + axis.max.getName();
    }

    /** Модификаторы сортируются, чтобы Ctrl+Shift+X и Shift+Ctrl+X считались одним комбо. */
    public static String signature(@Nullable KeyCode[] modifiers, KeyCode key){
        if(modifiers == null || modifiers.length == 0) return key.getName();
        String[] names = new String[modifiers.length];
        for(int i = 0; i < modifiers.length; i++) names[i] = modifiers[i].getModifierName();
        Arrays.sort(names);
        return String.join("+", names) + "+" + key.getName();
    }
}
