package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;

class NativeObjectInteractTypeResolverTest {
  @Test
  void usesNativeLevelBasedTrapAndLockChances() {
    assertEquals(5, NativeObjectInteractTypeResolver.trapChance(1));
    assertEquals(10, NativeObjectInteractTypeResolver.trapChance(40));
    assertEquals(8, NativeObjectInteractTypeResolver.lockChance(1));
    assertEquals(28, NativeObjectInteractTypeResolver.lockChance(40));
    assertEquals(100, NativeObjectInteractTypeResolver.trapChance(999));
    assertEquals(100, NativeObjectInteractTypeResolver.lockChance(999));
  }

  @Test
  void resolvesStableUrnTrapTypesWithoutLockBit() {
    Objects.Entry urn = object(2, true);
    boolean foundClear = false;
    boolean foundTrap = false;
    for (int seed = 1; seed <= 500; seed++) {
      int first = NativeObjectInteractTypeResolver.resolve(
          urn, 40, seed, 2, 5, 10, 20);
      int second = NativeObjectInteractTypeResolver.resolve(
          urn, 40, seed, 2, 5, 10, 20);
      assertEquals(first, second);
      assertFalse(NativeObjectInteractTypeResolver.locked(first));
      int trapType = NativeObjectInteractTypeResolver.trapType(first);
      if (trapType == 0) foundClear = true;
      else {
        foundTrap = true;
        assertTrue(trapType >= 1 && trapType <= 8);
      }
    }
    assertTrue(foundClear);
    assertTrue(foundTrap);
  }

  @Test
  void preservesIndependentChestLockAndTrapBits() {
    Objects.Entry chest = object(3, true);
    boolean foundLocked = false;
    boolean foundTrapped = false;
    for (int seed = 1; seed <= 500; seed++) {
      int interactType = NativeObjectInteractTypeResolver.resolve(
          chest, 40, seed, 2, 5, 10, 20);
      foundLocked |= NativeObjectInteractTypeResolver.locked(interactType);
      foundTrapped |= NativeObjectInteractTypeResolver.trapType(interactType) != 0;
    }
    assertTrue(foundLocked);
    assertTrue(foundTrapped);
    assertEquals(8, NativeObjectInteractTypeResolver.trapType(0x88));
    assertTrue(NativeObjectInteractTypeResolver.locked(0x88));
  }

  @Test
  void ignoresUnrelatedInitFunctions() {
    Objects.Entry object = object(0, true);
    assertFalse(NativeObjectInteractTypeResolver.supports(object));
    assertEquals(0, NativeObjectInteractTypeResolver.resolve(
        object, 99, 1, 2, 3, 4, 5));
  }

  private static Objects.Entry object(int initFn, boolean lockable) {
    Objects.Entry object = new Objects.Entry();
    object.InitFn = initFn;
    object.Lockable = lockable;
    return object;
  }
}
