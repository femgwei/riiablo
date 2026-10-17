package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Remaining native handlers, exercised against installed 1.10f data. */
class Remaining20261017AiIntegrationTest extends RiiabloTest {
  static final String[] NAMES = {
      "AbyssKnight", "Ancient", "BaalThrone", "BaalToStairs", "BatDemon",
      "CatapultSpotter", "ClawViperEx", "DarkWanderer", "FetishBlowgun", "FingerMage",
      "GenericSpawner", "HighPriest", "Imp", "InvisoSpawner", "MinionSpawner", "Mosquito",
      "Nihlathak", "OblivionKnight", "Overseer", "QuillMother", "ReanimatedHorde",
      "Regurgitator", "SiegeBeast", "SiegeTower", "SuicideMinion", "SuccubusWitch",
      "ThornHulk", "VileMother", "WillOWisp"};

  @Test void ancientAndSpawnerUseRealMonSeqModesAndActions() {
    String[] rows = {"ancientbarb1", "ancientbarb3", "evilhut"};
    for (String id : rows) {
      try (Fixture f = new Fixture(id, 0, 8)) {
        com.riiablo.engine.server.NativeMonsterSequence data =
            com.riiablo.engine.server.NativeMonsterSequence.load(f.ai.monster.monstats.Sk1mode);
        assertNotNull(data, id);
        assertTrue(data.frames.length > 0, id);
        assertTrue(java.util.stream.IntStream.range(0, data.actions.length)
            .anyMatch(i -> data.actions[i] != 0), id);
        assertTrue(f.ai.useMonsterSkill(0, f.target, f.targetPosition()));
        assertSame(data, f.sequence().nativeMonster);
        assertEquals(data.mode, f.sequence().mode1);
      }
    }
  }

