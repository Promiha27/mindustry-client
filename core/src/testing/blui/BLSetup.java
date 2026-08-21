package testing.blui;

import arc.func.Boolp;
import arc.func.Cons;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Align;
import mindustry.gen.Icon;
import mindustry.gen.Tex;

import static mindustry.Vars.*;

/**
 * Контейнер BLUI в нижнем левом углу HUD: несколько «страниц» (таблиц), между которыми
 * переключает кнопка-стрелка (клик - следующая страница, удержание - свернуть). В оригинале
 * библиотека общая для нескольких модов MEEPofFaith и искала уже добавленный контейнер через
 * reflection по имени класса из settings - в клиенте потребитель один (Testing Utilities),
 * поэтому контейнер создаётся напрямую без SafeReflect/«blui-table-class».
 * <p>
 * Видимость дополнительно гейтится выбором админ-панели ({@link sonkaextras.AdminPanel}):
 * «Scheme Size» прячет эту панель целиком, диалоги остаются доступны из FeaturesDialog.
 */
public class BLSetup{
    private static BLUITable bluiTable;

    private BLSetup(){
    }

    public static Table getBLUITable(){
        if(bluiTable != null) return bluiTable;

        BLStyles.init();

        BLUITable all = new BLUITable();
        all.bottom().left();
        all.name = "blui";
        all.setOrigin(Align.bottomLeft);
        ui.hudGroup.addChild(all);

        return bluiTable = all;
    }

    public static void addTable(Cons<Table> t){
        addTable(t, null);
    }

    public static void addTable(Cons<Table> t, Boolp visible){
        BLUITable table = (BLUITable)getBLUITable();
        table.tables.add(t);
        if(visible != null) table.visibles.put(t, visible);
    }

    /** Все страницы зарегистрированы - показать первую видимую (в оригинале - по ClientLoadEvent). */
    public static void finish(){
        if(bluiTable == null) return;
        bluiTable.next();
        mobileOffset(bluiTable);
    }

    /** Сдвиг вверх на мобильных: там в левом нижнем углу сидит кнопка командного режима. */
    public static void mobileOffset(Table table){
        if(mobile) table.moveBy(0f, Scl.scl(46f));
    }

    public static void customOffset(Table table, float xOff, float yOff, boolean addMobileOffset){
        table.setPosition(0, 0);
        table.moveBy(xOff, yOff);
        if(addMobileOffset) mobileOffset(table);
    }

    private static class BLUITable extends Table{
        private final Seq<Cons<Table>> tables = new Seq<>();
        private final ObjectMap<Cons<Table>, Boolp> visibles = new ObjectMap<>();
        private final Table cont = new Table();
        private int current = -1;
        private boolean folded;

        public BLUITable(){
            defaults().bottom().left();

            add(cont);
            table(Tex.buttonEdge3, t -> {
                HoldImageButton b = new HoldImageButton(Icon.refresh, BLStyles.bluiHoldImageStyle);
                b.clicked(() -> {
                    if(folded){
                        folded = false;
                        current--;
                    }
                    next();
                });
                b.held(() -> {
                    clearTable();
                    folded = true;
                });
                b.canHold(() -> !folded);
                b.resizeImage(BLVars.iconSize);

                b.getStyle().imageHeld = Icon.leftOpen;
                b.update(() -> b.getStyle().imageUp = folded ? Icon.rightOpen : Icon.refresh);

                t.add(b);
            }).update(t -> checkVisibility());
            visible(() -> ui.hudfrag.shown && !ui.minimapfrag.shown() && hasVisible() && sonkaextras.AdminPanel.testingEnabled());
        }

        private void next(){
            if(tables.isEmpty()) return;

            //защита от бесконечной рекурсии оригинала, когда ни одна страница не видима
            for(int i = 0; i < tables.size; i++){
                current = (current + 1) % tables.size;
                Cons<Table> table = tables.get(current);
                if(tableVisible(table)){
                    clearTable();
                    cont.defaults().bottom().left();
                    table.get(cont);
                    return;
                }
            }
        }

        private void clearTable(){
            cont.clear();
            cont.background(null);
        }

        /** Текущая страница стала невидимой - переключиться на следующую. */
        private void checkVisibility(){
            if(tables.any() && current >= 0 && !tableVisible(tables.get(current)) && hasVisible()) next();
        }

        private boolean hasVisible(){
            return tables.contains(this::tableVisible);
        }

        private boolean tableVisible(Cons<Table> table){
            return !visibles.containsKey(table) || visibles.get(table).get();
        }
    }
}
