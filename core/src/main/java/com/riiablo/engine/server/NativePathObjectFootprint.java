package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.path.D2Path;
import com.d2moo.common.path.D2PathUnits;
import com.d2moo.common.path.D2PathFootprint;
import com.riiablo.engine.server.component.Object;
import com.riiablo.codec.excel.Objects;

/** Serialized game-thread capture of host Objects.txt predicates for #10338/#10410.
 * Booleans are encoded as 0/1 for these nonzero tests, not original DLL bytes.
 * Caller supplies raw int32 coordinates and actual native room (possibly null).
 * This does not allocate rooms, infer coordinates, bind identity, or install collision.
 * Share the returned coordinates with the operation's lifetime-bound observation. */
public final class NativePathObjectFootprint {
  private NativePathObjectFootprint() {}

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
    if (metadata.type != 2) throw new IllegalArgumentException("resolved ECS object required");
    Object object = world.getMapper(Object.class).get(id);
    Objects.Entry row = object.base;
    int mode = object.mode & 0xff;
    if (mode >= 8) throw new IllegalArgumentException("object mode 0..7 required");
    if (row.HasCollision == null || row.HasCollision.length != 8) {
      throw new IllegalArgumentException("eight resolved Objects.txt collision modes required");
    }
    int[] modes = new int[8];
    for (int i = 0; i < modes.length; i++) modes[i] = row.HasCollision[i] ? 1 : 0;
    D2PathFootprint.ObjectData data = new D2PathFootprint.ObjectData(row.IsDoor ? 1 : 0,
        row.BlocksVis ? 1 : 0, row.BlockMissile ? 1 : 0, row.SubClass, modes);
    D2PathUnits.Unit coordinates = pathDirection == null ? metadata.staticUnit(point)
        : metadata.staticUnit(point, pathDirection.intValue());
    return D2PathFootprint.Unit.object(coordinates, room, mode, metadata.sizeY, data);
  }
}
