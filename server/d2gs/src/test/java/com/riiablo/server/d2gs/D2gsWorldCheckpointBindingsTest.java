package com.riiablo.server.d2gs;

import static org.junit.Assert.*;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.AuthoritativeSimulation;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.engine.server.PotionRecoverySystem;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.component.NativeAiState;
import com.riiablo.engine.server.component.NativeAiTargetOverride;
import com.riiablo.engine.server.component.NativeAiCommand;
import com.riiablo.engine.server.component.NativeAiPathCache;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.NativeConversionLifecycle;
import com.riiablo.engine.server.NativePregnancyLifecycle;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.engine.server.portal.TownPortalRegistry;
import com.riiablo.map.Map;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SCheckpoint;
import com.riiablo.save.D2SWriter96;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real 1.10f player/factory/D2S and live entity RNG bindings, not a full-map socket gate. */
public class D2gsWorldCheckpointBindingsTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  @BeforeClass public static void loadResources() { ServerEntityLifecycleTest.load110f(); }
  @AfterClass public static void unloadResources() { ServerEntityLifecycleTest.unload110f(); }

  @Test public void monsterBirthRollsComponentsThenInclusiveHpFromItsNativeSeedNotGlobalRandom() {
    try (Fixture source = new Fixture(); Fixture other = new Fixture()) {
      for (String row : new String[] {"quillrat1", "doomknight2", "doomknight3", "painworm1"}) {
        com.riiablo.codec.excel.MonStats.Entry data = Riiablo.files.monstats.get(row);
        int id = source.factory.createMonster(data, 20f, 10f);
        for (int i = 0; i < 31; i++) com.badlogic.gdx.math.MathUtils.random.nextLong();
        assertEquals(id, other.factory.createMonster(data, 20f, 10f));
        Monster monster = source.server.world.getMapper(Monster.class).get(id);
        com.d2moo.common.drlg.D2Seed expected = new com.d2moo.common.drlg.D2Seed(
            com.riiablo.engine.server.NativeRng.forUnit(1, id).state(), 666);
        int[] components = new int[16];
        for (int slot = 0; slot < monster.monstats2.ComponentV.length; slot++) {
          String values = monster.monstats2.ComponentV[slot].replace("\"", "");
          if (!values.isEmpty()) components[slot] = com.d2moo.common.seed.Seed.rollLimitedRandomNumber(
              expected, values.split(",").length);
        }
        if (row.startsWith("doomknight")) {
          components[10] = (int) (com.d2moo.common.seed.Seed.rollRandomNumber(expected) & 3);
          if (row.equals("doomknight3")) components[11] = components[10];
        }
        int level = data.Level[0]; // Normal difficulty, no generated area-level override.
        com.riiablo.engine.server.combat.MonsterStatsSnapshot stats =
            com.riiablo.engine.server.MonsterStatsCalculator.spawnSnapshot(Riiablo.files, data, 1, 0, level, 0, 0);
        int baseHp = stats.baseMinHp + com.d2moo.common.seed.Seed.rollLimitedRandomNumber(
            expected, stats.baseMaxHp - stats.baseMinHp + 1);
        assertArrayEquals(components, monster.componentVariants);
        assertEquals(stats.hitpointsForBaseRoll(baseHp), source.server.world.getMapper(AttributesWrapper.class)
            .get(id).attrs.get(Stat.hitpoints).asInt());
        assertEquals(expected.getNLowSeed(), monster.nativeSeed.getNLowSeed());
        assertEquals(expected.getNHighSeed(), monster.nativeSeed.getNHighSeed());
        assertArrayEquals(monster.componentVariants, other.server.world.getMapper(Monster.class).get(id).componentVariants);
        assertEquals(monster.rngState, other.server.world.getMapper(Monster.class).get(id).rngState);
      }
    }
  }

  @Test public void targetRegistryAndTypedRemovalLifecyclesResumeAcrossJvmRestart() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("targets.chk");
    String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateTargets();
      source.server.writeReplayCheckpoint(path);
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      assertTrue(archive.targetRuntime().hasAiControlRuntime());
      try { archive.withTargetRuntime(null); fail("active state membership lost its TARGETS owner"); }
      catch (IllegalArgumentException correct) {}
      byte[] bytes = archive.toBytes(), targetBytes = archive.targetRuntime().bytes();
      int offset = -1;
      for (int i = 0; i <= bytes.length - targetBytes.length; i++) {
        if (java.util.Arrays.equals(targetBytes, java.util.Arrays.copyOfRange(bytes, i, i + targetBytes.length))) {
          offset = i; break;
        }
      }
      assertTrue(offset >= 0); bytes[offset + 11] += 1; // Valid clock, invalid canonical witness.
      try { D2gsCheckpointSnapshot.fromBytes(bytes); fail("TARGETS witness corruption accepted"); }
      catch (IllegalArgumentException correct) {}
      expected = continueTargets(source);
    }
    assertEquals(expected, resumeTargets(path));
    Path output = temporary.getRoot().toPath().resolve("targets-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "targets").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("target recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  private static String resumeTargets(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      assertEquals(archive.d2sCheckpoint().canonical(), target.server.captureReplayCheckpoint().d2sCheckpoint().canonical());
      return continueTargets(target);
    }
  }

  @Test public void fullMonsterRuntimeResumesInPrebuiltMapAcrossWorldAndJvmRestart() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("monsters.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateMonsters(); source.server.writeReplayCheckpoint(path);
      expected = continueMonsters(source);
    }
    assertEquals(expected, resumeMonsters(path));
    Path output = temporary.getRoot().toPath().resolve("monsters-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "monsters").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("monster recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  @Test public void monsterOnlyChangesAlterCanonicalAndNestedCorruptionIsRejected() {
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateMonsters();
      D2gsCheckpointSnapshot before = source.server.captureReplayCheckpoint();
      Monster live = source.server.world.getMapper(Monster.class).get(1);
      for (int field = 0; field < 3; field++) {
        if (field == 0) live.componentVariants[0] += 1;
        else if (field == 1) live.chargedBoltsCooldown += 0.25f;
        else live.modeElements.drainMinimum[0] += 1;
        D2gsCheckpointSnapshot changed = source.server.captureReplayCheckpoint();
        assertNotEquals(before.summary().canonical(), changed.summary().canonical());
        for (int id : before.payloads().keySet()) assertArrayEquals(before.payloads().get(id), changed.payloads().get(id));
        assertEquals(before.d2sCheckpoint().canonical(), changed.d2sCheckpoint().canonical());
        assertEquals(before.summary().nativeUnitRngStates(), changed.summary().nativeUnitRngStates());
        before = changed;
      }
      byte[] encoded = before.toBytes(), nested = before.unitStateRuntime().get(1).monsterRuntime().bytes();
      int offset = -1;
      for (int i = 0; i <= encoded.length - nested.length; i++) {
        if (java.util.Arrays.equals(nested, java.util.Arrays.copyOfRange(encoded, i, i + nested.length))) { offset = i; break; }
      }
      assertTrue(offset >= 0);
      encoded[offset + nested.length - 22] ^= 1; // A valid raw scalar, but not the canonical witness.
      try { D2gsCheckpointSnapshot.fromBytes(encoded); fail("nested Monster corruption accepted"); }
      catch (IllegalArgumentException correct) {}
    }
  }

  @Test public void decisionObjectResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("decisions.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateDecisionAi(); source.server.writeReplayCheckpoint(path);
      expected = continueDecisions(source);
    }
    assertEquals(expected, resumeDecisions(path));
    Path output = temporary.getRoot().toPath().resolve("decisions-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "decisions").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("AI recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  @Test public void privateAiOnlyChangeAltersCanonicalAndLateFailureRestoresDecisionPhase() throws Exception {
    for (String row : new String[] {"doomknight1", "trap-firebolt", spiritRow(), rowForAi("BatDemon"), "gargoyletrap", "regurgitator1", "baboon1", "sandmaggot1", rowForAi("SandLeaper")}) {
      try (Fixture source = new Fixture(); Fixture target = new Fixture(); Fixture empty = new Fixture(); Fixture control = new Fixture()) {
        source.populate();
        if (row.equals(rowForAi("BatDemon"))) source.populateRegeneratingBatAi();
        else if (row.equals("gargoyletrap")) source.populateRecoveringGargoyleAi();
        else if (row.equals("regurgitator1")) source.populateFoodAi(1);
        else if (row.equals("baboon1")) source.populateLegacyBaboonAi();
        else if (row.equals("sandmaggot1")) source.populateBurrowedSandMaggotAi();
        else if (row.equals(rowForAi("SandLeaper"))) source.populateRecoveringSandLeaperAi();
        else source.populateDecisionAi(row);
        D2gsCheckpointSnapshot before = source.server.captureReplayCheckpoint();
        source.updateDecisionAi(0.013f); // Still before the pending absolute think event; only private elapsed time changes.
        D2gsCheckpointSnapshot changed = source.server.captureReplayCheckpoint();
        assertNotEquals(before.summary().canonical(), changed.summary().canonical());
        for (int id : before.payloads().keySet()) assertArrayEquals(before.payloads().get(id), changed.payloads().get(id));
        assertEquals(before.summary().nativeUnitRngStates(), changed.summary().nativeUnitRngStates());
        assertEquals(before.d2sCheckpoint().canonical(), changed.d2sCheckpoint().canonical());
        byte[] encoded = changed.toBytes(), nested = changed.unitStateRuntime().get(1).aiDecisionRuntime().bytes();
        int offset = -1;
        for (int i = 0; i <= encoded.length - nested.length; i++) {
          if (java.util.Arrays.equals(nested, java.util.Arrays.copyOfRange(encoded, i, i + nested.length))) { offset = i; break; }
        }
        assertTrue(offset >= 0);
        int privateByte = nested.length - 1;
        if (row.equals(rowForAi("SandLeaper"))) {
          // The FSM tail is a nullable enum, not an arbitrary scalar. Alter its finite timer instead.
          privateByte = -1;
          for (int i = 0; i <= nested.length - 4; i++)
            if (ByteBuffer.wrap(nested).getInt(i) == Float.floatToRawIntBits(.35f - .013f)) { privateByte = i + 3; break; }
          assertTrue(privateByte >= 0);
        }
        nested[privateByte] ^= 1;
        com.riiablo.engine.replay.AiDecisionRuntimeCheckpoint.fromBytes(nested); // Valid nested payload; the outer witness must reject the change.
        encoded[offset + privateByte] ^= 1;
        try { D2gsCheckpointSnapshot.fromBytes(encoded); fail("private AI corruption accepted"); }
        catch (IllegalArgumentException correct) {}
        assertTrue(target.server.restoreReplayCheckpoint(changed).success());
        assertTrue(control.server.restoreReplayCheckpoint(changed).success());
        Object liveAi = target.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
        CharData impostor = CharData.loadFromBuffer(0, ByteBuffer.wrap(before.d2sCheckpoint().entry(0).bytes()));
        impostor.name = "ImpostorHero";
        D2SCheckpoint wrong = D2SCheckpoint.of(java.util.Collections.singletonMap(0,
            D2SCheckpoint.entry(0, 0, new D2SWriter96().writeD2S(D2SWriter96.createD2S(impostor)))));
        D2gsCheckpointSnapshot incoming = D2gsCheckpointSnapshot.fromPayloads(before.tick(), before.payloads(),
            before.summary().rngStates(), before.summary().nativeUnitRngStates(), wrong, before.sessionSnapshot())
            .withTargetRuntime(before.targetRuntime()).withUnitStateRuntime(before.unitStateRuntime())
            .withAllocatorState(before.allocatorState());
        for (Fixture f : new Fixture[] {target, empty}) {
          D2gsCheckpointSnapshot previous = f.server.captureReplayCheckpoint();
          try { f.server.restoreReplayCheckpoint(incoming); fail("late D2S failure must restore private AI phase"); }
          catch (IllegalStateException expected) {}
          assertEquals(previous.summary().canonical(), f.server.captureReplayCheckpoint().summary().canonical());
        }
        assertSame(liveAi, target.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai);
        if (row.equals("sandmaggot1")) assertEquals(continueSandMaggot(control), continueSandMaggot(target));
        else if (row.equals(rowForAi("SandLeaper"))) assertEquals(continueSandLeaper(control), continueSandLeaper(target));
        else assertEquals(continueDecisions(control), continueDecisions(target));
      }
    }
  }

  @Test public void spentTrapBudgetResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("trap-decisions.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateDecisionAi("trap-firebolt");
      assertEquals("ATTACK", source.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai.getState());
      assertEquals("TrapMissile", source.server.captureReplayCheckpoint().unitStateRuntime().get(1).aiDecisionRuntime().implementation());
      source.server.writeReplayCheckpoint(path);
      expected = continueDecisions(source, 100);
      assertTrue(expected.contains("decisions=2"));
    }
    assertEquals(expected, resumeTrapDecisions(path));
    Path output = temporary.getRoot().toPath().resolve("trap-decisions-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "trap-decisions").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("trap recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  @Test public void spentSpiritAttackResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("spirit-decisions.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateDecisionAi(spiritRow());
      assertEquals("ATTACK", source.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai.getState());
      assertEquals("Spirit", source.server.captureReplayCheckpoint().unitStateRuntime().get(1).aiDecisionRuntime().implementation());
      source.server.writeReplayCheckpoint(path);
      expected = continueDecisions(source, 120, true);
    }
    assertEquals(expected, resumeSpiritDecisions(path));
    Path output = temporary.getRoot().toPath().resolve("spirit-decisions-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "spirit-decisions").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("spirit recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  private static String spiritRow() {
    return rowForAi("Spirit");
  }

  private static String rowForAi(String ai) {
    for (com.riiablo.codec.excel.MonStats.Entry row : Riiablo.files.monstats)
      if (ai.equals(row.AI)) return row.Id;
    throw new AssertionError("Missing AI row: " + ai);
  }

  @Test public void roostingBatRegenerationResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("bat-decisions.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateRegeneratingBatAi();
      assertEquals("BatDemon", source.server.captureReplayCheckpoint().unitStateRuntime().get(1).aiDecisionRuntime().implementation());
      source.server.writeReplayCheckpoint(path);
      expected = continueBatDecisions(source);
    }
    assertEquals(expected, resumeBatDecisions(path));
    Path output = temporary.getRoot().toPath().resolve("bat-decisions-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "bat-decisions").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("bat recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  @Test public void deathRemovesRestoredRoostingRegenBonusOnlyOnce() throws Exception {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateRegeneratingBatAi();
      D2gsCheckpointSnapshot image = source.server.captureReplayCheckpoint();
      assertTrue(target.server.restoreReplayCheckpoint(image).success());
      World previous = Riiablo.engine;
      try {
        Riiablo.engine = target.server.world;
        com.riiablo.engine.server.ai.AI ai = target.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
        com.riiablo.attributes.Attributes attrs = target.server.world.getMapper(AttributesWrapper.class).get(1).attrs;
        assertEquals(120, attrs.base().getValue(Stat.hpregen, 0));
        ai.kill(); assertEquals(80, attrs.base().getValue(Stat.hpregen, 0));
        assertEquals(80, attrs.getValue(Stat.hpregen, 0));
        ai.kill(); assertEquals(80, attrs.base().getValue(Stat.hpregen, 0));
        assertEquals(80, attrs.getValue(Stat.hpregen, 0));
      } finally { Riiablo.engine = previous; }
    }
  }

  @Test public void gargoyleRecoveryResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("gargoyle-decisions.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateRecoveringGargoyleAi();
      assertEquals("GargoyleTrap", source.server.captureReplayCheckpoint().unitStateRuntime().get(1).aiDecisionRuntime().implementation());
      source.server.writeReplayCheckpoint(path);
      expected = continueGargoyleDecisions(source);
    }
    assertEquals(expected, resumeGargoyleDecisions(path));
    Path output = temporary.getRoot().toPath().resolve("gargoyle-decisions-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "gargoyle-decisions").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("gargoyle recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  @Test public void corpseReferencesResumeAcrossFactoryWorldAndIndependentJvmWithoutRebindingRetiredFood() throws Exception {
    for (int lifetime = 0; lifetime < 3; lifetime++) {
      Path path = temporary.getRoot().toPath().resolve("food-" + lifetime + ".chk"); String expected;
      try (Fixture source = new Fixture()) {
        source.populate(); source.populateFoodAi(lifetime);
        source.server.writeReplayCheckpoint(path); expected = continueFoodDecisions(source, lifetime);
      }
      assertEquals(expected, resumeFoodDecisions(path, lifetime));
      Path output = temporary.getRoot().toPath().resolve("food-" + lifetime + "-child.log");
      Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
          System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
          Riiablo.home.path(), "food-decisions-" + lifetime).redirectErrorStream(true).redirectOutput(output.toFile()).start();
      try {
        assertTrue("food recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
        String log = Files.readString(output, StandardCharsets.UTF_8);
        assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
      } finally { if (child.isAlive()) child.destroyForcibly(); }
    }
  }

  @Test public void aiReferenceCoverageIsValidatedByTheOuterCheckpointNotOnlyTheRestoreAdapter() throws Exception {
    for (int lifetime = 0; lifetime < 3; lifetime++) {
      try (Fixture source = new Fixture()) {
        source.populate(); source.populateFoodAi(lifetime);
        D2gsCheckpointSnapshot image = source.server.captureReplayCheckpoint();
        java.util.Map<Integer, com.riiablo.engine.replay.UnitStateRuntimeCheckpoint> records =
            new java.util.TreeMap<>(image.unitStateRuntime());
        byte[] bytes = records.get(1).bytes();
        // Food reference is the last ID/kind pair in this explicit Regurgitator schema.
        ByteBuffer.wrap(bytes).putInt(bytes.length - 5, 999);
        records.put(1, com.riiablo.engine.replay.UnitStateRuntimeCheckpoint.fromBytes(bytes));
        if (lifetime == 2) assertNotEquals(image.summary().canonical(), image.withUnitStateRuntime(records).summary().canonical());
        else {
          try { image.withUnitStateRuntime(records); fail("out-of-scope actionable food reference accepted"); }
          catch (IllegalArgumentException expected) {}
        }
      }
    }
  }

  private static String resumeFoodDecisions(Path path, int lifetime) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueFoodDecisions(target, lifetime);
    }
  }

  @Test public void legacyBaboonRegenerationResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("legacy-baboon.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateLegacyBaboonAi();
      source.server.writeReplayCheckpoint(path); expected = continueLegacyBaboon(source);
    }
    assertEquals(expected, resumeLegacyBaboon(path));
    Path output = temporary.getRoot().toPath().resolve("legacy-baboon-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "legacy-baboon").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("legacy Baboon child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  private static String resumeLegacyBaboon(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueLegacyBaboon(target);
    }
  }

  private static String continueLegacyBaboon(Fixture f) throws Exception {
    com.riiablo.engine.server.ai.AI ai = f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
    assertEquals("REGEN", ai.getState());
    StringBuilder result = new StringBuilder("LEGACY_BABOON_CONTINUED"); int actions = 0;
    for (int frame = 0; frame < 60; frame++) {
      f.server.simulation.tick(null, null); boolean action = f.updateDecisionAi(.04f);
      if (action) actions++;
      com.riiablo.attributes.Attributes attrs = f.server.world.getMapper(AttributesWrapper.class).get(1).attrs;
      assertEquals(frame < 8 ? 51 : 40, attrs.base().getValue(Stat.hpregen, 0));
      assertEquals(frame < 8 ? 51 : 40, attrs.getValue(Stat.hpregen, 0));
      D2gsCheckpointSnapshot after = f.server.captureReplayCheckpoint();
      result.append('|').append(ai.getState()).append(':').append(action)
          .append(':').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    assertTrue("legacy Baboon never resumed its melee decisions", actions > 0);
    return result.toString();
  }

  @Test public void sandMaggotBurrowResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("sand-maggot.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateBurrowedSandMaggotAi();
      source.server.writeReplayCheckpoint(path); expected = continueSandMaggot(source);
    }
    assertEquals(expected, resumeSandMaggot(path));
    Path output = temporary.getRoot().toPath().resolve("sand-maggot-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "sand-maggot").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("Sand Maggot child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  private static String resumeSandMaggot(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueSandMaggot(target);
    }
  }

  private static int sandMaggotParam(Fixture f, String name) throws Exception {
    Object ai = f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
    java.lang.reflect.Field field = ai.getClass().getDeclaredField(name); field.setAccessible(true);
    return field.getInt(ai);
  }

  private static String continueSandMaggot(Fixture f) throws Exception {
    com.riiablo.engine.server.ai.AI ai = f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
    assertEquals("BURROW", ai.getState()); assertEquals(3, sandMaggotParam(f, "aiParam0"));
    StringBuilder result = new StringBuilder("SAND_MAGGOT_CONTINUED"); int actions = 0;
    boolean unburrowed = false, reburrowed = false;
    for (int frame = 0; frame < 60; frame++) {
      // After the accepted unburrow, remove proximity attack/RNG from this controlled trajectory.
      if (unburrowed) f.server.world.getMapper(Position.class).get(0).position.set(50f, 10f);
      f.server.simulation.tick(null, null); boolean action = f.updateDecisionAi(.04f);
      if (action) {
        actions++;
        if (!unburrowed) {
          assertEquals(8, frame); assertEquals("UNBURROW", ai.getState());
          assertEquals(1, sandMaggotParam(f, "aiParam0")); unburrowed = true;
        } else {
          assertEquals("BURROW", ai.getState()); assertEquals(3, sandMaggotParam(f, "aiParam0"));
          reburrowed = true;
        }
      }
      assertEquals(47, sandMaggotParam(f, "aiParam1")); assertEquals(2, sandMaggotParam(f, "aiParam2"));
      D2gsCheckpointSnapshot after = f.server.captureReplayCheckpoint();
      result.append('|').append(ai.getState()).append(':').append(action)
          .append(':').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    assertTrue(unburrowed); assertTrue(reburrowed); assertEquals(2, actions);
    return result.toString();
  }

  @Test public void legacyRangedFactoryRestoresValidateAndRetainMissileBindings() throws Exception {
    for (String type : new String[] {"CorruptArcher", "SkeletonBow", "SkeletonMage"}) {
      try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
        source.populate(); source.createDecisionAi(rowForAi(type));
        D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
        assertTrue(target.server.restoreReplayCheckpoint(archive).success());
        Object ai = target.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
        java.lang.reflect.Field field = ai.getClass().getDeclaredField("missile"); field.setAccessible(true);
        Object bound = field.get(ai);
        assertNotNull(type + " real table missile binding", bound);
        assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
        assertTrue(target.server.restoreReplayCheckpoint(archive).success());
        assertSame(ai, target.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai);
        assertSame(bound, field.get(ai));
      }
    }
  }

  @Test public void sandLeaperMeleeResumesAcrossFactoryWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("sand-leaper.chk"); String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateRecoveringSandLeaperAi();
      source.server.writeReplayCheckpoint(path); expected = continueSandLeaper(source);
    }
    assertEquals(expected, resumeSandLeaper(path));
    Path output = temporary.getRoot().toPath().resolve("sand-leaper-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "sand-leaper").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("Sand Leaper child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue()); assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  private static String resumeSandLeaper(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueSandLeaper(target);
    }
  }

  private static String continueSandLeaper(Fixture f) {
    com.riiablo.engine.server.ai.AI ai = f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
    assertEquals("ATTACK", ai.getState());
    StringBuilder result = new StringBuilder("SAND_LEAPER_CONTINUED"); int actions = 0;
    for (int frame = 0; frame < 60; frame++) {
      f.server.simulation.tick(null, null); boolean action = f.updateDecisionAi(.04f);
      if (frame < 8) assertFalse(action);
      if (action) actions++;
      assertEquals("ATTACK", ai.getState());
      D2gsCheckpointSnapshot after = f.server.captureReplayCheckpoint();
      result.append('|').append(ai.getState()).append(':').append(action)
          .append(':').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    assertTrue("Sand Leaper did not resume its native RNG melee branch", actions > 1);
    return result.toString();
  }

  private static Object foodField(Fixture f, String name) throws Exception {
    java.lang.reflect.Field field = com.riiablo.engine.server.ai.Regurgitator.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai);
  }

  private static String continueFoodDecisions(Fixture f, int lifetime) throws Exception {
    assertEquals(3, foodField(f, "phase")); assertEquals(2, foodField(f, "food"));
    NativePathIdentity.Token token = (NativePathIdentity.Token) foodField(f, "foodIdentity");
    if (lifetime == 0) assertNull(token);
    else assertEquals(lifetime == 1, token.isCurrent(f.server.world, 2));
    StringBuilder result = new StringBuilder("FOOD_DECISIONS_CONTINUED_" + lifetime);
    boolean observedFirstTransition = false;
    for (int frame = 0; frame < 60; frame++) {
      f.server.simulation.tick(null, null);
      boolean action = f.updateDecisionAi(0.04f);
      int phase = (Integer) foodField(f, "phase");
      if (!observedFirstTransition && phase != 3) {
        observedFirstTransition = true;
        assertEquals(lifetime == 2 ? 0 : 4, phase); assertEquals(lifetime != 2, action);
        if (lifetime == 2) assertEquals(-1, foodField(f, "food"));
      }
      D2gsCheckpointSnapshot after = f.server.captureReplayCheckpoint();
      result.append('|').append(phase).append(':').append(action)
          .append(':').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    assertTrue("food phase never resumed", observedFirstTransition);
    return result.toString();
  }

  private static String resumeGargoyleDecisions(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueGargoyleDecisions(target);
    }
  }

  private static String continueGargoyleDecisions(Fixture f) {
    StringBuilder result = new StringBuilder("GARGOYLE_DECISIONS_CONTINUED"); int shots = 0;
    for (int frame = 0; frame < 60; frame++) {
      f.server.simulation.tick(null, null);
      boolean action = f.updateDecisionAi(0.04f);
      if (frame < 18) assertFalse("gargoyle fired before its remaining recovery", action);
      if (action) shots++;
      com.riiablo.engine.server.ai.AI ai = f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
      D2gsCheckpointSnapshot after = f.server.captureReplayCheckpoint();
      result.append('|').append(action).append(':').append(ai.getState())
          .append(':').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    assertTrue("gargoyle never left recovery", shots > 0);
    return result.toString();
  }

  private static String resumeBatDecisions(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueBatDecisions(target);
    }
  }

  private static String continueBatDecisions(Fixture f) {
    StringBuilder result = new StringBuilder("BAT_DECISIONS_CONTINUED");
    com.riiablo.attributes.Attributes attrs = f.server.world.getMapper(AttributesWrapper.class).get(1).attrs;
    assertEquals(120, attrs.base().getValue(Stat.hpregen, 0));
    assertEquals(120, attrs.getValue(Stat.hpregen, 0));
    for (int frame = 0; frame < 45; frame++) {
      f.server.simulation.tick(null, null); f.updateDecisionAi(0.04f);
      // From initial frame 1: S3->S4 at 9/19, remove the bonus on S2 at frame 34.
      int expectedRegen = frame < 32 ? 120 : 80;
      assertEquals("bat base regen at continuation step " + frame, expectedRegen, attrs.base().getValue(Stat.hpregen, 0));
      assertEquals("bat aggregate regen at continuation step " + frame, expectedRegen, attrs.getValue(Stat.hpregen, 0));
      com.riiablo.engine.server.ai.AI ai = f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
      D2gsCheckpointSnapshot after = f.server.captureReplayCheckpoint();
      result.append('|').append(ai.getState()).append(':').append(ai.getDecisionDiagnostics())
          .append(':').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    return result.toString();
  }

  private static String resumeSpiritDecisions(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueDecisions(target, 120, true);
    }
  }

  private static String resumeTrapDecisions(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueDecisions(target, 100);
    }
  }

  private static String resumeDecisions(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      return continueDecisions(target);
    }
  }

  private static String continueDecisions(Fixture f) {
    return continueDecisions(f, 35);
  }

  private static String continueDecisions(Fixture f, int frames) {
    return continueDecisions(f, frames, false);
  }

  private static String continueDecisions(Fixture f, int frames, boolean spentSpirit) {
    StringBuilder result = new StringBuilder("DECISIONS_CONTINUED");
    for (int frame = 0; frame < frames; frame++) {
      f.server.simulation.tick(null, null);
      boolean action = f.updateDecisionAi(0.04f);
      if (spentSpirit) assertFalse("restored Spirit rearmed its one-shot attack at frame " + frame, action);
      com.riiablo.engine.server.ai.AI ai = f.server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
      result.append('|').append(ai.getState()).append(':').append(ai.getDecisionDiagnostics());
      D2gsCheckpointSnapshot checkpoint = f.server.captureReplayCheckpoint();
      result.append(':').append(checkpoint.summary().canonical()).append(':').append(checkpoint.d2sCheckpoint().canonical());
    }
    return result.toString();
  }

  private static String resumeMonsters(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      target.map.createSyntheticArena(2, 1, 1); // Map itself is a prebuilt dependency, not imported runtime.
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      assertEquals(archive.d2sCheckpoint().canonical(), target.server.captureReplayCheckpoint().d2sCheckpoint().canonical());
      return continueMonsters(target);
    }
  }

  private static String continueMonsters(Fixture f) {
    World world = f.server.world; Monster monster = world.getMapper(Monster.class).get(1);
    assertSame(f.map.getZones().first(), monster.spawnZone);
    assertEquals(2, monster.nativeSelfResurrections); assertTrue(monster.nativeSelfResurrect);
    assertEquals(37, monster.nativePackId); assertTrue(monster.nativePackLeader);
    assertEquals(1, monster.baalWaveIndex); assertEquals(99, monster.baalWaveRoomId); assertTrue(monster.baalWaveLeader);
    assertEquals(0, monster.nativeOwnerId); assertTrue(monster.nativeOwnerIdentity.isCurrent(world, 0));
    assertEquals(2, monster.nativeRequestedMountId); assertFalse(monster.nativeRequestedMountIdentity.isCurrent(world, 2));
    assertTrue(monster.resurrected); assertEquals(0, monster.resurrectedBy);
    Monster corpse = world.getMapper(Monster.class).get(2);
    assertTrue(corpse.fireDeathExplosionScheduled); assertTrue(corpse.coldDeathNovaScheduled);
    assertTrue(corpse.shatteredAtDeath); assertTrue(corpse.nativeCorpseCollision);
    assertTrue(corpse.frogSpawnMarkerInitialized); assertTrue(corpse.frogSpawnMarkerMoved);
    assertEquals(-13, corpse.frogSpawnMarkerX); assertEquals(49, corpse.frogSpawnMarkerY);
    assertNotNull(monster.modeElements); assertEquals(37, monster.modeElements.poisonLength);
    StringBuilder result = new StringBuilder("MONSTERS_CONTINUED");
    for (int frame = 0; frame < 4; frame++) {
      result.append('|').append(NativeUnitRandom.monster(monster, 1, 1).nextUnsigned());
      result.append(':').append(NativeUnitRandom.monster(corpse, 1, 2).nextUnsigned());
      monster.chargedBoltsCooldown -= 0.04f;
      monster.lightningEnchantedLastFrame += 1;
      f.server.simulation.tick(null, null);
      D2gsCheckpointSnapshot after = f.server.captureReplayCheckpoint();
      result.append(':').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    return result.toString();
  }

  @Test public void monsterRuntimeAndSeedDisagreementIsRejectedBeforeMutation() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateMonsters(); target.populate();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      java.util.List<SimulationCheckpoint.NativeUnitRngState> seeds = new java.util.ArrayList<>();
      for (SimulationCheckpoint.NativeUnitRngState seed : archive.summary().nativeUnitRngStates()) {
        seeds.add(seed.contextId == 1 ? new SimulationCheckpoint.NativeUnitRngState(seed.stream,
            seed.contextId, seed.lowSeed + 1, seed.highSeed) : seed);
      }
      D2gsCheckpointSnapshot inconsistent = D2gsCheckpointSnapshot.fromPayloads(archive.tick(), archive.payloads(),
          archive.summary().rngStates(), seeds, archive.d2sCheckpoint(), archive.sessionSnapshot())
          .withTargetRuntime(archive.targetRuntime()).withUnitStateRuntime(archive.unitStateRuntime());
      D2gsCheckpointSnapshot before = target.server.captureReplayCheckpoint();
      try { target.server.restoreReplayCheckpoint(inconsistent); fail("conflicting Monster/RNG owners accepted"); }
      catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("monster/native RNG")); }
      assertEquals(before.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
    }
  }

  @Test public void monsterZoneDependencyAndLateD2sFailureRollbackFullRuntimeInLiveAndEmptyWorlds() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture(); Fixture empty = new Fixture(); Fixture control = new Fixture()) {
      source.populate(); source.populateMonsters(); target.populate();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      D2gsCheckpointSnapshot beforeMissingMap = target.server.captureReplayCheckpoint();
      D2gsCheckpointSnapshot.RestoreResult rejected = target.server.restoreReplayCheckpoint(archive);
      assertFalse(rejected.success()); assertTrue(rejected.rolledBack);
      assertEquals(beforeMissingMap.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      target.prepareMonsterMap(); empty.prepareMonsterMap(); control.prepareMonsterMap();
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      Monster live = target.server.world.getMapper(Monster.class).get(1);
      live.nativeSelfResurrections = 7; live.componentVariants[0] += 17;
      live.chargedBoltsCooldown += 0.2f; live.modeElements.drainMinimum[0] += 9;
      D2gsCheckpointSnapshot before = target.server.captureReplayCheckpoint();
      assertTrue(control.server.restoreReplayCheckpoint(before).success());
      CharData impostor = CharData.loadFromBuffer(0, ByteBuffer.wrap(archive.d2sCheckpoint().entry(0).bytes()));
      impostor.name = "ImpostorHero";
      D2SCheckpoint wrong = D2SCheckpoint.of(java.util.Collections.singletonMap(0,
          D2SCheckpoint.entry(0, 0, new D2SWriter96().writeD2S(D2SWriter96.createD2S(impostor)))));
      D2gsCheckpointSnapshot incoming = D2gsCheckpointSnapshot.fromPayloads(archive.tick(), archive.payloads(),
          archive.summary().rngStates(), archive.summary().nativeUnitRngStates(), wrong, archive.sessionSnapshot())
          .withTargetRuntime(archive.targetRuntime()).withUnitStateRuntime(archive.unitStateRuntime())
          .withAllocatorState(archive.allocatorState());
      for (Fixture f : new Fixture[] {target, empty}) {
        D2gsCheckpointSnapshot previous = f.server.captureReplayCheckpoint();
        try { f.server.restoreReplayCheckpoint(incoming); fail("late D2S failure must roll back full Monster runtime"); }
        catch (IllegalStateException expected) {}
        assertEquals(previous.summary().canonical(), f.server.captureReplayCheckpoint().summary().canonical());
      }
      live.nativeSelfResurrections = 2;
      control.server.world.getMapper(Monster.class).get(1).nativeSelfResurrections = 2;
      assertEquals(continueMonsters(control), continueMonsters(target));
    }
  }

  private static String continueTargets(Fixture fixture) {
    World world = fixture.server.world;
    StateList conversion = world.getMapper(UnitStates.class).get(1).stateList;
    Monster monster = world.getMapper(Monster.class).get(1);
    assertTrue(monster.converted); assertEquals(0, monster.conversionOwnerId);
    assertTrue(((NativeConversionLifecycle.Callback) conversion.getState(StateId.CONVERSION).removalCallback)
        .matches(monster, world.getMapper(AttributesWrapper.class).get(1)));
    assertEquals(7, world.getMapper(NativeAiState.class).get(1).previousMode);
    assertEquals(-793, world.getMapper(NativeAiTargetOverride.class).get(1).rngState);
    NativeAiCommand command = world.getMapper(NativeAiCommand.class).get(1);
    assertEquals(NativeAiCommand.ATTACK, command.command); assertEquals(80, command.expiresAtFrame);
    assertTrue(command.ownerIsCurrent(world)); assertTrue(command.targetIsCurrent(world));
    assertSame(world.getMapper(NativePathIdentity.class).get(0).token(), command.ownerIdentity);
    assertSame(world.getMapper(NativePathIdentity.class).get(2).token(), command.targetIdentity);
    NativeAiPathCache path = world.getMapper(NativeAiPathCache.class).get(1);
    assertEquals(357, path.targetX); assertEquals(-91, path.targetY);
    assertFalse(path.coordinatesKnown); assertFalse(path.pointsKnown); assertTrue(path.hasPoints);
    assertTrue(world.getMapper(NativeAiPathCache.class).get(2).pointsKnown);
    assertFalse(world.getMapper(NativeAiPathCache.class).get(2).hasPoints);
    for (int id = 1; id <= 5; id++) {
      assertSame(world.getMapper(NativeTargetNode.class).get(id),
          world.getMapper(UnitStates.class).get(id).stateList.checkpointTargetNode());
    }
    StringBuilder result = new StringBuilder("TARGETS_CONTINUED");
    for (int frame = 1; frame <= 4; frame++) {
      if (frame == 1) {
        assertTrue(command.pop()); assertEquals(NativeAiCommand.MOVE, command.command);
        assertEquals(79, command.expiresAtFrame);
        assertFalse(command.ownerIsCurrent(world)); assertFalse(command.targetIsCurrent(world));
        path.targetCoordinates(412, -3); path.pathPoints(false);
      } else if (frame == 2) {
        assertTrue(command.pop()); assertEquals(NativeAiCommand.ATTACK, command.command);
        assertEquals(78, command.expiresAtFrame);
        assertNull(command.ownerIdentity); assertNull(command.targetIdentity);
        path.pathPoints(true);
      } else {
        assertFalse(command.pop()); assertEquals(NativeAiCommand.NONE, command.command);
        path.invalidate();
      }
      for (int id = 1; id <= 5; id++) world.getMapper(UnitStates.class).get(id).stateList.update();
      fixture.server.simulation.tick(null, null);
      if (frame == 1) assertEquals(5, world.getAspectSubscriptionManager().get(Aspect.all(Monster.class)).getEntities().size());
      if (frame >= 2) assertEquals(7, world.getAspectSubscriptionManager().get(Aspect.all(Monster.class)).getEntities().size());
      if (frame >= 3) {
        assertFalse(monster.converted); assertEquals(-1, monster.conversionOwnerId);
        assertFalse(conversion.hasState(StateId.CONVERSION_SAVE));
        assertEquals(20, world.getMapper(AttributesWrapper.class).get(1).attrs.get(Stat.level).asInt());
        assertEquals(100f, world.getMapper(AttributesWrapper.class).get(1).attrs.get(Stat.hitpoints).asFixed(), 0f);
        assertEquals(0, conversion.nativeConversionOrder());
      }
      D2gsCheckpointSnapshot after = fixture.server.captureReplayCheckpoint();
      result.append('|').append(after.summary().canonical()).append(':').append(after.d2sCheckpoint().canonical());
    }
    // A subsequent membership event must continue this World's archived clock.
    NativeTargetNode node = world.getMapper(NativeTargetNode.class).get(1);
    assertTrue(node.insert(NativeTargetNode.CONVERSION, 0));
    return result.append('|').append(node.order()).toString();
  }

  @Test public void lateD2sFailureRollsBackTargetClockNodesAndTypedHooksInLiveAndEmptyWorlds() {
    try (Fixture source = new Fixture(); Fixture empty = new Fixture(); Fixture control = new Fixture()) {
      source.populate(); source.populateTargets();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      CharData impostor = CharData.loadFromBuffer(0, ByteBuffer.wrap(archive.d2sCheckpoint().entry(0).bytes()));
      impostor.name = "ImpostorHero";
      D2SCheckpoint wrong = D2SCheckpoint.of(java.util.Collections.singletonMap(0,
          D2SCheckpoint.entry(0, 0, new D2SWriter96().writeD2S(D2SWriter96.createD2S(impostor)))));
      D2gsCheckpointSnapshot incoming = D2gsCheckpointSnapshot.fromPayloads(archive.tick(), archive.payloads(),
          archive.summary().rngStates(), archive.summary().nativeUnitRngStates(), wrong, archive.sessionSnapshot())
          .withAllocatorState(archive.allocatorState()).withTargetRuntime(archive.targetRuntime())
          .withUnitStateRuntime(archive.unitStateRuntime());
      source.server.world.getSystem(NativePlayerTargetSlots.class).nextOrder();
      D2gsCheckpointSnapshot before = source.server.captureReplayCheckpoint();
      assertTrue(control.server.restoreReplayCheckpoint(before).success());
      for (Fixture target : new Fixture[] {source, empty}) {
        D2gsCheckpointSnapshot previous = target.server.captureReplayCheckpoint();
        try { target.server.restoreReplayCheckpoint(incoming); fail("late D2S failure must roll back"); }
        catch (IllegalStateException correct) {
          assertEquals("checkpoint restore transaction rolled back", correct.getMessage());
        }
        assertEquals(previous.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      }
      assertEquals(continueTargets(control), continueTargets(source));
    }
  }

  @Test public void restoringPlayerRemovalAndRecreationDoesNotEraseRestoredChildMembership() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateTargets(); target.populate(); target.populateTargets();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      World world = target.server.world;
      world.delete(0); world.process();
      assertEquals(0, world.getMapper(NativeTargetNode.class).get(1).order());
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      assertEquals(continueTargets(source), continueTargets(target));
    }
  }

  @Test public void missingCurrentCommandLifetimeRollsBackTheEntireExistingWorld() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateTargets(); target.populate(); target.populateTargets();
      D2gsCheckpointSnapshot incoming = source.server.captureReplayCheckpoint();
      // Player owner: no earlier Pregnant hook requires this token, so AI control preflight fails.
      target.server.world.getMapper(NativePathIdentity.class).remove(0);
      D2gsCheckpointSnapshot before = target.server.captureReplayCheckpoint();
      try { target.server.restoreReplayCheckpoint(incoming); fail("current command lifetime was silently fabricated"); }
      catch (IllegalStateException expected) {
        assertEquals("allocator checkpoint transaction rolled back", expected.getMessage());
        assertTrue(expected.getCause().getMessage().contains("AI command current lifetime missing"));
      }
      assertEquals(before.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      assertArrayEquals(before.targetRuntime().bytes(), target.server.captureReplayCheckpoint().targetRuntime().bytes());
      assertEquals(before.d2sCheckpoint().canonical(), target.server.captureReplayCheckpoint().d2sCheckpoint().canonical());
    }
  }

  @Test public void commandAndPathOnlyChangesAlterCanonicalWithoutChangingEntitySync() {
    try (Fixture f = new Fixture()) {
      f.populate(); f.populateTargets();
      D2gsCheckpointSnapshot before = f.server.captureReplayCheckpoint();
      f.server.world.getMapper(NativeAiCommand.class).get(1).expiresAtFrame++;
      D2gsCheckpointSnapshot commandChanged = f.server.captureReplayCheckpoint();
      assertNotEquals(before.summary().canonical(), commandChanged.summary().canonical());
      f.server.world.getMapper(NativeAiPathCache.class).get(1).targetX++;
      D2gsCheckpointSnapshot pathChanged = f.server.captureReplayCheckpoint();
      assertNotEquals(commandChanged.summary().canonical(), pathChanged.summary().canonical());
      for (int id : before.payloads().keySet()) assertArrayEquals(before.payloads().get(id), pathChanged.payloads().get(id));
      assertEquals(before.d2sCheckpoint().canonical(), pathChanged.d2sCheckpoint().canonical());
      assertEquals(before.summary().nativeUnitRngStates(), pathChanged.summary().nativeUnitRngStates());
    }
  }

  @Test public void deletingAnUnexpectedPlayerSettlesBeforeApplyingTheIncomingTargetGroup() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateTargets(); target.populate(); target.populateTargets();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      CharData extra = CharData.loadFromBuffer(0, ByteBuffer.wrap(archive.d2sCheckpoint().entry(0).bytes()));
      extra.name = "ExtraHero";
      int extraId = target.factory.createPlayer(extra, new Vector2(40f, 10f));
      assertEquals(6, extraId);
      World world = target.server.world; world.process();
      NativeTargetNode node = world.getMapper(NativeTargetNode.class).get(1);
      node.detach(); assertTrue(node.insert(NativeTargetNode.SUMMON, extraId));
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertFalse(D2gsCheckpointSnapshot.isActive(world, extraId));
      assertArrayEquals(source.server.captureReplayCheckpoint().targetRuntime().bytes(),
          target.server.captureReplayCheckpoint().targetRuntime().bytes());
      assertTrue(target.server.restoreReplayCheckpoint(archive.withAllocatorState(null)).success());
      assertEquals(continueTargets(source), continueTargets(target));
    }
  }

  @Test public void callbackOwnerMismatchFailsClosedAndDeathRemovalDoesNotRaiseLifeAfterRestore() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateTargets();
      World world = source.server.world;
      UnitState conversion = world.getMapper(UnitStates.class).get(1).stateList.getState(StateId.CONVERSION);
      UnitState.RemovalCallback correct = conversion.removalCallback;
      NativeConversionLifecycle.bind(conversion, new Monster(), world.getMapper(AttributesWrapper.class).get(1));
      try { source.server.captureReplayCheckpoint(); fail("foreign conversion owner accepted"); }
      catch (IllegalStateException expected) {}
      conversion.removalCallback = correct;
      UnitState pregnancy = world.getMapper(UnitStates.class).get(3).stateList.getState(StateId.PREGNANT);
      UnitState.RemovalCallback ownPregnancy = pregnancy.removalCallback;
      pregnancy.removalCallback = world.getMapper(UnitStates.class).get(2).stateList.getState(StateId.PREGNANT).removalCallback;
      try { source.server.captureReplayCheckpoint(); fail("foreign pregnancy owner accepted"); }
      catch (IllegalStateException expected) {}
      pregnancy.removalCallback = ownPregnancy;
      assertTrue(target.server.restoreReplayCheckpoint(source.server.captureReplayCheckpoint()).success());
      AttributesWrapper wrapper = target.server.world.getMapper(AttributesWrapper.class).get(1);
      wrapper.attrs.get(Stat.hitpoints).set(0f);
      target.server.world.getMapper(UnitStates.class).get(1).stateList.retainForDeath(null, StateList.DeathUnitType.MONSTER);
      assertEquals(0f, wrapper.attrs.get(Stat.hitpoints).asFixed(), 0f);
      assertFalse(target.server.world.getMapper(Monster.class).get(1).converted);
      assertEquals(10, wrapper.attrs.get(Stat.level).asInt());
    }
  }

  @Test public void authoritativeStatesAndModifiedResourceOperandsResumeAcrossJvmRestart() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("states.chk");
    String expected;
    try (Fixture source = new Fixture(true, false, true)) {
      source.populate(); source.populateStates();
      source.server.writeReplayCheckpoint(path);
      expected = continueStates(source);
    }
    assertEquals(expected, resumeStates(path));
    Path output = temporary.getRoot().toPath().resolve("states-child.log");
    Process child = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "states").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("state recovery child JVM timed out", child.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, child.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (child.isAlive()) child.destroyForcibly(); }
  }

  private static String resumeStates(Path path) throws Exception {
    try (Fixture target = new Fixture(true, false, true)) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      assertFalse(target.server.world.getMapper(com.riiablo.engine.server.component.UnitStates.class)
          .get(0).snapshotOnly);
      return continueStates(target);
    }
  }

  @Test public void stateRuntimeHasFullCoverageIntegrityAndNoImplicitEcsDowngrade() {
    try (Fixture source = new Fixture(true, false, true)) {
      source.populate(); source.populateStates();
      D2gsCheckpointSnapshot original = source.server.captureReplayCheckpoint();
      com.riiablo.engine.server.component.UnitStates holder = source.server.world.getMapper(
          com.riiablo.engine.server.component.UnitStates.class).get(0);
      holder.stateList.getState(com.riiablo.engine.server.state.StateId.POISON).initialDuration += 23;
      D2gsCheckpointSnapshot changed = source.server.captureReplayCheckpoint();
      assertArrayEquals(original.payloads().get(0), changed.payloads().get(0));
      assertNotEquals(original.summary().canonical(), changed.summary().canonical());
      assertTrue(changed.withWorldIdentity(1, 0).withAllocatorState(null)
          .withMissileRuntime(changed.missileRuntime()).unitStateRuntime() != null);
      try { changed.withUnitStateRuntime(java.util.Collections.emptyMap()); fail("partial state coverage accepted"); }
      catch (IllegalArgumentException expected) {}
      try { changed.withAllocatorState(null).toEcsBytes(); fail("state runtime discarded"); }
      catch (IllegalStateException expected) {}
      byte[] encoded = changed.toBytes(), runtime = changed.unitStateRuntime().get(0).bytes();
      int offset = -1;
      for (int i = 0; i <= encoded.length - runtime.length; i++) {
        if (java.util.Arrays.equals(runtime, java.util.Arrays.copyOfRange(encoded, i, i + runtime.length))) {
          offset = i; break;
        }
      }
      assertTrue(offset >= 0);
      encoded[offset + 8] ^= 1; // Valid appliedMaxLifePercent scalar, invalid canonical witness.
      try { D2gsCheckpointSnapshot.fromBytes(encoded); fail("state witness corruption accepted"); }
      catch (IllegalArgumentException expected) {}
      D2gsCheckpointSnapshot legacy = changed.withUnitStateRuntime(null);
      assertNull(D2gsCheckpointSnapshot.fromBytes(legacy.toBytes()).unitStateRuntime());
      assertNotEquals(changed.summary().canonical(), legacy.summary().canonical());
    }
  }

  private static String continueStates(Fixture fixture) {
    StringBuilder result = new StringBuilder("STATES_CONTINUED");
    com.riiablo.engine.server.state.StateList states = fixture.server.world.getMapper(
        com.riiablo.engine.server.component.UnitStates.class).get(0).stateList;
    assertEquals(5, states.getStateDuration(com.riiablo.engine.server.state.StateId.POISON));
    for (int i = 1; i <= 32; i++) {
      int before = fixture.data().getStats().get(Stat.hitpoints).encodedValues();
      fixture.server.simulation.tick(null, null);
      if (i <= 5) {
        assertEquals("poison includes the final duration=1 frame", 64,
            before - fixture.data().getStats().get(Stat.hitpoints).encodedValues());
      }
      if (i == 5) assertFalse(states.hasState(com.riiablo.engine.server.state.StateId.POISON));
      if (i == 10) {
        assertFalse(states.hasState(com.riiablo.engine.server.state.StateId.BATTLEORDERS));
        assertEquals(40 * 256, fixture.data().getStats().get(Stat.maxhp).encodedValues());
        assertEquals(20 * 256, fixture.data().getStats().get(Stat.maxmana).encodedValues());
        assertEquals(60 * 256, fixture.data().getStats().get(Stat.maxstamina).encodedValues());
      }
      D2gsCheckpointSnapshot checkpoint = fixture.server.captureReplayCheckpoint();
      result.append('|').append(checkpoint.summary().canonical()).append(':')
          .append(fixture.data().capturePotionCheckpoint().canonical());
    }
    assertEquals(2, states.size()); // Two independent permanent anonymous lists.
    return result.append('|').append(fixture.server.captureReplayCheckpoint().d2sCheckpoint().canonical()).toString();
  }

  @Test public void lateD2sFailureRestoresLiveStatesStatsAndNextTickWithoutReapplyingBuffs() {
    try (Fixture source = new Fixture(true, false, true); Fixture empty = new Fixture(true, false, true)) {
      source.populate(); source.populateStates();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      CharData impostor = CharData.loadFromBuffer(0, ByteBuffer.wrap(archive.d2sCheckpoint().entry(0).bytes()));
      impostor.name = "ImpostorHero";
      D2SCheckpoint wrong = D2SCheckpoint.of(java.util.Collections.singletonMap(0,
          D2SCheckpoint.entry(0, 0, new D2SWriter96().writeD2S(D2SWriter96.createD2S(impostor)))));
      D2gsCheckpointSnapshot incoming = D2gsCheckpointSnapshot.fromPayloads(archive.tick(), archive.payloads(),
          archive.summary().rngStates(), archive.summary().nativeUnitRngStates(), wrong, archive.sessionSnapshot())
          .withAllocatorState(archive.allocatorState()).withTargetRuntime(archive.targetRuntime())
          .withUnitStateRuntime(archive.unitStateRuntime());
      try (Fixture control = new Fixture(true, false, true)) {
        for (int i = 0; i < 2; i++) source.server.simulation.tick(null, null);
        D2gsCheckpointSnapshot before = source.server.captureReplayCheckpoint();
        assertTrue(control.server.restoreReplayCheckpoint(before).success());
        for (Fixture target : new Fixture[] {source, empty}) {
          D2gsCheckpointSnapshot previous = target.server.captureReplayCheckpoint();
          try { target.server.restoreReplayCheckpoint(incoming); fail("late D2S failure must roll back"); }
          catch (IllegalStateException expected) {
            assertEquals("checkpoint restore transaction rolled back", expected.getMessage());
          }
          assertEquals(previous.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
          assertEquals(previous.d2sCheckpoint().canonical(), target.server.captureReplayCheckpoint().d2sCheckpoint().canonical());
        }
        for (int i = 0; i < 14; i++) {
          source.server.simulation.tick(null, null); control.server.simulation.tick(null, null);
          assertEquals(control.server.captureReplayCheckpoint().summary().canonical(),
              source.server.captureReplayCheckpoint().summary().canonical());
        }
      }
    }
  }

  @Test public void allocatorFifoAndHighWaterMarkContinueRealItemCreationAcrossJvmRestart() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("allocator.chk");
    String expected;
    try (Fixture source = new Fixture()) {
      source.populate(); source.populateSparseItems();
      java.util.List<Integer> temporaryIds = new java.util.ArrayList<>();
      int id;
      do { id = source.server.world.create(); temporaryIds.add(id); } while (id < 17);
      source.server.world.process();
      java.util.Collections.rotate(temporaryIds, 3);
      for (int deleted : temporaryIds) {
        source.server.world.delete(deleted); source.server.world.process();
      }
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      assertEquals(18, archive.allocatorState().nextId());
      assertEquals(15, archive.allocatorState().freeIds().length);
      source.server.writeReplayCheckpoint(path);
      expected = continueAllocator(source);
    }
    assertEquals(expected, resumeAllocator(path));
    Path output = temporary.getRoot().toPath().resolve("allocator-child.log");
    Process process = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "allocator")
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("allocator recovery child JVM timed out", process.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, process.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (process.isAlive()) process.destroyForcibly(); }
  }

  private static String resumeAllocator(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      // Different target history must not leak its tail or queue into restore.
      for (int i = 0; i < 30; i++) target.server.world.create();
      target.server.world.process();
      for (int i = 29; i >= 0; i--) target.server.world.delete(i);
      target.server.world.process();
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertArrayEquals(archive.allocatorState().bytes(),
          com.artemis.EntityAllocatorCheckpoint.capture(target.server.world).bytes());
      return continueAllocator(target);
    }
  }

  private static String continueAllocator(Fixture fixture) {
    StringBuilder result = new StringBuilder("ALLOCATOR_CONTINUED");
    int recycled = -1;
    for (int step = 0; step < 22; step++) {
      int id = fixture.factory.createItem(new ItemGenerator().generateStartItem("hp1", 2000 + step, -1),
          12f + step, 10f);
      if (step == 0) recycled = id;
      if (step == 5) fixture.server.world.delete(recycled);
      fixture.server.simulation.tick(null, null);
      result.append('|').append(id).append(':').append(fixture.server.captureReplayCheckpoint().summary().canonical());
    }
    assertEquals(24, fixture.server.captureReplayCheckpoint().allocatorState().nextId());
    return result.append('|').append(fixture.server.captureReplayCheckpoint().d2sCheckpoint().canonical()).toString();
  }

  @Test public void exactAllocatorRestoreRejectsForeignLocalsAndPendingDeletesWithoutMutation() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateSparseItems();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      target.server.world.create(); target.server.world.process();
      byte[] before = com.artemis.EntityAllocatorCheckpoint.capture(target.server.world).bytes();
      try { target.server.restoreReplayCheckpoint(archive); fail("foreign local entity overwritten"); }
      catch (IllegalStateException expected) { assertEquals("allocator local entity set mismatch", expected.getMessage()); }
      assertArrayEquals(before, com.artemis.EntityAllocatorCheckpoint.capture(target.server.world).bytes());
      assertFalse(target.server.world.getMapper(Networked.class).has(0));
      target.server.world.delete(0);
      try { target.server.captureReplayCheckpoint(); fail("pending deletion was silently archived"); }
      catch (IllegalStateException expected) { assertEquals("allocator deletion batch is pending", expected.getMessage()); }
    }
  }

  @Test public void allocatorRequiresPrebuiltLocalsAndPublishedNetworkInsertions() {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate(); source.populateSparseItems();
      assertEquals(1, source.server.world.create());
      source.server.world.getMapper(Position.class).create(1).position.set(91, 92);
      source.server.world.process();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      try { target.server.restoreReplayCheckpoint(archive); fail("missing local entity invented"); }
      catch (IllegalStateException expected) { assertEquals("allocator local entity set mismatch", expected.getMessage()); }
      assertEquals(0, com.artemis.EntityAllocatorCheckpoint.capture(target.server.world).nextId());
      assertEquals(0, target.server.world.create()); assertEquals(1, target.server.world.create());
      target.server.world.getMapper(Position.class).create(1).position.set(91, 92);
      target.server.world.process(); target.server.world.delete(0); target.server.world.process();
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      assertEquals(archive.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      assertEquals(91f, target.server.world.getMapper(Position.class).get(1).position.x, 0f);
      assertEquals(source.server.world.create(), target.server.world.create());
      source.factory.createItem(new ItemGenerator().generateStartItem("hp1", 3000, -1), 12, 10);
      try { source.server.captureReplayCheckpoint(); fail("unpublished network insertion archived as local"); }
      catch (IllegalStateException expected) {
        assertEquals("allocator checkpoint requires published network entities", expected.getMessage());
      }
      source.server.world.process();
      assertNotNull(source.server.captureReplayCheckpoint().allocatorState());
    }
  }

  @Test public void inFlightProjectilesResumeTheirCollisionAndDamageTimelineAcrossJvmRestart() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("projectiles.chk");
    String expected;
    try (Fixture source = new Fixture(false, true)) {
      source.populate();
      source.populateProjectiles();
      source.server.writeReplayCheckpoint(path);
      expected = continueProjectiles(source);
    }
    assertEquals(expected, resumeProjectiles(path));
    Path output = temporary.getRoot().toPath().resolve("projectile-child.log");
    Process process = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "projectiles")
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("projectile recovery child JVM timed out", process.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, process.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (process.isAlive()) process.destroyForcibly(); }
  }

  private static String resumeProjectiles(Path path) throws Exception {
    try (Fixture target = new Fixture(false, true)) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertEquals(java.util.Arrays.asList(0, 1, 2, 5), new java.util.ArrayList<>(archive.payloads().keySet()));
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      java.util.Map<Integer, com.riiablo.engine.replay.MissileRuntimeCheckpoint> restored =
          com.riiablo.engine.replay.MissileRuntimeCheckpoint.capture(target.server.world, new int[] {1, 2});
      for (int id : new int[] {1, 2}) assertArrayEquals(archive.missileRuntime().get(id).bytes(), restored.get(id).bytes());
      assertSame(target.server.world.getMapper(Missile.class).get(1).sharedHitTargets,
          target.server.world.getMapper(Missile.class).get(2).sharedHitTargets);
      // Lower missile ids were assembled before owner 5. Native structure
      // restoration must not re-roll or consult its not-yet-restored stats.
      for (SimulationCheckpoint.NativeUnitRngState state : archive.summary().nativeUnitRngStates()) {
        NativeUnitRandom random = NativeUnitRandom.unit(target.server.world, 1, (int) state.contextId);
        assertEquals(state.lowSeed, random.state());
        assertEquals(state.highSeed, random.high());
      }
      return continueProjectiles(target);
    }
  }

  @Test public void nativeFactoryPacketsRemainFrozenWhenOwnerCreationStateHasChanged() {
    try (Fixture source = new Fixture(false, true); Fixture target = new Fixture(false, true)) {
      source.populate(); source.populateProjectiles();
      com.riiablo.attributes.Attributes owner = source.server.world.getMapper(AttributesWrapper.class).get(5).attrs;
      owner.base().put(Stat.passive_critical_strike, 100);
      owner.base().put(Stat.firemindam, 2); owner.base().put(Stat.firemaxdam, 3); owner.reset();
      source.server.world.getSystem(CofManager.class).setMode(5, com.riiablo.engine.Engine.Monster.MODE_A1, true);
      assertEquals(3, source.factory.createMissile(Riiablo.files.Missiles.get("arrow"),
          new Vector2(-1, 0), new Vector2(-10, 10), 5, 1));
      Missile original = source.server.world.getMapper(Missile.class).get(3);
      assertTrue(original.nativePhysical.criticalCaptured);
      assertTrue(original.nativePhysical.creationCritical);
      assertNotNull(original.nativeElemental);
      // The checkpoint owner no longer has the state used to create this cast.
      owner.base().put(Stat.passive_critical_strike, 0);
      owner.base().put(Stat.firemindam, 0); owner.base().put(Stat.firemaxdam, 0); owner.reset();
      source.server.simulation.tick(null, null);
      D2gsCheckpointSnapshot archive = D2gsCheckpointSnapshot.fromBytes(source.server.captureReplayCheckpoint().toBytes());
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      Missile restored = target.server.world.getMapper(Missile.class).get(3);
      assertTrue(restored.nativePhysical.creationCritical);
      assertEquals(original.nativeElemental.minimum(1), restored.nativeElemental.minimum(1));
      assertArrayEquals(archive.missileRuntime().get(3).bytes(),
          com.riiablo.engine.replay.MissileRuntimeCheckpoint.capture(target.server.world, new int[] {1, 2, 3}).get(3).bytes());
      source.server.simulation.tick(null, null); target.server.simulation.tick(null, null);
      assertEquals(source.server.captureReplayCheckpoint().summary().canonical(),
          target.server.captureReplayCheckpoint().summary().canonical());
    }
  }

  private static String continueProjectiles(Fixture fixture) {
    int before = fixture.data().getStats().get(Stat.hitpoints).encodedValues();
    StringBuilder timeline = new StringBuilder("PROJECTILES_CONTINUED");
    for (int i = 0; i < 35; i++) {
      fixture.server.simulation.tick(null, null);
      D2gsCheckpointSnapshot checkpoint = fixture.server.captureReplayCheckpoint();
      timeline.append('|').append(checkpoint.summary().canonical());
    }
    int after = fixture.data().getStats().get(Stat.hitpoints).encodedValues();
    assertTrue("restored in-flight Fire Bolt must hit the real player", after < before);
    return timeline.append('|').append(after).append('|')
        .append(fixture.server.captureReplayCheckpoint().d2sCheckpoint().canonical()).toString();
  }

  @Test public void lateD2sFailureRestoresLiveProjectileRuntimeAndClearsNewWorldCreations() {
    try (Fixture source = new Fixture(false, true); Fixture empty = new Fixture(false, true)) {
      source.populate(); source.populateProjectiles();
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      CharData impostor = CharData.loadFromBuffer(0, ByteBuffer.wrap(archive.d2sCheckpoint().entry(0).bytes()));
      impostor.name = "ImpostorHero";
      D2SCheckpoint wrong = D2SCheckpoint.of(java.util.Collections.singletonMap(0,
          D2SCheckpoint.entry(0, 0, new D2SWriter96().writeD2S(D2SWriter96.createD2S(impostor)))));
      D2gsCheckpointSnapshot incoming = D2gsCheckpointSnapshot.fromPayloads(archive.tick(), archive.payloads(),
          archive.summary().rngStates(), archive.summary().nativeUnitRngStates(), wrong, archive.sessionSnapshot())
          .withMissileRuntime(archive.missileRuntime()).withAllocatorState(archive.allocatorState());
      Missile live = source.server.world.getMapper(Missile.class).get(1);
      live.nativeFrame += 11;
      live.damage.base().put(Stat.firemindam, 99);
      live.damage.reset();
      live.hitTargets.add(55);
      live.sharedHitTargets.add(57);
      for (Fixture target : new Fixture[] {source, empty}) {
        D2gsCheckpointSnapshot before = target.server.captureReplayCheckpoint();
        try { target.server.restoreReplayCheckpoint(incoming); fail("late D2S error must roll back"); }
        catch (IllegalStateException expected) {
          assertEquals("checkpoint restore transaction rolled back", expected.getMessage());
        }
        if (target == empty) target.server.world.process(); // deletion flush, no live missile tick
        assertEquals(before.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
        assertEquals(before.d2sCheckpoint().canonical(), target.server.captureReplayCheckpoint().d2sCheckpoint().canonical());
      }
      // Old archives lack the system stream. A late failure must rebind the
      // live combat RNG object, rather than restore a detached registry copy.
      D2gsCheckpointSnapshot legacy = D2gsCheckpointSnapshot.fromPayloads(archive.tick(), archive.payloads(),
          java.util.Collections.emptyList(), archive.summary().nativeUnitRngStates(), wrong, archive.sessionSnapshot())
          .withMissileRuntime(archive.missileRuntime());
      D2gsCheckpointSnapshot beforeLegacy = source.server.captureReplayCheckpoint();
      try { source.server.restoreReplayCheckpoint(legacy); fail("late legacy error must roll back"); }
      catch (IllegalStateException expected) {
        assertEquals("checkpoint restore transaction rolled back", expected.getMessage());
      }
      assertEquals(beforeLegacy.summary().canonical(), source.server.captureReplayCheckpoint().summary().canonical());
      com.riiablo.engine.server.NativeRng combatRng = source.server.world.getSystem(
          com.riiablo.engine.server.MissileCollisionSystem.class).checkpointCombatRng();
      assertSame(combatRng, source.server.replayRngRegistry().get(combatRng.streamName(), combatRng.contextId()));
      assertTrue(empty.server.world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().isEmpty());
    }
  }

  @Test public void projectileRuntimeChangesDigestWithoutChangingWirePayloadAndRejectsTampering() {
    try (Fixture source = new Fixture(false, true)) {
      source.populate(); source.populateProjectiles();
      D2gsCheckpointSnapshot before = source.server.captureReplayCheckpoint();
      source.server.world.getMapper(Missile.class).get(1).hitTargets.add(333);
      D2gsCheckpointSnapshot changed = source.server.captureReplayCheckpoint();
      byte[] wrongOwner = changed.missileRuntime().get(1).bytes();
      ByteBuffer.wrap(wrongOwner).putInt(12, 0);
      java.util.Map<Integer, com.riiablo.engine.replay.MissileRuntimeCheckpoint> wrongRuntime =
          new java.util.TreeMap<>(changed.missileRuntime());
      wrongRuntime.put(1, com.riiablo.engine.replay.MissileRuntimeCheckpoint.fromBytes(wrongOwner));
      try { changed.withMissileRuntime(wrongRuntime); fail("runtime/ECS owner mismatch accepted"); }
      catch (IllegalArgumentException expected) {}
      for (int id : before.payloads().keySet()) assertArrayEquals(before.payloads().get(id), changed.payloads().get(id));
      assertNotEquals(before.summary().canonical(), changed.summary().canonical());
      byte[] bytes = changed.toBytes();
      byte[] runtime = changed.missileRuntime().get(1).bytes();
      int offset = -1;
      for (int i = 0; i <= bytes.length - runtime.length; i++) {
        boolean matches = true;
        for (int j = 0; j < runtime.length && matches; j++) matches = bytes[i + j] == runtime[j];
        if (matches) { offset = i; break; }
      }
      assertTrue(offset >= 0);
      bytes[offset + 11] ^= 1; // finite range mutation: structural parse succeeds, witness must fail
      try { D2gsCheckpointSnapshot.fromBytes(bytes); fail("runtime corruption accepted"); }
      catch (IllegalArgumentException expected) {}
      D2gsCheckpointSnapshot onlyRuntime = D2gsCheckpointSnapshot.fromPayloads(before.tick(), before.payloads())
          .withMissileRuntime(before.missileRuntime());
      try { onlyRuntime.toEcsBytes(); fail("runtime metadata discarded"); }
      catch (IllegalStateException expected) {}
    }
  }

  @Test public void sparsePlayerAndOwnedItemsResumeInANewWorldAndIndependentJvm() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("sparse.chk");
    String expected;
    try (Fixture source = new Fixture()) {
      source.populate();
      source.populateSparseItems();
      source.server.writeReplayCheckpoint(path);
      expected = continueSparse(source);
    }
    assertEquals(expected, resumeSparse(path));
    Path output = temporary.getRoot().toPath().resolve("sparse-child.log");
    Process process = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "sparse")
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("sparse recovery child JVM timed out", process.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, process.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (process.isAlive()) process.destroyForcibly(); }
  }

  @Test public void sparseRestorePreservesLocalEntitiesInTheGaps() throws Exception {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.populate();
      source.populateSparseItems();
      // Legacy subset restore still permits foreign locals; an exact allocator
      // archive must reject them because they occupy archived reusable ids.
      D2gsCheckpointSnapshot checkpoint = source.server.captureReplayCheckpoint().withAllocatorState(null);
      for (int i = 0; i < 10; i++) assertEquals(i, target.server.world.create());
      target.server.world.getMapper(Position.class).create(1).position.set(91, 92);
      target.server.world.process();
      // Put higher checkpoint ids first in limbo. Restoring id 0 must hold
      // these reservations until their own native factory calls arrive.
      target.server.world.delete(9);
      target.server.world.process();
      target.server.world.delete(5);
      target.server.world.process();
      for (int id : new int[] {0, 2, 3, 4, 6, 7, 8}) target.server.world.delete(id);
      target.server.world.process();
      assertTrue(target.server.restoreReplayCheckpoint(checkpoint).success());
      target.server.world.process();
      assertEquals(checkpoint.summary().canonical(), target.server.captureReplayCheckpoint()
          .withAllocatorState(null).summary().canonical());
      assertEquals(91f, target.server.world.getMapper(Position.class).get(1).position.x, 0f);
      assertFalse(target.server.world.getMapper(Networked.class).has(1));
      assertEquals(4, target.server.world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
    }
  }

  private static String resumeSparse(Path path) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot archive = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(archive).success());
      target.server.world.process();
      D2gsCheckpointSnapshot restored = target.server.captureReplayCheckpoint();
      assertEquals(archive.summary().canonical(), restored.summary().canonical());
      assertEquals(archive.d2sCheckpoint().canonical(), restored.d2sCheckpoint().canonical());
      assertEquals(3, target.server.world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
      for (int id : new int[] {5, 9}) {
        com.riiablo.engine.server.component.Item item = target.server.world
            .getMapper(com.riiablo.engine.server.component.Item.class).get(id);
        assertEquals(0, item.dropOwnerId);
        assertEquals(123456L, item.dropOwnerUntilMillis);
        assertEquals(7, item.dropPartyId);
        assertEquals(234567L, item.dropPartyUntilMillis);
        assertTrue(item.partyShareGold);
      }
      return continueSparse(target);
    }
  }

  private static String continueSparse(Fixture fixture) {
    for (int i = 0; i < 3; i++) fixture.server.simulation.tick(() -> fixture.server.world
        .getMapper(Position.class).get(0).position.add(1, 0), null);
    D2gsCheckpointSnapshot after = fixture.server.captureReplayCheckpoint();
    return "SPARSE_CONTINUED|" + after.summary().canonical() + "|" + after.d2sCheckpoint().canonical();
  }

  @Test public void newWorldRestoresFullCharacterAndClaimsTheArchivedActor() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("world.chk");
    D2gsCheckpointSnapshot checkpoint;
    long nextDraw;
    try (Fixture source = new Fixture()) {
      source.populate();
      checkpoint = source.server.captureReplayCheckpoint();
      assertEquals(1, checkpoint.d2sCheckpoint().entries().size());
      assertEquals(1, checkpoint.summary().nativeUnitRngStates().size());
      source.server.writeReplayCheckpoint(path);
      nextDraw = NativeUnitRandom.unit(source.server.world, 1, 0).nextUnsigned();
    }
    String expected = resume(path, nextDraw);
    Path output = temporary.getRoot().toPath().resolve("child.log");
    Process process = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(),
        Long.toString(nextDraw), Riiablo.home.path())
        .redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("world recovery child JVM timed out", process.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, process.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  private static String resume(Path path, long nextDraw) throws Exception {
    try (Fixture target = new Fixture()) {
      D2gsCheckpointSnapshot checkpoint = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(path).success());
      target.server.world.process();
      assertEquals(checkpoint.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      byte[] originalSave = checkpoint.d2sCheckpoint().entry(0).bytes();
      byte[] restoredSave = target.server.captureReplayCheckpoint().d2sCheckpoint().entry(0).bytes();
      assertArrayEquals(originalSave, restoredSave);
      assertEquals(checkpoint.d2sCheckpoint().canonical(), target.server.captureReplayCheckpoint().d2sCheckpoint().canonical());
      CharData restored = target.data();
      assertEquals(4321L, restored.getStats().getValue(Stat.gold, 0L));
      assertEquals(0x15, restored.getWaypoints(0));
      assertEquals(4L, restored.progressionRevision());
      assertEquals(7L, restored.getItems().equipmentRevision());
      assertSame(restored.getStats(), target.server.world.getMapper(AttributesWrapper.class).get(0).attrs);
      String before = target.server.captureReplayCheckpoint().summary().canonical();
      try {
        target.server.claimReplayPlayer(0, "CheckpointHero", Riiablo.NECROMANCER);
        fail("wrong class must not claim the archived actor");
      } catch (IllegalStateException expected) {
        assertEquals(before, target.server.captureReplayCheckpoint().summary().canonical());
      }
      assertEquals(0, target.server.claimReplayPlayer(0, "CheckpointHero", Riiablo.AMAZON));
      assertSame(restored, target.data());
      assertEquals(2L, target.server.authoritativeSessions().activeConnection(0).generation);
      assertEquals(0, target.server.player.get(0, -1));
      try {
        target.server.claimReplayPlayer(1, "CheckpointHero", Riiablo.AMAZON);
        fail("a restored actor must not be claimed twice");
      } catch (IllegalStateException expected) {
        assertNull(target.server.authoritativeSessions().activeConnection(1));
      }
      assertEquals(1, target.server.world.getAspectSubscriptionManager()
          .get(Aspect.all(Networked.class)).getEntities().size());
      assertEquals(nextDraw, NativeUnitRandom.unit(target.server.world, 1, 0).nextUnsigned());
      target.server.simulation.tick(() -> target.server.world.getMapper(Position.class).get(0)
          .position.add(1, 0), null);
      assertEquals(78L, target.server.simulation.tickNumber());
      assertEquals(11f, target.server.world.getMapper(Position.class).get(0).position.x, 0f);
      return "WORLD_CONTINUED|" + target.server.captureReplayCheckpoint().summary().canonical();
    }
  }

  @Test public void captureRemovesDeletedPlayerBindingsAndRebindsRecycledUnitSeeds() {
    try (Fixture fixture = new Fixture()) {
      fixture.populate();
      D2gsCheckpointSnapshot before = fixture.server.captureReplayCheckpoint();
      fixture.server.world.delete(0);
      fixture.server.world.process();
      assertTrue(fixture.server.captureReplayCheckpoint().d2sCheckpoint().entries().isEmpty());
      assertTrue(fixture.server.captureReplayCheckpoint().summary().nativeUnitRngStates().isEmpty());
      assertEquals(0, fixture.factory.createPlayer("CheckpointHero", Riiablo.AMAZON, 20f, 20f));
      fixture.server.world.process();
      NativeUnitRandom.unit(fixture.server.world, 1, 0).nextUnsigned();
      NativeUnitRandom.unit(fixture.server.world, 1, 0).nextUnsigned();
      assertNotEquals(before.summary().nativeUnitRngStates(),
          fixture.server.captureReplayCheckpoint().summary().nativeUnitRngStates());
    }
  }

  @Test public void repeatedRestoreResetsPlayerRevisionsFromTheArchive() {
    try (Fixture fixture = new Fixture()) {
      fixture.populate();
      D2gsCheckpointSnapshot checkpoint = fixture.server.captureReplayCheckpoint();
      fixture.data().restoreProgressionRevision(900);
      fixture.data().getItems().restoreEquipmentRevision(800);
      assertTrue(fixture.server.restoreReplayCheckpoint(checkpoint).success());
      assertEquals(4L, fixture.data().progressionRevision());
      assertEquals(7L, fixture.data().getItems().equipmentRevision());
      assertEquals(checkpoint.summary().canonical(), fixture.server.captureReplayCheckpoint().summary().canonical());
    }
  }

  @Test public void failedD2sIdentityRollsBackLiveAndNewWorldBindings() {
    try (Fixture source = new Fixture(); Fixture empty = new Fixture()) {
      source.populate();
      source.populateSparseItems();
      source.data().restorePotionCheckpoint(new CharData.PotionCheckpoint(111, 30, 90, 60, 45));
      D2gsCheckpointSnapshot archive = source.server.captureReplayCheckpoint();
      CharData impostor = CharData.loadFromBuffer(0,
          ByteBuffer.wrap(archive.d2sCheckpoint().entry(0).bytes()));
      impostor.name = "ImpostorHero";
      byte[] bytes = new D2SWriter96().writeD2S(D2SWriter96.createD2S(impostor));
      D2SCheckpoint invalid = D2SCheckpoint.of(java.util.Collections.singletonMap(0,
          D2SCheckpoint.entry(0, 0, bytes)));
      D2gsCheckpointSnapshot incoming = D2gsCheckpointSnapshot.fromPayloads(archive.tick(),
          archive.payloads(), archive.summary().rngStates(), archive.summary().nativeUnitRngStates(),
          invalid, archive.sessionSnapshot()).withAllocatorState(archive.allocatorState());
      source.data().getStats().base().put(Stat.gold, 8765);
      source.data().update();
      source.data().restoreProgressionRevision(9);
      source.data().restorePotionCheckpoint(new CharData.PotionCheckpoint(222, 40, 80, 50, 35));
      source.server.world.getMapper(Position.class).get(0).position.set(30, 30);
      NativeUnitRandom.unit(source.server.world, 1, 0).nextUnsigned();
      source.server.simulation.restoreFromCheckpoint(81);
      for (Fixture target : new Fixture[] {source, empty}) {
        D2gsCheckpointSnapshot before = target.server.captureReplayCheckpoint();
        try {
          target.server.restoreReplayCheckpoint(incoming);
          fail("D2S/ECS identity mismatch must roll back");
        } catch (IllegalStateException expected) {
          assertEquals("checkpoint restore transaction rolled back", expected.getMessage());
        }
        target.server.world.process();
        D2gsCheckpointSnapshot after = target.server.captureReplayCheckpoint();
        assertEquals(before.summary().canonical(), after.summary().canonical());
        assertEquals(before.d2sCheckpoint().canonical(), after.d2sCheckpoint().canonical());
      }
      assertTrue(empty.server.world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().isEmpty());
      // All skipped reservations and rolled-back native creations are reusable.
      java.util.Set<Integer> allocated = new java.util.HashSet<>();
      for (int i = 0; i < 10; i++) allocated.add(empty.server.world.create());
      for (int i = 0; i < 10; i++) assertTrue(allocated.contains(i));
      assertEquals(9, source.data().progressionRevision());
      assertSame(source.data().getStats(),
          source.server.world.getMapper(AttributesWrapper.class).get(0).attrs);
    }
  }

  @Test public void automaticNativeBindingsCaptureBothWordsForMonsterAndMissile() {
    World world = new World();
    try {
      int monster = world.create();
      int missile = world.create();
      world.getMapper(Networked.class).create(monster);
      world.getMapper(Networked.class).create(missile);
      world.getMapper(Monster.class).create(monster);
      world.getMapper(Missile.class).create(missile);
      world.process();
      D2gsWorldCheckpointBindings bindings = new D2gsWorldCheckpointBindings(world, 123);
      java.util.List<SimulationCheckpoint.NativeUnitRngState> initial =
          bindings.captureNativeRegistry().snapshot();
      assertEquals(2, initial.size());
      for (SimulationCheckpoint.NativeUnitRngState state : initial) {
        assertEquals(123 ^ (int) state.contextId * 0x45D9F3B, state.lowSeed);
        assertEquals(666, state.highSeed); // capture initializes, but never draws
      }
      for (int id : new int[] {monster, missile}) {
        NativeUnitRandom.unit(world, 123, id).nextUnsigned();
        NativeUnitRandom.unit(world, 123, id).nextUnsigned();
      }
      java.util.List<SimulationCheckpoint.NativeUnitRngState> saved =
          bindings.captureNativeRegistry().snapshot();
      long[] next = {NativeUnitRandom.unit(world, 123, monster).nextUnsigned(),
          NativeUnitRandom.unit(world, 123, missile).nextUnsigned()};
      bindings.prepareNativeRestore(saved).restore(saved);
      assertEquals(next[0], NativeUnitRandom.unit(world, 123, monster).nextUnsigned());
      assertEquals(next[1], NativeUnitRandom.unit(world, 123, missile).nextUnsigned());
    } finally { world.dispose(); }
  }

  @Test public void activePotionsContinueLikeTheOriginalWorldAfterJvmRestart() throws Exception {
    Path path = temporary.getRoot().toPath().resolve("potions.chk");
    String expected;
    try (Fixture source = new Fixture(true)) {
      source.populate();
      source.data().getStats().base().put(Stat.maxhp, 100f);
      source.data().getStats().base().put(Stat.maxmana, 100f);
      source.data().update();
      source.data().getStats().aggregate().put(Stat.hitpoints, 1f);
      source.data().getStats().aggregate().put(Stat.mana, 2f);
      consumePotion(source.data(), "hp1", 1101);
      consumePotion(source.data(), "mp1", 1102);
      for (int i = 0; i < 31; i++) source.server.simulation.tick(null, null);
      CharData.PotionCheckpoint potion = source.data().capturePotionCheckpoint();
      assertNotEquals(0, potion.seed);
      assertTrue(potion.healthFrames > 0);
      assertTrue(potion.manaFrames > 0);
      source.server.writeReplayCheckpoint(path);
      expected = continuePotions(source);
    }
    assertEquals(expected, resumePotions(path));
    Path output = temporary.getRoot().toPath().resolve("potion-child.log");
    Process process = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp",
        System.getProperty("java.class.path"), Probe.class.getName(), path.toString(), "0",
        Riiablo.home.path(), "potions").redirectErrorStream(true).redirectOutput(output.toFile()).start();
    try {
      assertTrue("potion recovery child JVM timed out", process.waitFor(45, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, process.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally { if (process.isAlive()) process.destroyForcibly(); }
  }

  @Test public void legacyNonemptyD2sArchivesReadWithoutInventingPotionState() throws Exception {
    try (Fixture source = new Fixture()) {
      source.populate();
      byte[] save = source.server.captureReplayCheckpoint().d2sCheckpoint().entry(0).bytes();
      D2SCheckpoint legacyD2s = D2SCheckpoint.of(java.util.Collections.singletonMap(0,
          D2SCheckpoint.entry(0, 0, save)));
      D2gsCheckpointSnapshot expected = D2gsCheckpointSnapshot.fromPayloads(77,
          java.util.Collections.emptyMap(), null, null, legacyD2s, null);
      for (int version : new int[] {1, 2, 3, 4, 5, 6}) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeInt(0x44324743);
        out.writeInt(version);
        out.writeLong(77);
        out.writeInt(0); // ECS
        out.writeInt(0); // compatibility RNG
        out.writeInt(0); // native RNG
        out.writeInt(1); // D2S
        out.writeInt(0);
        out.writeInt(0);
        out.writeInt(save.length);
        out.write(save);
        if (version >= 3) out.writeBoolean(false); // absent potion state
        out.writeInt(version == 1 ? 0 : -1); // absent sessions
        if (version >= 3) out.writeBoolean(false); // absent world identity
        if (version >= 4) out.writeInt(0); // absent missile runtime
        if (version >= 5) out.writeBoolean(false); // absent allocator
        if (version >= 6) out.writeInt(-1); // absent unit-state runtime
        for (String witness : new String[] {expected.summary().canonical(), legacyD2s.canonical()}) {
          byte[] encoded = witness.getBytes(StandardCharsets.UTF_8);
          out.writeInt(encoded.length);
          out.write(encoded);
        }
        D2gsCheckpointSnapshot decoded = D2gsCheckpointSnapshot.fromBytes(bytes.toByteArray());
        assertEquals(expected.summary().canonical(), decoded.summary().canonical());
        assertArrayEquals(save, decoded.d2sCheckpoint().entry(0).bytes());
        assertNull(decoded.d2sCheckpoint().entry(0).potionCheckpoint());
        assertNull(decoded.worldIdentity());
      }
    }
  }

  @Test public void potionMetadataIsIntegrityCheckedAndChangesTheReplaySummary() {
    try (Fixture source = new Fixture()) {
      source.populate();
      D2gsCheckpointSnapshot before = source.server.captureReplayCheckpoint();
      source.data().restorePotionCheckpoint(new CharData.PotionCheckpoint(42, 10, 30, 20, 40));
      D2gsCheckpointSnapshot changed = source.server.captureReplayCheckpoint();
      assertArrayEquals(before.d2sCheckpoint().entry(0).bytes(), changed.d2sCheckpoint().entry(0).bytes());
      assertNotEquals(before.summary().canonical(), changed.summary().canonical());
      D2gsCheckpointSnapshot minimal = D2gsCheckpointSnapshot.fromPayloads(0,
          java.util.Collections.emptyMap(), null, null, changed.d2sCheckpoint(), null);
      int potionPresence = 44 + changed.d2sCheckpoint().entry(0).bytes().length;
      byte[] badSeed = minimal.toBytes();
      badSeed[potionPresence + 4] ^= 1;
      byte[] badFrames = minimal.toBytes();
      ByteBuffer.wrap(badFrames).putInt(potionPresence + 9, -1);
      byte[] badPresence = minimal.toBytes();
      badPresence[potionPresence] = 2;
      for (byte[] corrupt : new byte[][] {badSeed, badFrames, badPresence}) {
        try {
          D2gsCheckpointSnapshot.fromBytes(corrupt);
          fail("corrupt potion metadata must be rejected");
        } catch (IllegalArgumentException expected) {}
      }
    }
  }

  private static void consumePotion(CharData character, String code, int id) {
    Item potion = new ItemGenerator().generateStartItem(code, id, -1);
    assertTrue(character.getItems().addToInventory(potion));
    assertTrue(character.useInventoryPotion(potion));
  }

  private static String resumePotions(Path path) throws Exception {
    try (Fixture target = new Fixture(true)) {
      D2gsCheckpointSnapshot checkpoint = D2gsCheckpointStore.read(path);
      assertTrue(target.server.restoreReplayCheckpoint(path).success());
      assertEquals(checkpoint.d2sCheckpoint().entry(0).potionCheckpoint().canonical(),
          target.data().capturePotionCheckpoint().canonical());
      // Do not call World.process() for subscription activation: that would
      // consume a potion frame outside the restored authoritative clock.
      return continuePotions(target);
    }
  }

  private static String continuePotions(Fixture fixture) {
    StringBuilder result = new StringBuilder("POTIONS_CONTINUED");
    for (int i = 1; i <= 400; i++) {
      if (i == 6) {
        consumePotion(fixture.data(), "hp1", 1201);
        consumePotion(fixture.data(), "mp1", 1202);
      }
      fixture.server.simulation.tick(null, null);
      if (i == 1 || i == 6 || i == 97 || i == 161 || i == 225 || i == 353 || i == 400) {
        result.append('|').append(fixture.server.simulation.tickNumber()).append(':')
            .append(fixture.data().getStats().get(Stat.hitpoints).encodedValues()).append(':')
            .append(fixture.data().getStats().get(Stat.mana).encodedValues()).append(':')
            .append(fixture.data().capturePotionCheckpoint().canonical());
      }
    }
    assertEquals(0, fixture.data().capturePotionCheckpoint().healthFrames);
    assertEquals(0, fixture.data().capturePotionCheckpoint().manaFrames);
    D2gsCheckpointSnapshot after = fixture.server.captureReplayCheckpoint();
    return result.append('|').append(after.summary().canonical()).append('|')
        .append(after.d2sCheckpoint().canonical()).toString();
  }

  public static final class Probe {
    public static void main(String[] args) throws Exception {
      System.setProperty("d2Home", args[2]);
      loadResources();
      try { System.out.println(args.length > 3 && "sand-leaper".equals(args[3])
          ? resumeSandLeaper(Path.of(args[0])) : args.length > 3 && "sand-maggot".equals(args[3])
          ? resumeSandMaggot(Path.of(args[0])) : args.length > 3 && "legacy-baboon".equals(args[3])
          ? resumeLegacyBaboon(Path.of(args[0])) : args.length > 3 && args[3].startsWith("food-decisions-")
          ? resumeFoodDecisions(Path.of(args[0]), Integer.parseInt(args[3].substring("food-decisions-".length())))
          : args.length > 3 && "gargoyle-decisions".equals(args[3])
          ? resumeGargoyleDecisions(Path.of(args[0])) : args.length > 3 && "bat-decisions".equals(args[3])
          ? resumeBatDecisions(Path.of(args[0])) : args.length > 3 && "spirit-decisions".equals(args[3])
          ? resumeSpiritDecisions(Path.of(args[0])) : args.length > 3 && "trap-decisions".equals(args[3])
          ? resumeTrapDecisions(Path.of(args[0])) : args.length > 3 && "decisions".equals(args[3])
          ? resumeDecisions(Path.of(args[0])) : args.length > 3 && "monsters".equals(args[3])
          ? resumeMonsters(Path.of(args[0])) : args.length > 3 && "targets".equals(args[3])
          ? resumeTargets(Path.of(args[0])) : args.length > 3 && "states".equals(args[3])
          ? resumeStates(Path.of(args[0])) : args.length > 3 && "allocator".equals(args[3])
          ? resumeAllocator(Path.of(args[0])) : args.length > 3 && "projectiles".equals(args[3])
          ? resumeProjectiles(Path.of(args[0])) : args.length > 3 && "sparse".equals(args[3])
          ? resumeSparse(Path.of(args[0])) : args.length > 3 && "potions".equals(args[3])
              ? resumePotions(Path.of(args[0])) : resume(Path.of(args[0]), Long.parseLong(args[1]))); }
      finally { unloadResources(); }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final D2GS server = new D2GS(new FileHandle("."), 1, 0);
    final ServerEntityFactory factory = new ServerEntityFactory();
    final Map map = new Map(1, 0);

    Fixture() { this(false); }

    Fixture(boolean potions) { this(potions, false); }

    Fixture(boolean potions, boolean projectiles) { this(potions, projectiles, false); }

    Fixture(boolean potions, boolean projectiles, boolean states) {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
          .with(new net.mostlyoriginal.api.event.common.EventSystem(), new ItemManager(),
              new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), factory,
              new NativePlayerTargetSlots(), new CofManager(), new SerializationManager(),
              new com.riiablo.engine.server.Pathfinder());
      if (potions) builder.with(new PotionRecoverySystem());
      if (states) builder.with(new com.riiablo.engine.server.StateUpdater());
      if (projectiles) builder.with(new com.riiablo.engine.server.MissileCollisionSystem());
      server.world = new World(builder.build().register("factory", factory).register("map", map)
          .register("serverContext", new com.riiablo.engine.server.ServerGameplayContext(Riiablo.files, 1))
          .register("townPortalRegistry", new TownPortalRegistry()));
      server.factory = factory;
      server.simulation = new AuthoritativeSimulation(server.world);
      server.setReplayEntityLifecycle(new ServerEntityLifecycle(server.world, factory, Riiablo.files));
      server.installWorldReplayBindings();
    }

    void populate() {
      // Match ordinary Connection: a complete decoded D2S, not the factory's
      // partial createRemote convenience record (which is not a valid save).
      CharData character = CharData.obtain().clear()
          .set(Riiablo.NORMAL, false, "CheckpointHero", Riiablo.AMAZON);
      com.riiablo.codec.excel.CharStats.Entry stats = character.classId.entry();
      character.createTime = character.lastTime = 123456789;
      character.mapSeed = 0x43484B50;
      character.getStats().base().put(Stat.strength, stats.str);
      character.getStats().base().put(Stat.energy, stats._int);
      character.getStats().base().put(Stat.dexterity, stats.dex);
      character.getStats().base().put(Stat.vitality, stats.vit);
      character.getStats().base().put(Stat.statpts, 0);
      character.getStats().base().put(Stat.newskills, 0);
      character.getStats().base().put(Stat.level, 1);
      character.getStats().base().put(Stat.experience, 0);
      character.getStats().base().put(Stat.gold, 4321);
      character.getStats().base().put(Stat.goldbank, 0);
      character.getStats().base().put(Stat.hitpoints, 40f);
      character.getStats().base().put(Stat.maxhp, 40f);
      character.getStats().base().put(Stat.mana, 20f);
      character.getStats().base().put(Stat.maxmana, 20f);
      character.getStats().base().put(Stat.stamina, 60f);
      character.getStats().base().put(Stat.maxstamina, 60f);
      character.getStats().reset();
      character.initializeStartItems(stats);
      character.update();
      byte[] save = new D2SWriter96().writeD2S(D2SWriter96.createD2S(character));
      character = CharData.loadFromBuffer(Riiablo.NORMAL, ByteBuffer.wrap(save));
      character.update();
      assertEquals(0, factory.createPlayer(character, new Vector2(10f, 10f)));
      server.world.process();
      data().setWaypointMask(0, 0, 0x15);
      data().restoreProgressionRevision(4);
      data().getItems().restoreEquipmentRevision(7);
      NativeUnitRandom.unit(server.world, 1, 0).nextUnsigned();
      assertTrue(server.authoritativeSessions().connect(0, "CheckpointHero", 0, 77).accepted);
      server.simulation.restoreFromCheckpoint(77);
    }

    CharData data() { return server.world.getMapper(Player.class).get(0).data; }

    void populateDecisionAi() {
      populateDecisionAi("doomknight1");
    }

    void populateDecisionAi(String row) {
      createDecisionAi(row); updateDecisionAi(0.04f);
    }

    com.riiablo.engine.server.ai.AI createDecisionAi(String row) {
      assertEquals(1, factory.createMonster(Riiablo.files.monstats.get(row), 11f, 10f));
      server.world.process();
      return server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
    }

    void populateRegeneratingBatAi() throws Exception {
      com.riiablo.engine.server.ai.AI ai = createDecisionAi(rowForAi("BatDemon"));
      // Test-only parameters, not a global MonStats edit or a production state codec.
      java.lang.reflect.Field params = com.riiablo.engine.server.ai.AI.class.getDeclaredField("params");
      params.setAccessible(true); params.set(ai, new int[] {0, 0, 100, 0, 4, 0, 0, 0});
      com.riiablo.attributes.Attributes attrs = server.world.getMapper(AttributesWrapper.class).get(1).attrs;
      attrs.base().put(Stat.hpregen, 80); attrs.aggregate().put(Stat.hpregen, 80);
      updateDecisionAi(0.04f);
      assertEquals(120, attrs.base().getValue(Stat.hpregen, 0));
      assertEquals(120, attrs.getValue(Stat.hpregen, 0));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void populateLegacyBaboonAi() throws Exception {
      com.riiablo.engine.server.ai.AI ai = createDecisionAi("baboon1");
      java.lang.Class<?> type = ai.getClass();
      for (String name : new String[] {"aiParam0", "aiParam1", "aiParam2", "time", "nextAction"}) {
        java.lang.reflect.Field field = type.getDeclaredField(name); field.setAccessible(true);
        if (name.equals("time")) field.setFloat(ai, .35f);
        else if (name.equals("nextAction")) field.setFloat(ai, 1.25f);
        else field.setInt(ai, name.equals("aiParam2") ? 11 : 1);
      }
      java.lang.reflect.Field field = type.getDeclaredField("stateMachine"); field.setAccessible(true);
      com.badlogic.gdx.ai.fsm.StateMachine machine = (com.badlogic.gdx.ai.fsm.StateMachine) field.get(ai);
      for (Object state : machine.getCurrentState().getClass().getEnumConstants())
        if (state.toString().equals("REGEN")) machine.changeState((com.badlogic.gdx.ai.fsm.State) state);
      com.riiablo.attributes.Attributes attrs = server.world.getMapper(AttributesWrapper.class).get(1).attrs;
      attrs.base().put(Stat.hitpoints, 50f); attrs.base().put(Stat.maxhp, 100f); attrs.reset();
      attrs.base().put(Stat.hpregen, 51); attrs.aggregate().put(Stat.hpregen, 51);
      server.world.getMapper(com.riiablo.engine.server.component.UnitStates.class).create(1);
      com.artemis.EntityAllocatorCheckpoint.flushStructure(server.world);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void populateBurrowedSandMaggotAi() throws Exception {
      com.riiablo.engine.server.ai.AI ai = createDecisionAi("sandmaggot1");
      java.lang.Class<?> type = ai.getClass();
      java.lang.reflect.Field params = com.riiablo.engine.server.ai.AI.class.getDeclaredField("params");
      params.setAccessible(true); params.set(ai, new int[] {100, 0, 3, 0, 47, 0, 0, 0});
      for (String name : new String[] {"aiParam0", "aiParam1", "aiParam2", "time", "nextAction"}) {
        java.lang.reflect.Field field = type.getDeclaredField(name); field.setAccessible(true);
        if (name.equals("time")) field.setFloat(ai, .35f);
        else if (name.equals("nextAction")) field.setFloat(ai, 1.25f);
        else field.setInt(ai, name.equals("aiParam0") ? 3 : name.equals("aiParam1") ? 47 : 2);
      }
      java.lang.reflect.Field field = type.getDeclaredField("stateMachine"); field.setAccessible(true);
      com.badlogic.gdx.ai.fsm.StateMachine machine = (com.badlogic.gdx.ai.fsm.StateMachine) field.get(ai);
      for (Object state : machine.getCurrentState().getClass().getEnumConstants())
        if (state.toString().equals("BURROW")) machine.changeState((com.badlogic.gdx.ai.fsm.State) state);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void populateRecoveringSandLeaperAi() throws Exception {
      com.riiablo.engine.server.ai.AI ai = createDecisionAi(rowForAi("SandLeaper"));
      java.lang.reflect.Field params = com.riiablo.engine.server.ai.AI.class.getDeclaredField("params");
      params.setAccessible(true); params.set(ai, new int[] {0, 100, 0, 0, 0, 0, 0, 0});
      java.lang.reflect.Field sleep = com.riiablo.engine.server.ai.AI.class.getDeclaredField("SLEEP");
      sleep.setAccessible(true); sleep.setFloat(ai, .2f);
      java.lang.reflect.Field time = ai.getClass().getDeclaredField("time"); time.setAccessible(true); time.setFloat(ai, .35f);
      java.lang.reflect.Field field = ai.getClass().getDeclaredField("stateMachine"); field.setAccessible(true);
      com.badlogic.gdx.ai.fsm.StateMachine machine = (com.badlogic.gdx.ai.fsm.StateMachine) field.get(ai);
      for (Object state : machine.getCurrentState().getClass().getEnumConstants())
        if (state.toString().equals("ATTACK")) machine.changeState((com.badlogic.gdx.ai.fsm.State) state);
    }

    void populateFoodAi(int lifetime) throws Exception {
      com.riiablo.engine.server.ai.AI ai = createDecisionAi("regurgitator1");
      World world = server.world;
      // Another native-decision actor supplies the soft corpse, without adding a legacy FSM dependency.
      assertEquals(2, factory.createMonster(Riiablo.files.monstats.get("regurgitator1"), 12f, 10f));
      world.getMapper(AttributesWrapper.class).get(2).attrs.base().put(Stat.hitpoints, 0f);
      world.getMapper(AttributesWrapper.class).get(2).attrs.reset();
      world.getMapper(com.riiablo.engine.server.component.CofReference.class).get(2).mode =
          com.riiablo.engine.Engine.Monster.MODE_DD;
      if (lifetime == 0) world.getMapper(NativePathIdentity.class).remove(2);
      world.process();
      java.lang.reflect.Field params = com.riiablo.engine.server.ai.AI.class.getDeclaredField("params");
      params.setAccessible(true); params.set(ai, new int[] {0, 100, 0, 0, 100, 30, 0, 0});
      java.lang.reflect.Field sleep = com.riiablo.engine.server.ai.AI.class.getDeclaredField("SLEEP");
      sleep.setAccessible(true); sleep.setFloat(ai, 0.04f);
      updateDecisionAi(0.04f); updateDecisionAi(0.04f);
      assertEquals(3, foodField(this, "phase")); assertEquals(2, foodField(this, "food"));
      if (lifetime == 2) world.getMapper(NativePathIdentity.class).get(2).allocated(world, 2);
    }

    void populateRecoveringGargoyleAi() throws Exception {
      com.riiablo.engine.server.ai.AI ai = createDecisionAi("gargoyletrap");
      java.lang.reflect.Field params = com.riiablo.engine.server.ai.AI.class.getDeclaredField("params");
      params.setAccessible(true); params.set(ai, new int[] {20, 100, 25, 1, 0, 0, 0, 0});
      assertTrue(updateDecisionAi(0.04f));
      assertFalse(updateDecisionAi(0.25f)); // Keep three quarters of the post-shot recovery.
    }

    boolean updateDecisionAi(float delta) {
      World previous = Riiablo.engine;
      try {
        Riiablo.engine = server.world;
        com.riiablo.engine.server.ai.AI ai = server.world.getMapper(com.riiablo.engine.server.component.AIWrapper.class).get(1).ai;
        ai.update(delta);
        boolean action = server.world.getMapper(com.riiablo.engine.server.component.Casting.class).has(1)
            || server.world.getMapper(com.riiablo.engine.server.component.Sequence.class).has(1);
        // Controlled action-completion boundary: Casting/Sequence/path restoration is a later batch.
        server.world.getMapper(com.riiablo.engine.server.component.Casting.class).remove(1);
        server.world.getMapper(com.riiablo.engine.server.component.Sequence.class).remove(1);
        server.world.getMapper(com.riiablo.engine.server.component.Pathfind.class).remove(1);
        ai.onActionReturnedToNeutral();
        com.artemis.EntityAllocatorCheckpoint.flushStructure(server.world);
        return action;
      } finally { Riiablo.engine = previous; }
    }

    void prepareMonsterMap() {
      map.createSyntheticArena(2, 1, 1);
      // The minimal fixture has no zone-transition updater. Settle an already-created
      // player into the newly prebuilt arena, just as creating it in that map would.
      if (server.world.getMapper(Player.class).has(0)) {
        server.world.getMapper(com.riiablo.engine.server.component.MapWrapper.class).get(0).zone =
            map.getZone(server.world.getMapper(Position.class).get(0).position);
      }
    }

    void populateMonsters() {
      prepareMonsterMap();
      for (int id = 1; id <= 2; id++) assertEquals(id,
          factory.createMonster(Riiablo.files.monstats.get("quillrat1"), 20f + id, 10f));
      // Finish normal mode entry before freezing the runtime image to be checkpointed.
      server.world.process();
      World world = server.world; Monster m = world.getMapper(Monster.class).get(1);
      m.nativeOwnerId = 0; m.nativeOwnerIdentity = world.getMapper(NativePathIdentity.class).get(0).token();
      m.nativeRequestedMountId = 2; m.nativeRequestedMountIdentity = world.getMapper(NativePathIdentity.class).get(2).token();
      world.getMapper(NativePathIdentity.class).get(2).allocated(world, 2);
      m.nativeSelfResurrections = 2; m.nativeSelfResurrect = true; m.nativeChargedBolts = true;
      m.setNativePack(37, true); m.setBaalWaveMember(1, 12, 99, true);
      m.baalLinkedOwnerId = 2; m.baalClonesCreated = 1; m.setResurrected(0, false);
      m.chargedBoltsCooldown = 0.37f; m.lightningEnchantedLastFrame = 9007199254740001d;
      m.modeElements = new com.riiablo.engine.server.combat.NativeMonsterModeElements();
      m.modeElements.minimum[1] = 11; m.modeElements.maximum[1] = 23; m.modeElements.poisonLength = 37;
      m.modeElements.drainMinimum[0] = 7; m.modeElements.drainMaximum[0] = 19; m.modeElements.matchedRows = 2;
      Monster corpse = world.getMapper(Monster.class).get(2);
      corpse.fireDeathExplosionScheduled = corpse.coldDeathNovaScheduled = true;
      corpse.shatteredAtDeath = corpse.nativeCorpseCollision = true;
      corpse.nativeRestoredCollisionPattern = true; corpse.frogSmallPresence = true;
      corpse.frogSpawnMarkerInitialized = corpse.frogSpawnMarkerMoved = true;
      corpse.frogSpawnMarkerX = -13; corpse.frogSpawnMarkerY = 49;
      world.getMapper(AttributesWrapper.class).get(2).attrs.base().put(Stat.hitpoints, 0f);
      world.getMapper(AttributesWrapper.class).get(2).attrs.reset();
      world.process();
    }

    void populateTargets() {
      World world = server.world;
      com.riiablo.codec.excel.Skills.Entry pregnancy = null;
      for (com.riiablo.codec.excel.Skills.Entry skill : Riiablo.files.skills) {
        if (skill.srvdofunc == 133) { pregnancy = skill; break; }
      }
      assertNotNull(pregnancy);
      for (int id = 1; id <= 5; id++) {
        assertEquals(id, factory.createMonster(Riiablo.files.monstats.get("quillrat1"), 20f + id, 10f));
        AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(id);
        wrapper.attrs.base().put(Stat.level, 10);
        wrapper.attrs.base().put(Stat.hitpoints, id == 1 || id == 5 ? 50f : 0f);
        wrapper.attrs.base().put(Stat.maxhp, 100f); wrapper.attrs.reset();
        StateList states = world.getMapper(UnitStates.class).get(id).stateList;
        if (id == 1) {
          Monster monster = world.getMapper(Monster.class).get(id);
          monster.converted = true; monster.conversionOwnerId = 0;
          UnitState saved = states.addPermanentState(StateId.CONVERSION_SAVE);
          saved.conversionOriginalLevel = 20; saved.conversionOriginalMaxHpEncoded = 200 * 256;
          NativeConversionLifecycle.bind(states.addState(StateId.CONVERSION, 3, 1, 0), monster, wrapper);
          world.getMapper(NativeAiState.class).create(id).previousMode = 7;
          world.getMapper(NativeAiTargetOverride.class).create(id).setConfuse(0, 76, 12, -793);
        } else {
          if (id == 4) world.getMapper(NativePathIdentity.class).remove(id);
          UnitState state = states.addState(StateId.PREGNANT, id == 5 ? 1 : 2);
          state.skillId = pregnancy.Id;
          NativePregnancyLifecycle.bind(state, world, id, factory, Riiablo.files, pregnancy);
          if (id == 3) world.getMapper(NativePathIdentity.class).get(id).allocated(world, id);
        }
      }
      NativeAiCommand command = world.getMapper(NativeAiCommand.class).create(1).set(0, NativeAiCommand.ATTACK, 2);
      command.expiresAtFrame = 78; // Null lifetime legacy command displaced last.
      command.copy(world, 3, NativeAiCommand.MOVE, 3).expiresAtFrame = 79;
      world.getMapper(NativePathIdentity.class).get(3).allocated(world, 3); // Retain stale owner AND target.
      command.copy(world, 0, NativeAiCommand.ATTACK, 2).expiresAtFrame = 80;
      world.getMapper(NativeAiCommand.class).create(4); // Present NONE differs from absent.
      NativeAiPathCache path = world.getMapper(NativeAiPathCache.class).create(1);
      path.targetCoordinates(357, -91); path.pathPoints(true); path.invalidate();
      world.getMapper(NativeAiPathCache.class).create(2).allocated();
      world.getMapper(NativeAiPathCache.class).create(3).pathPoints(true);
      world.process();
    }

    void populateStates() {
      com.riiablo.engine.server.state.StateList states = server.world.getMapper(
          com.riiablo.engine.server.component.UnitStates.class).get(0).stateList;
      com.riiablo.engine.server.state.UnitState poison = states.addState(
          com.riiablo.engine.server.state.StateId.POISON, 8, 1, -1);
      poison.exactDamagePerFrame = 0.25f; poison.damageType = 4;
      com.riiablo.engine.server.state.UnitState orders = states.addState(
          com.riiablo.engine.server.state.StateId.BATTLEORDERS, 13, 4, -1);
      orders.maxLifeModifier = 37; orders.maxManaModifier = 23;
      orders.setStatContribution(Stat.skill_staminapercent, 0,
          com.riiablo.attributes.NativeStatResolver.Operation.ADD, 31);
      states.addStateLayer(com.riiablo.engine.server.state.StateId.COLD, 6, 1, 14, 7).velocityModifier = -17;
      states.addStateLayer(com.riiablo.engine.server.state.StateId.COLD, 4, 2, 15, 8).animationRateModifier = -12;
      states.addAnonymousStatList(0).setStatContribution(Stat.firemindam, 7,
          com.riiablo.attributes.NativeStatResolver.Operation.ADD, 333);
      states.addAnonymousStatList(0).setStatContribution(Stat.firemindam, 8,
          com.riiablo.attributes.NativeStatResolver.Operation.ADD, 444);
      consumePotion(data(), "mp1", 4400);
      for (int i = 0; i < 3; i++) server.simulation.tick(null, null);
      assertEquals(5, states.getStateDuration(com.riiablo.engine.server.state.StateId.POISON));
    }

    void populateProjectiles() {
      // A mid-game checkpoint must restore a consumed skill combat stream,
      // not merely happen to restart at the same seed before its first hit.
      for (int i = 0; i < 7; i++) server.world.getSystem(
          com.riiablo.engine.server.MissileCollisionSystem.class).checkpointCombatRng().nextInt();
      for (int id = 1; id < 5; id++) assertEquals(id, server.world.create());
      assertEquals(5, factory.createMonster(Riiablo.files.monstats.get("quillrat1").hcIdx, -20f, 10f));
      for (int id = 1; id < 5; id++) server.world.delete(id);
      server.world.process();
      com.badlogic.gdx.utils.IntSet common = new com.badlogic.gdx.utils.IntSet();
      for (int id = 1; id <= 2; id++) {
        assertEquals(id, factory.createMissile(Riiablo.files.Missiles.get("firebolt").Id,
            new Vector2(1f, 0f), new Vector2(0f, 10f), 5));
        Missile missile = server.world.getMapper(Missile.class).get(id);
        assertTrue(com.riiablo.engine.server.missile.MissileDamageResolver.initializeSkill(Riiablo.files,
            missile, Riiablo.files.skills.get("Fire Bolt"), server.world.getMapper(AttributesWrapper.class).get(5).attrs, 3));
        missile.acceleration.restore(4, 64, 50);
        missile.nativeFrame = 7;
        missile.nativeLifetimeFrames = 80;
        missile.range = 100f;
        missile.distanceTraveled = 1.75f;
        missile.shareHitTargets(common);
      }
      for (int i = 0; i < 3; i++) server.simulation.tick(null, null);
      assertEquals(2, server.captureReplayCheckpoint().missileRuntime().size());
    }

    void populateSparseItems() {
      java.util.List<Integer> gaps = new java.util.ArrayList<>();
      for (int id = 1; id < 5; id++) gaps.add(server.world.create());
      createOwnedItem(5, "hp1");
      for (int id = 6; id < 9; id++) gaps.add(server.world.create());
      createOwnedItem(9, "mp1");
      for (int id : gaps) server.world.delete(id);
      server.world.process();
    }

    private void createOwnedItem(int expectedId, String code) {
      assertEquals(expectedId, factory.createItem(new ItemGenerator().generateStartItem(code,
          1000 + expectedId, -1), 12f + expectedId, 10f));
      com.riiablo.engine.server.component.Item item = server.world
          .getMapper(com.riiablo.engine.server.component.Item.class).get(expectedId);
      item.dropOwnerId = 0;
      item.dropOwnerUntilMillis = 123456L;
      item.dropPartyId = 7;
      item.dropPartyUntilMillis = 234567L;
      item.partyShareGold = true;
    }
    @Override public void close() { server.world.dispose(); }
  }
}
