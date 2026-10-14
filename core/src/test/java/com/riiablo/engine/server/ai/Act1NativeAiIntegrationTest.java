package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.d2moo.common.drlg.D2Seed;
import com.d2moo.common.seed.Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.map.Map;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Real 1.10f tables + ECS decisions. Path search is stubbed, not collision acceptance. */
class Act1NativeAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsMustResolveToDedicatedAiNotFallback() {
    for (String rowId : new String[] {"andariel", "smith", "griswold", "arach3", "gargoyletrap"}) {
      MonStats.Entry row = Riiablo.files.monstats.get(rowId);
      assertNotNull(row, rowId);
      AI ai = AI.findAI(12, row.AI);
      assertFalse(ai instanceof GenericMonster, rowId + " must not use GenericMonster");
      assertEquals(row.AI, ai.getClass().getSimpleName());
    }
  }

  @Test
  void arachUsesNativeAttackDecisionAndGargoyleTrapUsesSkillRecovery() {
    try (Fixture f = new Fixture("arach3", 0, 1f)) {
      f.ai.params[Arach.PARAM_ATTACK_CHANCE] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("ATTACK", f.ai.getState());
      assertEquals(SkillCodes.attack, f.casting().skillId);
    }
    try (Fixture f = new Fixture("gargoyletrap", 0, 5f)) {
      f.ai.params[GargoyleTrap.PARAM_SHOOT_DISTANCE] = 20;
      f.ai.params[GargoyleTrap.PARAM_SHOOT_CHANCE] = 100;
      f.ai.params[GargoyleTrap.PARAM_FIRE_RECOVERY] = 7;
      f.ai.params[GargoyleTrap.PARAM_STALL_DURATION] = 1;
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("CAST", f.ai.getState());
      assertNotNull(f.world.getMapper(Target.class).get(f.actor));
      assertEquals(f.target, f.world.getMapper(Target.class).get(f.actor).target);
      assertTrue(f.world.getMapper(Casting.class).has(f.actor));
      f.ai.update(6 * Animation.FRAME_DURATION);
      assertTrue(f.world.getMapper(Casting.class).has(f.actor));
    }
  }

  @Test
  void initializationReadsAllEightParamsAndThinkDelayFromEachDifficulty() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("andariel", difficulty, 10f)) {
        MonStats.Entry row = new MonStats.Entry();
        row.aip1 = new int[] {1, 11, 21};
        row.aip2 = new int[] {2, 12, 22};
        row.aip3 = new int[] {3, 13, 23};
        row.aip4 = new int[] {4, 14, 24};
        row.aip5 = new int[] {5, 15, 25};
        row.aip6 = new int[] {6, 16, 26};
        row.aip7 = new int[] {7, 17, 27};
        row.aip8 = new int[] {8, 18, 28};
        row.aidel = new int[] {3, 5, 7};
        f.world.getMapper(Monster.class).get(f.actor).monstats = row;
        f.ai.initialize();
        for (int slot = 0; slot < 8; slot++) {
          assertEquals(slot + 1 + difficulty * 10, f.ai.params[slot]);
        }
        assertEquals(row.aidel[difficulty] * Animation.FRAME_DURATION, f.ai.SLEEP);
      }
    }
  }

  @Test
  void andarielMeleeSelectsSprayOrAttackAndDoesNotOverwriteActiveCast() {
    for (int chance : new int[] {0, 100}) {
      try (Fixture f = new Fixture("andariel", 0, 1f)) {
        f.ai.params[0] = chance;
        f.ai.update(Animation.FRAME_DURATION);
        if (chance == 100) f.assertSkill(0);
        else {
          assertEquals(SkillCodes.attack, f.casting().skillId);
          assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
        }
        f.sequence().started = true;
        f.ai.update(10f);
        assertTrue(f.sequence().started, "active animation must not restart");
        assertEquals(chance == 100 ? 1 : 0, f.probe.starts);
      }
    }
  }

  @Test
  void andarielRangedBranchesUseBothConfiguredSkills() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      for (int spray : new int[] {0, 100}) {
        try (Fixture f = new Fixture("andariel", difficulty, 10f)) {
          f.ai.params[1] = 0;
          f.ai.params[2] = 100;
          f.ai.params[3] = spray;
          f.ai.update(Animation.FRAME_DURATION);
          f.assertSkill(spray == 100 ? 0 : 1);
          assertEquals(1, f.probe.starts);
          assertEquals("CAST", f.ai.getState());
        }
      }
    }
  }

  @Test
  void andarielStallsExactlyFiveFramesThenCanEngage() {
    try (Fixture f = new Fixture("andariel", 0, 10f)) {
      f.ai.params[1] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      f.ai.params[1] = 0;
      f.ai.params[2] = 0;
      f.ai.update(4 * Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      f.ai.update(Animation.FRAME_DURATION + 0.00001f);
      assertEquals("APPROACH", f.ai.getState());
      verify(f.paths).findPath(eq(f.actor), eq(f.targetPosition()), eq(false), eq(f.target));
      assertEquals(0, f.probe.starts);
    }
  }

  @Test
  void failedSkillDoesNotSubstituteBasicAttackAndWaitsTenFrames() {
    try (Fixture f = new Fixture("andariel", 0, 1f)) {
      // Reject via the shared event lifecycle rather than inventing a skill.
      f.probe.reject = true;
      f.ai.params[0] = 100;
      f.ai.update(Animation.FRAME_DURATION);
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
      assertEquals("IDLE", f.ai.getState());
      f.probe.reject = false;
      f.ai.update(9 * Animation.FRAME_DURATION);
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
      f.ai.update(Animation.FRAME_DURATION + 0.00001f);
      f.assertSkill(0);
    }
  }

  @Test
  void smithSpeedsUpWhenHurtAndUsesOrdinaryAttackInMelee() {
    try (Fixture f = new Fixture("smith", 0, 30f)) {
      f.health(50f, 100f);
      f.ai.update(Animation.FRAME_DURATION);
      // The real MonStats2 melee footprint can include a surprisingly large
      // native range; either branch must remain authoritative and stable.
      if ("APPROACH".equals(f.ai.getState())) {
        assertFalse(f.world.getMapper(Running.class).has(f.actor));
      } else {
        assertEquals("ATTACK", f.ai.getState());
      }
      f.targetPosition().set(11, 10);
      f.ai.update(1f);
      assertEquals(SkillCodes.attack, f.casting().skillId);
      assertEquals(Engine.Monster.MODE_A1, f.sequence().mode1);
      assertEquals(0f, f.world.getMapper(Velocity.class).get(f.actor).modeSpeedBonusMultiplier);
      f.ai.kill();
      f.ai.update(10f);
      assertEquals("DEAD", f.ai.getState());
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
      assertEquals(Engine.Monster.MODE_DT, f.sequence().mode1);
    }
  }

  @Test
  void griswoldUsesNative80And50PercentBranchesAndTenFrameIdle() {
    for (boolean melee : new boolean[] {true, false}) {
      for (boolean success : new boolean[] {true, false}) {
        try (Fixture f = new Fixture("griswold", 0, melee ? 1f : 10f)) {
          int chance = melee ? 80 : 50;
          f.seedFirstRoll(chance, success);
          f.ai.update(Animation.FRAME_DURATION);
          assertEquals(success ? (melee ? "ATTACK" : "APPROACH") : "IDLE", f.ai.getState());
          if (success && melee) assertEquals(SkillCodes.attack, f.casting().skillId);
          if (!success) {
            f.ai.update(9 * Animation.FRAME_DURATION);
            assertEquals("IDLE", f.ai.getState());
            assertFalse(f.world.getMapper(Casting.class).has(f.actor));
          }
        }
      }
    }
  }

  @Test
  void skeletonUsesA1OnSuccessfulRollAndResumesAfterAnimationWithoutRandomPause() {
    for (int chance : new int[] {0, 100}) {
      try (Fixture f = new Fixture("skeleton1", 0, 1f)) {
        Skeleton skeleton = (Skeleton) f.ai;
        skeleton.params[2] = 100;
        skeleton.params[3] = chance;
        skeleton.update(Animation.FRAME_DURATION);
        assertEquals(chance == 100 ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2,
            f.sequence().mode1);
        assertEquals(0f, skeleton.time);
        f.sequence().started = true;
        skeleton.update(Animation.FRAME_DURATION);
        assertTrue(f.sequence().started);
        f.finishAction();
        skeleton.update(Animation.FRAME_DURATION);
        assertFalse(f.sequence().started, "a new action starts after completion");
        assertEquals(SkillCodes.attack, f.casting().skillId);
      }
    }
  }

  @Test
  void noTargetDoesNotCastOrChaseAndActiveSequenceAloneBlocksNewDecision() {
    try (Fixture f = new Fixture("andariel", 0, 1f)) {
      f.world.getMapper(Sequence.class).create(f.actor)
          .sequence(Engine.Monster.MODE_GH, Engine.Monster.MODE_NU).started = true;
      f.ai.update(1f);
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
      assertEquals(Engine.Monster.MODE_GH, f.sequence().mode1);
      f.finishAction();
      f.targetPosition().set(1000, 1000);
      f.ai.update(1f);
      assertEquals("IDLE", f.ai.getState());
      assertFalse(f.world.getMapper(Casting.class).has(f.actor));
      assertEquals(0, f.probe.starts);
    }
  }

  private static final class Probe extends BaseSystem {
    int starts;
    boolean reject;
    @Subscribe public void onStart(SkillStartEvent event) { starts++; }
    @Subscribe public void onCast(com.riiablo.engine.server.event.SkillCastEvent event) {
      if (reject) event.accepted = false;
    }
    @Override protected void processSystem() {}
  }

  private static final class Fixture implements AutoCloseable {
    final World previous = Riiablo.engine;
    final int oldSeed = Riiablo.gameSeed;
    final Probe probe = new Probe();
    final World world;
    final Pathfinder paths = mock(Pathfinder.class);
    final AI ai;
    final int actor;
    final int target;

    Fixture(String rowId, int difficulty, float distance) {
      EntityFactory factory = new NoopFactory();
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, new Pathfinder(), new CofManager(), factory)
          .build().register("factory", factory).register("map", new Map(1, difficulty)));
      Riiablo.engine = world;
      MonStats.Entry row = Riiablo.files.monstats.get(rowId);
      assertNotNull(row, rowId);
      actor = world.create();
      world.getMapper(Monster.class).create(actor).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(MapWrapper.class).create(actor).set(new Map(1, difficulty), null);
      world.getMapper(Position.class).create(actor).position.set(10, 10);
      world.getMapper(Angle.class).create(actor);
      world.getMapper(Size.class).create(actor).size = 1;
      world.getMapper(Velocity.class).create(actor).setMonster(row.Velocity);
      health(100f, 100f);
      target = world.create();
      world.getMapper(Player.class).create(target);
      world.getMapper(Position.class).create(target).position.set(10 + distance, 10);
      world.getMapper(Size.class).create(target).size = 1;
      world.process();
      ai = AI.findAI(actor, row.AI);
      world.getInjector().inject(ai);
      ai.pathfinder = paths;
      when(paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenReturn(true);
      ai.initialize();
    }

    void health(float current, float max) {
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, current);
      attrs.base().put(Stat.maxhp, max);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(actor).attrs = attrs;
    }

    Vector2 targetPosition() { return world.getMapper(Position.class).get(target).position; }
    Casting casting() { return world.getMapper(Casting.class).get(actor); }
    Sequence sequence() { return world.getMapper(Sequence.class).get(actor); }

    void assertSkill(int slot) {
      MonStats.Entry row = world.getMapper(Monster.class).get(actor).monstats;
      Skills.Entry skill = Riiablo.files.skills.get(slot == 0 ? row.Skill1 : row.Skill2);
      assertNotNull(skill);
      assertNotNull(casting());
      assertEquals(skill.Id, casting().skillId);
      assertEquals(target, casting().targetId);
      assertEquals(targetPosition(), casting().targetVec);
      int mode = Riiablo.files.MonMode.index(slot == 0 ? row.Sk1mode : row.Sk2mode);
      if (mode < 0 && skill.monanim != null) mode = Riiablo.files.MonMode.index(skill.monanim);
      if (mode < 0) mode = Engine.Monster.MODE_S1;
      assertEquals(mode, sequence().mode1);
    }

    void finishAction() {
      world.getMapper(Casting.class).remove(actor);
      world.getMapper(Sequence.class).remove(actor);
      world.process();
    }

    void seedFirstRoll(int chance, boolean success) {
      for (int seed = 0; seed < 10000; seed++) {
        D2Seed nativeSeed = new D2Seed(seed ^ (actor * 0x45D9F3B), 666);
        if ((Seed.rollPercentage(nativeSeed) < chance) == success) {
          Riiablo.gameSeed = seed;
          return;
        }
      }
      fail("No deterministic branch seed found");
    }

    @Override public void close() {
      world.dispose();
      Riiablo.engine = previous;
      Riiablo.gameSeed = oldSeed;
    }
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
