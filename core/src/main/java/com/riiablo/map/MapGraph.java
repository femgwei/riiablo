package com.riiablo.map;

import com.badlogic.gdx.ai.pfa.GraphPath;
import com.badlogic.gdx.ai.pfa.SmoothableGraphPath;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.riiablo.map.pfa.PathFinder;
import com.riiablo.map.pfa.PathSmoother;
import com.riiablo.map.pfa.Point2;
import com.riiablo.map.pfa.RaycastCollisionDetector;

public class MapGraph {
  Map map;
  PathSmoother<Point2> smoother;
  RaycastCollisionDetector raycaster;

  final Point2 tmpPoint = new Point2();
  final ObjectSet<Point2> identity = new ObjectSet<>();
  private boolean canOpenDoors;

  public MapGraph(Map map) {
    this.map = map;
    raycaster = new RaycastCollisionDetector(map, this);
    smoother = new PathSmoother<>(raycaster);
  }

  public void clear() {
    identity.clear();
  }

  public Point2 getOrCreate(Vector2 src) {
    return getOrCreate(tmpPoint.set(src));
  }

  public Point2 getOrCreate(int x, int y) {
    return getOrCreate(tmpPoint.set(x, y));
  }

  private Point2 getOrCreate(Point2 src) {
    Point2 existing = identity.get(src);
    if (existing == null) {
      existing = new Point2(src);
      identity.add(existing);
      existing.updateClearance(map, 0);
    }

    return existing;
  }

  public boolean searchNodePath(PathFinder pathFinder, Vector2 src, Vector2 dst, int flags, int size, GraphPath<Point2> outPath) {
    return searchNodePath(pathFinder, src, dst, flags, size, outPath, null);
  }

  public boolean searchNodePath(PathFinder pathFinder, Vector2 src, Vector2 dst,
      int flags, int size, GraphPath<Point2> outPath, Obstacle obstacle) {
    return searchNodePath(pathFinder, src, dst, flags, size, outPath, obstacle, -1, -1);
  }

  public boolean searchNodePath(PathFinder pathFinder, Vector2 src, Vector2 dst,
      int flags, int size, GraphPath<Point2> outPath, Obstacle obstacle,
      int moverId, int targetId) {
    return searchNodePath(pathFinder, src, dst, flags, size, outPath,
        obstacle, moverId, targetId, false);
  }

  public boolean searchNodePath(PathFinder pathFinder, Vector2 src, Vector2 dst,
      int flags, int size, GraphPath<Point2> outPath, Obstacle obstacle,
      int moverId, int targetId, boolean canOpenDoors) {
    outPath.clear();
    if (dst == null) return false;
    boolean previousCanOpenDoors = this.canOpenDoors;
    this.canOpenDoors = canOpenDoors;
    try {
      if (!isWalkable(Map.round(dst.x), Map.round(dst.y), flags, size,
          obstacle, moverId, targetId)) return false;
      Point2 srcP = getOrCreate(src);
      Point2 dstP = getOrCreate(dst);
      return searchNodePath(pathFinder, srcP, dstP, flags, size, outPath, obstacle);
    } finally {
      this.canOpenDoors = previousCanOpenDoors;
    }
  }

  boolean searchNodePath(PathFinder pathFinder, Point2 src, Point2 dst, int flags, int size, GraphPath<Point2> outPath) {
    return searchNodePath(pathFinder, src, dst, flags, size, outPath, null);
  }

  boolean searchNodePath(PathFinder pathFinder, Point2 src, Point2 dst,
      int flags, int size, GraphPath<Point2> outPath, Obstacle obstacle) {
    return pathFinder.search(src, dst, flags, size, outPath, obstacle);
  }

  public void smoothPath(int flags, int size, SmoothableGraphPath<Point2, Vector2> path) {
    smoother.smoothPath(flags, size, path);
  }

  public Array<Point2> getNeighbors(Point2 src, int flags, Array<Point2> neighbors) {
    return getNeighbors(src, flags, 0, null, -1, -1, neighbors);
  }

  public Array<Point2> getNeighbors(Point2 src, int flags, int size,
      Obstacle obstacle, int moverId, int targetId, Array<Point2> neighbors) {
    neighbors.clear();
    tryNeighbor(neighbors, flags, size, obstacle, moverId, targetId, src.x - 1, src.y    );
    tryNeighbor(neighbors, flags, size, obstacle, moverId, targetId, src.x    , src.y - 1);
    tryNeighbor(neighbors, flags, size, obstacle, moverId, targetId, src.x    , src.y + 1);
    tryNeighbor(neighbors, flags, size, obstacle, moverId, targetId, src.x + 1, src.y    );

    for (int dy : new int[] {-1, 1}) for (int dx : new int[] {-1, 1}) {
      // D2MOO AStar.cpp PATH_AStar_ExploreChildren tests the complete
      // collision pattern at the candidate only. Testing that pattern at
      // both orthogonal centres as well expands SMALL's cross into an
      // unintended swept square and disconnects the River's diagonal stairs.
      tryNeighbor(neighbors, flags, size, obstacle, moverId, targetId, src.x + dx, src.y + dy);
    }
    return neighbors;
  }

  public boolean tryNeighbor(Array<Point2> neighbors, int flags, int x, int y) {
    return tryNeighbor(neighbors, flags, 0, null, -1, -1, x, y);
  }

  public boolean tryNeighbor(Array<Point2> neighbors, int flags, int size,
      Obstacle obstacle, int moverId, int targetId, int x, int y) {
    if (!isWalkable(x, y, flags, size, obstacle, moverId, targetId)) return false;
    Point2 point = getOrCreate(x, y);
    neighbors.add(point);
    return true;
  }

  public boolean isWalkable(int x, int y, int flags) {
    return isWalkable(x, y, flags, 0, null, -1, -1);
  }

  public boolean isWalkable(int x, int y, int flags, int size,
      Obstacle obstacle, int moverId, int targetId) {
    if (map.isBlockedForPath(x, y, flags, size, canOpenDoors)) return false;
    return obstacle == null || obstacle.isFree(moverId, targetId, x, y, size);
  }

  @FunctionalInterface
  public interface Obstacle {
    boolean isFree(int moverId, int targetId, int x, int y, int size);
  }
}
