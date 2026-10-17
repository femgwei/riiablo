package com.riiablo.engine.server.quest;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Versioned binary codec for the world-owned quest RNG state. */
public final class NativeQuestRandomSnapshotCodec {
  private static final int MAGIC = 0x51524E47; // QRNG
  private static final int VERSION = 1;

  private NativeQuestRandomSnapshotCodec() {}

  public static void write(DataOutput out, NativeQuestRandomState.Snapshot snapshot)
      throws IOException {
    if (out == null || snapshot == null) throw new NullPointerException();
    out.writeInt(MAGIC);
    out.writeInt(VERSION);
    out.writeInt(snapshot.lowSeed);
    out.writeInt(snapshot.highSeed);
  }

  public static NativeQuestRandomState.Snapshot read(DataInput in) throws IOException {
    if (in == null) throw new NullPointerException("in");
    if (in.readInt() != MAGIC) throw new IOException("invalid quest RNG snapshot magic");
    if (in.readInt() != VERSION) throw new IOException("unsupported quest RNG snapshot version");
    return new NativeQuestRandomState.Snapshot(in.readInt(), in.readInt());
  }
}
