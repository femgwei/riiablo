package com.d2moo.testutil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UpstreamTestUtilitiesTest {
  @TempDir Path directory;
  private void write(byte[] data) throws IOException {
    Path path = directory.resolve("data/global/excel/sample.bin");
    Files.createDirectories(path.getParent()); Files.write(path, data);
  }

  @Test void readsDeclaredLittleEndianRecordsAndIgnoresTrailingBytes() throws IOException {
    write(ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).putInt(2)
        .putShort((short) 0x1234).putShort((short) 0xffff).putShort((short) 99).array());
    ByteBuffer records = UpstreamTestUtilities.readBinFile(directory, "sample.bin", 2);
    assertEquals(4, records.remaining()); assertEquals(0x1234, records.getShort());
    assertEquals(-1, records.getShort());
    assertThrows(ReadOnlyBufferException.class, () -> records.put(0, (byte) 1));
  }

  @Test void rejectsMissingTruncatedNegativeAndOverflowingCounts() throws IOException {
    assertThrows(IOException.class, () -> UpstreamTestUtilities.readBinFile(directory, "sample.bin", 1));
    for (byte[] data : new byte[][] {new byte[3], {1,0,0,0}, {-1,-1,-1,-1}, {-1,-1,-1,127}}) {
      write(data);
      assertThrows(IOException.class, () -> UpstreamTestUtilities.readBinFile(directory, "sample.bin", 8));
    }
    write(new byte[4]);
    assertEquals(0, UpstreamTestUtilities.readBinFile(directory, "sample.bin", 8).remaining());
    assertThrows(IllegalArgumentException.class,
        () -> UpstreamTestUtilities.readBinFile(directory, "sample.bin", 0));
  }

  @Test void unsignedRandomCoversFullDomainEndpointsAndSingletons() {
    Random values = new Random() {
      int calls;
      @Override public int nextInt() { return calls++ == 0 ? 0 : -1; }
    };
    assertEquals(0, UpstreamTestUtilities.randomUnsignedInteger(values, 0, 0xffffffffL));
    assertEquals(0xffffffffL, UpstreamTestUtilities.randomUnsignedInteger(values, 0, 0xffffffffL));
    assertEquals(0xffffffffL, UpstreamTestUtilities.randomUnsignedInteger(values, 0xffffffffL, 0xffffffffL));
    assertThrows(IllegalArgumentException.class, () -> UpstreamTestUtilities.randomUnsignedInteger(values, -1, 2));
    assertThrows(IllegalArgumentException.class, () -> UpstreamTestUtilities.randomUnsignedInteger(values, 3, 2));
    assertThrows(IllegalArgumentException.class, () -> UpstreamTestUtilities.randomUnsignedInteger(values, 0, 1L << 32));
  }

  @Test void rejectionSamplingDiscardsModuloBiasTail() {
    Random values = new Random() {
      int calls;
      @Override public int nextInt() { return calls++ == 0 ? -1 : 2; }
    };
    assertEquals(12, UpstreamTestUtilities.randomUnsignedInteger(values, 10, 12));
    Random seeded = new Random(123);
    for (int i = 0; i < 1000; i++) {
      long value = UpstreamTestUtilities.randomUnsignedInteger(seeded, 0x80000000L, 0xffffffffL);
      assertTrue(value >= 0x80000000L && value <= 0xffffffffL);
    }
  }
}
