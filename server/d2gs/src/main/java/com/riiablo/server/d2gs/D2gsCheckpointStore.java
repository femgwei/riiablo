package com.riiablo.server.d2gs;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable hand-off for a complete D2GS checkpoint envelope.
 *
 * <p>The store intentionally knows nothing about ECS or registries.  It only
 * provides the process boundary: bytes are written and flushed to a sibling
 * temporary file, then renamed into place.  Readers always decode through the
 * versioned {@link D2gsCheckpointSnapshot} codec, so a torn or tampered file
 * cannot be accepted as a partial restore.</p>
 */
public final class D2gsCheckpointStore {
  private D2gsCheckpointStore() {}

  /** Atomically replaces {@code path} with a flushed complete checkpoint. */
  public static void write(Path path, D2gsCheckpointSnapshot checkpoint) throws IOException {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(checkpoint, "checkpoint");
    Path absolute = path.toAbsolutePath();
    Path parent = absolute.getParent();
    if (parent == null) throw new IOException("checkpoint path has no parent: " + path);
    Files.createDirectories(parent);
    Path temporary = parent.resolve(absolute.getFileName().toString()
        + ".tmp-" + UUID.randomUUID());
    boolean moved = false;
    try {
      byte[] encoded = checkpoint.toBytes();
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE)) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(encoded);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
      }
      // Fail closed on filesystems without atomic rename. A non-atomic fallback
      // could destroy the last usable checkpoint during replacement.
      Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      moved = true;
    } finally {
      if (!moved) Files.deleteIfExists(temporary);
    }
  }

  /** Reads and integrity-validates a complete checkpoint from disk. */
  public static D2gsCheckpointSnapshot read(Path path) throws IOException {
    Objects.requireNonNull(path, "path");
    return D2gsCheckpointSnapshot.fromBytes(Files.readAllBytes(path));
  }
}
