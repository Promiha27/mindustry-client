package sectorstats;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import mindustry.game.SectorInfo;
import mindustry.game.SectorInfo.ExportStat;
import mindustry.type.Item;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.content;

/**
 * Feature 1: a dialog summing production/export/import rates for every item across every captured
 * campaign sector (all planets at once, or filtered to one). Ported from {@code buildProductionDialog()}
 * and {@code collectStats()} in the source's {@code scripts/main.js}.
 * <p>
 * The source compared planets by {@code .name} string rather than object identity because Rhino can wrap
 * the same underlying Java object in more than one JS wrapper - not a concern in plain Java, so this port
 * compares {@link Planet} references directly.
 */
public class SectorStatsDialog{
    private final BaseDialog dialog;

    private boolean perMinute = true;
    /** null = every planet at once. */
    private String selectedPlanetName;

    public SectorStatsDialog(){
        dialog = new BaseDialog(Core.bundle.get("campaignutils.production-title"));
        dialog.addCloseButton();
        dialog.shown(this::rebuild);
    }

    public void show(){
        dialog.show();
    }

    private void rebuild(){
        dialog.cont.clearChildren();

        Seq<Planet> planets = capturedPlanets();
        Planet selectedPlanet = null;
        if(selectedPlanetName != null){
            for(Planet p : planets){
                if(p.name.equals(selectedPlanetName)){
                    selectedPlanet = p;
                    break;
                }
            }
            //the selected planet lost every base (or became inaccessible) since - fall back to "all planets"
            if(selectedPlanet == null) selectedPlanetName = null;
        }

        Stats data;
        try{
            data = collectStats(selectedPlanet);
        }catch(Throwable t){
            Log.err("[campaign-utils] failed to collect sector production stats", t);
            dialog.cont.add(Core.bundle.get("campaignutils.stats-collect-failed")).left().row();
            return;
        }

        dialog.cont.add(Core.bundle.format("campaignutils.captured-sectors", data.sectorCount, data.planetCount)).left().padBottom(6).row();

        Table controls = new Table();
        controls.button(Core.bundle.get("campaignutils.refresh"), this::rebuild).width(200).height(56).padRight(10);
        controls.button(Core.bundle.get(perMinute ? "campaignutils.per-minute" : "campaignutils.per-second"), () -> {
            perMinute = !perMinute;
            rebuild();
        }).width(200).height(56);
        dialog.cont.add(controls).left().padTop(4).padBottom(8).row();

        String planetLabel = selectedPlanet == null ? Core.bundle.get("campaignutils.all-planets") :
            (selectedPlanet.localizedName != null ? selectedPlanet.localizedName : selectedPlanet.name);
        dialog.cont.button(Core.bundle.format("campaignutils.planet-prefix", planetLabel), () -> {
            //cycles: all -> planet[0] -> planet[1] -> ... -> all again
            int idx = -1;
            for(int pi = 0; pi < planets.size; pi++){
                if(planets.get(pi).name.equals(selectedPlanetName)){
                    idx = pi;
                    break;
                }
            }
            idx++;
            selectedPlanetName = idx >= planets.size ? null : planets.get(idx).name;
            rebuild();
        }).width(410).height(56).left().padBottom(14).row();

        if(data.rows.isEmpty()){
            dialog.cont.add(Core.bundle.get("campaignutils.no-production-data")).left().row();
            return;
        }

        Table head = new Table();
        head.add("").width(40);
        head.add(Core.bundle.get("campaignutils.col-production")).right().width(150).padBottom(8);
        head.add(Core.bundle.get("campaignutils.col-export")).right().width(150).padBottom(8);
        head.add(Core.bundle.get("campaignutils.col-import")).right().width(150).padBottom(8);
        dialog.cont.add(head).left().padTop(6).row();

        Table list = new Table();
        for(Row row : data.rows){
            list.image(row.item.uiIcon).size(32).padRight(6).padTop(3).padBottom(3);
            list.add(fmtRate(row.production, perMinute)).right().width(150);
            list.add(fmtRate(row.exportv, perMinute)).right().width(150);
            list.add(fmtRate(row.importv, perMinute)).right().width(150);
            list.row();
        }

        //grow() breaks the layout here: BaseDialog itself packs to its content's size, and there's nowhere
        //for a ScrollPane to grow into when its own container also grows to fit content - it ends up with
        //empty/random bounds. Fixed size instead, same as the source.
        dialog.cont.pane(list).width(500).height(420).row();
    }

    /** Planets with at least one captured sector - the only ones with anything to show here. */
    private static Seq<Planet> capturedPlanets(){
        Seq<Planet> result = new Seq<>();
        for(Planet planet : content.planets()){
            if(planet.accessible && planet.sectors.contains(Sector::hasBase)) result.add(planet);
        }
        return result;
    }

    private static Stats collectStats(Planet planetFilter){
        ObjectMap<Item, Row> acc = new ObjectMap<>();
        int sectorCount = 0;
        ObjectSet<Planet> planetsSeen = new ObjectSet<>();

        for(Planet planet : content.planets()){
            if(!planet.accessible) continue;
            if(planetFilter != null && planet != planetFilter) continue;

            for(Sector sector : planet.sectors){
                if(!sector.hasBase()) continue;

                sectorCount++;
                planetsSeen.add(planet);

                SectorInfo info = sector.info;
                for(Item item : content.items()){
                    ExportStat rp = info.rawProduction.get(item);
                    ExportStat ex = info.export.get(item);
                    ExportStat im = info.imports.get(item);

                    if(rp == null && ex == null && im == null) continue;

                    Row row = acc.get(item, () -> new Row(item));

                    if(rp != null) row.production += rp.mean;
                    if(ex != null) row.exportv += ex.mean;
                    if(im != null) row.importv += im.mean;
                }
            }
        }

        Seq<Row> rows = new Seq<>();
        acc.each((item, row) -> {
            if(row.production > 0.005f || row.exportv > 0.005f || row.importv > 0.005f) rows.add(row);
        });
        rows.sort((a, b) -> Float.compare(b.production, a.production));

        return new Stats(rows, sectorCount, planetsSeen.size);
    }

    //Math.floor instead of rounding: 5.96 must truncate to "5.9", not round up to "6.0".
    private static float truncate1(float v){
        return (float)Math.floor(v * 10) / 10f;
    }

    private static String fmtRate(float perSecond, boolean perMinute){
        float scaled = perMinute ? perSecond * 60f : perSecond;
        String suffix = Core.bundle.get(perMinute ? "campaignutils.rate-per-minute-suffix" : "campaignutils.rate-per-second-suffix");

        if(scaled < 0.005f) return "-";
        if(scaled >= 1000f) return Strings.fixed(truncate1(scaled / 1000f), 1) + "k" + suffix;
        return Strings.fixed(truncate1(scaled), 1) + suffix;
    }

    private static final class Row{
        final Item item;
        float production, exportv, importv;

        Row(Item item){
            this.item = item;
        }
    }

    private static final class Stats{
        final Seq<Row> rows;
        final int sectorCount, planetCount;

        Stats(Seq<Row> rows, int sectorCount, int planetCount){
            this.rows = rows;
            this.sectorCount = sectorCount;
            this.planetCount = planetCount;
        }
    }
}
