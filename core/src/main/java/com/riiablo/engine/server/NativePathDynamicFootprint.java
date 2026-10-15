package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.drlg.D2ActiveRoom;
import com.d2moo.common.path.D2PathFootprint;
import com.d2moo.common.path.D2PathUnits;
import com.riiablo.engine.server.component.CofReference;

/** Serialized capture of logical dynamic unit metadata, not a production collision installer.
 * The caller must supply verified raw path/pattern/mask/room; none are inferred from
 * floats, Size, visual overrides, or the host dynamic collision index.
 * Use inside observeFootprints to bind the returned coordinates to unit lifetimes. */
public final class NativePathDynamicFootprint {
  private NativePathDynamicFootprint() {}

  public static D2PathFootprint.Unit read(World world, int id, D2PathUnits.DynamicPath path,
      D2ActiveRoom room, int nativePattern, int nativeFootprintMask) {
    NativePathUnitMetadata metadata = NativePathUnitMetadata.read(world, id);
    if (metadata.type != 0 && metadata.type != 1 && metadata.type != 3) {
      throw new IllegalArgumentException("dynamic ECS unit required");
    }
    CofReference cof = world.getMapper(CofReference.class).get(id);
    if (cof == null) throw new IllegalArgumentException("resolved logical mode required");
    return D2PathFootprint.Unit.dynamic(metadata.dynamicUnit(path), room, cof.mode & 0xff,
        nativePattern, nativeFootprintMask);
  }
}
