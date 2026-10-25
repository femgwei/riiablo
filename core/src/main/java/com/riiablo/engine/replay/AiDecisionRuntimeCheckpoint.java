package com.riiablo.engine.replay;

import com.artemis.World;
import com.riiablo.engine.server.ai.AiRuntimeState;
import com.riiablo.engine.server.component.AIWrapper;
import java.io.*;

/** AIWrapper absence/null/sentinel/concrete image; the factory still owns object/service binding. */
public final class AiDecisionRuntimeCheckpoint {
  public static final int MAX_BYTES = AiRuntimeState.MAX_BYTES + 16;
  private final byte[] bytes;
  private final int kind;
  private final AiRuntimeState ai;
  private AiDecisionRuntimeCheckpoint(byte[] bytes, int kind, AiRuntimeState ai) {
    this.bytes = bytes; this.kind = kind; this.ai = ai;
  }
  public byte[] bytes() { return bytes.clone(); }
  public String implementation() { return ai == null ? null : ai.type(); }
  public void validateHost(int id) {
    if (ai != null && ai.owner() != id) throw new IllegalArgumentException("AI image host mismatch");
  }
  public static AiDecisionRuntimeCheckpoint capture(World world, int id) {
    AIWrapper wrapper = world.getMapper(AIWrapper.class).get(id);
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(buffer);
      out.writeInt(1); int kind = wrapper == null ? 0 : wrapper.ai == null ? 1 : 2; out.writeByte(kind);
      if (kind == 2) { byte[] ai = AiRuntimeState.capture(wrapper.ai, id).bytes(); out.writeInt(ai.length); out.write(ai); }
      out.flush(); return fromBytes(buffer.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }
  public static AiDecisionRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("AI decision image length");
    byte[] copy = bytes.clone();
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(copy));
      if (in.readInt() != 1) throw new IllegalArgumentException("AI decision image version");
      int kind = in.readUnsignedByte(); if (kind > 2) throw new IllegalArgumentException("AI wrapper presence");
      AiRuntimeState ai = null;
      if (kind == 2) {
        int size = CheckpointIO.count(in, AiRuntimeState.MAX_BYTES);
        if (size == 0 || size > in.available()) throw new IllegalArgumentException("AI image length");
        byte[] nested = new byte[size]; in.readFully(nested); ai = AiRuntimeState.fromBytes(nested);
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing AI decision image");
      return new AiDecisionRuntimeCheckpoint(copy, kind, ai);
    } catch (IOException e) { throw new IllegalArgumentException("truncated AI decision image", e); }
  }
  public void validateRestore(World world, int id) {
    validateHost(id);
    AIWrapper wrapper = world.getMapper(AIWrapper.class).get(id);
    if ((wrapper != null) != (kind != 0) || kind == 1 && wrapper.ai != null) {
      throw new IllegalStateException("AI wrapper factory host mismatch: " + id);
    }
    if (kind == 2) ai.validateRestore(wrapper.ai, id);
  }
  public void restore(World world, int id) {
    validateRestore(world, id);
    if (kind == 2) ai.restore(world.getMapper(AIWrapper.class).get(id).ai, id);
  }
}
