package com.riiablo.map.pfa;

import com.badlogic.gdx.ai.pfa.GraphPath;
import com.riiablo.map.MapGraph;

public interface PathFinder {
  boolean search(Point2 startNode, Point2 endNode, int flags, int size, GraphPath<Point2> outPath);

  default boolean search(Point2 startNode, Point2 endNode, int flags, int size,
      GraphPath<Point2> outPath, MapGraph.Obstacle obstacle) {
    return search(startNode, endNode, flags, size, outPath);
  }
}
