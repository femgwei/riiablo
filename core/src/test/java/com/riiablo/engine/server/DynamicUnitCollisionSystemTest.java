package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Monster;
import org.junit.jupiter.api.Test;

class DynamicUnitCollisionSystemTest {
  @Test
  void ambientCrittersAreNotDynamicPathObstacles() {
    Monster critter = new Monster().set(null, new MonStats2.Entry());
    critter.monstats2.critter = true;

    assertFalse(DynamicUnitCollisionSystem.isDynamicObstacle(critter));
  }

  @Test
  void ordinaryMonstersRemainDynamicPathObstacles() {
    Monster monster = new Monster().set(null, new MonStats2.Entry());

    assertTrue(DynamicUnitCollisionSystem.isDynamicObstacle(monster));
  }

  @Test
  void nativeMonsterPathOnlyTreatsReviveOrSmallUnitsAsPetPresence() {
    assertTrue(DynamicUnitCollisionSystem.isPetPresence(true, true, false, false));
    assertTrue(DynamicUnitCollisionSystem.isPetPresence(true, false, true, false));
    assertFalse(DynamicUnitCollisionSystem.isPetPresence(true, false, false, false));
    assertFalse(DynamicUnitCollisionSystem.isPetPresence(true, true, true, true));
    assertFalse(DynamicUnitCollisionSystem.isPetPresence(false, true, true, false));
  }

  @Test
  void nativeNoPathMarkerPreventsOrdinaryMonstersFromOverlapping() {
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(true, false));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(true, true));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(false, false));
    assertFalse(DynamicUnitCollisionSystem.blocksNativePath(false, true));
  }

  @Test
  void nativeFlyingPathIgnoresPetPresenceButStillSeesNoPath() {
    assertFalse(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.FLYING, true));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.FLYING, false));
  }

  @Test
  void nativeDoorOpeningMonsterUsesOrdinaryDynamicUnitMask() {
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.MONSTER_CAN_OPEN_DOORS, true));
    assertTrue(DynamicUnitCollisionSystem.blocksNativePath(
        DynamicUnitCollisionSystem.NativePathProfile.MONSTER_CAN_OPEN_DOORS, false));
  }
}
