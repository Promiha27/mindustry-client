package mindustrytool;

import arc.ApplicationListener;
import arc.Core;
import arc.graphics.Texture;
import arc.graphics.g2d.TextureRegion;
import arc.math.geom.*;
import arc.scene.style.TextureRegionDrawable;
import arc.struct.*;
import arc.util.Log;
import arc.util.io.*;
import arc.util.serialization.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.Schematic;
import mindustry.game.Schematic.*;
import mindustry.gen.Icon;
import mindustry.io.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.legacy.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.blocks.storage.*;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.*;

import static mindustry.Vars.*;

/**
 * Порт: jackson (ObjectMapper) заменён на собственный маленький маппер поверх
 * движкового {@link Jval} — клиенту не добавляется ни одной новой зависимости.
 * Маппер покрывает ровно то, что нужно DTO мода: строки, примитивы и их
 * обёртки, enum'ы, вложенные POJO, массивы и {@code java.util.List<E>}
 * (тип элемента берётся из generic-сигнатуры поля). Неизвестные поля JSON
 * игнорируются — как и было с {@code FAIL_ON_UNKNOWN_PROPERTIES=false}.
 * Также убран lombok: у DTO обычные поля + написанные руками геттеры.
 */
public class Utils {

    public static ObjectMap<String, Schematic> schematicData = new ObjectMap<>();
    private static ConcurrentHashMap<String, TextureRegionDrawable> iconCache = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<TextureRegionDrawable, TextureRegionDrawable> scalableIconCache = new ConcurrentHashMap<>();

    private static final byte[] header = { 'm', 's', 'c', 'h' };

    public static synchronized Schematic readSchematic(String data) {
        return schematicData.get(data, () -> readBase64(data));
    }

