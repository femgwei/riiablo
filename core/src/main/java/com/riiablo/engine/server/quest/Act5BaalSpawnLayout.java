package com.riiablo.engine.server.quest;

import com.badlogic.gdx.math.Vector2;

/**
 * Coordinates used by the native A5Q6 Baal throne callback.
 *
 * <p>D2Game does not place a wave at five hard-coded map coordinates.  The
 * Baal AI casts {@code Baal Monster Spawn} at the caster's position plus
 * {@code (0, 13)}; the missile then calls the SuperUnique preset spawner.  The
 * preset spawner asks the room collision placer for points around that
 * location.  This class keeps that distinction explicit and supplies the
 * deterministic candidate order used by the Java room placer when a native
 * RoomCoordList is not available.</p>
 */
public final class Act5BaalSpawnLayout {
  /** Native AITHINK_BaalThrone target offset, in map subtiles. */
  public static final float SUMMON_OFFSET_X = 0f;
  public static final float SUMMON_OFFSET_Y = 13f;

  /** Neutral spawn facing used by D2GAME_SpawnNormalMonster. */
  public static final float FACING_X = 0f;
  public static final float FACING_Y = -1f;

  /*
   * D2GAME_SpawnMinions_6FC6F440 delegates every point to the normal monster
   * collision placer.  These are candidates, not absolute spawn positions:
   * the caller still runs each one through Zone.findFreeCoordinates.  The
   * order is stable so an exported map without RoomCoordList produces the
   * same result for every client/reconnect.
   */
  private static final float[][] MINION_CANDIDATES = {
      { 3f,  0f}, {-3f,  0f}, { 0f,  3f}, { 0f, -3f},
      { 2f,  2f}, {-2f,  2f}, {-2f, -2f}, { 2f, -2f},
      { 4f,  1f}, {-4f,  1f}, {-4f, -1f}, { 4f, -1f},
      { 1f,  4f}, {-1f,  4f}, {-1f, -4f}, { 1f, -4f}
  };

  private Act5BaalSpawnLayout() {}

  public static Vector2 summonPoint(float baalX, float baalY, Vector2 out) {
    if (out == null) out = new Vector2();
    return out.set(baalX + SUMMON_OFFSET_X, baalY + SUMMON_OFFSET_Y);
  }

  public static int candidateCount() {
    return MINION_CANDIDATES.length;
  }

  public static float candidateX(int index) {
    return MINION_CANDIDATES[Math.floorMod(index, MINION_CANDIDATES.length)][0];
  }

  public static float candidateY(int index) {
    return MINION_CANDIDATES[Math.floorMod(index, MINION_CANDIDATES.length)][1];
  }
}
