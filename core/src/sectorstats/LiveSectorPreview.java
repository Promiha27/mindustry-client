package sectorstats;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import mindustry.core.GameState.State;
import mindustry.type.Sector;
import mindustry.world.Tiles;
import mindustry.world.WorldParams;

import static mindustry.Vars.logic;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.ui;
import static mindustry.Vars.world;

/**
 * Feature 3: instead of a static terrain pixmap, actually loads the sector into {@code Vars.world} and
 * puts the game into {@code State.playing} ({@code Logic.play()}) so it renders live - no core, no
 * landing, forced into an un-liftable pause so nothing ticks while just looking around. Ported from the
 * source's {@code enterLiveSectorPreview()}/{@code attachLivePreviewExitOverlay()} - the source called
 * this experimental at first, but sonka confirmed it fully working in-game (see project memory) before
 * this port, so it's treated as a normal stable feature here, not gated behind anything extra.
 * <p>
 * Every engine quirk below was independently re-confirmed against this repo's own source while porting
 * (not just carried over from the source's own debugging history):
 * <ul>
 * <li>Without a core, campaign {@code Logic.checkGameState()} would flag an instant loss on the very
 * first tick - but {@code Logic.update()} only calls it when {@code state.rules.canGameOver} is true
 * ({@code runStateCheck}), so that's cleared before {@code play()}. {@code waves} is cleared too, since
 * spawning waves with no win/lose check wired up would be pointless.</li>
 * <li>The camera is {@code arc.Core.camera} - a static field of {@code arc.Core}, not an instance field of
 * {@code Vars.renderer} (confirmed via {@code Renderer.java}'s own {@code import static arc.Core.*};
 * {@code Renderer} never declares its own {@code camera} field).</li>
 * <li>{@code PlanetDialog} sets {@code shouldPause = true} in its constructor, and the inherited
 * {@code BaseDialog} hides itself by doing {@code state.set(State.playing)} whenever the dialog wasn't
 * already paused back when it was last shown (confirmed in {@code BaseDialog.java}) - so
 * {@code ui.planet.hide()} has to run <em>before</em> the forced {@code state.set(State.paused)} below, or
 * hiding the dialog would silently undo the pause.</li>
 * <li>The pause has to be un-liftable: the pause hotkey toggles {@code state} directly
 * ({@code Control.java}), and Esc opens its own menu with its own resume path - rather than special-case
 * every way to undo a one-shot pause, the exit overlay's own per-frame watcher just re-asserts
 * {@code State.paused} whenever it notices the game isn't paused anymore.</li>
 * </ul>
 */
public final class LiveSectorPreview{
    private LiveSectorPreview(){
    }

    public static void enter(Sector sector){
        try{
            ui.loadAnd(() -> {
                try{
                    logic.reset();

                    WorldParams params = new WorldParams();
                    params.seedOffset = sector.id;
                    //same as the static preview (see SectorPreview) - don't let a look-only load overwrite
                    //this sector's real SectorInfo (resources/waves) with data generated just for the preview.
                    params.saveInfo = false;
                    world.loadSector(sector, params);

                    state.rules.canGameOver = false;
                    state.rules.waves = false;

                    Tiles tiles = world.tiles;
                    //arc.Core.camera, not Vars.renderer.camera - see class javadoc.
                    Core.camera.position.set(tiles.width * tilesize / 2f, tiles.height * tilesize / 2f);

                    logic.play();

                    //must run before state.set(paused) below - see class javadoc.
                    ui.planet.hide();

                    //Direct state.set(), not Control.pause() - that helper is gated by the "pause in
                    //background" setting and by state.rules.pauseDisabled (set for some combat sectors),
                    //and a forced pause here needs to work unconditionally.
                    state.set(State.paused);

                    attachExitOverlay();
                }catch(Throwable t){
                    Log.err("[campaign-utils] failed to start live sector preview", t);
                    try{
                        logic.reset();
                    }catch(Throwable ignored){
                    }
                }
            });
        }catch(Throwable t){
            Log.err("[campaign-utils] failed to start live sector preview", t);
        }
    }

    /**
     * A custom overlay button added straight to {@code Core.scene}, not the normal Esc/pause menu - that
     * flow assumes a real {@code Control.playSector} launch with an actual save to fall back to, which
     * this bypasses entirely. Exiting calls the same {@code Logic.reset()} already proven safe by the
     * static preview, then reopens the campaign screen.
     */
    private static void attachExitOverlay(){
        Table bar = new Table();
        bar.button(Core.bundle.get("campaignutils.end-live-preview"), () -> {
            try{
                logic.reset();
            }catch(Throwable t){
                Log.err("[campaign-utils] failed to exit live sector preview", t);
            }
            try{
                ui.planet.show();
            }catch(Throwable ignored){
            }
        }).width(420).height(56);
        bar.pack();

        Core.scene.add(bar);

        bar.update(() -> {
            if(state.isMenu()){
                bar.remove();
                return;
            }
            if(!state.isPaused()){
                //re-assert every frame - GameState.set() no-ops when already at the target state, so this
                //is cheap; see class javadoc for why a one-shot pause isn't enough.
                state.set(State.paused);
            }
            //y grows bottom-to-top - 16 is near the very bottom of the screen.
            bar.setPosition(Core.graphics.getWidth() / 2f - bar.getWidth() / 2f, 16f);
        });
    }
}
