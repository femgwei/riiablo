package com.d2moo.testutil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** Test-only counterparts of D2MOO TestUtilities aec1395/da15a17.
 * Randomness is fixture data, never a native D2Seed or a compatible MT19937 stream. */
public final class UpstreamTestUtilities {
  private UpstreamTestUtilities() {}

  /** Inclusive uint32 bounds, represented without narrowing as Java longs. */
  public static long randomUnsignedInteger(Random random, long min, long max) {
    if (min < 0 || max > 0xffffffffL || min > max) {
      throw new IllegalArgumentException("ordered uint32 bounds required");
    }
    long range = max - min + 1, domain = 1L << 32;
    long limit = domain - domain % range;
    long value;
    do { value = Integer.toUnsignedLong(random.nextInt()); } while (value >= limit);
    return min + value % range;
  }

  /** Raw fixed-width records; Java callers supply their own typed record decoder.
   * Unlike the upstream stream helper, malformed/truncated files fail explicitly.
   * Trailing bytes are allowed, as upstream only reads the declared records. */
  public static ByteBuffer readBinFile(Path workingDirectory, String filename, int recordSize)
      throws IOException {
    if (recordSize <= 0) throw new IllegalArgumentException("positive record size required");
    byte[] bytes = Files.readAllBytes(workingDirectory.resolve("data/global/excel").resolve(filename));
    if (bytes.length < 4) throw new IOException("missing bin record count");
    ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    int count = data.getInt();
    long length = (long) count * recordSize;
    if (count < 0 || length > bytes.length - 4) throw new IOException("invalid or truncated bin records");
    data.limit(4 + (int) length);
    return data.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
  }
}
