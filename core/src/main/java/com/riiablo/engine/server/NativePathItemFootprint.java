package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.path.D2Path;
import com.d2moo.common.path.D2PathFootprint;
import com.d2moo.common.path.D2PathUnits;

/** Serialized game-thread capture of an ECS item's native size-based footprint.
 * Caller supplies raw static int32 coordinates and the actual native room, possibly null.
 * This does not resolve ground/inventory eligibility, infer coordinates or facing,
 * bind lifetime, or install collision. Use inside observeFootprints for an operation. */
public final class NativePathItemFootprint {
  private NativePathItemFootprint() {}

  public static D2PathFootprint.Unit read(World world, int id, D2Path.Point point, D2ActiveRoom room) {
    return read(world, id, point, room, null);
  }

  public static D2PathFootprint.Unit read(World world, int id, D2Path.Point point,
      D2ActiveRoom room, int pathDirection) {
    return read(world, id, point, room, Integer.valueOf(pathDirection));
  }

  private static D2PathFootprint.Unit read(World world, int id, D2Path.Point point,
      D2ActiveRoom room, Integer pathDirection) {
    NativePathUnitMetadata metadata = NativePathUnitMetadata.read(world, id);
    if (metadata.type != 4) throw new IllegalArgumentException("resolved ECS item required");
    D2PathUnits.Unit coordinates = pathDirection == null ? metadata.staticUnit(point)
        : metadata.staticUnit(point, pathDirection.intValue());
    return D2PathFootprint.Unit.stationary(coordinates, room);
  }
}
