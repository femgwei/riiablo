package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import org.junit.jupiter.api.Test;

class NativeBlindTargetApplierTest extends RiiabloTest {
  @Test void negativeRavenStatUsesLevelDifferenceAndRollMargin() {
    assertEquals(8, NativeBlindTargetApplier.rollSkillLevel(98, 1, -1, false, n -> 64));
    assertEquals(20, NativeBlindTargetApplier.rollSkillLevel(98, 1, -1, false, n -> 0));
    assertEquals(0, NativeBlindTargetApplier.rollSkillLevel(98, 1, -1, false, n -> 99));
    assertEquals(1, NativeBlindTargetApplier.rollSkillLevel(1, 99, -1, false, n -> 0));
    assertEquals(0, NativeBlindTargetApplier.rollSkillLevel(1, 99, -1, false, n -> 1));
    assertEquals(0, NativeBlindTargetApplier.rollSkillLevel(98, 1, 0, false,
        n -> { fail("no item stat must consume no random draw"); return 0; }));
  }

  @Test void difficultyDividesTheNativeCurseLengthAndSnapshotsCannotApplyIt() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture()) {
        f.apply(difficulty);
        UnitState state = f.state();
        assertNotNull(state);
        int expected = com.riiablo.engine.server.skill.NecromancerSkills.curseDuration(
            Riiablo.files.skills.get(71), state.level, Riiablo.files.DifficultyLevels.get(difficulty));
        assertEquals(expected, state.duration);
        assertEquals(71, state.skillId);
        assertEquals(f.source, state.sourceEntityId);
        assertTrue(state.needsSync);
      }
    }
    try (Fixture f = new Fixture()) {
      f.world.getMapper(UnitStates.class).get(f.target).snapshotOnly = true;
      f.apply(0);
      assertNull(f.state());
    }
  }

  @Test void bossesUnattackableAndUninterruptableTargetsRejectBlindness() {
    for (int variant = 0; variant < 4; variant++) {
      try (Fixture f = new Fixture()) {
        if (variant == 0) f.world.getMapper(Monster.class).get(f.target).rank = MonsterRank.UNIQUE;
        if (variant == 1) f.world.getMapper(Monster.class).get(f.target).rank = MonsterRank.SUPER_UNIQUE;
        if (variant == 2) f.world.getMapper(NativeUnitFlags.class).create(f.target);
        if (variant == 3) f.world.getMapper(UnitStates.class).get(f.target).stateList
            .addState(StateId.UNINTERRUPTABLE, 100, 1, f.target);
        f.apply(0);
        assertNull(f.state());
      }
    }
  }

  static final class Fixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder().build());
    final int source = world.create(), target = world.create();
    Fixture() {
      world.getMapper(Monster.class).create(source).set(Riiablo.files.monstats.get("druidhawk"), null);
      world.getMapper(Monster.class).create(source).rngState = 1068;
      world.getMapper(Monster.class).create(target).set(Riiablo.files.monstats.get(0), null);
      for (int id : new int[] {source, target}) {
        Attributes a = Attributes.obtainStandard();
        a.base().put(Stat.level, id == source ? 98 : 1);
        a.base().put(Stat.hitpoints, 10000f);
        a.base().put(Stat.maxhp, 10000f);
        if (id == source) a.base().put(Stat.item_stupidity, -1);
        a.reset();
        world.getMapper(AttributesWrapper.class).create(id).attrs = a;
        world.getMapper(UnitStates.class).create(id).init(world, id);
      }
      world.process();
    }
    void apply(int difficulty) { NativeBlindTargetApplier.apply(world, Riiablo.files, source, target, difficulty, 1234); }
    UnitState state() { return world.getMapper(UnitStates.class).get(target).stateList.getState(StateId.DIMVISION); }
    @Override public void close() { world.dispose(); }
  }
}
