package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f rows and Fn135 action boundaries; not a skill-effect/client test. */
class BaalCrabAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsResolveDedicatedAiAndFixedSkillsMatchRealIds() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"BaalCrab".equalsIgnoreCase(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof BaalCrab);
      for (String skill : new String[] {row.Skill5, row.Skill6, row.Skill7}) {
        assertNotNull(Riiablo.files.skills.get(skill));
      }
    }
    assertTrue(rows > 0, "1.10f contains no MonStats row for BaalCrab");
    assertEquals("Baal Tentacle", Riiablo.files.skills.get(BaalCrab.SKILL_TENTACLE).skill);
    assertEquals("Baal Nova", Riiablo.files.skills.get(BaalCrab.SKILL_NOVA).skill);
    assertEquals("Baal Inferno", Riiablo.files.skills.get(BaalCrab.SKILL_INFERNO).skill);
    assertEquals("Baal Cold Missiles", Riiablo.files.skills.get(BaalCrab.SKILL_COLD_MISSILES).skill);
  }

  @Test
  void meleeTablesPreserveDifficultyAndStrictHealthBoundary() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      int[] expected = {0, difficulty == 0 ? 75 : 50, 0, 0, 0, 0, 0, 0,
          20, 30, 150, 10, 10, difficulty == 0 ? 45 : 70, 40, 0};
      assertArrayEquals(expected, BaalCrab.weights(difficulty, true, false, 1,
          100, 33, false, 1, false, false));
      expected[1] += 60;
      expected[10] += 50;
      assertArrayEquals(expected, BaalCrab.weights(difficulty, true, false, 1,
          40, 32, false, 1, false, false));
    }
    int[] cold = BaalCrab.weights(2, true, false, 1, 100, 100, true, 1, false, false);
    assertEquals(0, cold[8]);
    assertEquals(0, cold[11]);
    assertEquals(0, cold[13]);
    assertEquals(10, cold[12]);
    int[] wall = BaalCrab.weights(2, true, true, 1, 100, 100, false, 1, false, false);
    assertEquals(20, wall[8]);
    assertEquals(0, wall[11]);
    assertEquals(0, wall[12]);
    assertEquals(0, wall[13]);
  }

  @Test
  void rangedWallTablesPreserveSingleTargetAndAnchorAdjustments() {
    assertArrayEquals(new int[] {0, 125, 45, 20, 20, 0, 20, 0, 80, 70, 25, 0, 0, 0, 0, 0},
        BaalCrab.weights(0, false, true, 30, 100, 100, false, 1, false, false));
    assertArrayEquals(new int[] {0, 160, 0, 35, 20, 60, 0, 0, 80, 70, 0, 25, 0, 25, 0, 0},
        BaalCrab.weights(1, false, true, 30, 40, 100, false, 2, true, true));
  }

  @Test
  void rangedOpenTablesKeepStrict25And35AndTargetCountThresholds() {
    assertArrayEquals(new int[] {0, 125, 5, 5, 5, 0, 5, 0, 40, 40, 0, 70, 80, 40, 20, 20},
        BaalCrab.weights(0, false, false, 25, 100, 100, false, 2, false, false));
    int[] at35 = BaalCrab.weights(2, false, false, 35, 100, 100, false, 1, false, false);
    assertEquals(5, at35[6]);
    assertEquals(60, at35[13]);
    assertEquals(-10, at35[11], "native signed weight, not zero-clamped");
    assertEquals(30, at35[14]);
    int[] beyond35 = BaalCrab.weights(2, false, false, 36, 100, 100, false, 4, true, true);
    assertArrayEquals(new int[] {0, 100, 0, 25, 5, 60, 40, 0, 40, 40, 0, 0, 80, 25, 65, 20},
        beyond35);
    assertEquals(60, BaalCrab.weights(2, false, false, 25, 100, 100,
        false, 3, false, false)[13]);
    assertEquals(85, BaalCrab.weights(2, false, false, 25, 100, 100,
        false, 4, false, false)[13]);
  }

  @Test
  void weightedSelectionUsesSignedCumulativeThresholdAndUnsignedRawRoll() {
    int[] signed = {0, 10, -5, 10};
    for (int roll = 0; roll < 30; roll++) {
      assertEquals(roll % 15 < 10 ? 1 : 3, BaalCrab.selectAction(signed, roll));
    }
    assertEquals(1, BaalCrab.selectAction(signed, -1L)); // uint32 max % 15 == 0
    assertEquals(1, BaalCrab.selectAction(new int[16], 0));
    assertEquals(1, BaalCrab.selectAction(new int[] {-5}, 123));
  }

  @Test
  void nativeSizedDistanceKeepsAbsoluteSubtractionAndAnchorBoundaries() {
    assertEquals(4, BaalCrab.distanceWithSize(new Vector2(10, 10), new Vector2(10, 10), 3));
    assertEquals(75, BaalCrab.distanceWithSize(new Vector2(86, 10), new Vector2(10, 10), 1));
    assertEquals(76, BaalCrab.distanceWithSize(new Vector2(87, 10), new Vector2(10, 10), 1));
    assertEquals(100, BaalCrab.distanceWithSize(new Vector2(111, 10), new Vector2(10, 10), 1));
    assertEquals(101, BaalCrab.distanceWithSize(new Vector2(112, 10), new Vector2(10, 10), 1));
    assertEquals(8, BaalCrab.distanceBetweenUnits(new Vector2(10, 10), new Vector2(20, 10), 3, 3));
    assertEquals(0, BaalCrab.distanceBetweenUnits(new Vector2(10, 10), new Vector2(10, 10), 3, 3));
  }

  @Test
  void fixedSkillsUseExactNativeIdModeTargetAndPublicStartEvent() {
    int[] actions = {9, 11, 12, 13};
    int[] skills = {315, 316, 317, 318};
    int[] modes = {Engine.Monster.MODE_S2, Engine.Monster.MODE_S3,
        Engine.Monster.MODE_S1, Engine.Monster.MODE_A1};
    for (int i = 0; i < actions.length; i++) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
        ((BaalCrab) f.ai).execute(actions[i], f.target);
        assertEquals("CAST", f.ai.getState());
        assertEquals(skills[i], f.casting().skillId);
        assertEquals(modes[i], f.sequence().mode1);
        assertEquals(f.target, f.casting().targetId);
        assertEquals(f.targetPosition(), f.casting().targetVec);
        assertEquals(1, f.probe.starts);
        f.sequence().started = true;
        f.ai.update(100 * Animation.FRAME_DURATION);
        assertEquals(1, f.probe.starts, "active casts cannot restart");
        assertTrue(f.sequence().started);
      }
    }
  }

  @Test
  void realDecisionUsesHurtMeleeTableAndOneRawDraw() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (int action : new int[] {9, 10, 13}) {
        try (Act1NativeAiIntegrationTest.Fixture f = fixture(difficulty)) {
          f.targetPosition().set(11, 10);
          f.health(40.9f, 100f);
          targetStats(f, 100f, 100f);
          Attributes targetAttrs = f.world.getMapper(AttributesWrapper.class).get(f.target).attrs;
          targetAttrs.base().put(Stat.hitpoints, 32.9f);
          targetAttrs.reset();
          int[] table = BaalCrab.weights(difficulty, true, false, 1,
              40, 32, false, 1, false, false);
          int selectedSeed = -1;
          long nextRoll = 0;
          for (int seed = 0; seed < 10000; seed++) {
            D2Seed nativeSeed = new D2Seed(seed ^ (f.actor * 0x45D9F3B), 666);
            if (BaalCrab.selectAction(table, Seed.rollRandomNumber(nativeSeed)) == action) {
              selectedSeed = seed;
              nextRoll = Seed.rollRandomNumber(nativeSeed) & 0xFFFFFFFFL;
              break;
            }
          }
          assertTrue(selectedSeed >= 0);
          Riiablo.gameSeed = selectedSeed;
          f.ai.update(Animation.FRAME_DURATION);
          assertNotNull(f.casting());
          assertEquals(action == 9 ? 315 : action == 13 ? 318 : SkillCodes.attack,
              f.casting().skillId);
          assertEquals(nextRoll, f.ai.rollAiRandomNumber(), "one table draw per decision");
        }
      }
    }
  }

  @Test
  void attack2RemainsBasicAttackRatherThanColdMissileSkill() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      ((BaalCrab) f.ai).execute(10, f.target);
      assertEquals("ATTACK", f.ai.getState());
      assertEquals(SkillCodes.attack, f.casting().skillId);
      assertEquals(Engine.Monster.MODE_A2, f.sequence().mode1);
      assertEquals(0, f.probe.starts);
    }
  }

  @Test
  void curseBranchComparesMaxLifeToMaxManaAndHandlesNonPlayers() {
    for (int scenario = 0; scenario < 4; scenario++) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
        targetStats(f, scenario == 0 ? 101f : scenario == 3 ? 100.9f : 100f, 100f);
        if (scenario == 2) f.world.getMapper(Player.class).remove(f.target);
        ((BaalCrab) f.ai).execute(8, f.target);
        MonStats.Entry row = f.ai.monster.monstats;
        String skill = scenario == 1 ? row.Skill7 : row.Skill6;
        assertEquals(Riiablo.files.skills.get(skill).Id, f.casting().skillId);
        assertEquals(1, f.probe.starts);
      }
    }
  }

  @Test
  void rejectionNeverSubstitutesOtherSkillOrBasicAttack() {
    for (int action : new int[] {8, 9, 11, 12, 13}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
        targetStats(f, 101f, 100f);
        f.probe.reject = true;
        ((BaalCrab) f.ai).execute(action, f.target);
        assertEquals("IDLE", f.ai.getState());
        assertNull(f.casting());
        assertNull(f.sequence());
        assertEquals(0, f.probe.starts);
      }
    }
  }

  @Test
  void fn135Schedules25FramesAfterEveryDifficultyAndPreservesActiveCast() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(difficulty)) {
        f.targetPosition().set(1000, 1000);
        seedNoTargetTentacle(f);
        ((BaalCrab) f.ai).execute(9, Engine.INVALID_ENTITY);
        assertEquals(Engine.INVALID_ENTITY, f.casting().targetId);
        assertEquals(new Vector2(10, 10), f.casting().targetVec);
        f.finishAction();
        f.ai.update(24 * Animation.FRAME_DURATION);
        assertNull(f.casting());
        assertEquals(1, f.probe.starts);
        f.ai.update(2 * Animation.FRAME_DURATION);
        assertEquals(BaalCrab.SKILL_TENTACLE, f.casting().skillId);
        assertEquals(Engine.INVALID_ENTITY, f.casting().targetId);
        assertEquals(2, f.probe.starts);
      }
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(difficulty)) {
        f.targetPosition().set(1000, 1000);
        seedNoTargetTentacle(f);
        ((BaalCrab) f.ai).execute(1, Engine.INVALID_ENTITY);
        f.ai.update(24 * Animation.FRAME_DURATION);
        assertNull(f.casting());
        f.ai.update(2 * Animation.FRAME_DURATION);
        assertEquals(BaalCrab.SKILL_TENTACLE, f.casting().skillId,
            "Fn135 replaces the difficulty-specific neutral idle event with 25 frames");
      }
    }
  }

  @Test
  void returnActionWalksToInitialAnchorAndPathFailureDoesNotAttack() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      BaalCrab baal = (BaalCrab) f.ai;
      baal.beforeDecision();
      f.world.getMapper(Position.class).get(f.actor).position.set(40, 40);
      baal.beforeDecision();
      baal.execute(5, f.target);
      verify(f.paths).findPath(f.actor, new Vector2(10, 10), false, Engine.INVALID_ENTITY);
      assertEquals("MOVE", f.ai.getState());
      when(f.paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenReturn(false);
      baal.execute(5, f.target);
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
    }
  }

  @Test
  void retreatRequiresSafeLandingAndUsesConfiguredTeleportSlot() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      BaalCrab baal = (BaalCrab) f.ai;
      baal.execute(14, f.target);
      assertNull(f.casting(), "no map is not proof of safe landing");
      Map map = mock(Map.class);
      Map.Zone zone = mock(Map.Zone.class);
      f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone);
      when(map.getZone(any(Vector2.class))).thenReturn(zone);
      when(zone.areRoomsAdjacent(10f, 10f, -15f, 10f)).thenReturn(true);
      when(map.isBlockedForPath(zone, -15, 10, DT1.Tile.FLAG_BLOCK_WALK, 1, false))
          .thenReturn(true);
      baal.execute(14, f.target);
      assertNull(f.casting());
      when(map.isBlockedForPath(zone, -15, 10, DT1.Tile.FLAG_BLOCK_WALK, 1, false))
          .thenReturn(false);
      baal.execute(14, f.target);
      assertEquals(Riiablo.files.skills.get(f.ai.monster.monstats.Skill5).Id, f.casting().skillId);
      assertEquals(Engine.INVALID_ENTITY, f.casting().targetId);
      assertEquals(new Vector2(-15, 10), f.casting().targetVec);
    }
  }

  @Test
  void deferredCloneBranchDoesNotCreateRewardBearingMonsterOrCast() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      int entities = f.world.getAspectSubscriptionManager()
          .get(com.artemis.Aspect.all()).getEntities().size();
      ((BaalCrab) f.ai).execute(15, f.target);
      f.world.process();
      assertEquals(entities, f.world.getAspectSubscriptionManager()
          .get(com.artemis.Aspect.all()).getEntities().size());
      assertEquals("IDLE", f.ai.getState());
      assertNull(f.casting());
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(int difficulty) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if ("BaalCrab".equalsIgnoreCase(row.AI)) {
        return new Act1NativeAiIntegrationTest.Fixture(row.Id, difficulty, 10f);
      }
    }
    throw new AssertionError("1.10f contains no BaalCrab row");
  }

  private static void targetStats(Act1NativeAiIntegrationTest.Fixture f, float life, float mana) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.maxhp, life);
    attrs.base().put(Stat.hitpoints, life);
    attrs.base().put(Stat.maxmana, mana);
    attrs.reset();
    f.world.getMapper(AttributesWrapper.class).create(f.target).attrs = attrs;
  }

  private static void seedNoTargetTentacle(Act1NativeAiIntegrationTest.Fixture f) {
    for (int seed = 0; seed < 10000; seed++) {
      D2Seed nativeSeed = new D2Seed(seed ^ (f.actor * 0x45D9F3B), 666);
      if ((Seed.rollRandomNumber(nativeSeed) & 0xFFFFFFFFL) % 100 < 8) {
        Riiablo.gameSeed = seed;
        return;
      }
    }
    fail("No deterministic tentacle seed");
  }
}
