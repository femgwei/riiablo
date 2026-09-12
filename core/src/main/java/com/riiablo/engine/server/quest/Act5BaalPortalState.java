package com.riiablo.engine.server.quest;

/**
 * Idempotent server-side state for the two portals in A5Q6.
 *
 * <p>The native quest keeps these as object modes on the current game level.
 * Keeping the transition separately from entity ids makes repeated death and
 * reconnect events harmless and allows the state machine to be unit tested
 * without a rendered world.</p>
 */
public final class Act5BaalPortalState {
  private boolean worldstoneChamberOpen;
  private boolean lastPortalCreated;

  /** Opens the Throne portal once; returns true only on the transition. */
  public boolean openWorldstoneChamber() {
    if (worldstoneChamberOpen) return false;
    worldstoneChamberOpen = true;
    return true;
  }

  /** Creates the end-of-Baal portal once; returns true only on the transition. */
  public boolean createLastPortal() {
    if (lastPortalCreated) return false;
    lastPortalCreated = true;
    return true;
  }

  public boolean isWorldstoneChamberOpen() {
    return worldstoneChamberOpen;
  }

  public boolean isLastPortalCreated() {
    return lastPortalCreated;
  }
}
