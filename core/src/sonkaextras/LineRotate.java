package sonkaextras;

import arc.*;
import arc.math.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.gen.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;
import mindustry.world.*;
import mindustry.world.blocks.distribution.*;

import static mindustry.Vars.*;

/**
 * Инверсия роли Alt при повороте протягиваемой линии колесом - для выбранных в пикере блоков.
 * <p>
 * Ваниль-механика (без правок работает для НЕвыбранных блоков): колесо во время протяжки
 * ({@code mode == placing}) через латч {@code overrideLineRotation} в
 * {@code DesktopInput.pollInputPlayer} переводит {@code InputHandler.iterateLine} в режим
 * "штамповать ВСЕМ планам текущий {@code rotation}" - т.е. крутит весь ряд разом; но латч
 * взводится только когда курсор уже увели со стартового тайла, а до того колесо крутит
 * единственный (последний) план напрямую. Alt в ваниль-повороте не участвует вовсе
 * (Alt при отпускании линии = force place, другая фича).
 * <p>
 * Для ВЫБРАННЫХ здесь блоков поведение переопределяется детерминированно:
 * <ul>
 * <li>колесо БЕЗ Alt - крутит весь ряд (латч взводится сразу, без требования увести курсор);</li>
 * <li>Alt+колесо - крутит ТОЛЬКО последний блок линии ({@code InputHandler.lineLastRotation},
 *     применяется постшагом в {@code updateLine} поверх любых пересчётов - path-поворотов,
 *     override-штампа и {@code handlePlacementLine} мостовых замен).</li>
 * </ul>
 * Дефолтный набор - мосты Эрекира (семейство {@link DirectionBridge}): у них хвост линии часто
 * хочется развернуть (выход моста = его собственный rotation), а весь ряд - оставить как проложен.
 * <p>
 * Кандидаты пикера собираются КОНТЕНТ-ДРИВЕН, без хардкода имён: все "линейные" транспортные
 * блоки ({@code rotate && conveyorPlacement} - Conveyor/StackConveyor/Duct/OverflowDuct/Conduit
 * и их модовые наследники) плюс семейство {@link DirectionBridge}. Серпуловские мосты
 * ({@code ItemBridge}) сознательно НЕ кандидаты: их хвост и так не имеет собственного выхода.
 * <p>
 * Настройка {@link #settingKey}: список имён блоков через запятую; ключ ОТСУТСТВУЕТ = дефолт
 * (все DirectionBridge), пустая строка = никто не выбран. Храним имена, а не ссылки на Block -
 * переживает перезагрузку контента.
 */
public final class LineRotate{
    public static final String settingKey = "sonka-linerotate-blocks";

    /** Кэш выбранных имён блоков; null = ещё не разобран из настройки (лениво). */
    static ObjectSet<String> selected;

    private LineRotate(){
    }

    /** Инвертирована ли роль Alt для этого блока (выбран ли он в пикере). Зовётся из DesktopInput на колесо. */
    public static boolean inverted(Block b){
        if(b == null) return false;
        if(selected == null) rebuild();
        return selected.contains(b.name);
    }

    /** Кандидат пикера: линейный транспорт или эрекирский мост (см. javadoc класса). */
    static boolean candidate(Block b){
        return (b.rotate && b.conveyorPlacement) || b instanceof DirectionBridge;
    }

    static Seq<Block> candidates(){
        return content.blocks().select(b -> candidate(b) && !b.isHidden() && b.uiIcon != null && b.uiIcon.found());
    }

    static void rebuild(){
        selected = new ObjectSet<>();
        String raw = Core.settings.getString(settingKey, null);
        if(raw == null){
            //ключа нет = дефолт: все мосты Эрекира, сколько бы их ни было в текущем контенте
            for(Block b : content.blocks()){
                if(b instanceof DirectionBridge) selected.add(b.name);
            }
        }else if(!raw.isEmpty()){
            for(String name : raw.split(",")) selected.add(name);
        }
    }

    static void save(){
        Seq<String> names = selected.toSeq();
        names.sort(); //стабильный порядок - settings.bin не «дребезжит» от порядка ObjectSet
        Core.settings.put(settingKey, names.toString(","));
    }

    static void toggle(Block b){
        if(selected == null) rebuild();
        if(!selected.add(b.name)) selected.remove(b.name);
        save();
    }

    /**
     * Пикер блоков: сетка иконок-тоглов (паттерн {@link MenuUnitDialog}) + кнопки быстрого
     * выбора. Открывается кнопкой из секции «Sonka Extras» вкладки «Моды» (регистрируется в
     * {@link ChainWarn#init}, где уже живёт билдер этой секции).
     */
    public static class PickerDialog extends BaseDialog{
        public PickerDialog(){
            super("@client.sonka.linerotate.title");
            addCloseButton();
            shown(this::setup);
            onResize(this::setup);
        }

        private void setup(){
            cont.clear();
            if(selected == null) rebuild();

            cont.add("@client.sonka.linerotate.hint").width(520f).wrap().pad(6f).row();

            cont.table(t -> {
                t.defaults().growX().height(44f).pad(2f);
                t.button("@client.sonka.linerotate.default", () -> {
                    //дефолт = снести ключ: rebuild сам соберёт актуальные DirectionBridge
                    Core.settings.remove(settingKey);
                    selected = null;
                    setup();
                });
                t.button("@client.sonka.linerotate.all", () -> {
                    for(Block b : candidates()) selected.add(b.name);
                    save();
                    setup();
                });
                t.button("@client.sonka.linerotate.none", () -> {
                    selected.clear();
                    save();
                    setup();
                });
            }).growX().row();

            Seq<Block> blocks = candidates();
            int cols = Mathf.clamp((int)(Core.graphics.getWidth() / Scl.scl(64f)) - 4, 4, 12);

            cont.pane(p -> {
                int i = 0;
                for(Block b : blocks){
                    //LineRotate.toggle, а не унаследованный Dialog.toggle() - имена совпадают
                    ImageButton btn = p.button(Tex.whiteui, Styles.clearTogglei, 40, () -> LineRotate.toggle(b))
                        .size(56f).tooltip(b.localizedName).get();
                    //ImageButton копирует shared-стиль в конструкторе - мутировать getStyle() безопасно
                    //(тот же приём, что в MenuUnitDialog)
                    btn.getStyle().imageUp = new TextureRegionDrawable(b.uiIcon);
                    btn.update(() -> btn.setChecked(selected != null && selected.contains(b.name)));
                    if(++i % cols == 0) p.row();
                }
            }).grow().pad(4f);
        }
    }
}
