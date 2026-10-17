package com.riiablo.map.pfa;

import com.badlogic.gdx.ai.pfa.SmoothableGraphPath;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;

public class PathSmoother<N> {
  final RaycastCollisionDetector raycaster;
  final Ray<Vector2> ray = new Ray<>(new Vector2(), new Vector2());

  public PathSmoother(RaycastCollisionDetector raycaster) {
    this.raycaster = raycaster;
  }

  public int smoothPath(int flags, int size, SmoothableGraphPath<N, Vector2> path) {
    int length = path.getCount();
    if (length <= 2) return 0;

    int outId = 1;
    int inId  = 2;
    while (inId < length) {
      ray.start.set(path.getNodePosition(outId - 1));
      ray.end.set(path.getNodePosition(inId));

      // Size is a native footprint selector, not an extra lateral radius.
      // The raycaster checks the whole footprint at every sample already.
      if (raycaster.collides(ray, flags, size)) {
        path.swapNodes(outId, inId - 1);
        outId++;
      }

      inId++;
    }

    path.swapNodes(outId, inId - 1);
    path.truncatePath(outId + 1);
    return inId - outId - 1;
  }
}
