package com.riiablo.engine.server;

import com.artemis.World;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Object;
import java.util.Objects;

/** ECS table-backed subset of UNITS_GetUnitSizeX/Y (#10336/#10337).
 * Capture on the game thread. This is metadata only, not a native coordinate reader.
 * WRP mixes tile warps and portal objects and cannot be mapped by Class alone. */
public final class NativePathUnitMetadata {
  public final int type, sizeX, sizeY;

  private NativePathUnitMetadata(int type, int sizeX, int sizeY) {
    this.type = type;
    this.sizeX = sizeX;
    this.sizeY = sizeY;
  }

  public static NativePathUnitMetadata read(World world, int id) {
    Objects.requireNonNull(world);
    if (id < 0 || !world.getEntityManager().isActive(id)) {
      throw new IllegalArgumentException("active ECS unit required");
    }
    Class unit = world.getMapper(Class.class).get(id);
    if (unit == null || unit.type == null) {
      throw new IllegalArgumentException("known ECS unit class required");
    }
    switch (unit.type) {
      case PLR: return new NativePathUnitMetadata(0, 2, 2);
      case MON: {
        Monster monster = world.getMapper(Monster.class).get(id);
        if (monster == null || monster.monstats2 == null) throw missing("MonStats2");
        return new NativePathUnitMetadata(1, monster.monstats2.SizeX, monster.monstats2.SizeY);
      }
      case OBJ: {
        Object object = world.getMapper(Object.class).get(id);
        if (object == null || object.base == null) throw missing("Objects");
        return new NativePathUnitMetadata(2, object.base.SizeX, object.base.SizeY);
      }
      case MIS: {
        Missile missile = world.getMapper(Missile.class).get(id);
        if (missile == null || missile.missile == null) throw missing("Missiles");
        return new NativePathUnitMetadata(3, missile.missile.Size, missile.missile.Size);
      }
      case ITM: return new NativePathUnitMetadata(4, 1, 1);
      default: throw new IllegalArgumentException("ambiguous warp/portal native unit type");
    }
  }

  private static IllegalArgumentException missing(String table) {
    // Missing host data is unknown, not proof that the native table lookup returned null.
    return new IllegalArgumentException("resolved " + table + " row required");
  }
}
