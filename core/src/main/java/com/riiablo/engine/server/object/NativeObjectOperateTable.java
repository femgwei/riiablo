package com.riiablo.engine.server.object;

import com.riiablo.map.NativePresetObjectResolver;

/** Stateful lifecycle categories from D2Game's {@code gpObjOperateFnTable}. */
public final class NativeObjectOperateTable {
  public enum Lifecycle {
    NONE,
    ANIMATED_CONTAINER,
    INSTANT_CONTAINER,
    SHRINE,
    TOGGLE_DOOR,
    ONE_WAY_DOOR,
    ARCANE_SYMBOL
  }

  private NativeObjectOperateTable() {}

  public static Lifecycle resolve(int operateFn, boolean tableDoor,
      NativePresetObjectResolver.Kind kind) {
    if (kind == NativePresetObjectResolver.Kind.ARCANE_SYMBOL) {
      return Lifecycle.ARCANE_SYMBOL;
    }
    if (kind == NativePresetObjectResolver.Kind.SHRINE || operateFn == 2) {
      return Lifecycle.SHRINE;
    }
    if (kind == NativePresetObjectResolver.Kind.SPECIAL_CHEST
        || kind == NativePresetObjectResolver.Kind.PRESET_CHEST) {
      return Lifecycle.ANIMATED_CONTAINER;
    }

    switch (operateFn) {
      // Casket, urn/jar, chest, barrel, Tower Tome, exploding barrel,
      // corpse, exploding/quest chests, Wirt, jungle stash, evil urn.
      case 1: case 3: case 4: case 5: case 6: case 7: case 14: case 30:
      case 33: case 39: case 40: case 41: case 51: case 57: case 58: case 59:
      case 68:
        return Lifecycle.ANIMATED_CONTAINER;
      // These handlers switch directly from NU to ON in ObjMode.cpp.
      case 19: case 20: case 26:
        return Lifecycle.INSTANT_CONTAINER;
      // Secret doors clear targetability and never return to NU.
      case 18:
        return Lifecycle.ONE_WAY_DOOR;
      case 8: case 29:
        return Lifecycle.TOGGLE_DOOR;
      default:
        return tableDoor ? Lifecycle.TOGGLE_DOOR : Lifecycle.NONE;
    }
  }
}
