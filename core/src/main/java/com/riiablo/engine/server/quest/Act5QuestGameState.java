package com.riiablo.engine.server.quest;

/**
 * Game-scoped Act V quest state kept outside ECS entity ids.
 *
 * <p>Baal's five waves belong to the current game, not to a character D2S.
 * Keeping this object in the D2GS/game owner lets disconnecting clients and a
 * rebuilt quest system resume the exact delay/wave without granting that
 * progress to a new game.</p>
 */
public final class Act5QuestGameState {
  public final Act5BaalWaveState baalWaves = new Act5BaalWaveState();
  public final Act5BaalPortalState baalPortals = new Act5BaalPortalState();
  private float baalOriginX;
  private float baalOriginY;
  private boolean hasBaalOrigin;
  private float tyraelOriginX;
  private float tyraelOriginY;
  private boolean tyraelSpawned;

  public static final class Snapshot {
    public final Act5BaalWaveState.Snapshot waves;
    public final Act5BaalPortalState.Snapshot portals;
    public final float baalOriginX;
    public final float baalOriginY;
    public final boolean hasBaalOrigin;
    public final float tyraelOriginX;
    public final float tyraelOriginY;
    public final boolean tyraelSpawned;

    public Snapshot(Act5BaalWaveState.Snapshot waves,
        Act5BaalPortalState.Snapshot portals, float baalOriginX,
        float baalOriginY, boolean hasBaalOrigin, float tyraelOriginX,
        float tyraelOriginY, boolean tyraelSpawned) {
      this.waves = waves;
      this.portals = portals;
      this.baalOriginX = baalOriginX;
      this.baalOriginY = baalOriginY;
      this.hasBaalOrigin = hasBaalOrigin;
      this.tyraelOriginX = tyraelOriginX;
      this.tyraelOriginY = tyraelOriginY;
      this.tyraelSpawned = tyraelSpawned;
    }
  }

  public void setBaalOrigin(float x, float y) {
    if (!Float.isFinite(x) || !Float.isFinite(y)) {
      throw new IllegalArgumentException("Baal wave origin must be finite");
    }
    baalOriginX = x;
    baalOriginY = y;
    hasBaalOrigin = true;
  }

  public boolean hasBaalOrigin() {
    return hasBaalOrigin;
  }

  public float baalOriginX() {
    return baalOriginX;
  }

  public float baalOriginY() {
    return baalOriginY;
  }

  /** Records the authoritative Tyrael3 spawn point once per game. */
  public boolean markTyraelSpawned(float x, float y) {
    if (!Float.isFinite(x) || !Float.isFinite(y)) {
      throw new IllegalArgumentException("Tyrael spawn must be finite");
    }
    if (tyraelSpawned) return false;
    tyraelOriginX = x;
    tyraelOriginY = y;
    tyraelSpawned = true;
    return true;
  }

  public boolean isTyraelSpawned() {
    return tyraelSpawned;
  }

  public float tyraelOriginX() {
    return tyraelOriginX;
  }

  public float tyraelOriginY() {
    return tyraelOriginY;
  }

  public Snapshot snapshot() {
    return new Snapshot(baalWaves.snapshot(), baalPortals.snapshot(),
        baalOriginX, baalOriginY, hasBaalOrigin,
        tyraelOriginX, tyraelOriginY, tyraelSpawned);
  }

  public void restore(Snapshot snapshot) {
    if (snapshot == null) return;
    baalWaves.restore(snapshot.waves);
    baalPortals.restore(snapshot.portals);
    if (snapshot.hasBaalOrigin) setBaalOrigin(snapshot.baalOriginX, snapshot.baalOriginY);
    else {
      hasBaalOrigin = false;
      baalOriginX = 0f;
      baalOriginY = 0f;
    }
    tyraelOriginX = snapshot.tyraelOriginX;
    tyraelOriginY = snapshot.tyraelOriginY;
    tyraelSpawned = snapshot.tyraelSpawned;
  }
}
