package com.riiablo.server.d2gs;

import static org.junit.Assert.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.engine.AuthoritativeSessionRegistry;
import com.riiablo.engine.D2gsCommandAdapter;
import com.riiablo.engine.replay.CheckpointRngRegistry;
import com.riiablo.engine.replay.NativeUnitRandomRegistry;
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.engine.server.AuthoritativeSimulation;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.component.Flags;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Position;
import com.riiablo.save.D2SCheckpoint;
import com.riiablo.save.D2SCheckpointRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Real serializer + D2GS restore transaction; no native maps or socket transport. */
public class D2gsCheckpointRecoveryTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void missingMetadataAdaptersRejectBeforeChangingEcsOrClock() throws Exception {
    try (Fixture live = new Fixture()) {
      D2gsCheckpointSnapshot before = live.server.captureReplayCheckpoint();
      assertRejected(live, before, D2gsCheckpointSnapshot.fromPayloads(42L, before.payloads(),
          Collections.singletonList(new SimulationCheckpoint.RngState("hit", 0, 12, 3))));
      assertRejected(live, before, D2gsCheckpointSnapshot.fromPayloads(42L, before.payloads(),
          null, Collections.singletonList(
              new SimulationCheckpoint.NativeUnitRngState("unit", 0, 1, 2))));
      assertRejected(live, before, D2gsCheckpointSnapshot.fromPayloads(42L, before.payloads(),
          null, null, D2SCheckpoint.of(Collections.emptyMap())));
    }
  }

  @Test public void emptyRngCheckpointRemovesStreamsAbsentFromTheArchive() throws Exception {
    try (Fixture live = new Fixture()) {
      live.installRegistries();
      D2gsCheckpointSnapshot empty = live.server.captureReplayCheckpoint();
      live.seedStreams();
      assertFalse(live.server.replayRngRegistry().snapshot().isEmpty());
      assertTrue(live.server.restoreReplayCheckpoint(empty).success());
      assertTrue(live.server.replayRngRegistry().snapshot().isEmpty());
      assertTrue(live.server.replayNativeUnitRngRegistry().snapshot().isEmpty());
      assertEquals(empty.summary().canonical(), live.server.captureReplayCheckpoint().summary().canonical());
    }
  }

  @Test public void anotherJvmRestoresRealEcsAndContinuesAfterGenerationTakeover() throws Exception {
    Path archive = temporary.getRoot().toPath().resolve("restart.chk");
    try (Fixture source = new Fixture()) {
      source.installRegistries();
      source.seedStreams();
      source.server.authoritativeSessions().connect(3, "hero", 0, 0L);
      source.server.simulation.restoreFromCheckpoint(42L);
      source.server.writeReplayCheckpoint(archive);
    }
    String expected = continueCheckpoint(archive);
    Path output = temporary.getRoot().toPath().resolve("child.log");
    Process process = new ProcessBuilder(javaBinary(), "-cp", System.getProperty("java.class.path"),
        Probe.class.getName(), archive.toString()).redirectErrorStream(true)
        .redirectOutput(output.toFile()).start();
    try {
      assertTrue("checkpoint child JVM timed out", process.waitFor(30, TimeUnit.SECONDS));
      String log = Files.readString(output, StandardCharsets.UTF_8);
      assertEquals(log, 0, process.exitValue());
      assertTrue(log, log.lines().anyMatch(expected::equals));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  @Test public void lateNativeFactoryFailureRollsBackEcsRngSessionsAndClock() throws Exception {
    try (Fixture source = new Fixture(); Fixture target = new Fixture()) {
      source.installRegistries();
      source.seedStreams();
      source.server.authoritativeSessions().connect(3, "hero", 0, 0L);
      source.server.simulation.restoreFromCheckpoint(42L);
      D2gsCheckpointSnapshot incoming = source.server.captureReplayCheckpoint();

      target.installRegistries();
      target.server.setReplayNativeUnitRngRegistry(new NativeUnitRandomRegistry(state -> {
        throw new IllegalStateException("injected native factory failure");
      }));
      target.server.replayRngRegistry().register(
          NativeRng.fromSnapshot(new NativeRng.Snapshot("hit", 0, 777, 9)));
      target.position().position.set(900, 900);
      target.server.simulation.restoreFromCheckpoint(10L);
      D2gsCheckpointSnapshot before = target.server.captureReplayCheckpoint();
      try {
        target.server.restoreReplayCheckpoint(incoming);
        fail("factory failure must roll back the complete transaction");
      } catch (IllegalStateException expected) {
        assertEquals("checkpoint restore transaction rolled back", expected.getMessage());
      }
      assertEquals(before.summary().canonical(), target.server.captureReplayCheckpoint().summary().canonical());
      assertNull(target.server.replayCheckpoint());
      assertEquals(10L, target.server.simulation.tickNumber());
      assertEquals(900f, target.position().position.x, 0f);
    }
  }

  static String continueCheckpoint(Path archive) throws Exception {
    try (Fixture resumed = new Fixture()) {
      resumed.installRegistries();
      resumed.position().position.set(999, 999);
      D2gsCheckpointSnapshot checkpoint = D2gsCheckpointStore.read(archive);
      assertTrue(resumed.server.restoreReplayCheckpoint(archive).success());
      assertEquals(checkpoint.summary().canonical(),
          resumed.server.captureReplayCheckpoint().summary().canonical());
      D2gsCommandAdapter commands = resumed.commands();
      AuthoritativeSessionRegistry.Session archived = resumed.server.authoritativeSessions().stableKey("hero");
      assertTrue(commands.rebind(3, "hero", 0, checkpoint.tick(), archived.generation).accepted);
      long generation = resumed.server.authoritativeSessions().activeConnection(3).generation;
      assertEquals(D2gsCommandAdapter.Rejection.STALE_SESSION,
          commands.submit(3, archived.generation, "move", 1, checkpoint.tick(), 1, -1).rejection);
      assertTrue(commands.submit(3, generation, "move", 1, checkpoint.tick() + 1, 2, -1).accepted);
      resumed.server.simulation.tick(() -> commands.drain(resumed.server.simulation.tickNumber(),
          command -> resumed.position().position.x += resumed.server.replayRngRegistry()
              .get("hit", 0).nextInt(10) + resumed.server.replayNativeUnitRngRegistry()
              .get("unit", 0).nextInt(10)), null);
      assertEquals(43L, resumed.server.simulation.tickNumber());
      assertEquals(0, commands.pendingCount());
      return "CONTINUED|" + resumed.server.captureReplayCheckpoint().summary().canonical();
    }
  }

  private static void assertRejected(Fixture fixture, D2gsCheckpointSnapshot before,
      D2gsCheckpointSnapshot incoming) {
    try {
      fixture.server.restoreReplayCheckpoint(incoming);
      fail("missing registry must not silently discard checkpoint metadata");
    } catch (IllegalStateException expected) {
      assertTrue(expected.getMessage(), expected.getMessage().contains("registry is not installed"));
    }
    assertEquals(before.summary().canonical(), fixture.server.captureReplayCheckpoint().summary().canonical());
    assertNull(fixture.server.replayCheckpoint());
  }

  static String javaBinary() {
    return Path.of(System.getProperty("java.home"), "bin",
        System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
  }

  public static final class Probe {
    public static void main(String[] args) throws Exception {
      System.out.println(continueCheckpoint(Path.of(args[0])));
    }
  }

  static final class Fixture implements AutoCloseable {
    final D2GS server = new D2GS(new FileHandle("."), 1, 0);

    Fixture() {
      server.world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new CofManager(), new SerializationManager()).build());
      server.simulation = new AuthoritativeSimulation(server.world);
      int id = server.world.create();
      server.world.getMapper(Networked.class).create(id);
      server.world.getMapper(Flags.class).create(id);
      server.world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      server.world.getMapper(Position.class).create(id).position.set(10, 20);
      server.world.process();
    }

    void installRegistries() {
      server.setReplayRngRegistry(new CheckpointRngRegistry());
      server.setReplayNativeUnitRngRegistry(new NativeUnitRandomRegistry(
          state -> NativeUnitRandom.unit(server.world, 1, (int) state.contextId)));
      server.setReplayD2sRegistry(new D2SCheckpointRegistry());
    }

    void seedStreams() {
      server.replayRngRegistry().register(NativeRng.fromSnapshot(new NativeRng.Snapshot("hit", 0, 91, 4)));
      server.replayNativeUnitRngRegistry().register("unit", 0, NativeUnitRandom.unit(server.world, 1, 0));
    }

    Position position() { return server.world.getMapper(Position.class).get(0); }

    D2gsCommandAdapter commands() throws Exception {
      java.lang.reflect.Field field = D2GS.class.getDeclaredField("commandAdapter");
      field.setAccessible(true);
      return (D2gsCommandAdapter) field.get(server);
    }

    @Override public void close() { server.world.dispose(); }
  }
}
