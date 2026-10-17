package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class NativeQuestRandomSnapshotCodecTest {
  @Test void roundTripPreservesBothSeedWords() throws Exception {
    NativeQuestRandomState.Snapshot source =
        new NativeQuestRandomState.Snapshot(0x89abcdef, 0xfedcba98);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    NativeQuestRandomSnapshotCodec.write(new DataOutputStream(bytes), source);
    NativeQuestRandomState.Snapshot copy = NativeQuestRandomSnapshotCodec.read(
        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    assertEquals(source.lowSeed, copy.lowSeed);
    assertEquals(source.highSeed, copy.highSeed);
  }

  @Test void rejectsUnknownVersion() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    out.writeInt(0x51524E47);
    out.writeInt(99);
    assertThrows(IOException.class, () -> NativeQuestRandomSnapshotCodec.read(
        new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
  }
}
