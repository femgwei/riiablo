package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.path.D2Path;
import com.d2moo.common.path.D2PathUnits;
import com.d2moo.common.path.D2PathRequest;
import com.d2moo.common.path.D2PathFootprint;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Object;
import java.util.Objects;

/** ECS table-backed subset of UNITS_GetUnitSizeX/Y (#10336/#10337),
 * UNITS_IsDoor (#10392) and UNITS_CheckIfObjectOrientationIs1.
 * Capture on the game thread. Coordinate composition requires caller-supplied raw data.
 * WRP mixes tile warps and portal objects and cannot be mapped by Class alone. */
public final class NativePathUnitMetadata {
  public final int type, sizeX, sizeY;
  /** Predicate results from the host table; these are not original DLL record bytes. */
  public final boolean door, orientationIsOne;
  private final boolean hasTownMetadata, npc, inTown, interact;

  private NativePathUnitMetadata(int type, int sizeX, int sizeY) {
    this(type, sizeX, sizeY, false, false);
  }

  private NativePathUnitMetadata(int type, int sizeX, int sizeY, boolean door, boolean orientationIsOne) {
    this(type, sizeX, sizeY, door, orientationIsOne, false, false, false, false);
  }

  private NativePathUnitMetadata(int type, int sizeX, int sizeY, boolean door, boolean orientationIsOne,
      boolean hasTownMetadata, boolean npc, boolean inTown, boolean interact) {
    this.type = type;
    this.sizeX = sizeX;
    this.sizeY = sizeY;
    this.door = door;
    this.orientationIsOne = orientationIsOne;
    this.hasTownMetadata = hasTownMetadata;
    this.npc = npc;
    this.inTown = inTown;
    this.interact = interact;
  }

  /** #11281 derivation from the supplied native path size, not a live pattern read.
   * Wraith/skill overrides and the actual runtime pattern remain caller-owned. */
  public int collisionPatternFromSize(int nativeSize, boolean nativeIsRevive) {
    if (nativeSize < 0 || nativeSize >= 4 || type != 1) {
      return D2PathFootprint.patternFromSize(nativeSize, false, false, false);
    }
    return D2PathFootprint.patternFromSize(nativeSize, true, ownerCanBeInTown(nativeIsRevive), interact);
  }

  /** Owner eligibility for Request.State, not the owner's current room location.
   * nativeIsRevive must be the resolved native runtime flag, not Monster.resurrected,
   * MonStats2.revive, or a guess based on ownership. Unknown MonStats is rejected. */
  public boolean ownerCanBeInTown(boolean nativeIsRevive) {
    if (type != 1) return true;
    if (!hasTownMetadata) throw missing("MonStats town metadata");
    return D2PathRequest.monsterCanBeInTown(true, npc, inTown, nativeIsRevive);
  }

  /** Compose with an authoritative raw fp16 path snapshot; null retains native missing-path semantics. */
  public D2PathUnits.Unit dynamicUnit(D2PathUnits.DynamicPath path) {
    return D2PathUnits.Unit.dynamic(type, sizeX, path);
  }

  /** Raw static int32 coordinates. This does not invent the optional path +0x64 direction byte. */
  public D2PathUnits.Unit staticUnit(D2Path.Point point) {
    return door ? D2PathUnits.Unit.door(sizeX, point, orientationIsOne)
        : D2PathUnits.Unit.stationary(type, sizeX, point);
  }

  /** Explicit static path +0x64 byte is required for nonzero-speed lead sampling. */
  public D2PathUnits.Unit staticUnit(D2Path.Point point, int pathDirection) {
    return door ? D2PathUnits.Unit.door(sizeX, point, orientationIsOne, pathDirection)
        : D2PathUnits.Unit.stationary(type, sizeX, point, pathDirection);
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
        return new NativePathUnitMetadata(1, monster.monstats2.SizeX, monster.monstats2.SizeY,
            false, false, monster.monstats != null,
            monster.monstats != null && monster.monstats.npc,
            monster.monstats != null && monster.monstats.inTown,
            monster.monstats != null && monster.monstats.interact);
      }
      case OBJ: {
        Object object = world.getMapper(Object.class).get(id);
        if (object == null || object.base == null) throw missing("Objects");
        if (object.base.Orientation < 0 || object.base.Orientation > 255) {
          throw new IllegalArgumentException("Objects.txt unsigned orientation byte required");
        }
        return new NativePathUnitMetadata(2, object.base.SizeX, object.base.SizeY,
            object.base.IsDoor, object.base.Orientation == 1);
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
