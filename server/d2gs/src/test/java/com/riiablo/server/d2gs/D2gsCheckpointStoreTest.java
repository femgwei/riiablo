package com.riiablo.server.d2gs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.riiablo.engine.AuthoritativeSessionRegistry;
import com.riiablo.engine.replay.SimulationCheckpoint;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/** Verifies the durable process hand-off around the versioned checkpoint codec. */
public class D2gsCheckpointStoreTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void writesAtomicallyAndRoundTripsThroughAnotherJvm() throws Exception {
    Path directory = temporary.getRoot().toPath();
    Path path = directory.resolve("active.chk");
    AuthoritativeSessionRegistry sessions = new AuthoritativeSessionRegistry();
    assertTrue(sessions.connect(3, "hero", 17, 10L).accepted);
    D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.fromPayloads(42L,
        Collections.singletonMap(17, "entity-state".getBytes(StandardCharsets.US_ASCII)),
        Collections.singletonList(new SimulationCheckpoint.RngState("combat.hit", 17, 91, 4L)),
        Collections.singletonList(new SimulationCheckpoint.NativeUnitRngState("unit.hit", 17,
            12, 34)), null, sessions.snapshot());

    D2gsCheckpointStore.write(path, checkpoint);
    assertEquals(checkpoint.summary().canonical(),
        D2gsCheckpointStore.read(path).summary().canonical());

    Path log = directory.resolve("probe.log");
    Process process = new ProcessBuilder(D2gsCheckpointRecoveryTest.javaBinary(), "-cp", System.getProperty("java.class.path"),
        D2gsCheckpointStoreProcessProbe.class.getName(), path.toString())
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
    try {
      assertTrue("checkpoint child JVM timed out", process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
      String output = Files.readString(log, StandardCharsets.UTF_8).trim();
      assertEquals(output, 0, process.exitValue());
      assertEquals(checkpoint.summary().canonical(), output);
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }

    D2gsCheckpointSnapshot replacement = D2gsCheckpointSnapshot.fromPayloads(43L,
        checkpoint.payloads());
    D2gsCheckpointStore.write(path, replacement);
    assertEquals(replacement.summary().canonical(), D2gsCheckpointStore.read(path).summary().canonical());
    try (java.util.stream.Stream<Path> files = Files.list(directory)) {
      assertFalse(files.anyMatch(file -> file.getFileName().toString().contains(".tmp-")));
    }
  }

  @Test public void corruptReplacementIsRejectedAndOldCheckpointRemainsReadable() throws Exception {
    Path directory = temporary.getRoot().toPath();
    Path path = directory.resolve("active.chk");
    D2gsCheckpointSnapshot original = D2gsCheckpointSnapshot.fromPayloads(1L,
        Collections.singletonMap(1, new byte[] {1, 2, 3}));
    D2gsCheckpointStore.write(path, original);
    byte[] corrupted = Files.readAllBytes(path);
    corrupted[corrupted.length - 1] ^= 1;
    Path incoming = directory.resolve("incoming.chk");
    Files.write(incoming, corrupted);
    boolean rejected = false;
    try {
      D2gsCheckpointStore.read(incoming);
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    assertTrue(rejected);
    assertEquals(original.summary().canonical(), D2gsCheckpointStore.read(path).summary().canonical());
    assertFalse(Files.exists(directory.resolve("active.chk.tmp")));
  }

  @Test public void failedPublishCleansTemporaryFileAndPreservesExistingTarget() throws Exception {
    Path target = temporary.newFolder("occupied").toPath();
    Path marker = target.resolve("must-remain.txt");
    Files.writeString(marker, "original");
    try {
      D2gsCheckpointStore.write(target,
          D2gsCheckpointSnapshot.fromPayloads(0L, Collections.emptyMap()));
      org.junit.Assert.fail("a checkpoint cannot replace a nonempty directory");
    } catch (java.io.IOException expected) {
      assertEquals("original", Files.readString(marker));
    }
    try (java.util.stream.Stream<Path> files = Files.list(temporary.getRoot().toPath())) {
      assertFalse(files.anyMatch(file -> file.getFileName().toString().contains(".tmp-")));
    }
  }

}

/** Separate JVM probe used by the process-boundary test above. */
final class D2gsCheckpointStoreProcessProbe {
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("checkpoint path required");
    System.out.print(D2gsCheckpointStore.read(Path.of(args[0])).summary().canonical());
  }
}
