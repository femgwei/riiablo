package com.riiablo.engine.server.ai;

import com.badlogic.gdx.Input;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** AiBaal.cpp selected-hand counterplay and Monster.cpp bonus difficulty gate. */
class BaalCrabCounterplayIntegrationTest extends RiiabloTest {
  @Test
  void nativeSpecialSkillIdsMatchReal110fRows() {
    assertEquals("Immolation Arrow", Riiablo.files.skills.get(BaalCrab.SKILL_IMMOLATION_ARROW).skill);
    assertEquals("Fire Wall", Riiablo.files.skills.get(BaalCrab.SKILL_FIRE_WALL).skill);
    assertEquals("Meteor", Riiablo.files.skills.get(BaalCrab.SKILL_METEOR).skill);
    assertEquals("Blizzard", Riiablo.files.skills.get(BaalCrab.SKILL_BLIZZARD).skill);
  }

  @Test
  void bothSelectedHandsUseExactSpecialSkillAndLevelBoundaries() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      for (int button : new int[] {Input.Buttons.LEFT, Input.Buttons.RIGHT}) {
        for (int[] row : new int[][] {{59, 1, 1}, {56, 1, 1}, {51, 3, 0},
            {51, 4, 1}, {27, 7, 0}, {27, 8, 1}, {36, 20, 0}, {64, 20, 0}}) {
          hand(f, button, row[0], row[1]);
          assertEquals(row[2] == 1, baal(f).hasSpecialPrimeEvilSkill(f.target),
              "hand=" + button + " skill=" + row[0] + " level=" + row[1]);
        }
      }
    }
  }

  @Test
  void stateSkillBonusCrossesFireWallAndImmolationThresholdsOnlyForOwnedSkills() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      for (int[] row : new int[][] {{51, 3}, {27, 7}}) {
        hand(f, Input.Buttons.RIGHT, row[0], row[1]);
        assertFalse(baal(f).hasSpecialPrimeEvilSkill(f.target));
        UnitStates states = f.world.getMapper(UnitStates.class).create(f.target).init(f.target);
        states.stateList.addState(StateId.BATTLECOMMAND, 100, 1, f.target).skillModifier = 1;
        assertTrue(baal(f).hasSpecialPrimeEvilSkill(f.target));
        states.stateList.clearAll();
      }
      hand(f, Input.Buttons.RIGHT, 51, 0);
      f.world.getMapper(UnitStates.class).get(f.target).stateList
          .addState(StateId.BATTLECOMMAND, 100, 1, f.target).skillModifier = 10;
      assertFalse(baal(f).hasSpecialPrimeEvilSkill(f.target), "bonus alone does not create a skill pointer");
    }
  }

  @Test
  void currentWeaponSetControlsSelectedHands() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      CharData data = hand(f, Input.Buttons.RIGHT, 59, 1);
      assertTrue(baal(f).hasSpecialPrimeEvilSkill(f.target));
      data.getItems().setAlternate(1);
      assertFalse(baal(f).hasSpecialPrimeEvilSkill(f.target));
      data.setSkillLevel(51, 4);
      data.setAction(1, Input.Buttons.LEFT, 51);
      assertTrue(baal(f).hasSpecialPrimeEvilSkill(f.target));
      data.getItems().setAlternate(0);
      assertTrue(baal(f).hasSpecialPrimeEvilSkill(f.target));
    }
  }

  @Test
  void castingSkillDoesNotReplaceSelectedHandPredicate() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      hand(f, Input.Buttons.RIGHT, 36, 20);
      f.world.getMapper(Casting.class).create(f.target).set(59, f.actor, f.targetPosition());
      assertFalse(baal(f).hasSpecialPrimeEvilSkill(f.target));
      hand(f, Input.Buttons.RIGHT, 59, 1);
      f.world.getMapper(Casting.class).get(f.target).set(36, f.actor, f.targetPosition());
      assertTrue(baal(f).hasSpecialPrimeEvilSkill(f.target));
    }
  }

  @Test
  void unavailablePlayerAndNonPlayerHandsDoNotInventSpecialSkills() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      assertFalse(baal(f).hasSpecialPrimeEvilSkill(f.target));
      hand(f, Input.Buttons.LEFT, 59, 0);
      assertFalse(baal(f).hasSpecialPrimeEvilSkill(f.target));
      int monster = f.ally("skeleton1", 100, 20, 10);
      assertFalse(baal(f).hasSpecialPrimeEvilSkill(monster));
    }
  }

  @Test
  void openRangedSpecialSkillAdjustsExactWeightsAcrossAllDifficulties() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(difficulty)) {
        hand(f, Input.Buttons.RIGHT, 59, 1);
        select(f);
        assertArrayEquals(new int[] {0, difficulty == 0 ? 125 : 100, 5, 5, 5, 0, 35, 0,
            difficulty == 2 ? 40 : 0, 40, 0, 70, 80, difficulty == 0 ? 40 : 60, 35, 20},
            baal(f).decisionWeights(f.target, false));
      }
    }
  }

  @Test
  void specialSkillAddsAfterDistanceAndSingleTargetSignedAdjustments() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      hand(f, Input.Buttons.RIGHT, 59, 1);
      f.targetPosition().set(47, 10);
      select(f);
      int[] weights = baal(f).decisionWeights(f.target, false);
      assertEquals(45, weights[6]); // >35 sets 15, then special adds 30
      assertEquals(0, weights[11]); // >25 sets 0, one target subtracts 10, special adds 10
      assertEquals(45, weights[14]); // >25 sets 30, special adds 15
      assertEquals(0, weights[13]);
    }
  }

  @Test
  void specialResponseStopsAtStrict75AnchorBoundaryForBothWallBranches() {
    for (boolean blocked : new boolean[] {false, true}) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
        baal(f).beforeDecision();
        if (blocked) barrier(f);
        for (int x : new int[] {86, 87, 111, 112}) {
          f.world.getMapper(Position.class).get(f.actor).position.set(x - 10, 10);
          f.targetPosition().set(x, 10);
          hand(f, Input.Buttons.RIGHT, 36, 1);
          select(f);
          int[] ordinary = baal(f).decisionWeights(f.target, false);
          hand(f, Input.Buttons.RIGHT, 59, 1);
          select(f);
          int[] special = baal(f).decisionWeights(f.target, false);
          if (x == 86) {
            if (blocked) { ordinary[14] += 50; ordinary[13] += 50; }
            else { ordinary[6] += 30; ordinary[11] += 10; ordinary[14] += 15; }
          }
          assertArrayEquals(ordinary, special, "barrier=" + blocked + " targetX=" + x);
          assertEquals(x == 112 ? 60 : 0, special[5], "far anchor remains strictly >100");
        }
      }
    }
  }

  @Test
  void barrierResponseAddsRetreatAndColdMissilesButDoesNotApplyCurseGate() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(difficulty)) {
        hand(f, Input.Buttons.LEFT, 56, 1);
        barrier(f);
        select(f);
        assertArrayEquals(new int[] {0, difficulty == 0 ? 125 : 100, 45, 20, 20, 0, 20, 0,
            80, 70, 25, 0, 0, 50, 50, 0}, baal(f).decisionWeights(f.target, false));
      }
    }
  }

  @Test
  void meleeKeepsNativeColdAndBarrierTablesWithoutSpecialOrSingleTargetAdjustments() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      hand(f, Input.Buttons.RIGHT, 59, 1);
      f.targetPosition().set(11, 10);
      select(f);
      assertArrayEquals(BaalCrab.weights(0, true, false, 1, 100, 100, false, 1, false, false),
          baal(f).decisionWeights(f.target, true));
      f.world.getMapper(UnitStates.class).create(f.target).init(f.target).stateList
          .addState(StateId.COLD, 100, 1, f.actor);
      barrier(f);
      assertArrayEquals(BaalCrab.weights(0, true, true, 1, 100, 100, true, 1, false, false),
          baal(f).decisionWeights(f.target, true));
    }
  }

  @Test
  void singleTargetGateFollowsScoreBoostAndUsesGameDifficultyNotPlayerCount() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Act1NativeAiIntegrationTest.Fixture f = fixture(difficulty)) {
        // Extra connected player is outside the target-head cull: nCount remains one.
        int other = f.world.create();
        f.world.getMapper(Player.class).create(other);
        f.world.getMapper(Position.class).create(other).position.set(1000, 1000);
        f.world.process();
        for (boolean highScore : new boolean[] {false, true}) {
          Attributes attrs = targetStats(f);
          attrs.base().put(Stat.maxdamage, highScore ? 668 : 0);
          attrs.reset();
          select(f);
          assertEquals(1, baal(f).lastTargetCount());
          assertEquals(difficulty < 2 ? 0 : highScore ? 70 : 40,
              baal(f).decisionWeights(f.target, false)[8]);
        }
        // A low-threat summon still counts as a second node; no second player needed.
        int pet = f.ally("skeleton1", 100, 20, 10);
        MonStats.Entry row = new MonStats.Entry();
        row.threat = 1;
        f.world.getMapper(com.riiablo.engine.server.component.Monster.class).get(pet).monstats = row;
        f.world.getMapper(SummonedPet.class).create(pet).set(f.target, "skeleton", 70, 1, false, 0);
        f.world.process();
        select(f);
        assertEquals(2, baal(f).lastTargetCount());
        assertEquals(70, baal(f).decisionWeights(f.target, false)[8]);
      }
    }
  }

  @Test
  void cloneInheritsSpecialSkillResponseAndSingleTargetDifficultyGate() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture("BaalCrabClone", 1)) {
      assertTrue(f.ai instanceof BaalCrabClone);
      hand(f, Input.Buttons.RIGHT, 51, 4);
      select(f);
      int[] weights = baal(f).decisionWeights(f.target, false);
      assertEquals(0, weights[8]);
      assertEquals(35, weights[6]);
      assertEquals(70, weights[11]);
      assertEquals(35, weights[14]);
    }
  }

  @Test
  void realDecisionUsesAdjustedTableWithOneNativeRandomDraw() {
    try (Act1NativeAiIntegrationTest.Fixture f = fixture(0)) {
      hand(f, Input.Buttons.RIGHT, 59, 1);
      select(f);
      int[] weights = baal(f).decisionWeights(f.target, false);
      int[] oldBase = BaalCrab.weights(0, false, false, 9, 100, 100, false, 1, false, false);
      int chosenSeed = -1;
      long next = 0;
      for (int seed = 0; seed < 10000; seed++) {
        D2Seed nativeSeed = new D2Seed(seed ^ (f.actor * 0x45D9F3B), 666);
        long draw = Seed.rollRandomNumber(nativeSeed);
        if (BaalCrab.selectAction(weights, draw) != 11 || BaalCrab.selectAction(oldBase, draw) == 11) continue;
        chosenSeed = seed;
        next = Seed.rollRandomNumber(nativeSeed) & 0xFFFFFFFFL;
        break;
      }
      assertTrue(chosenSeed >= 0);
      Riiablo.gameSeed = chosenSeed;
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting());
      assertEquals(BaalCrab.SKILL_NOVA, f.casting().skillId);
      assertEquals(f.target, f.casting().targetId);
      assertEquals(1, f.probe.starts);
      assertEquals(next, f.ai.rollAiRandomNumber());
    }
  }

  private static Act1NativeAiIntegrationTest.Fixture fixture(int difficulty) {
    return fixture("BaalCrab", difficulty);
  }
  private static Act1NativeAiIntegrationTest.Fixture fixture(String ai, int difficulty) {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (ai.equalsIgnoreCase(row.AI)) return new Act1NativeAiIntegrationTest.Fixture(row.Id, difficulty, 10);
    }
    throw new AssertionError("No 1.10f " + ai + " row");
  }
  private static BaalCrab baal(Act1NativeAiIntegrationTest.Fixture f) { return (BaalCrab) f.ai; }
  private static void select(Act1NativeAiIntegrationTest.Fixture f) {
    assertEquals(f.target, baal(f).findNearestTargetWithAidist(new float[] {Float.MAX_VALUE}));
  }
  private static CharData hand(Act1NativeAiIntegrationTest.Fixture f, int button, int skill, int level) {
    CharData data = CharData.createRemote("BaalCounterplay", (byte) 1);
    data.setSkillLevel(skill, level);
    data.setAction(button, skill);
    f.world.getMapper(Player.class).get(f.target).data = data;
    return data;
  }
  private static Attributes targetStats(Act1NativeAiIntegrationTest.Fixture f) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 100f);
    attrs.base().put(Stat.maxhp, 100f);
    attrs.reset();
    f.world.getMapper(AttributesWrapper.class).create(f.target).attrs = attrs;
    return attrs;
  }
  private static void barrier(Act1NativeAiIntegrationTest.Fixture f) {
    int difficulty = f.ai.resolveAiDifficulty();
    Map map = mock(Map.class);
    when(map.getDifficulty()).thenReturn(difficulty);
    when(map.getAct()).thenReturn(-1);
    f.world.getMapper(MapWrapper.class).get(f.actor).set(map, mock(Map.Zone.class));
    when(map.castRay(any(), any(), eq(DT1.Tile.FLAG_BLOCK_JUMP), eq(0), any(), eq(false))).thenReturn(true);
  }
}
