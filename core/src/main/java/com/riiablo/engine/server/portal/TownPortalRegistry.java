package com.riiablo.engine.server.portal;

import java.util.HashMap;
import java.util.Map.Entry;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
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

  /** Returns the native town-side anchor, with a conservative center fallback. */
  public static Vector2 preferredTownPosition(Zone townZone) {
    return preferredTownPosition(townZone, null);
  }

  /**
   * Resolves a town anchor using the generated native bonfire when the
   * D2MOO spawn marker is unavailable (some 1.10 town DS1 variants do not
   * populate the tile-info table).  The bonfire is the stable visual landmark
   * used by the original client; the portal is placed just below it, rather
   * than behind a tent at the rectangular zone center.
   */
  public static Vector2 preferredTownPosition(Zone townZone, World world) {
    if (townZone == null) return null;
    Vector2 nativeSpawn = townZone.townPortalSpawn();
    if (nativeSpawn != null) return nativeSpawn;
    if (world != null && townZone.levelId() == 1) {
      // The 1.10 Rogue Encampment DS1s used by some MPQ variants omit the
      // tile-info record consumed by DUNGEON_FindActSpawnLocationEx.  The
      // native fallback is the town-side end of the Blood Moor gate, inset
      // into the camp; using the bonfire here puts the portal behind the
      // fire/tents instead of at the original red-diamond location.
      ComponentMapper<Warp> warps = world.getMapper(Warp.class);
      ComponentMapper<com.riiablo.engine.server.component.Object> objects =
          world.getMapper(com.riiablo.engine.server.component.Object.class);
      ComponentMapper<Position> positions = world.getMapper(Position.class);
      ComponentMapper<MapWrapper> wrappers = world.getMapper(MapWrapper.class);
      com.badlogic.gdx.utils.IntArray warpEntities = townZone.getWarpEntities();
      for (int i = 0; i < warpEntities.size; i++) {
        int entity = warpEntities.get(i);
        Warp warp = warps.get(entity);
        Position position = positions.get(entity);
        if (warp == null || position == null || warp.dstLevel == null
            || warp.dstLevel.Id != 2) continue; // Rogue Encampment -> Blood Moor
        Vector2 candidate = new Vector2(position.position);
        float inset = 24f;
        // The DS1 coordinate origin is not consistent with the semantic
        // NORTH/SOUTH names used by the DRLG direction field (Rogue's south
        // exit is at y=0 in the generated TownS1 map).  Infer the inward
        // normal from the actual gate edge first; use the direction only for
        // malformed markers that are not on a zone boundary.
        float left = townZone.x();
        float top = townZone.y();
        float right = left + townZone.width();
        float bottom = top + townZone.height();
        if (position.position.x <= left + 8f) candidate.x += inset;
        else if (position.position.x >= right - 8f) candidate.x -= inset;
        else if (position.position.y <= top + 8f) candidate.y += inset;
        else if (position.position.y >= bottom - 8f) candidate.y -= inset;
        else {
          switch (townZone.townExitDirection) {
            case 0: candidate.x += inset; break;
            case 1: candidate.y += inset; break;
            case 2: candidate.x -= inset; break;
            case 3: candidate.y -= inset; break;
            default: break;
          }
        }
        if (townZone.findFreeCoordinates(candidate, 1, 16, true, candidate)) {
          if (com.badlogic.gdx.Gdx.app != null) {
            com.badlogic.gdx.Gdx.app.log("TownPortalRegistry",
                "[TOWN_PORTAL_SPAWN] source=town_gate gate=("
                    + position.position.x + "," + position.position.y
                    + ") portal=(" + candidate.x + "," + candidate.y + ")");
          }
          return candidate;
        }
      }

      com.badlogic.gdx.utils.IntArray entities = townZone.getEntities();
      for (int i = 0; i < entities.size; i++) {
        int entity = entities.get(i);
        com.riiablo.engine.server.component.Object object = objects.get(entity);
        Position position = positions.get(entity);
        MapWrapper wrapper = wrappers.get(entity);
        if (object == null || object.base == null || position == null
            || wrapper == null || wrapper.zone != townZone) continue;
        // Act I obj.txt: RogueBonfire (DS1 index 2) -> Objects class 39.
        if (object.base.Id == 39) {
          Vector2 result = new Vector2(position.position.x,
              position.position.y + 8f);
          if (townZone.findFreeCoordinates(result, 1, 8, true, result)) {
            if (com.badlogic.gdx.Gdx.app != null) {
              com.badlogic.gdx.Gdx.app.log("TownPortalRegistry",
                  "[TOWN_PORTAL_SPAWN] source=rogue_bonfire fire=("
                      + position.position.x + "," + position.position.y
                      + ") portal=(" + result.x + "," + result.y + ")");
            }
            return result;
          }
        }
      }
    }
    return new Vector2(townZone.x() + Math.max(1, townZone.width() / 2f),
        townZone.y() + Math.max(1, townZone.height() / 2f));
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
