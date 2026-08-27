package com.riiablo.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.Ping;
import org.junit.jupiter.api.Test;

class SizePrefixedPacketAccumulatorTest {
  private final SizePrefixedPacketAccumulator accumulator =
      new SizePrefixedPacketAccumulator(8, 1024, 4096);
  private final List<byte[]> frames = new ArrayList<>();

  @Test
  void retainsOnePacketSplitAcrossThreeReads() {
    byte[] packet = packet(1, 2, 3, 4, 5);

    append(packet, 0, 2);
    assertEquals(0, drain());
    append(packet, 2, 4);
    assertEquals(0, drain());
    append(packet, 6, packet.length - 6);

    assertEquals(1, drain());
    assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, frames.get(0));
    assertEquals(0, accumulator.pendingBytes());
  }

  @Test
  void emitsMultiplePacketsFromOneRead() {
    byte[] first = packet(10, 11);
    byte[] second = packet(20, 21, 22);
    byte[] combined = new byte[first.length + second.length];
    System.arraycopy(first, 0, combined, 0, first.length);
    System.arraycopy(second, 0, combined, first.length, second.length);

    append(combined, 0, combined.length);

    assertEquals(2, drain());
    assertArrayEquals(new byte[] {10, 11}, frames.get(0));
    assertArrayEquals(new byte[] {20, 21, 22}, frames.get(1));
  }

  @Test
  void emitsCompletePacketAndRetainsHalfOfNextPacket() {
    byte[] first = packet(30);
    byte[] second = packet(40, 41, 42, 43);
    byte[] partial = new byte[first.length + 6];
    System.arraycopy(first, 0, partial, 0, first.length);
    System.arraycopy(second, 0, partial, first.length, 6);

    append(partial, 0, partial.length);
    assertEquals(1, drain());
    assertEquals(6, accumulator.pendingBytes());
    assertEquals(4, accumulator.expectedFrameSize());

    append(second, 6, second.length - 6);
    assertEquals(1, drain());
    assertArrayEquals(new byte[] {40, 41, 42, 43}, frames.get(1));
  }

  @Test
  void rejectsZeroAndOversizedPrefixesAndClearsCorruptStream() {
    append(prefix(0), 0, 4);
    assertThrows(SizePrefixedPacketAccumulator.InvalidFrameException.class, this::drain);
    assertEquals(0, accumulator.pendingBytes());

    append(prefix(1025), 0, 4);
    assertThrows(SizePrefixedPacketAccumulator.InvalidFrameException.class, this::drain);
    assertEquals(0, accumulator.pendingBytes());
  }

  @Test
  void applicationFailureDoesNotCorruptFollowingPacketBoundary() {
    byte[] first = packet(50, 51);
    byte[] second = packet(60, 61);
    byte[] combined = new byte[first.length + second.length];
    System.arraycopy(first, 0, combined, 0, first.length);
    System.arraycopy(second, 0, combined, first.length, second.length);
    append(combined, 0, combined.length);

    assertThrows(IllegalStateException.class,
        () -> accumulator.drain(frame -> { throw new IllegalStateException("application"); }));
    assertEquals(second.length, accumulator.pendingBytes());

    assertEquals(1, drain());
    assertArrayEquals(new byte[] {60, 61}, frames.get(0));
  }

  @Test
  void emittedPayloadIsDirectlyParseableAsRealD2gsFlatBuffer() {
    FlatBufferBuilder builder = new FlatBufferBuilder();
    int ping = Ping.createPing(builder, 73, 1000L, 2000L, true);
    int root = D2GS.createD2GS(builder, D2GSData.Ping, ping);
    D2GS.finishSizePrefixedD2GSBuffer(builder, root);
    ByteBuffer encoded = builder.dataBuffer();
    byte[] bytes = new byte[encoded.remaining()];
    encoded.get(bytes);

    // Split inside the FlatBuffer payload, matching a normal TCP short read.
    append(bytes, 0, 7);
    assertEquals(0, accumulator.drain(frame -> {}));
    append(bytes, 7, bytes.length - 7);

    assertEquals(1, accumulator.drain(frame -> {
      D2GS packet = D2GS.getRootAsD2GS(frame);
      assertEquals(D2GSData.Ping, packet.dataType());
      Ping decoded = (Ping) packet.data(new Ping());
      assertEquals(73, decoded.tickCount());
      assertEquals(1000L, decoded.sendTime());
      assertEquals(2000L, decoded.processTime());
      assertEquals(true, decoded.ack());
    }));
  }

  private int drain() {
    return accumulator.drain(frame -> {
      byte[] bytes = new byte[frame.remaining()];
      frame.get(bytes);
      frames.add(bytes);
    });
  }

  private void append(byte[] bytes, int offset, int length) {
    accumulator.append(bytes, offset, length);
  }

  private static byte[] packet(int... payload) {
    ByteBuffer buffer = ByteBuffer.allocate(4 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(payload.length);
    for (int value : payload) buffer.put((byte) value);
    return buffer.array();
  }

  private static byte[] prefix(int value) {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
  }
}
