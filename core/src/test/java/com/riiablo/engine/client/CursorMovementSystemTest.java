package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Target;
import com.riiablo.skill.SkillCodes;

class CursorMovementSystemTest {
  @Test
  void normalMeleeInputDoesNotUseServerHitGraceRange() {
    assertEquals(0, CursorMovementSystem.MELEE_APPROACH_RANGE_BONUS);
  }

  @Test
  void untargetedRightAttackMovesUnlessShiftIsHeld() {
    Skills.Entry attack = new Skills.Entry();
    attack.Id = SkillCodes.attack;

    assertTrue(CursorMovementSystem.shouldMoveOnUntargetedRightClick(
        Engine.INVALID_ENTITY, false, attack));
    assertFalse(CursorMovementSystem.shouldMoveOnUntargetedRightClick(
        Engine.INVALID_ENTITY, true, attack));
    assertFalse(CursorMovementSystem.shouldMoveOnUntargetedRightClick(42, false, attack));
  }

  @Test
  void inputTickDelayHandlesStartupAndNeverReportsNegativeLag() {
    assertEquals(-1L, CursorMovementSystem.inputTickDelay(0L, 4L));
    assertEquals(-1L, CursorMovementSystem.inputTickDelay(4L, 0L));
    assertEquals(0L, CursorMovementSystem.inputTickDelay(8L, 7L));
    assertEquals(1L, CursorMovementSystem.inputTickDelay(8L, 9L));
  }

  @Test
  void explicitThrowIsNotLimitedByMeleeRangeAdder() {
    assertTrue(CursorMovementSystem.canStartExplicitThrow(true, true, 60));
    assertFalse(CursorMovementSystem.canStartExplicitThrow(false, true, 60));
    assertFalse(CursorMovementSystem.canStartExplicitThrow(true, false, 60));
    assertFalse(CursorMovementSystem.canStartExplicitThrow(true, true, 0));
  }

  @Test
  void heldPointerDoesNotRebuildAPathWhileDirectionRemainsStable() {
    Vector2 position = new Vector2(10f, 10f);
    Pathfind pathfind = new Pathfind();
    pathfind.destination.set(20f, 10f);

    assertFalse(CursorMovementSystem.shouldRefreshHeldGroundPath(
        position, pathfind, new Vector2(20.5f, 10.4f)));
  }

  @Test
  void heldPointerRefreshesNearPathEndOrAfterDirectionChange() {
    Vector2 position = new Vector2(10f, 10f);
    Pathfind pathfind = new Pathfind();
    pathfind.destination.set(11f, 10f);
    assertTrue(CursorMovementSystem.shouldRefreshHeldGroundPath(
        position, pathfind, new Vector2(20f, 10f)));

    pathfind.destination.set(20f, 10f);
    assertTrue(CursorMovementSystem.shouldRefreshHeldGroundPath(
        position, pathfind, new Vector2(10f, 20f)));

    pathfind.targetEntityId = 42;
    assertTrue(CursorMovementSystem.shouldRefreshHeldGroundPath(
        position, pathfind, new Vector2(20f, 10f)));
  }

  @Test
  void sameEntityClickRestartsAPathThatAlreadyEnded() {
    Target target = new Target();
    target.target = 42;

    assertTrue(CursorMovementSystem.shouldIssueTargetMove(target, null, 42));

    Pathfind pathfind = new Pathfind();
    pathfind.targetEntityId = 42;
    assertFalse(CursorMovementSystem.shouldIssueTargetMove(target, pathfind, 42));

    pathfind.targetEntityId = 7;
    assertTrue(CursorMovementSystem.shouldIssueTargetMove(target, pathfind, 42));
  }
}
