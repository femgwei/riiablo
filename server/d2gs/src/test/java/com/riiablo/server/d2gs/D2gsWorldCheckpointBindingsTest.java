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
      D2gsCheckpointSnapshot checkpoint = source.server.captureReplayCheckpoint();
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
      assertEquals(checkpoint.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
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
          invalid, archive.sessionSnapshot());
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
      for (int version : new int[] {1, 2}) {
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
        out.writeInt(version == 1 ? 0 : -1); // absent sessions
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
      try { System.out.println(args.length > 3 && "sparse".equals(args[3])
          ? resumeSparse(Path.of(args[0])) : args.length > 3 && "potions".equals(args[3])
              ? resumePotions(Path.of(args[0])) : resume(Path.of(args[0]), Long.parseLong(args[1]))); }
      finally { unloadResources(); }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final D2GS server = new D2GS(new FileHandle("."), 1, 0);
    final ServerEntityFactory factory = new ServerEntityFactory();

    Fixture() { this(false); }

    Fixture(boolean potions) {
      WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
          .with(new net.mostlyoriginal.api.event.common.EventSystem(), new ItemManager(),
              new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), factory,
              new NativePlayerTargetSlots(), new CofManager(), new SerializationManager());
      if (potions) builder.with(new PotionRecoverySystem());
      server.world = new World(builder.build().register("factory", factory).register("map", new Map(1, 0))
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
