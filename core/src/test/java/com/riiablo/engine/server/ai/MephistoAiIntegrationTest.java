package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.function.Predicate;
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
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.DT1;
import com.riiablo.map.Map;
import com.riiablo.skill.SkillCodes;
import org.junit.jupiter.api.Test;

/** Real 1.10f tables + ECS action lifecycle; paths are stubbed, not a client effect test. */
class MephistoAiIntegrationTest extends RiiabloTest {
  @Test
  void mephistoRowsResolveDedicatedAiAndSixNativeSkillSlots() {
    int rows = 0;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"Mephisto".equals(row.AI)) continue;
      rows++;
      assertTrue(AI.findAI(12, row.AI) instanceof Mephisto);
      for (int slot = 0; slot < 6; slot++) {
        assertNotNull(Riiablo.files.skills.get(skillName(row, slot)), row.Id + " slot " + slot);
      }
      assertTrue(row.Skill7 == null || row.Skill7.isEmpty());
    }
    assertTrue(rows > 0);
  }

  @Test
  void rangedCycleMovesBeforeCastingAndPreservesActiveAction() {
    try (Fixture f = fixture(0, 10f)) {
      seedFor(f, seed -> Seed.rollPercentage(seed) < 50);
      D2Seed expected = unitSeed(f, Riiablo.gameSeed);
      Seed.rollPercentage(expected); // enter state 2
      Seed.rollRandomNumber(expected); // 3..5 burst count
      Seed.rollRandomNumber(expected); // orbit path type
      Seed.rollPercentage(expected); // alternate target chance (zero)
      int slot = Mephisto.selectSkillSlot(Seed.rollPercentage(expected), 6, 10f, 0, false);
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      assertEquals("MOVE", f.ai.getState());
      f.ai.update(1f);
      assertSkill(f, slot, f.target);
      assertEquals(1, f.probe.starts);
      f.ai.update(1f);
      assertEquals(1, f.probe.starts, "active Casting/Sequence cannot be replaced");
    }
  }

  @Test
  void zeroCombatChanceStillAcceptsNativeZeroRoll() {
    try (Fixture f = fixture(0, 1f)) {
      f.ai.params[Mephisto.PARAM_CAST_OR_MELEE_CHANCE] = 0;
      seedFor(f, seed -> {
        Seed.rollPercentage(seed);
        return Seed.rollPercentage(seed) == 0;
      });
      // A newly initialized stream (high seed 666) cannot produce zero on
      // its first roll. Advance once to exercise the actual native boundary.
      f.ai.rollAiPercentage();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("MOVE", f.ai.getState());
      assertNull(f.casting(), "native <=0 must enter the casting cycle, not melee");
    }
  }

  @Test
  void rangedBurstCountsInitialRepositionAndReturnsToIdleAfterExpiry() {
    try (Fixture f = fixture(0, 10f)) {
      seedFor(f, seed -> {
        if (Seed.rollPercentage(seed) >= 50) return false;
        if ((Seed.rollRandomNumber(seed) & 0xFFFFFFFFL) % 3 != 0) return false;
        Seed.rollRandomNumber(seed); // initial orbit
        for (int i = 0; i < 2; i++) {
          Seed.rollPercentage(seed); // alternate target
          Seed.rollPercentage(seed); // spell choice
          if (Seed.rollPercentage(seed) < 50) return false; // no extra reposition
        }
        return Seed.rollPercentage(seed) >= 50 && Seed.rollPercentage(seed) >= 65;
      });
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      for (int i = 0; i < 2; i++) {
        f.ai.update(1f);
        assertNotNull(f.casting());
        f.finishAction();
      }
      f.ai.update(1f);
      assertNull(f.casting());
      assertEquals("IDLE", f.ai.getState());
      assertEquals(2, f.probe.starts);
    }
  }

  @Test
  void meleeCanSelectOrdinaryAttackOrSkill3UsingLifeChance() {
    try (Fixture f = fixture(0, 1f)) {
      f.ai.params[Mephisto.PARAM_CAST_OR_MELEE_CHANCE] = -1;
      seedFor(f, seed -> {
        Seed.rollPercentage(seed);
        return Seed.rollPercentage(seed) < 80 && Seed.rollPercentage(seed) < 80;
      });
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(SkillCodes.attack, f.casting().skillId);
      assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
    }
    try (Fixture f = fixture(0, 1f)) {
      f.health(20f, 100f);
      f.ai.params[Mephisto.PARAM_CAST_OR_MELEE_CHANCE] = -1;
      seedFor(f, seed -> {
        Seed.rollPercentage(seed);
        return Seed.rollPercentage(seed) < 96 && Seed.rollPercentage(seed) >= 64;
      });
      f.ai.update(Animation.FRAME_DURATION);
      assertSkill(f, 2, f.target);
    }
  }

  @Test
  void distantTargetUsesFiftyPercentWalkingBonusWithoutCasting() {
    try (Fixture f = fixture(0, 21f)) {
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      assertEquals("MOVE", f.ai.getState());
      assertEquals(0.5f, f.world.getMapper(Velocity.class).get(f.actor).modeSpeedBonusMultiplier);
      verify(f.paths).findPath(eq(f.actor), eq(f.targetPosition()), eq(false), eq(f.target));
    }
  }

  @Test
  void woundedRetreatUsesIntegerLifeAndNativeSignOffsets() {
    try (Fixture f = fixture(0, 4f)) {
      f.health(20.9f, 100.9f); // native truncates both fixed-point stats before dividing
      seedFor(f, seed -> Seed.rollPercentage(seed) < 40);
      // Explicit non-combat tick isolates this gate from MonStats2 melee footprint.
      ((Mephisto) f.ai).think(f.target, 4f, false);
      assertNull(f.casting());
      verify(f.paths).findPath(eq(f.actor), eq(new Vector2(2, 10)), eq(false),
          eq(Engine.INVALID_ENTITY));
      assertEquals(0.5f, f.world.getMapper(Velocity.class).get(f.actor).modeSpeedBonusMultiplier);
    }
  }

  @Test
  void nightmareAndHellUseBlizzardBehindMissileBarrierButNormalDoesNot() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = fixture(difficulty, 10f)) {
        Map map = mock(Map.class);
        Map.Zone zone = mock(Map.Zone.class);
        when(map.getDifficulty()).thenReturn(difficulty);
        when(map.castRay(eq(zone), any(), eq(DT1.Tile.FLAG_BLOCK_JUMP), eq(0), any(), eq(false)))
            .thenReturn(true);
        f.world.getMapper(MapWrapper.class).get(f.actor).set(map, zone);
        seedFor(f, seed -> Seed.rollPercentage(seed) < 50);
        f.ai.update(Animation.FRAME_DURATION);
        f.ai.update(1f);
        if (difficulty == 0) {
          assertNotEquals(Riiablo.files.skills.get(f.ai.monster.monstats.Skill6).Id,
              f.casting().skillId);
        } else {
          assertSkill(f, 5, f.target);
        }
      }
    }
  }

  @Test
  void rangedCastUsesRetargetedUnitsPositionAndServerEvent() {
    try (Fixture f = fixture(0, 10f)) {
      setHealth(f, f.target, 100f, 100f);
      int weak = player(f, 22, 10, 20f, 100f);
      f.world.process();
      f.ai.params[Mephisto.PARAM_LOWEST_LIFE_TARGET_CHANCE] = 100;
      seedFor(f, seed -> Seed.rollPercentage(seed) < 50);
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.update(1f);
      assertNotNull(f.casting());
      assertEquals(weak, f.casting().targetId);
      assertEquals(new Vector2(22, 10), f.casting().targetVec);
      assertEquals(1, f.probe.starts);
    }
  }

  @Test
  void spellTargetChoosesAbsoluteLifeNotPercentageOrNearestAndKeepsFirstTie() {
    try (Fixture f = fixture(0, 10f)) {
      setHealth(f, f.target, 100f, 10000f); // 1%, but more absolute life
      int first = player(f, 12, 10, 20.9f, 21f);
      player(f, 13, 10, 20.1f, 100f); // integer tie: first wins
      f.world.process();
      f.ai.params[Mephisto.PARAM_LOWEST_LIFE_TARGET_CHANCE] = 100;
      assertEquals(first, ((Mephisto) f.ai).findSpellTarget(f.target));
    }
  }

  @Test
  void spellTargetFiltersDeadFlagsCorpseTownOtherMapAndBeyond32Subtiles() {
    try (Fixture f = fixture(0, 10f)) {
      setHealth(f, f.target, 100f, 100f);
      int boundary = player(f, 42, 10, 20f, 100f);
      player(f, 42, 11, 1f, 100f);
      player(f, 12, 10, 0f, 100f);
      int corpse = player(f, 12, 10, 1f, 100f);
      f.world.getMapper(Corpse.class).create(corpse);
      int untargetable = player(f, 12, 10, 1f, 100f);
      f.world.getMapper(NativeUnitFlags.class).create(untargetable).set(NativeUnitFlags.TARGETABLE);
      int otherMap = player(f, 12, 10, 1f, 100f);
      f.world.getMapper(MapWrapper.class).create(otherMap).set(new Map(2, 0), null);
      int town = player(f, 12, 10, 1f, 100f);
      Map.Zone townZone = mock(Map.Zone.class);
      when(townZone.isTown()).thenReturn(true);
      f.world.getMapper(MapWrapper.class).create(town).set(null, townZone);
      f.world.process();
      f.ai.params[Mephisto.PARAM_LOWEST_LIFE_TARGET_CHANCE] = 100;
      assertEquals(boundary, ((Mephisto) f.ai).findSpellTarget(f.target));
    }
  }

  @Test
  void failedRetargetChancePreservesFallbackEvenWhenSecondChanceIsCertain() {
    try (Fixture f = fixture(0, 10f)) {
      player(f, 12, 10, 1f, 100f);
      f.world.process();
      f.ai.params[Mephisto.PARAM_LOWEST_LIFE_TARGET_CHANCE] = 0;
      f.ai.params[Mephisto.PARAM_SECOND_TARGET_CHANCE] = 100;
      assertEquals(f.target, ((Mephisto) f.ai).findSpellTarget(f.target),
          "native second bucket checks caster UNIT_PLAYER, not target type");
    }
  }

  @Test
  void rejectedSpellWaitsInsteadOfReplacingItWithOrdinaryAttack() {
    try (Fixture f = fixture(0, 10f)) {
      seedFor(f, seed -> Seed.rollPercentage(seed) < 50);
      f.ai.update(Animation.FRAME_DURATION);
      f.probe.reject = true;
      f.ai.update(1f);
      assertNull(f.casting());
      assertEquals("IDLE", f.ai.getState());
      assertEquals(0, f.probe.starts);
    }
  }

  private static Fixture fixture(int difficulty, float distance) {
    Fixture f = new Fixture("mephisto", difficulty, distance);
    assertTrue(f.ai instanceof Mephisto);
    f.ai.params[Mephisto.PARAM_LOWEST_LIFE_TARGET_CHANCE] = 0;
    return f;
  }

  private static void seedFor(Fixture f, Predicate<D2Seed> predicate) {
    for (int seed = 0; seed < 100000; seed++) {
      if (predicate.test(unitSeed(f, seed))) {
        Riiablo.gameSeed = seed;
        return;
      }
    }
    fail("No deterministic Mephisto branch seed");
  }

  private static D2Seed unitSeed(Fixture f, int seed) {
    return new D2Seed(seed ^ (f.actor * 0x45D9F3B), 666);
  }

  private static int player(Fixture f, float x, float y, float hp, float maxHp) {
    int id = f.world.create();
    f.world.getMapper(Player.class).create(id);
    f.world.getMapper(Position.class).create(id).position.set(x, y);
    setHealth(f, id, hp, maxHp);
    return id;
  }

  private static void setHealth(Fixture f, int id, float hp, float maxHp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.reset();
    f.world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
  }

  private static void assertSkill(Fixture f, int slot, int targetId) {
    assertNotNull(f.casting());
    assertEquals(Riiablo.files.skills.get(skillName(f.ai.monster.monstats, slot)).Id,
        f.casting().skillId);
    assertEquals(targetId, f.casting().targetId);
    assertEquals(f.world.getMapper(Position.class).get(targetId).position, f.casting().targetVec);
  }

  private static String skillName(MonStats.Entry row, int slot) {
    switch (slot) {
      case 0: return row.Skill1;
      case 1: return row.Skill2;
      case 2: return row.Skill3;
      case 3: return row.Skill4;
      case 4: return row.Skill5;
      case 5: return row.Skill6;
      default: throw new IllegalArgumentException("slot " + slot);
    }
  }
}
