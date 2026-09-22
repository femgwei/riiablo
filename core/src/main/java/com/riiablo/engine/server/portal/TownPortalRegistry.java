package com.riiablo.engine.server.portal;

import java.util.HashMap;
import java.util.Map.Entry;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Warp;
import com.riiablo.map.Map.Zone;

/**
 * Authoritative owner-to-pair index for ordinary player Town Portals.
 *
 * <p>A normal portal is not two unrelated warps: the wilderness and town
 * endpoints share one owner and one lifecycle.  Keeping that relationship in
 * a small registry lets a new cast replace only the same player's old pair,
 * while leaving other players' portals untouched.</p>
 */
public final class TownPortalRegistry {
  /** Minimum separation between town-side portal anchors. */
  private static final float PORTAL_SEPARATION = 3f;

  private final HashMap<Integer, Pair> active = new HashMap<>();

  public static final class Pair {
    public final int ownerPlayerId;
    public final Zone sourceZone;
    public final Zone townZone;
    public final int sourceVisual;
    public final int sourceWarp;
    public final int townVisual;
    public final int townWarp;
    public final Vector2 sourcePosition;
    public final Vector2 townPosition;

    Pair(int ownerPlayerId, Zone sourceZone, Vector2 sourcePosition,
        int sourceVisual, int sourceWarp, Zone townZone, Vector2 townPosition,
        int townVisual, int townWarp) {
      this.ownerPlayerId = ownerPlayerId;
      this.sourceZone = sourceZone;
      this.sourcePosition = new Vector2(sourcePosition);
      this.sourceVisual = sourceVisual;
      this.sourceWarp = sourceWarp;
      this.townZone = townZone;
      this.townPosition = new Vector2(townPosition);
      this.townVisual = townVisual;
      this.townWarp = townWarp;
    }
  }

  /**
   * Finds a walkable town-side position which does not overlap another
   * ordinary player's town portal.  The search deliberately probes a ring of
   * preferred coordinates because Zone.findFreeCoordinates checks static map
   * collision, not other dynamic portal entities.
   */
  public synchronized boolean findFreeTownPosition(Zone townZone, Vector2 preferred, int unitSize,
      int searchRadius, Vector2 out) {
    if (townZone == null || preferred == null || out == null) return false;
    int radiusLimit = Math.max(0, searchRadius);
    for (int radius = 0; radius <= radiusLimit; radius++) {
      if (radius == 0) {
        if (tryTownPosition(townZone, preferred.x, preferred.y, unitSize, out)) return true;
        continue;
      }
      for (int dy = -radius; dy <= radius; dy++) {
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          if (tryTownPosition(townZone, preferred.x + dx, preferred.y + dy,
              unitSize, out)) return true;
        }
      }
    }
    return false;
  }

  /** Registers a new pair and atomically replaces the owner's previous pair. */
  public synchronized Pair replace(int ownerPlayerId, World world,
      Zone sourceZone, Vector2 sourcePosition, int sourceVisual, int sourceWarp,
      Zone townZone, Vector2 townPosition, int townVisual, int townWarp) {
    Pair next = new Pair(ownerPlayerId, sourceZone, sourcePosition, sourceVisual,
        sourceWarp, townZone, townPosition, townVisual, townWarp);
    Pair previous = active.put(ownerPlayerId, next);
    if (sourceZone != null) sourceZone.addWarp(sourceWarp);
    if (townZone != null) townZone.addWarp(townWarp);
    if (world != null) {
      Warp sourceEndpoint = world.getMapper(Warp.class).get(sourceWarp);
      Warp townEndpoint = world.getMapper(Warp.class).get(townWarp);
      if (sourceEndpoint != null) {
        sourceEndpoint.townPortalOwner = ownerPlayerId;
        sourceEndpoint.linkedTownPortal = townWarp;
      }
      if (townEndpoint != null) {
        townEndpoint.townPortalOwner = ownerPlayerId;
        townEndpoint.linkedTownPortal = sourceWarp;
      }
    }
    if (previous != null) dispose(previous, world);
    return next;
  }

  /** Removes both endpoints belonging to one player, if present. */
  public synchronized Pair remove(int ownerPlayerId, World world) {
    Pair pair = active.remove(ownerPlayerId);
    if (pair != null) dispose(pair, world);
    return pair;
  }

  public synchronized Pair get(int ownerPlayerId) {
    return active.get(ownerPlayerId);
  }

  public synchronized int size() {
    return active.size();
  }

  /** Removes all active ordinary portals; useful when a game world shuts down. */
  public synchronized void clear(World world) {
    for (Entry<Integer, Pair> entry : active.entrySet()) dispose(entry.getValue(), world);
    active.clear();
  }

  private boolean tryTownPosition(Zone townZone, float x, float y, int unitSize, Vector2 out) {
    Vector2 candidate = new Vector2(x, y);
    if (!townZone.findFreeCoordinates(candidate, unitSize, 0, true, candidate)) return false;
    if (occupied(townZone, candidate, Math.max(PORTAL_SEPARATION, unitSize * 2f))) return false;
    out.set(candidate);
    return true;
  }

  private boolean occupied(Zone zone, Vector2 position, float separation) {
    float minDistance2 = separation * separation;
    for (Pair pair : active.values()) {
      if (pair.townZone == zone && pair.townPosition.dst2(position) < minDistance2) return true;
    }
    return false;
  }

  private void dispose(Pair pair, World world) {
    if (pair.sourceZone != null) pair.sourceZone.removeWarp(pair.sourceWarp);
    if (pair.townZone != null) pair.townZone.removeWarp(pair.townWarp);
    if (world == null) return;
    clearEndpoint(world, pair.sourceWarp);
    clearEndpoint(world, pair.townWarp);
    delete(world, pair.sourceVisual);
    delete(world, pair.sourceWarp);
    delete(world, pair.townVisual);
    delete(world, pair.townWarp);
  }

  private static void clearEndpoint(World world, int entityId) {
    Warp endpoint = world.getMapper(Warp.class).get(entityId);
    if (endpoint != null) {
      endpoint.townPortalOwner = Engine.INVALID_ENTITY;
      endpoint.linkedTownPortal = Engine.INVALID_ENTITY;
    }
  }

  private static void delete(World world, int entityId) {
    if (entityId != Engine.INVALID_ENTITY && entityId >= 0) world.delete(entityId);
  }
}