    public static Schematic readBase64(String schematic) {
        try {
            return read(new ByteArrayInputStream(Base64Coder.decode(schematic.trim())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Собственный ридер схем у мода существует ради лимита 1028 вместо
     * Vars.maxSchematicSize — схемы с mindustry-tool.com бывают больше ванильных.
     */
    public static Schematic read(InputStream input) throws IOException {
        for (byte b : header) {
            if (input.read() != b) {
                throw new IOException("Not a schematic file (missing header).");
            }
        }

        int ver = input.read();

        try (DataInputStream stream = new DataInputStream(new InflaterInputStream(input))) {
            short width = stream.readShort(), height = stream.readShort();

            if (width > 1028 || height > 1028)
                throw new IOException("Invalid schematic: Too large (max possible size is 128x128)");

            StringMap map = new StringMap();
            int tags = stream.readUnsignedByte();
            for (int i = 0; i < tags; i++) {
                map.put(stream.readUTF(), stream.readUTF());
            }

            String[] labels = null;

            // try to read the categories, but skip if it fails
            try {
                labels = JsonIO.read(String[].class, map.get("labels", "[]"));
            } catch (Exception ignored) {
            }

            IntMap<Block> blocks = new IntMap<>();
            byte length = stream.readByte();
            for (int i = 0; i < length; i++) {
                String name = stream.readUTF();
                Block block = Vars.content.getByName(ContentType.block, SaveFileReader.fallback.get(name, name));
                blocks.put(i, block == null || block instanceof LegacyBlock ? Blocks.air : block);
            }

            int total = stream.readInt();

            if (total > 128 * 128)
                throw new IOException("Invalid schematic: Too many blocks.");

            Seq<Stile> tiles = new Seq<>(total);
            for (int i = 0; i < total; i++) {
                Block block = blocks.get(stream.readByte());
                int position = stream.readInt();
                Object config = ver == 0 ? mapConfig(block, stream.readInt(), position)
                        : TypeIO.readObject(new Reads(stream));
                byte rotation = stream.readByte();
                if (block != Blocks.air) {
                    tiles.add(new Stile(block, Point2.x(position), Point2.y(position), config, rotation));
                }
            }

            Schematic out = new Schematic(tiles, map, width, height);
            if (labels != null)
                out.labels.addAll(labels);
            return out;
        }
    }

    private static Object mapConfig(Block block, int value, int position) {
        if (block instanceof Sorter || block instanceof Unloader || block instanceof ItemSource)
            return content.item(value);
        if (block instanceof LiquidSource)
            return content.liquid(value);
        if (block instanceof MassDriver || block instanceof ItemBridge)
            return Point2.unpack(value).sub(Point2.x(position), Point2.y(position));
        if (block instanceof LightBlock)
            return value;

        return null;
    }

    // ==================== JSON: Jval-маппер вместо jackson ====================

    public static String toJson(Object object) {
        return writeJval(object).toString();
    }

    public static <T> T fromJson(Class<T> clazz, String json) {
        try {
            return readJval(Jval.read(json), clazz, null);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> List<T> fromJsonArray(Class<T> clazz, String json) {
        try {
            Jval val = Jval.read(json);
            List<T> out = new ArrayList<>();
            if (val.isArray()) {
                for (Jval item : val.asArray()) {
                    out.add(readJval(item, clazz, null));
                }
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Object → Jval. Поддержка: null, строки, числа, bool, enum, Map, Iterable, массивы, POJO. */
    @SuppressWarnings("rawtypes")
    private static Jval writeJval(Object o) {
        if (o == null) return Jval.newObject(); // null на верхнем уровне не встречается
        if (o instanceof String s) return Jval.valueOf(s);
        if (o instanceof Character c) return Jval.valueOf(String.valueOf(c));
        if (o instanceof Boolean b) return Jval.valueOf(b);
        if (o instanceof Integer i) return Jval.valueOf(i);
        if (o instanceof Long l) return Jval.valueOf(l);
        if (o instanceof Float f) return Jval.valueOf(f);
        if (o instanceof Double d) return Jval.valueOf(d);
        if (o instanceof Number n) return Jval.valueOf(n.doubleValue());
        if (o instanceof Enum e) return Jval.valueOf(e.name());
        if (o instanceof Map<?, ?> map) {
            Jval obj = Jval.newObject();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                putJval(obj, String.valueOf(e.getKey()), e.getValue());
            }
            return obj;
        }
        if (o instanceof Iterable<?> it) {
            Jval arr = Jval.newArray();
            for (Object v : it) {
                arr.asArray().add(writeJval(v));
            }
            return arr;
        }
        if (o.getClass().isArray()) {
            Jval arr = Jval.newArray();
            int len = Array.getLength(o);
            for (int i = 0; i < len; i++) {
                arr.asArray().add(writeJval(Array.get(o, i)));
            }
            return arr;
        }
        // POJO — все нестатические поля по иерархии
        Jval obj = Jval.newObject();
        for (Field field : allFields(o.getClass())) {
            try {
                Object value = field.get(o);
                if (value != null) {
                    putJval(obj, field.getName(), value);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return obj;
    }

    private static void putJval(Jval obj, String key, Object value) {
        if (value == null) return;
        obj.put(key, writeJval(value));
    }

    /** Jval → объект. elementType — тип элементов, если target — List (из generic-сигнатуры поля). */
    @SuppressWarnings("unchecked")
    private static <T> T readJval(Jval v, Class<T> type, Class<?> elementType) throws Exception {
        if (v == null || v.isNull()) return null;

        if (type == String.class) return (T) v.asString();
        if (type == int.class || type == Integer.class) return (T) (Integer) v.asInt();
        if (type == long.class || type == Long.class) return (T) (Long) v.asLong();
        if (type == float.class || type == Float.class) return (T) (Float) v.asFloat();
        if (type == double.class || type == Double.class) return (T) (Double) v.asDouble();
        if (type == boolean.class || type == Boolean.class) return (T) (Boolean) v.asBool();
        if (type == short.class || type == Short.class) return (T) (Short) (short) v.asInt();
        if (type == byte.class || type == Byte.class) return (T) (Byte) (byte) v.asInt();
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(v.asString())) return (T) constant;
            }
            return null;
        }
        if (type == List.class || type == ArrayList.class) {
            List<Object> list = new ArrayList<>();
            if (v.isArray()) {
                for (Jval item : v.asArray()) {
                    list.add(elementType == null ? plainValue(item) : readJval(item, elementType, null));
                }
            }
            return (T) list;
        }
        if (type.isArray()) {
            Class<?> component = type.getComponentType();
            Seq<Jval> arr = v.isArray() ? v.asArray() : new Seq<>();
            Object out = Array.newInstance(component, arr.size);
            for (int i = 0; i < arr.size; i++) {
                Array.set(out, i, readJval(arr.get(i), component, null));
            }
            return (T) out;
        }

        // POJO
        if (!v.isObject()) return null;
        var ctor = type.getDeclaredConstructor();
        ctor.setAccessible(true);
        T instance = ctor.newInstance();
        for (Field field : allFields(type)) {
            Jval member = v.get(field.getName());
            if (member == null || member.isNull()) continue; // неизвестные/отсутствующие поля игнорируем
            Class<?> element = null;
            Type generic = field.getGenericType();
            if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1
                    && pt.getActualTypeArguments()[0] instanceof Class<?> c) {
                element = c;
            }
            try {
                Object value = readJval(member, field.getType(), element);
                if (value != null) field.set(instance, value);
            } catch (Exception e) {
                Log.err("JSON: failed to map field " + type.getSimpleName() + "." + field.getName(), e);
            }
        }
        return instance;
    }

    /** Значение без объявленного типа (List<Object> и т.п.). */
    private static Object plainValue(Jval v) {
        if (v.isString()) return v.asString();
        if (v.isBoolean()) return v.asBool();
        if (v.isNumber()) return v.asDouble();
        return null;
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || f.isSynthetic()) continue;
                f.setAccessible(true);
                out.add(f);
            }
        }
        return out;
    }

    // ==================== разное ====================

    public static String getString(String text) {
        if (text == null) {
            return "";
        }

        if (text.startsWith("@")) {
            String key = text.substring(1);
            try {
                return Core.bundle.get(key);
            } catch (Exception e) {
                return text;
            }
        }
        // Порт: у части фич мода имена без "@" ("feature.smart-drill" и т.п.), и апстрим
        // показывал их сырым ключом; здесь пробуем бандл и для таких строк.
        if (Core.bundle.has(text)) {
            return Core.bundle.get(text);
        }
        return text;
    }

    public static TextureRegionDrawable scalable(TextureRegionDrawable original) {
        return scalableIconCache.computeIfAbsent(original, _key -> new TextureRegionDrawable(original.getRegion()));
    }

    /** Иконки мода теперь лежат в core/assets/mindustrytool/icons (грузятся как отдельные текстуры, мимо атласа). */
    public static TextureRegionDrawable icons(String name) {
        if (iconCache.containsKey(name)) {
            return iconCache.get(name);
        }

        try {
            var texture = new TextureRegion(new Texture(Core.files.internal("mindustrytool/icons/" + name)));
            var drawable = new TextureRegionDrawable(texture);
            iconCache.put(name, drawable);
            return drawable;
        } catch (Exception e) {
            Log.err(e.getMessage());
            iconCache.put(name, Icon.book);
            return Icon.book;
        }
    }

    public static void onAppExit(Runnable callback) {
        Core.app.addListener(new ApplicationListener() {
            @Override
            public void exit() {
                try {
                    callback.run();
                } catch (Throwable e) {
                    Log.err(e);
                }
            }
        });
    }

    public static void setField(Object object, String fieldName, Object value) {
        if (object == null || fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Object or field name is null or empty");
        }

        Class<?> clazz = object.getClass();

        while (clazz != null) {
            try {
                var field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(object, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access field: " + fieldName, e);
            }
        }

        throw new RuntimeException("Field not found: " + fieldName);
    }
}
