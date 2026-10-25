package com.riiablo.engine.server.ai;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.engine.replay.*;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.component.AIWrapper;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiRuntimeStateTest {
  static final List<IntFunction<AI>> SCHEMAS = Arrays.asList(QuillRat::new, Scarab::new, Fallen::new,
      Brute::new, Zombie::new, Skeleton::new, id -> new GenericMonster(id, "unknown-token"),
      Andariel::new, DoomKnight::new, FlyingScimitar::new, BaalMinion::new, ReanimatedHorde::new, PutridDefiler::new, VileDog::new,
      BaalCrab::new, Diablo::new, Mephisto::new, Duriel::new, Summoner::new, OblivionKnight::new,
      AbyssKnight::new, ArcaneTower::new, TrapMissile::new, TrapNova::new, TrapRightArrow::new,
      TrappedSoul::new, ThornHulk::new, VileMother::new, ZakarumPriest::new, ZakarumZealot::new,
      TrapPoison::new, TrapMelee::new, TrapLeftArrow::new,
      BaalCrabClone::new, BaalTaunt::new, BaalTentacle::new, BaalThrone::new,
      Tentacle::new, TentacleHead::new, SuicideMinion::new, WillOWisp::new,
      SevenTIllusion::new, Spirit::new, SandMaggotQueen::new, Sarcophagus::new,
      Ancient::new, SiegeTower::new, SiegeBeast::new, Succubus::new, SuccubusWitch::new);

  @Test void allSupportedSchemasPreserveEveryOwnedScalarAndDoNotTouchInjectedObjects() throws Exception {
    for (IntFunction<AI> factory : SCHEMAS) {
      AI source = factory.apply(12), target = factory.apply(12);
      for (Class<?> type = source.getClass(); type != Object.class; type = type.getSuperclass()) {
        int value = 57;
        for (Field f : type.getDeclaredFields()) {
          if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers()) || f.getName().equals("entityId")) continue;
          f.setAccessible(true);
          if (f.getType() == int.class) f.setInt(source, value++);
          else if (f.getType() == long.class) f.setLong(source, 9007199254740001L);
          else if (f.getType() == double.class) f.setDouble(source, 987654321.1234567);
          else if (f.getType() == float.class) f.setFloat(source, value++ + .00390625f);
          else if (f.getType() == boolean.class) f.setBoolean(source, true);
          else if (f.getType() == String.class) f.set(source, "frozen-" + f.getName());
        }
      }
      source.params = new int[] {1, -2, 3, 4, 5, 6, 7, 8};
      put(source, AI.class, "specialAiRng", NativeRng.fromSnapshot(new NativeRng.Snapshot("private-control", 12, 0, 41)));
      put(source, AI.class, "aiSeed", new D2Seed(17, -123));
      setStates(source);
      Object targetMonster = target.monster;
      AiRuntimeState image = AiRuntimeState.capture(source, 12);
      byte[] original = image.bytes(); byte[] exported = image.bytes(); exported[0] ^= 1;
      byte[] untouched = AiRuntimeState.capture(target, 12).bytes();
      image.validateRestore(target, 12);
      assertArrayEquals(untouched, AiRuntimeState.capture(target, 12).bytes(), "preflight changed " + source.getClass().getSimpleName());
      image.restore(target, 12);
      assertArrayEquals(original, AiRuntimeState.capture(target, 12).bytes(), source.getClass().getName());
      assertArrayEquals(source.params, target.params); assertNotSame(source.params, target.params);
      assertSame(targetMonster, target.monster);
      for (Class<?> type = source.getClass(); type != Object.class; type = type.getSuperclass()) {
        for (Field f : type.getDeclaredFields()) {
          if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
          if (!f.getType().isPrimitive() && f.getType() != String.class) continue;
          f.setAccessible(true);
          assertEquals(f.get(source), f.get(target), type.getSimpleName() + "." + f.getName());
        }
      }
      for (int i = 0; i < 25; i++) assertEquals(source.rollAiRandomNumber(), target.rollAiRandomNumber());
      NativeRng s = (NativeRng) get(source, AI.class, "specialAiRng"), t = (NativeRng) get(target, AI.class, "specialAiRng");
      assertNotSame(s, t); assertEquals(41, t.drawCount());
      for (int i = 0; i < 25; i++) assertEquals(s.nextInt(), t.nextInt());
      assertArrayEquals(AiRuntimeState.capture(source, 12).bytes(), AiRuntimeState.capture(target, 12).bytes());
    }
  }

  @Test void nullRngAndPositiveInfinitySentinelsAreNotTurnedIntoFreshStreams() throws Exception {
    QuillRat source = new QuillRat(0), target = new QuillRat(0);
    source.time = Float.POSITIVE_INFINITY; source.SLEEP = Float.POSITIVE_INFINITY;
    target.rollAiRandomNumber();
    put(target, AI.class, "specialAiRng", new NativeRng(3));
    AiRuntimeState.capture(source, 0).restore(target, 0);
    assertNull(get(target, AI.class, "specialAiRng")); assertNull(get(target, AI.class, "aiSeed"));
    assertEquals(Float.POSITIVE_INFINITY, target.time); assertEquals(Float.POSITIVE_INFINITY, target.SLEEP);
  }

  @Test void explicitSchemaGuardsRequireAFieldDecisionForEverySupportedClass() {
    Map<Class<?>, String> expected = new HashMap<>();
    expected.put(AI.class, "SLEEP params entityId monster monsound movementActive lastMovementRunning lastMovementVelocityBonus nextWarCryThink specialAiRng aiSeed");
    expected.put(NativeDecisionAI.class, "nextThinkFrame state dead elapsedFrames decisionCount lastDecisionTarget lastApproach");
    expected.put(QuillRat.class, "tmpVec2 stateMachine nextAction time");
    expected.put(Scarab.class, "tmpVec2 stateMachine nextAction time aiParam0");
    expected.put(Fallen.class, "monsterEntities tmpVec2 tmpVec2_2 stateMachine nextAction time aiParam0");
    expected.put(Brute.class, "enemyEntities tmpVec2 stateMachine nextAction time");
    expected.put(Zombie.class, "enemyEntities tmpVec2 stateMachine nextAction time");
    expected.put(Skeleton.class, "tmpVec2 stateMachine nextAction time targetId");
    expected.put(GenericMonster.class, "nativeAiName nextThink state");
    expected.put(ReanimatedHorde.class, "resurrectionDelay deathScheduled");
    expected.put(PutridDefiler.class, "escapePoint");
    expected.put(VileDog.class, "initialized");
    expected.put(BaalCrab.class, "anchor destination ray collision anchored targetCount maxTargetScore");
    expected.put(Diablo.class, "action actionCount");
    expected.put(Mephisto.class, "burstRemaining spellCounter phase destination ray collision");
    expected.put(Duriel.class, "holyFreezeSelected");
    expected.put(Summoner.class, "novaCooldown firewallCooldown escape");
    expected.put(OblivionKnight.class, "curseDeadline previousTarget");
    expected.put(AbyssKnight.class, "recovery");
    expected.put(ArcaneTower.class, "phase remaining nextAction");
    expected.put(TrapMissile.class, "shots needsDelay");
    expected.put(TrapNova.class, "casts needsDelay");
    expected.put(TrapRightArrow.class, "elapsedFrames nextAttackFrame");
    expected.put(TrappedSoul.class, "awakened frame nextAttackFrame");
    expected.put(ThornHulk.class, "burst recovery waitAfterFrenzy");
    expected.put(VileMother.class, "spawned");
    expected.put(ZakarumPriest.class, "frame teleportAfter blizzardAfter lightningAfter ray collision landing");
    expected.put(ZakarumZealot.class, "attackArmed");
    expected.put(TrapPoison.class, "");
    expected.put(TrapMelee.class, "");
    expected.put(TrapLeftArrow.class, "");
    expected.put(BaalCrabClone.class, "");
    expected.put(BaalTaunt.class, "neutralFrames");
    expected.put(BaalTentacle.class, "ownerId frame expireFrame");
    expected.put(BaalThrone.class, "preparedWave transformed castWave");
    expected.put(TentacleAI.class, "phase frame changeAfter");
    expected.put(Tentacle.class, "ownerId");
    expected.put(TentacleHead.class, "");
    expected.put(SuicideMinion.class, "deadline explosionFrame detonated hitRequested");
    expected.put(WillOWisp.class, "phase count indexOrDeadline");
    expected.put(SevenTIllusion.class, "attacked");
    expected.put(Spirit.class, "attacked");
    expected.put(SandMaggotQueen.class, "spawned spawnPhase");
    expected.put(Sarcophagus.class, "spawned nextSpawn");
    expected.put(Ancient.class, "");
    expected.put(SiegeTower.class, "");
    expected.put(SiegeBeast.class, "");
    expected.put(Succubus.class, "");
    expected.put(SuccubusWitch.class, "");
    for (Class<?> type : Arrays.asList(Andariel.class, DoomKnight.class, FlyingScimitar.class, BaalMinion.class, Idle.class)) expected.put(type, "");
    for (IntFunction<AI> factory : SCHEMAS) {
      for (Class<?> type = factory.apply(0).getClass(); type != Object.class; type = type.getSuperclass()) {
        assertTrue(expected.containsKey(type), "missing field policy: " + type.getName());
      }
    }
    for (Map.Entry<Class<?>, String> e : expected.entrySet()) {
      Set<String> actual = new TreeSet<>();
      for (Field f : e.getKey().getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || com.artemis.ComponentMapper.class.isAssignableFrom(f.getType())
            || com.artemis.BaseSystem.class.isAssignableFrom(f.getType())
            || Arrays.asList("serverContext", "factory", "partyManager").contains(f.getName())) continue;
        actual.add(f.getName());
      }
      Set<String> fields = new TreeSet<>(); if (!e.getValue().isEmpty()) fields.addAll(Arrays.asList(e.getValue().split(" ")));
      assertEquals(fields, actual, e.getKey().getName());
    }
  }

  @Test void unsupportedTypesAndForeignOwnerOrConfigurationCannotBeRestored() {
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(new Arach(2), 2));
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(new TrapNova(2) {}, 2));
    assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(new QuillRat(2), 1));
    AiRuntimeState image = AiRuntimeState.capture(new GenericMonster(2, "first"), 2);
    assertThrows(IllegalStateException.class, () -> image.restore(new GenericMonster(2, "second"), 2));
    assertThrows(IllegalStateException.class, () -> image.restore(new QuillRat(2), 2));
    assertThrows(IllegalStateException.class, () -> image.restore(new GenericMonster(3, "first"), 3));
    assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(new Idle(), 2));
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(new QuillRat(2) {}, 2));
  }

  @Test void malformedImagesAndNonfiniteValuesFailBeforeChangingTheHost() {
    QuillRat source = new QuillRat(0), target = new QuillRat(0); target.time = 123;
    byte[] image = AiRuntimeState.capture(source, 0).bytes();
    for (int i = 0; i < image.length; i++) {
      byte[] cut = Arrays.copyOf(image, i); assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(cut));
    }
    byte[] bad = image.clone(); bad[3] = 2;
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(bad));
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(Arrays.copyOf(image, image.length + 1)));
    assertEquals(123, target.time);
    source.time = Float.NaN; assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(source, 0));
    source.time = Float.NEGATIVE_INFINITY; assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(source, 0));
    source.time = 0; source.params = new int[9]; assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(source, 0));
    source.params = new int[8]; source.stateMachine.setGlobalState(null);
    ((CheckpointStateMachine<QuillRat.State>) source.stateMachine).restore(null, null, null);
    AiRuntimeState.capture(source, 0).restore(target, 0); assertNull(target.stateMachine.getCurrentState());
  }

  @Test void allHostsArePreflightedAndLegacyV3DoesNotClaimAnAiImage() {
    try (WorldFixture source = new WorldFixture(); WorldFixture target = new WorldFixture()) {
      source.wrapper(0).ai = new QuillRat(0); source.wrapper(1).ai = new Scarab(1);
      target.wrapper(0).ai = new QuillRat(0); target.wrapper(1).ai = new Fallen(1);
      ((QuillRat) source.wrapper(0).ai).time = 9;
      Map<Integer, UnitStateRuntimeCheckpoint> records = UnitStateRuntimeCheckpoint.capture(source.world, new int[] {0, 1});
      assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.restore(target.world, records));
      assertEquals(0, ((QuillRat) target.wrapper(0).ai).time);
      assertThrows(IllegalStateException.class, () -> UnitStateRuntimeCheckpoint.capture(source.world, new int[] {0}));
      byte[] current = records.get(0).bytes();
      byte[] legacy = Arrays.copyOf(current, current.length - 4 - records.get(0).aiDecisionRuntime().bytes().length); legacy[3] = 3;
      UnitStateRuntimeCheckpoint uncovered = UnitStateRuntimeCheckpoint.fromBytes(legacy);
      assertNull(uncovered.aiDecisionRuntime());
      UnitStateRuntimeCheckpoint.restore(target.world, Collections.singletonMap(0, uncovered));
      assertEquals(0, ((QuillRat) target.wrapper(0).ai).time);
      records.get(0).aiDecisionRuntime().restore(target.world, 0);
      assertEquals(9, ((QuillRat) target.wrapper(0).ai).time);
      assertThrows(IllegalArgumentException.class, () -> records.get(0).validateAiHost(1));
    }
  }

  @Test void wrapperAbsenceNullAndSharedIdleAreDistinctAndNeverMutateTheSentinel() {
    try (WorldFixture source = new WorldFixture(); WorldFixture target = new WorldFixture()) {
      AiDecisionRuntimeCheckpoint absent = AiDecisionRuntimeCheckpoint.capture(source.world, 0);
      source.wrapper(0).ai = null; AiDecisionRuntimeCheckpoint nil = AiDecisionRuntimeCheckpoint.capture(source.world, 0);
      source.wrapper(0).ai = AI.IDLE; AiDecisionRuntimeCheckpoint idle = AiDecisionRuntimeCheckpoint.capture(source.world, 0);
      assertFalse(Arrays.equals(absent.bytes(), nil.bytes())); assertFalse(Arrays.equals(nil.bytes(), idle.bytes()));
      absent.restore(target.world, 0);
      assertThrows(IllegalStateException.class, () -> nil.restore(target.world, 0));
      target.wrapper(0).ai = null; nil.restore(target.world, 0);
      assertThrows(IllegalStateException.class, () -> idle.restore(target.world, 0));
      target.wrapper(0).ai = AI.IDLE; idle.restore(target.world, 0); assertSame(AI.IDLE, target.wrapper(0).ai);
    }
  }

  @Test void fsmRestorePreservesRevertHistoryWithoutAnyEnterExitCallbacks() {
    CheckpointStateMachine<Observed> machine = new CheckpointStateMachine<>(7, Observed.FIRST);
    Observed.callbacks = 0;
    machine.restore(Observed.SECOND, Observed.FIRST, Observed.SECOND);
    assertEquals(0, Observed.callbacks); assertEquals(7, machine.getOwner());
    assertEquals(Observed.SECOND, machine.getCurrentState()); assertEquals(Observed.FIRST, machine.getPreviousState());
    assertEquals(Observed.SECOND, machine.getGlobalState());
    assertTrue(machine.revertToPreviousState()); assertEquals(Observed.FIRST, machine.getCurrentState());
    assertEquals(2, Observed.callbacks);
  }

  @Test void nestedWrapperImagesRejectTruncationBadKindsAndInvalidStateNames() {
    try (WorldFixture f = new WorldFixture()) {
      f.wrapper(0).ai = new QuillRat(0);
      byte[] bytes = AiDecisionRuntimeCheckpoint.capture(f.world, 0).bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> AiDecisionRuntimeCheckpoint.fromBytes(truncated));
      }
      byte[] bad = bytes.clone(); bad[4] = 3;
      assertThrows(IllegalArgumentException.class, () -> AiDecisionRuntimeCheckpoint.fromBytes(bad));
      assertThrows(IllegalArgumentException.class, () -> AiDecisionRuntimeCheckpoint.fromBytes(Arrays.copyOf(bytes, bytes.length + 1)));
      byte[] ai = AiRuntimeState.capture(f.wrapper(0).ai, 0).bytes();
      for (int i = 0; i < ai.length - 3; i++) if (ai[i] == 'I' && ai[i + 1] == 'D' && ai[i + 2] == 'L' && ai[i + 3] == 'E') {
        ai[i] = 'X'; break;
      }
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(ai));
    }
  }

  @Test void stateMachineOwnerCannotSilentlyDriftFromItsHost() {
    QuillRat source = new QuillRat(2), target = new QuillRat(2);
    AiRuntimeState image = AiRuntimeState.capture(source, 2);
    ((CheckpointStateMachine<?>) source.stateMachine).setOwner(17);
    assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(source, 2));
    ((CheckpointStateMachine<?>) target.stateMachine).setOwner(17); target.time = 37;
    assertThrows(IllegalStateException.class, () -> image.restore(target, 2));
    assertEquals(37, target.time);
  }

  @Test void bossAnchorIsHistoryRatherThanScratchAndKeepsTheTargetVectorIdentity() throws Exception {
    for (boolean clone : new boolean[] {false, true}) {
      BaalCrab source = clone ? new BaalCrabClone(4) : new BaalCrab(4);
      BaalCrab target = clone ? new BaalCrabClone(4) : new BaalCrab(4);
      com.badlogic.gdx.math.Vector2 anchor = (com.badlogic.gdx.math.Vector2) get(source, BaalCrab.class, "anchor");
      anchor.set(14.125f, -6.5f); put(source, BaalCrab.class, "anchored", true);
      Object targetAnchor = get(target, BaalCrab.class, "anchor");
      AiRuntimeState.capture(source, 4).restore(target, 4);
      assertSame(targetAnchor, get(target, BaalCrab.class, "anchor"));
      assertNotSame(anchor, targetAnchor); assertEquals(anchor, targetAnchor);
      assertEquals(true, get(target, BaalCrab.class, "anchored"));
      anchor.x = Float.NaN;
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(source, 4));
    }
  }

  @Test void everyExtendedSchemaRejectsEachTruncationAndNonfiniteLocalClocks() throws Exception {
    for (IntFunction<AI> factory : SCHEMAS) {
      AI source = factory.apply(3); byte[] bytes = AiRuntimeState.capture(source, 3).bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] truncated = Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(truncated), source.getClass().getSimpleName());
      }
      for (Class<?> type = source.getClass(); type != AI.class; type = type.getSuperclass()) {
        for (Field f : type.getDeclaredFields()) {
          if (f.getType() != double.class || Modifier.isStatic(f.getModifiers())) continue;
          f.setAccessible(true); double saved = f.getDouble(source);
          for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            f.setDouble(source, bad);
            assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(source, 3));
          }
          f.setDouble(source, saved);
        }
      }
    }
  }

  @Test void tentacleUninitializedLifetimeIsExplicitAndDoesNotRearmAnExistingClock() throws Exception {
    BaalTentacle source = new BaalTentacle(3), target = new BaalTentacle(3);
    put(target, BaalTentacle.class, "expireFrame", 175f);
    byte[] unset = AiRuntimeState.capture(source, 3).bytes();
    AiRuntimeState image = AiRuntimeState.fromBytes(unset);
    image.validateRestore(target, 3);
    assertEquals(175f, get(target, BaalTentacle.class, "expireFrame"));
    image.restore(target, 3);
    assertTrue(Float.isNaN((Float) get(target, BaalTentacle.class, "expireFrame")));
    assertNull(get(source, AI.class, "aiSeed"));
    assertNull(get(target, AI.class, "aiSeed"));
    byte[] badKind = unset.clone(); badKind[badKind.length - 1] = 2;
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(badKind));
    put(source, BaalTentacle.class, "expireFrame", 175.125f);
    image = AiRuntimeState.capture(source, 3); image.restore(target, 3);
    assertEquals(175.125f, get(target, BaalTentacle.class, "expireFrame"));
    byte[] badClock = image.bytes();
    java.nio.ByteBuffer.wrap(badClock).putInt(badClock.length - 4, Float.floatToRawIntBits(Float.NaN));
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(badClock));
    for (float invalid : new float[] {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      put(source, BaalTentacle.class, "expireFrame", invalid);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(source, 3));
    }
    for (String field : new String[] {"frame", "nextSpawn"}) {
      AI clock = field.equals("frame") ? new BaalTentacle(3) : new Sarcophagus(3);
      for (float invalid : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
        put(clock, clock.getClass(), field, invalid);
        assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(clock, 3));
      }
    }
  }

  enum Observed implements com.badlogic.gdx.ai.fsm.State<Integer> {
    FIRST, SECOND;
    static int callbacks;
    @Override public void enter(Integer owner) { callbacks++; }
    @Override public void exit(Integer owner) { callbacks++; }
    @Override public void update(Integer owner) {}
    @Override public boolean onMessage(Integer owner, com.badlogic.gdx.ai.msg.Telegram message) { return false; }
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) static void setStates(AI ai) throws Exception {
    Field field;
    try { field = ai.getClass().getDeclaredField("stateMachine"); } catch (NoSuchFieldException notFsm) { return; }
    field.setAccessible(true); StateMachine machine = (StateMachine) field.get(ai);
    Class<?> type = machine.getCurrentState().getClass();
    Object[] states = type.getEnumConstants(); machine.changeState((com.badlogic.gdx.ai.fsm.State) states[states.length - 1]);
    machine.changeState((com.badlogic.gdx.ai.fsm.State) states[0]); machine.setGlobalState((com.badlogic.gdx.ai.fsm.State) states[1]);
  }
  static Object get(AI ai, Class<?> type, String name) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(ai); }
  static void put(AI ai, Class<?> type, String name, Object value) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(ai, value); }
  static final class WorldFixture implements AutoCloseable {
    final World world = new World(new WorldConfigurationBuilder().build());
    WorldFixture() { world.create(); world.create(); world.process(); }
    AIWrapper wrapper(int id) { return world.getMapper(AIWrapper.class).create(id); }
    @Override public void close() { world.dispose(); }
  }
}
