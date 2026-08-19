package agzam4.events;

import mindustry.world.Tile;

/** Событие "тап по тайлу мира" (мимо UI) - его слушает генератор процессоров доставки. */
public class SceneTileTap {

	public final Tile tile;

	public SceneTileTap(Tile tile) {
		this.tile = tile;
	}
}
