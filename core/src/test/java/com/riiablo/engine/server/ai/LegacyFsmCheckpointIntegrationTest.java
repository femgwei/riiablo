package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.ai.fsm.StateMachine;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.replay.UnitStateRuntimeCheckpoint;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.UnitStates;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Private legacy FSM restoration, not a claim that global RNG or actions are checkpointed. */
class LegacyFsmCheckpointIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @ValueSource(strings = {"Baboon", "Bighead", "BloodHawk", "ClawViper", "CorruptLancer", "CorruptRogue", "Goatman",
      "Mummy", "MaggotLarva", "PinHead", "PantherWoman", "Wraith", "Fetish", "SandMaggot",
      "CorruptArcher", "FallenShaman", "FetishShaman", "SandLeaper", "SandRaider", "SkeletonBow", "SkeletonMage", "Vampire"})
  void realWorldRestoresMachineHistoryAndKeepsTargetServicesAndRemainingDelay(String type) throws Exception {
    Map<Integer, UnitStateRuntimeCheckpoint> image; Object sourceSubscription = null;
    try (Fixture f = fixture(type, 30)) {
      AiRuntimeStateTest.setStates(f.ai);
      // Keep a valid target, but do not trigger the ranged hosts' immediate-response shortcut.
      if (f.ai instanceof CorruptArcher) ((CheckpointStateMachine<CorruptArcher.State>)
          ((CorruptArcher) f.ai).stateMachine).restore(CorruptArcher.State.ATTACK, CorruptArcher.State.DEAD, CorruptArcher.State.WANDER);
      if (f.ai instanceof SkeletonBow) ((CheckpointStateMachine<SkeletonBow.State>)
          ((SkeletonBow) f.ai).stateMachine).restore(SkeletonBow.State.ATTACK, SkeletonBow.State.DEAD, SkeletonBow.State.WANDER);
      AiRuntimeStateTest.put(f.ai, f.ai.getClass(), "nextAction", 1.25f);
      AiRuntimeStateTest.put(f.ai, f.ai.getClass(), "time", .5f);
      if (hasSubscription(type)) sourceSubscription = AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "enemyEntities");
      image = UnitStateRuntimeCheckpoint.capture(f.world, new int[] {0, 1});
    }
    try (Fixture f = fixture(type, 30)) {
      Object machine = AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "stateMachine");
      Object scratch = AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "tmpVec2");
      Object subscription = hasSubscription(type) ? AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "enemyEntities") : null;
      image.get(0).aiDecisionRuntime().validateRestore(f.world, 0);
      assertEquals(0f, AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "time"));
      UnitStateRuntimeCheckpoint.restore(f.world, image);
      assertSame(f.ai, f.world.getMapper(AIWrapper.class).get(0).ai);
      assertSame(machine, AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "stateMachine"));
      assertSame(scratch, AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "tmpVec2"));
      if (hasSubscription(type)) {
        assertNotSame(sourceSubscription, subscription);
        assertSame(subscription, AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "enemyEntities"));
      }
      assertArrayEquals(image.get(0).bytes(), UnitStateRuntimeCheckpoint.capture(f.world, new int[] {0, 1}).get(0).bytes());
      clearInvocations(f.paths);
      f.ai.update(.125f);
      assertEquals(.375f, AiRuntimeStateTest.get(f.ai, f.ai.getClass(), "time"));
      assertNull(f.casting()); verifyNoInteractions(f.paths);
      assertTrue(machine(f.ai).revertToPreviousState());
      assertEquals("DEAD", f.ai.getState());
      f.ai.update(10f); f.ai.kill();
      assertNull(f.sequence()); assertNull(f.casting()); verifyNoInteractions(f.paths);
    }
  }

  @Test void codecsDoNotReadOrReplaceTheUncoveredGlobalRandomGenerator() throws Exception {
    java.util.Random previous = MathUtils.random;
    RandomXS128 uncovered = mock(RandomXS128.class);
    try {
      MathUtils.random = uncovered;
      for (String type : new String[] {"Baboon", "Bighead", "BloodHawk", "ClawViper", "CorruptLancer", "CorruptRogue", "Goatman",
          "Mummy", "MaggotLarva", "PinHead", "PantherWoman", "Wraith", "Fetish", "SandMaggot",
          "CorruptArcher", "FallenShaman", "FetishShaman", "SandLeaper", "SandRaider", "SkeletonBow", "SkeletonMage", "Vampire"}) {
        AI source = AI.findAI(0, type), target = AI.findAI(0, type);
        AiRuntimeStateTest.setStates(source);
        AiRuntimeState image = AiRuntimeState.fromBytes(AiRuntimeState.capture(source, 0).bytes());
        image.validateRestore(target, 0); image.restore(target, 0);
        assertSame(uncovered, MathUtils.random);
      }
      verifyNoInteractions(uncovered);
    } finally { MathUtils.random = previous; }
  }

  @Test void baboonPendingRegenerationRemovalResumesWithoutApplyingTheBonusTwice() throws Exception {
    Map<Integer, UnitStateRuntimeCheckpoint> image;
    try (Fixture f = fixture("Baboon", 10)) {
      f.health(50, 100);
      com.riiablo.attributes.Attributes attrs = f.world.getMapper(AttributesWrapper.class).get(0).attrs;
      attrs.base().put(Stat.hpregen, 51); attrs.aggregate().put(Stat.hpregen, 51);
      f.world.getMapper(UnitStates.class).create(0);
      Baboon ai = (Baboon) f.ai;
      ai.aiParam0 = 1; ai.aiParam1 = 1; ai.aiParam2 = 11; ai.time = .2f;
      ai.stateMachine.changeState(Baboon.State.REGEN);
      image = UnitStateRuntimeCheckpoint.capture(f.world, new int[] {0, 1});
    }
    try (Fixture f = fixture("Baboon", 10)) {
      UnitStateRuntimeCheckpoint.restore(f.world, image);
      Baboon ai = (Baboon) f.ai;
      com.riiablo.attributes.Attributes attrs = f.world.getMapper(AttributesWrapper.class).get(0).attrs;
      assertEquals(51, attrs.getValue(Stat.hpregen, 0));
      ai.update(.1f); assertEquals(1, ai.aiParam0); assertEquals(51, attrs.getValue(Stat.hpregen, 0));
      ai.update(.15f);
      assertEquals(0, ai.aiParam0); assertEquals(40, attrs.base().getValue(Stat.hpregen, 0));
      assertEquals(40, attrs.getValue(Stat.hpregen, 0));
      ai.update(1f); assertEquals(40, attrs.getValue(Stat.hpregen, 0));
    }
  }

  @Test void lancerRetainsRunMemoryUntilTheNextMeleeDecisionWithExplicitExternalRng() throws Exception {
    AiRuntimeState image;
    try (Fixture f = fixture("CorruptLancer", 1)) {
      CorruptLancer ai = (CorruptLancer) f.ai; ai.ranToTarget = true; ai.time = .2f;
      ai.params = new int[8]; ai.params[4] = 20; image = AiRuntimeState.capture(ai, 0);
    }
    try (Fixture f = fixture("CorruptLancer", 1)) {
      java.util.Random previous = MathUtils.random;
      try {
        MathUtils.random = mock(RandomXS128.class); // Explicit test dependency, not checkpoint-restored RNG.
        image.restore(f.ai, 0); CorruptLancer ai = (CorruptLancer) f.ai;
        ai.update(.1f); assertTrue(ai.ranToTarget); assertNull(f.casting());
        ai.update(.15f); assertFalse(ai.ranToTarget); assertNotNull(f.casting());
        assertEquals("ATTACK", ai.getState());
      } finally { MathUtils.random = previous; }
    }
  }

  private static boolean hasSubscription(String type) {
    return java.util.Arrays.asList("BloodHawk", "ClawViper", "CorruptLancer", "CorruptRogue", "Goatman",
        "Mummy", "MaggotLarva", "PinHead", "PantherWoman", "Wraith", "Fetish",
        "FetishShaman", "SandRaider", "Vampire").contains(type);
  }
  @Test void legacyMachinesRejectForeignOwnersBadEnumsNonfiniteClocksAndNonBooleanRunMemory() throws Exception {
    for (AI source : new AI[] {new Baboon(0), new Bighead(0), new BloodHawk(0), new ClawViper(0),
        new CorruptLancer(0), new CorruptRogue(0), new Goatman(0),
        new Mummy(0), new MaggotLarva(0), new PinHead(0), new PantherWoman(0),
        new Wraith(0), new Fetish(0), new SandMaggot(0),
        new CorruptArcher(0), new FallenShaman(0), new FetishShaman(0), new SandLeaper(0),
        new SandRaider(0), new SkeletonBow(0), new SkeletonMage(0), new Vampire(0)}) {
      java.lang.Class<?> type = source.getClass();
      AI target = AI.findAI(0, type.getSimpleName());
      AiRuntimeStateTest.put(source, type, "time", Float.POSITIVE_INFINITY);
      AiRuntimeState.capture(source, 0).restore(target, 0);
      assertEquals(Float.POSITIVE_INFINITY, AiRuntimeStateTest.get(target, type, "time"));
      for (float invalid : new float[] {Float.NaN, Float.NEGATIVE_INFINITY}) {
        AiRuntimeStateTest.put(source, type, "time", invalid);
        assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(source, 0));
      }
      AiRuntimeStateTest.put(source, type, "time", 0f);
      for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
        AiRuntimeStateTest.put(source, type, "nextAction", invalid);
        assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(source, 0));
      }
      AiRuntimeStateTest.put(source, type, "nextAction", 0f);
      AiRuntimeStateTest.setStates(source);
      byte[] bad = AiRuntimeState.capture(source, 0).bytes();
      for (int i = 0; i < bad.length - 5; i++) {
        if (bad[i] == 'W' && bad[i + 1] == 'A' && bad[i + 2] == 'N') { bad[i] = 'X'; break; }
      }
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(bad));
      AiRuntimeState image = AiRuntimeState.capture(source, 0);
      ((CheckpointStateMachine<?>) machine(source)).setOwner(99);
      assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(source, 0));
      ((CheckpointStateMachine<?>) machine(target)).setOwner(99);
      AiRuntimeStateTest.put(target, type, "time", 17f);
      assertThrows(IllegalStateException.class, () -> image.restore(target, 0));
      assertEquals(17f, AiRuntimeStateTest.get(target, type, "time"));
    }
    byte[] bad = AiRuntimeState.capture(new CorruptLancer(0), 0).bytes(); bad[bad.length - 1] = 2;
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(bad));
  }

  private static StateMachine<Integer, ?> machine(AI ai) throws Exception {
    return (StateMachine<Integer, ?>) AiRuntimeStateTest.get(ai, ai.getClass(), "stateMachine");
  }
  static Fixture fixture(String type, float distance) {
    String rowId = null;
    for (com.riiablo.codec.excel.MonStats.Entry row : Riiablo.files.monstats) if (type.equals(row.AI)) { rowId = row.Id; break; }
    assertNotNull(rowId, type);
    Fixture f = new Fixture(rowId, 0, distance);
    f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(f.target).type =
        com.riiablo.engine.server.component.Class.Type.PLR;
    f.world.getMapper(AIWrapper.class).create(f.actor).ai = f.ai;
    f.world.process(); return f;
  }
}
