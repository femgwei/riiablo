package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;

/** Locks MonStats aip column meanings to the native D2MOO AI enums. */
class MonsterAiParamParityTest {
  @Test
  void skeletonParamsMatchD2Moo() {
    assertEquals(0, Skeleton.PARAM_APPROACH_CHANCE);
    assertEquals(1, Skeleton.PARAM_STALL_TIME);
    assertEquals(2, Skeleton.PARAM_ATTACK_CHANCE);
    assertEquals(3, Skeleton.PARAM_ATTACK1_OR_2_CHANCE);
  }

  @Test
  void bruteParamsMatchD2Moo() {
    assertEquals(2, Brute.PARAM_ATTACK_CHANCE);
    assertEquals(3, Brute.PARAM_ATTACK1_OR_2_CHANCE);
  }

  @Test
  void bigheadParamsMatchD2Moo() {
    assertEquals(0, Bighead.PARAM_HURT_PCT);
    assertEquals(1, Bighead.PARAM_CIRCLE_CHANCE);
    assertEquals(2, Bighead.PARAM_FIRE_WHILE_HEALTHY_CHANCE);
    assertEquals(3, Bighead.PARAM_FIRE_WHILE_HURT_CHANCE);
  }

  @Test
  void corruptArcherParamsMatchD2Moo() {
    assertEquals(0, CorruptArcher.PARAM_APPROACH_CHANCE);
    assertEquals(1, CorruptArcher.PARAM_SHOOT_CHANCE);
    assertEquals(2, CorruptArcher.PARAM_STALL_DURATION);
    assertEquals(3, CorruptArcher.PARAM_RUN_CHANCE);
    assertEquals(4, CorruptArcher.PARAM_ALWAYS_RUN_DISTANCE);
    assertEquals(5, CorruptArcher.PARAM_USE_SKILL_2_CHANCE);
    assertEquals(6, CorruptArcher.PARAM_USE_SKILL_3_CHANCE);
    assertEquals(7, CorruptArcher.PARAM_WALK_TOW_DISTANCE);
  }

  @Test
  void mummyParamsMatchD2Moo() {
    assertEquals(0, Mummy.PARAM_AWAKE_DISTANCE);
    assertEquals(1, Mummy.PARAM_APPROACH_CHANCE);
    assertEquals(2, Mummy.PARAM_ATTACK_CHANCE);
    assertEquals(3, Mummy.PARAM_ATTACK1_OR_2_CHANCE);
    assertEquals(4, Mummy.PARAM_STALL_DURATION);
  }

  @Test
  void vultureParamsMatchD2Moo() {
    assertEquals(0, Vulture.PARAM_ATTACK_CHANCE);
    assertEquals(1, Vulture.PARAM_STALL_DURATION);
    assertEquals(2, Vulture.PARAM_WOUNDED_PCT);
    assertEquals(3, Vulture.PARAM_CIRCLE_CHANCE);
    assertEquals(4, Vulture.PARAM_MOVE_CHANCE);
  }

  @Test
  void fallenShamanParamsAndShootBoundaryMatchD2Moo() {
    assertEquals(0, FallenShaman.PARAM_RESURRECT_AND_COMMAND_CHANCE);
    assertEquals(1, FallenShaman.PARAM_SHOOT_CHANCE);
    assertEquals(2, FallenShaman.PARAM_MELEE_AND_CIRCLE_CHANCE);
    assertEquals(3, FallenShaman.PARAM_RESURRECT_DISTANCE);
    assertEquals(4, FallenShaman.PARAM_SHOOT_DISTANCE);
    assertEquals(com.riiablo.engine.Engine.Monster.MODE_A2,
        FallenShaman.SHAMAN_SEQUENCE_MODE);

    assertTrue(FallenShaman.withinShootDistance(9.99f, 10));
    assertFalse(FallenShaman.withinShootDistance(10f, 10));
    assertFalse(FallenShaman.withinShootDistance(1f, 0));
  }

  @Test
  void fallenShamanOnlySelectsUsableNormalFallenCorpses() {
    MonStats.Entry stats = new MonStats.Entry();
    stats.Id = "fallen2";
    stats.BaseId = "fallen1";
    stats.Align = 1;
    MonStats2.Entry stats2 = new MonStats2.Entry();
    stats2.revive = true;
    Monster monster = new Monster().set(stats, stats2);
    Corpse corpse = new Corpse();

    assertTrue(FallenShaman.isResurrectableFallen(monster, corpse, 0f));

    corpse.fading = true;
    assertFalse(FallenShaman.isResurrectableFallen(monster, corpse, 0f));
    corpse.fading = false;
    stats.boss = true;
    assertFalse(FallenShaman.isResurrectableFallen(monster, corpse, 0f));
    stats.boss = false;
    stats.BaseId = "skeleton1";
    assertFalse(FallenShaman.isResurrectableFallen(monster, corpse, 0f));
    stats.BaseId = "fallenshaman1";
    assertFalse(FallenShaman.isResurrectableFallen(monster, corpse, 1f));
  }
}