  @Test void allOriginalMissingRowsAndBaboonScarabResolveDedicatedHandlers() {
    java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(NAMES));
    names.addAll(java.util.Arrays.asList("7TIllusion", "Catapult", "Trap-Melee", "Trap-Missile",
        "Trap-Nova", "Trap-Poison", "Trap-RightArrow", "Trap-LeftArrow", "Baboon", "Scarab"));
    java.util.Set<String> found = new java.util.HashSet<>();
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!names.contains(row.AI)) continue;
      AI ai = AI.findAI(12, row.AI);
      assertFalse(ai instanceof GenericMonster, row.Id + "/" + row.AI);
      found.add(row.AI);
    }
    assertEquals(names, found);
  }

  @Test void everyRemainingConfiguredMonSeqHasAnExecutableDefinition() {
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!java.util.Arrays.asList(NAMES).contains(row.AI)) continue;
      for (String mode : new String[] {row.Sk1mode, row.Sk2mode, row.Sk3mode, row.Sk4mode,
          row.Sk5mode, row.Sk6mode, row.Sk7mode, row.Sk8mode}) {
        if (mode != null && mode.startsWith("seq_")) assertNotNull(
            com.riiablo.engine.server.NativeMonsterSequence.load(mode), row.Id + "/" + mode);
      }
    }
  }

  private String row(String name) {
    for (MonStats.Entry row : Riiablo.files.monstats)
      if (name.equals(row.AI)) return row.Id;
    throw new AssertionError("Missing 1.10f row: " + name);
  }

  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> everyRemainingHandlerLoadsRunsAndDiesInAllDifficulties() {
    return java.util.Arrays.stream(NAMES).flatMap(name -> java.util.stream.IntStream.range(0, 3)
        .mapToObj(difficulty -> org.junit.jupiter.api.DynamicTest.dynamicTest(name + "/" + difficulty, () -> {
      try (Fixture f = new Fixture(row(name), difficulty, 12)) {
        assertEquals(name, f.ai.getClass().getSimpleName());
        assertTrue(f.ai instanceof NativeDecisionAI);
        f.ai.update(Animation.FRAME_DURATION);
        f.finishAction();
        f.world.getMapper(Player.class).remove(f.target);
        f.world.process();
        f.ai.update(30 * Animation.FRAME_DURATION);
        f.ai.kill();
        f.ai.kill();
        assertEquals("DEAD", f.ai.getState(), name);
        assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1, name);
      }
    })));
  }

  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> remainingHandlersExerciseChanceBoundariesAndRepeatedDecisions() {
    return java.util.Arrays.stream(NAMES).flatMap(name -> java.util.stream.IntStream.of(0, 100)
        .boxed().flatMap(chance -> java.util.stream.IntStream.of(1, 8, 24).mapToObj(distance ->
            org.junit.jupiter.api.DynamicTest.dynamicTest(name + "/chance=" + chance + "/distance=" + distance, () -> {
              try (Fixture f = new Fixture(row(name), 0, distance)) {
                // Force probability slots one at a time, retaining native distance,
                // duration and count parameters in all the other slots.
                int[] original = f.ai.params.clone();
                for (int slot = 0; slot < original.length; slot++) {
                  System.arraycopy(original, 0, f.ai.params, 0, original.length);
                  f.ai.params[slot] = chance;
                  f.finishAction();
                  f.ai.update(30 * Animation.FRAME_DURATION);
                  assertNotNull(f.ai.getState(), name);
                }
              }
            }))));
  }

  @Test void attachedImpReleasesADeletedOwnerAndRestoresTargetFlags() {
    try (Fixture f = new Fixture(row("Imp"), 0, 12)) {
      Monster imp = f.world.getMapper(Monster.class).get(f.actor);
      int owner = f.world.create();
      f.world.getMapper(Position.class).create(owner).position.set(15, 17);
      MonStats.Entry row = Riiablo.files.monstats.get("siegebeast1");
      f.world.getMapper(Monster.class).create(owner).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      imp.nativeOwnerId = owner;
      f.world.getMapper(NativeUnitFlags.class).create(f.actor).clear(NativeUnitFlags.MONSTER_TARGET);
      UnitStates states = f.world.getMapper(UnitStates.class).create(f.actor).init(f.world, f.actor);
      states.stateList.addState(StateId.ATTACHED, 0, 1, owner);
      f.world.process();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(f.world.getMapper(Position.class).get(owner).position,
          f.world.getMapper(Position.class).get(f.actor).position);
      f.world.delete(owner);
      f.world.process();
      f.finishAction();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(-1, imp.nativeOwnerId);
      assertFalse(states.stateList.hasState(StateId.ATTACHED));
      assertTrue(f.world.getMapper(NativeUnitFlags.class).get(f.actor).has(NativeUnitFlags.MONSTER_TARGET));
    }
  }

  @Test void allOrdinaryMeleeBranchesRespectTheirNativeAttackChance() {
    String[] names = {"AbyssKnight", "ClawViperEx", "FingerMage", "HighPriest", "Mosquito",
        "OblivionKnight", "Overseer", "QuillMother", "ReanimatedHorde", "Regurgitator",
        "SiegeBeast", "SuccubusWitch", "ThornHulk", "VileMother"};
    for (String name : names) {
      try (Fixture f = new Fixture(row(name), 0, 1)) {
        java.util.Arrays.fill(f.ai.params, 0);
        if (name.equals("AbyssKnight") || name.equals("ClawViperEx")
            || name.equals("FingerMage") || name.equals("Mosquito")) f.ai.params[2] = 100;
        else if (name.equals("HighPriest")) f.ai.params[0] = 100;
        else if (name.equals("Overseer")) f.ai.params[5] = 100;
        else if (name.equals("SiegeBeast")) f.ai.params[1] = 100;
        else if (name.equals("VileMother")) f.ai.params[3] = 100;
        else f.ai.params[0] = 100;
        if (name.equals("OblivionKnight")) continue; // Its melee fallback is a spell.
        f.ai.update(Animation.FRAME_DURATION);
        assertNotNull(f.casting(), name);
        assertEquals(SkillCodes.attack, f.casting().skillId, name);
        assertEquals(f.target, f.casting().targetId, name);
        f.sequence().started = true;
        f.ai.update(10f);
        assertTrue(f.sequence().started, name + " must preserve active attack");
      }
    }
  }

  @Test void fingerMagePursuesAnAlreadyCursedTargetAfterFailedCastRoll() {
    try (Fixture f = new Fixture(row("FingerMage"), 0, 8)) {
      java.util.Arrays.fill(f.ai.params, 0);
      f.ai.params[4] = 20;
      UnitStates states = f.world.getMapper(UnitStates.class).create(f.target).init(f.world, f.target);
      states.stateList.addState(StateId.FINGERMAGECURSE, 100, 1, f.actor);
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("APPROACH", f.ai.getState());
    }
  }

  @Test void roostingBatKeepsItsClockAndRemovesRegenBonusOnDeath() {
    try (Fixture f = new Fixture(row("BatDemon"), 0, 40)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(Engine.Monster.MODE_S3, f.sequence().mode1);
      f.finishAction();
      f.ai.update(9 * Animation.FRAME_DURATION);
      assertEquals(Engine.Monster.MODE_S4, f.sequence().mode1);
      f.ai.update(11 * Animation.FRAME_DURATION);
      f.targetPosition().set(11, 10);
      f.ai.update(16 * Animation.FRAME_DURATION);
      assertEquals(Engine.Monster.MODE_S2, f.sequence().mode1);
    }
  }

  @Test void suicideCountdownSurvivesLosingTheTargetAndKillsOnlyOnce() {
    try (Fixture f = new Fixture(row("SuicideMinion"), 0, 1)) {
      f.ai.params[4] = 5;
      f.ai.params[1] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      f.world.getMapper(Player.class).remove(f.target);
      f.world.process();
      f.ai.update(7 * Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
      assertEquals(0, f.world.getMapper(AttributesWrapper.class).get(f.actor).attrs
          .getValue(com.riiablo.attributes.Stat.hitpoints, 0));
      f.ai.update(5 * Animation.FRAME_DURATION);
      assertEquals("DEAD", f.ai.getState());
    }
  }
}
