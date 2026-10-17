package com.riiablo.map.pfa;

import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.Map;
import com.riiablo.map.MapGraph;

public class RaycastCollisionDetector {
  private static final int SAMPLES = 5; // points per unit
  private static final float DELTA = 1f / SAMPLES;

  private final Vector2 delta = new Vector2();
  private final Vector2 sample = new Vector2();
  private final Collision<Vector2> probeCollision = new Collision<>(new Vector2(), new Vector2());

  private Map map;

  public RaycastCollisionDetector(Map map, MapGraph graph) {
    this.map = map;
  }

  public boolean collides(Ray<Vector2> ray, int flags, int size) {
    // Keep boolean/smoothing queries identical to authoritative movement,
    // including native size patterns, masks and the final footprint.
    return findCollision(ray, flags, size, probeCollision);
  }


  public boolean findCollision(Ray<Vector2> ray, int flags, int size, Collision<Vector2> collision) {
    return findCollision(ray, flags, size, collision, false);
  }

  public boolean findCollision(Ray<Vector2> ray, int flags, int size,
      Collision<Vector2> collision, boolean canOpenDoors) {
    Vector2 start = ray.start;
    Vector2 end = ray.end;

    Vector2 last = collision.point.setZero();
    Vector2 normal = collision.normal.setZero(); // TODO: calculate normal

    sample.set(start);
    delta.set(end).sub(start).setLength(DELTA);
    float add = delta.len();
    for (float curDist = 0, maxDist = start.dst(end); curDist < maxDist; curDist += add, last.set(sample), sample.add(delta)) {
      // Use the caller's native collision mask here as well.  In particular,
      // a missile ray must ignore FLAG_BLOCK_WALK while still stopping on
      // FLAG_BLOCK_JUMP (walls/explicit missile barriers).
      // Use the same native footprint as A* for every unit, not only door
      // openers/players. Legacy clearance treats BIG as a larger radius and
      // rejects a legal three-cell doorway for ordinary monsters.
      boolean blocked = map.isBlockedForPath(
          Map.round(sample.x), Map.round(sample.y), flags, size, canOpenDoors);
      if (blocked) {
        return true;
      }
    }

    // The final sample (also a zero-length ray) needs the whole footprint.
    return map.isBlockedForPath(Map.round(end.x), Map.round(end.y), flags, size, canOpenDoors);
  }
}
