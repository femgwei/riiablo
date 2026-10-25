package com.riiablo.engine.replay;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Bounded primitive readers shared by explicit runtime checkpoint codecs. */
public final class CheckpointIO {
  private CheckpointIO() {}
  public static int count(DataInputStream in, int max) throws IOException {
    int value = in.readInt();
    if (value < 0 || value > max) throw new IllegalArgumentException("checkpoint count");
    return value;
  }
  public static boolean readBoolean(DataInputStream in) throws IOException {
    int value = in.readUnsignedByte();
    if (value > 1) throw new IllegalArgumentException("checkpoint boolean");
    return value == 1;
  }
  public static void writeFloat(DataOutputStream out, float value) throws IOException {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite checkpoint value");
    out.writeInt(Float.floatToRawIntBits(value));
  }
  public static float readFloat(DataInputStream in) throws IOException {
    float value = Float.intBitsToFloat(in.readInt());
    if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite checkpoint value");
    return value;
  }
  public static void writeString(DataOutputStream out, String value) throws IOException {
    out.writeBoolean(value != null);
    if (value == null) return;
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 4096) throw new IllegalArgumentException("checkpoint string");
    out.writeInt(bytes.length); out.write(bytes);
  }
  public static String readString(DataInputStream in) throws IOException {
    if (!readBoolean(in)) return null;
    byte[] bytes = new byte[count(in, 4096)]; in.readFully(bytes);
    String value = new String(bytes, StandardCharsets.UTF_8);
    if (!java.util.Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) {
      throw new IllegalArgumentException("checkpoint UTF-8");
    }
    return value;
  }
}
