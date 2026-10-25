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

  public static final class Probe {
    public static void main(String[] args) throws Exception {
      System.setProperty("d2Home", args[2]);
      loadResources();
      try { System.out.println(resume(Path.of(args[0]), Long.parseLong(args[1]))); }
      finally { unloadResources(); }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final D2GS server = new D2GS(new FileHandle("."), 1, 0);
    final ServerEntityFactory factory = new ServerEntityFactory();

    Fixture() {
      server.world = new World(new WorldConfigurationBuilder()
          .with(new net.mostlyoriginal.api.event.common.EventSystem(), new ItemManager(),
              new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(), factory,
              new NativePlayerTargetSlots(), new CofManager(), new SerializationManager())
          .build().register("factory", factory).register("map", new Map(1, 0))
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
    @Override public void close() { server.world.dispose(); }
  }
}
