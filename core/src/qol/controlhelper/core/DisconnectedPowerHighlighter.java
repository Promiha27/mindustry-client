package qol.controlhelper.core;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.game.EventType.Trigger;
import mindustry.world.Block;
import mindustry.world.blocks.power.PowerGraph;
import qol.core.SafeSettings;

import java.util.function.BooleanSupplier;

import static mindustry.Vars.headless;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;

/**
 * Outlines any power node or power-using building sitting on a network OTHER than your biggest one,
 * while that OTHER network is itself starved (zero or negative power balance) - the common mistake this
 * catches is a building that LOOKS connected (sitting right next to other infrastructure) but never
 * actually got linked, so it's silently sitting on its own tiny graph with nothing feeding it.
 * <p>
 * Two refinements on top of "just flag anything not on the biggest graph":
 * <p>
 * 1. A disconnected sub-network that's fully self-sufficient (its own generators covering its own
 * consumers, balance {@literal >} 0 - a deliberately separate outpost, say) is left alone. Being
 * disconnected from the main base isn't itself a problem; having no power is.
 * <p>
 * 2. If the main network itself is in deficit, nothing gets highlighted at all, anywhere - a
 * base-wide brownout means production genuinely can't keep up, which isn't a wiring mistake and
 * isn't fixed by relinking anything, so flagging every single building on the (correctly connected)
 * main network would just be noise.
 * <p>
 * "The main network" is whichever graph currently has the most buildings in it - there's no inherent
 * way to know which network a player considers "the real one", but the biggest one almost always is it
 * in practice.
 */
public class DisconnectedPowerHighlighter{
    static final float RESCAN_INTERVAL_TICKS = 30f;
    /** A graph's balance this close to zero counts as "starved" too, not just strictly negative - floating point noise, and a graph with no consumers/producers at all reports exactly 0. */
    static final float DEFICIT_EPSILON = 0.001f;

    final BooleanSupplier masterEnabled;
    final Seq<Building> disconnected = new Seq<>();
    float scanTimer = 0f;

    public DisconnectedPowerHighlighter(BooleanSupplier masterEnabled){
        this.masterEnabled = masterEnabled;
    }

    public void Init(){
        Events.run(Trigger.update, this::update);
        //drawOver, not draw: Trigger.draw fires as part of the world-rendering pass, so a highlight
        //drawn there ends up UNDERNEATH building sprites - invisible for small buildings whose own
        //sprite covers the highlight's margin entirely (which is exactly why nodes specifically never
        //showed anything: a 1-tile node's sprite fully covers a few-pixel outline). drawOver is the
        //later overlay pass everything else renders on top of - ExtinguishedRebuilder's own
        //world-space selection box already uses it for the same reason.
        Events.run(Trigger.drawOver, this::draw);
    }

    void update(){
        if(headless || !masterEnabled.getAsBoolean() || !IsEnabled() || !state.isGame() || player == null){
            disconnected.clear();
            return;
        }

        scanTimer += Time.delta;
        if(scanTimer < RESCAN_INTERVAL_TICKS) return;
        scanTimer = 0f;
        rescan();
    }

    void rescan(){
        disconnected.clear();

        Seq<PowerGraph> seenGraphs = new Seq<>();
        Seq<Building> candidates = new Seq<>();
        PowerGraph largest = null;
        int largestSize = -1;

        for(Building b : Groups.build){
            if(b.team != player.team() || b.power == null || b.power.graph == null) continue;
            candidates.add(b);

            PowerGraph g = b.power.graph;
            if(seenGraphs.contains(g)) continue;
            seenGraphs.add(g);
            if(g.all.size > largestSize){
                largestSize = g.all.size;
                largest = g;
            }
        }
        if(largest == null) return;

        //the main network itself is short on power - that's a production problem, not a wiring
        //mistake, and every OTHER (smaller) graph is equally likely to be starved right now for the
        //same reason - flagging any of them would just be "everything is red", not useful
        if(largest.getPowerBalance() < DEFICIT_EPSILON) return;

        for(Building b : candidates){
            if(b.power.graph == largest || isFlaky(b.block)) continue;
            if(b.power.graph.getPowerBalance() < DEFICIT_EPSILON) disconnected.add(b);
        }
    }

    /**
     * Phase conveyor/conduit only draw their (small) power cost while actively carrying something
     * across the gap, not continuously - so a graph containing one can swing between "fine" and
     * "starved" from one rescan to the next depending on whether it happened to be mid-transfer at that
     * instant, flickering the highlight on and off with nothing actually wrong. Left out of the
     * highlighted set entirely rather than trying to smooth that noise out.
     */
    static boolean isFlaky(Block block){
        return block == Blocks.phaseConveyor || block == Blocks.phaseConduit;
    }

    void draw(){
        if(headless || disconnected.isEmpty() || !masterEnabled.getAsBoolean() || !IsEnabled() || state.isMenu()) return;

        Lines.stroke(3f);
        float alpha = 0.5f + Mathf.absin(Time.time, 6f, 0.35f);
        for(Building b : disconnected){
            if(b == null || b.tile == null || b.tile.build != b) continue;
            Draw.color(Color.scarlet, alpha);
            //a few pixels of margin reads fine on a big factory but is basically invisible on a 1-tile
            //node, whose own sprite often fills its whole tile - floor it at a clearly visible radius
            float half = Math.max(b.block.size * tilesize / 2f + 3f, tilesize * 0.9f);
            Lines.square(b.x, b.y, half);
        }
        Draw.reset();
    }

    public boolean IsEnabled(){
        return SafeSettings.getBool("highlightDisconnectedPower", false);
    }

    public void setEnabled(boolean value){
        Core.settings.put("highlightDisconnectedPower", value);
        if(!value) disconnected.clear();
    }
}
