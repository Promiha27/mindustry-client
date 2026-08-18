package qol.core;

/**
 * Two independent features both mutate the player's build-plan queue every tick: Control Helper's
 * {@code PlansPrioritizer} moves turret/liquid-chain plans to the front of the queue, and Force Build
 * Schematic queues a run of "breaking" (demolish) plans, then waits for those specific tiles to clear
 * before queuing the schematic's own plans. Neither knew about the other - if PlansPrioritizer bumped
 * something unrelated to the front of the queue while a force-build was in progress, that push builds
 * ahead of the still-queued demolish plans, delaying (sometimes indefinitely, if new priority matches
 * keep appearing) the moment Force Build Schematic sees its target tiles as clear and queues the actual
 * schematic. This is a minimal shared signal so PlansPrioritizer can just skip its own reordering pass
 * while a force-build sequence has an active demolish-then-build in flight, instead of the two features
 * needing a direct reference to each other.
 */
public final class QueueCoordination{
    public static boolean forceBuildPending = false;

    private QueueCoordination(){
    }
}
