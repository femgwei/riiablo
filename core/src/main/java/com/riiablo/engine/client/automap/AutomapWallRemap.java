package com.riiablo.engine.client.automap;

import com.riiablo.map.Orientation;

/** D2MOO's linked-room wall-type remap used before native automap lookup. */
final class AutomapWallRemap {
  private static final int[] INPUT_ROWS = {
      Orientation.LEFT_WALL,
      Orientation.RIGHT_WALL,
      Orientation.RIGHT_NORTH_CORNER_WALL,
      Orientation.LEFT_END_WALL,
      Orientation.RIGHT_END_WALL,
      Orientation.SOUTH_CORNER_WALL,
  };

  // Rows are the requested room wall type; columns are the linked tile type
  // (left wall through south corner wall, including LEFT_NORTH_CORNER_WALL).
  private static final int[][] REMAP = {
      {1, 3, 3, 4, 1, 3, 1},
      {1, 2, 3, 4, 3, 2, 2},
      {3, 3, 3, 4, 3, 3, 3},
      {1, 3, 3, 4, 5, 6, 1},
      {3, 2, 3, 4, 3, 6, 2},
      {1, 2, 3, 4, 1, 2, 7},
  };

  private static final int[] LINKED_TYPES = {
      Orientation.LEFT_WALL,
      Orientation.RIGHT_WALL,
      Orientation.RIGHT_NORTH_CORNER_WALL,
      Orientation.LEFT_NORTH_CORNER_WALL,
      Orientation.LEFT_END_WALL,
      Orientation.RIGHT_END_WALL,
      Orientation.SOUTH_CORNER_WALL,
  };

  private AutomapWallRemap() {}

  static int remap(int requested, int linked) {
    int row = indexOf(INPUT_ROWS, requested);
    int column = indexOf(LINKED_TYPES, linked);
    if (row < 0 || column < 0) return requested;
    return REMAP[row][column];
  }

  static boolean isRemappable(int orientation) {
    return indexOf(INPUT_ROWS, orientation) >= 0;
  }

  private static int indexOf(int[] values, int value) {
    for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
    return -1;
  }
}
