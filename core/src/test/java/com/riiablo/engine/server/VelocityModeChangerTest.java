package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class VelocityModeChangerTest {
  @Test
  void changingMovementModePreservesTheTerminalShortStep() {
    com.badlogic.gdx.math.Vector2 step = new com.badlogic.gdx.math.Vector2(0.3f, 0.4f);
    VelocityModeChanger.limitMovementSpeed(step, 9.375f);
    assertEquals(0.5f, step.len(), 0.0001f);
    VelocityModeChanger.limitMovementSpeed(step, 14.0625f);
    assertEquals(0.5f, step.len(), 0.0001f);
    step.set(14.0625f, 0);
    VelocityModeChanger.limitMovementSpeed(step, 9.375f);
    assertEquals(9.375f, step.len(), 0.0001f);
    step.setZero();
    VelocityModeChanger.limitMovementSpeed(step, 14.0625f);
    assertEquals(0f, step.len());
  }

  @Test void unsetMonsterWeaponClassCannotCrashBaseAnimationLookup() {
    com.riiablo.codec.D2 saved = com.riiablo.Riiablo.anim;
    try {
      // Valid empty AnimData table ensures this exercises the invalid-class
      // guard, not the null-table guard.
      com.riiablo.Riiablo.anim = com.riiablo.codec.D2.loadFromStream(
          new java.io.ByteArrayInputStream(new byte[256 * 4]));
      com.riiablo.codec.excel.MonStats.Entry base = new com.riiablo.codec.excel.MonStats.Entry();
      base.Code = "FA";
      com.riiablo.engine.server.component.CofReference cof = new com.riiablo.engine.server.component.CofReference();
      for (byte invalid : new byte[] {-1, 15, 127}) {
        cof.wclass = invalid;
        assertEquals(200, VelocityModeChanger.lookupBaseAnimationSpeed(
            base, com.riiablo.engine.Engine.Monster.MODE_WL, cof, 200));
      }
      assertEquals(200, VelocityModeChanger.lookupBaseAnimationSpeed(
          base, com.riiablo.engine.Engine.Monster.MODE_WL, null, 200));
    } finally { com.riiablo.Riiablo.anim = saved; }
  }

  @Test void unknownNativeBaseWeaponUsesHthRatherThanNegativeArrayIndex() {
    assertEquals(com.riiablo.engine.Engine.WEAPON_HTH, ServerEntityFactory.nativeMonsterWeaponClass(-1));
    assertEquals(com.riiablo.engine.Engine.WEAPON_HTH, ServerEntityFactory.nativeMonsterWeaponClass(15));
    assertEquals(com.riiablo.engine.Engine.WEAPON_2HS,
        ServerEntityFactory.nativeMonsterWeaponClass(com.riiablo.engine.Engine.WEAPON_2HS));
    assertEquals(com.riiablo.engine.Engine.WEAPON_NIL, ServerEntityFactory.nativeMonsterWeaponClass(0));
  }

  @Test
  void temporaryRunCombinesWithPersistentPreferenceWithoutChangingIt() {
    assertFalse(VelocityModeChanger.isRunRequested(false, false));
    assertTrue(VelocityModeChanger.isRunRequested(false, true));
    assertTrue(VelocityModeChanger.isRunRequested(true, false));
    assertTrue(VelocityModeChanger.isRunRequested(true, true));
  }

  @Test
  void usesNativePlayerWalkAndRunAnimationRatesAtBaseVelocity() {
    assertEquals(213, VelocityModeChanger.scaleAnimationSpeed(
        VelocityModeChanger.PLAYER_WALK_ANIM_SPEED, 6f, 6f));
    assertEquals(101, VelocityModeChanger.scaleAnimationSpeed(
        VelocityModeChanger.PLAYER_RUN_ANIM_SPEED, 9f, 9f));
  }

  @Test
  void scalesAnimationRateWithActualMovementSpeed() {
    assertEquals(107, VelocityModeChanger.scaleAnimationSpeed(213, 3f, 6f));
    assertEquals(202, VelocityModeChanger.scaleAnimationSpeed(101, 18f, 9f));
  }

  @Test
  void safelyHandlesStoppedOrInvalidVelocity() {
    assertEquals(0, VelocityModeChanger.scaleAnimationSpeed(213, 0f, 6f));
    assertEquals(0, VelocityModeChanger.scaleAnimationSpeed(213, 6f, 0f));
    assertEquals(0, VelocityModeChanger.scaleAnimationSpeed(0, 6f, 6f));
  }

  @Test
  void appliesNativeMonsterBaseAndAiVelocityBonus() {
    Velocity velocity = new Velocity().setMonster(8f);

    assertEquals(6f, velocity.speed(false));
    assertEquals(6f, velocity.speed(true));

    velocity.setModeSpeedBonusPercent(75f);
    assertEquals(12f, velocity.speed(false));

    velocity.setModeSpeedBonusPercent(-100f);
    assertEquals(2f, velocity.speed(false));
  }

  @Test
  void derivesVariantWalkAndRunAnimationRatesLikeD2Common() {
    assertEquals(300, VelocityModeChanger.deriveVariantAnimationSpeed(200, 9, 6));
    assertEquals(100, VelocityModeChanger.deriveVariantAnimationSpeed(200, 3, 6));
  }

  @Test
  void keepsRawAnimationRateWhenMovementReferenceIsUnavailable() {
    assertEquals(200, VelocityModeChanger.deriveVariantAnimationSpeed(200, 0, 6));
    assertEquals(200, VelocityModeChanger.deriveVariantAnimationSpeed(200, 6, 0));
    assertEquals(0, VelocityModeChanger.deriveVariantAnimationSpeed(0, 6, 6));
  }
}
