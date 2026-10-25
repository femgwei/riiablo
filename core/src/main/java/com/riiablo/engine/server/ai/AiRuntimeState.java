package com.riiablo.engine.server.ai;

import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.riiablo.engine.replay.CheckpointIO;
import java.io.*;
import java.util.*;

/** Explicit, exact-class AI schemas. Never serialize injected services or arbitrary object graphs. */
public final class AiRuntimeState {
  public static final int MAX_BYTES = 16384;
  private final byte[] bytes;
  private final String type, configuration;
  private final int owner;
  private AiRuntimeState(byte[] bytes, String type, String configuration, int owner) {
    this.bytes = bytes; this.type = type; this.configuration = configuration; this.owner = owner;
  }
  public byte[] bytes() { return bytes.clone(); }
  public int owner() { return owner; }
  public String type() { return type; }

  public static AiRuntimeState capture(AI ai, int id) {
    if (ai == null || id < 0 || ai != AI.IDLE && ai.entityId != id) throw new IllegalStateException("AI owner mismatch");
    String type = ai.getClass().getSimpleName();
    AI schema = schema(type, ai.checkpointConfiguration(), id);
    if (schema.getClass() != ai.getClass() || ai instanceof Idle && ai != AI.IDLE) {
      throw new IllegalStateException("unsupported AI checkpoint class: " + ai.getClass().getName());
    }
    validateMachineOwner(ai);
    try {
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(buffer);
      out.writeInt(1); out.writeInt(id); CheckpointIO.writeString(out, type);
      CheckpointIO.writeString(out, ai.checkpointConfiguration());
      if (ai != AI.IDLE) { ai.writeRuntime(out); writeSpecific(ai, out); }
      out.flush(); return fromBytes(buffer.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  public static AiRuntimeState fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("AI runtime length");
    byte[] copy = bytes.clone();
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(copy));
      if (in.readInt() != 1) throw new IllegalArgumentException("AI runtime version");
      int id = in.readInt(); if (id < 0) throw new IllegalArgumentException("AI runtime owner");
      String type = CheckpointIO.readString(in), configuration = CheckpointIO.readString(in);
      AI prototype = schema(type, configuration, id); // Constructors only, never inject/initialize/update or draw RNG.
      if (prototype != AI.IDLE) { prototype.readRuntime(in, false); readSpecific(prototype, in, false); }
      if (in.read() != -1) throw new IllegalArgumentException("trailing AI runtime");
      return new AiRuntimeState(copy, type, configuration, id);
    } catch (IOException e) { throw new IllegalArgumentException("truncated AI runtime", e); }
  }

  public void validateRestore(AI ai, int id) {
    if (ai == null || id != owner || ai != AI.IDLE && ai.entityId != id
        || ai.getClass() != schema(type, configuration, id).getClass()
        || !Objects.equals(configuration, ai.checkpointConfiguration())
        || ai instanceof Idle && ai != AI.IDLE) throw new IllegalStateException("AI runtime implementation/owner mismatch: " + id);
    validateMachineOwner(ai); read(ai, false);
  }
  public void restore(AI ai, int id) { validateRestore(ai, id); read(ai, true); }
  private void read(AI ai, boolean apply) {
    if (ai == AI.IDLE) return; // Shared sentinel has no entity-owned state; never mutate it.
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      in.readInt(); in.readInt(); CheckpointIO.readString(in); CheckpointIO.readString(in);
      ai.readRuntime(in, apply); readSpecific(ai, in, apply);
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  private static AI schema(String type, String configuration, int id) {
    if (type == null || configuration != null && !"GenericMonster".equals(type)) throw new IllegalArgumentException("AI schema identity");
    switch (type) {
      case "Idle": return AI.IDLE;
      case "QuillRat": return new QuillRat(id);
      case "Scarab": return new Scarab(id);
      case "Fallen": return new Fallen(id);
      case "Brute": return new Brute(id);
      case "Zombie": return new Zombie(id);
      case "Skeleton": return new Skeleton(id);
      case "GenericMonster": return new GenericMonster(id, configuration);
      case "Andariel": return new Andariel(id);
      case "DoomKnight": return new DoomKnight(id);
      case "FlyingScimitar": return new FlyingScimitar(id);
      case "BaalMinion": return new BaalMinion(id);
      case "ReanimatedHorde": return new ReanimatedHorde(id);
      case "PutridDefiler": return new PutridDefiler(id);
      case "VileDog": return new VileDog(id);
      default: throw new IllegalArgumentException("unsupported AI checkpoint schema: " + type);
    }
  }

  private static void validateMachineOwner(AI ai) {
    StateMachine<Integer, ?> machine = ai instanceof QuillRat ? ((QuillRat) ai).stateMachine
        : ai instanceof Scarab ? ((Scarab) ai).stateMachine : ai instanceof Fallen ? ((Fallen) ai).stateMachine
        : ai instanceof Brute ? ((Brute) ai).stateMachine : ai instanceof Zombie ? ((Zombie) ai).stateMachine
        : ai instanceof Skeleton ? ((Skeleton) ai).stateMachine : null;
    if (machine != null && (machine.getClass() != CheckpointStateMachine.class
        || !Objects.equals(((CheckpointStateMachine<?>) machine).getOwner(), ai.entityId))) {
      throw new IllegalStateException("AI state machine owner mismatch");
    }
  }

  private static void writeSpecific(AI ai, DataOutputStream out) throws IOException {
    if (ai instanceof QuillRat) { QuillRat a = (QuillRat) ai; fsm(out, a.stateMachine, a.nextAction, a.time); }
    else if (ai instanceof Scarab) { Scarab a = (Scarab) ai; fsm(out, a.stateMachine, a.nextAction, a.time); out.writeInt(a.aiParam0); }
    else if (ai instanceof Fallen) { Fallen a = (Fallen) ai; fsm(out, a.stateMachine, a.nextAction, a.time); out.writeBoolean(a.aiParam0); }
    else if (ai instanceof Brute) { Brute a = (Brute) ai; fsm(out, a.stateMachine, a.nextAction, a.time); }
    else if (ai instanceof Zombie) { Zombie a = (Zombie) ai; fsm(out, a.stateMachine, a.nextAction, a.time); }
    else if (ai instanceof Skeleton) { Skeleton a = (Skeleton) ai; fsm(out, a.stateMachine, a.nextAction, a.time); out.writeInt(a.targetId); }
  }
  private static void readSpecific(AI ai, DataInputStream in, boolean apply) throws IOException {
    if (ai instanceof QuillRat) { QuillRat a = (QuillRat) ai; float[] t = fsm(in, a.stateMachine, QuillRat.State.values(), apply); if (apply) { a.nextAction = t[0]; a.time = t[1]; } }
    else if (ai instanceof Scarab) { Scarab a = (Scarab) ai; float[] t = fsm(in, a.stateMachine, Scarab.State.values(), apply); int p = in.readInt(); if (apply) { a.nextAction = t[0]; a.time = t[1]; a.aiParam0 = p; } }
    else if (ai instanceof Fallen) { Fallen a = (Fallen) ai; float[] t = fsm(in, a.stateMachine, Fallen.State.values(), apply); boolean p = CheckpointIO.readBoolean(in); if (apply) { a.nextAction = t[0]; a.time = t[1]; a.aiParam0 = p; } }
    else if (ai instanceof Brute) { Brute a = (Brute) ai; float[] t = fsm(in, a.stateMachine, Brute.State.values(), apply); if (apply) { a.nextAction = t[0]; a.time = t[1]; } }
    else if (ai instanceof Zombie) { Zombie a = (Zombie) ai; float[] t = fsm(in, a.stateMachine, Zombie.State.values(), apply); if (apply) { a.nextAction = t[0]; a.time = t[1]; } }
    else if (ai instanceof Skeleton) { Skeleton a = (Skeleton) ai; float[] t = fsm(in, a.stateMachine, Skeleton.State.values(), apply); int target = in.readInt(); if (target < -1) throw new IllegalArgumentException("AI target"); if (apply) { a.nextAction = t[0]; a.time = t[1]; a.targetId = target; } }
  }
  private static void fsm(DataOutputStream out, StateMachine<Integer, ?> fsm, float next, float time) throws IOException {
    if (fsm.getClass() != CheckpointStateMachine.class) throw new IllegalStateException("unsupported AI state machine");
    CheckpointIO.writeFloat(out, next); writeTimer(out, time);
    CheckpointIO.writeString(out, name(fsm.getCurrentState()));
    CheckpointIO.writeString(out, name(fsm.getPreviousState()));
    CheckpointIO.writeString(out, name(fsm.getGlobalState()));
  }
  private static String name(Object state) { return state == null ? null : ((Enum<?>) state).name(); }
  private static <S extends Enum<S> & State<Integer>> float[] fsm(DataInputStream in,
      StateMachine<Integer, S> machine, S[] values, boolean apply) throws IOException {
    if (machine.getClass() != CheckpointStateMachine.class) throw new IllegalStateException("unsupported AI state machine");
    float next = CheckpointIO.readFloat(in), time = readTimer(in);
    S current = state(CheckpointIO.readString(in), values), previous = state(CheckpointIO.readString(in), values),
        global = state(CheckpointIO.readString(in), values);
    if (apply) ((CheckpointStateMachine<S>) machine).restore(current, previous, global);
    return new float[] {next, time};
  }
  private static <S extends Enum<S>> S state(String name, S[] values) {
    if (name == null) return null;
    for (S value : values) if (value.name().equals(name)) return value;
    throw new IllegalArgumentException("unknown AI state: " + name);
  }
  static void writeTimer(DataOutputStream out, float value) throws IOException {
    if (Float.isNaN(value) || value == Float.NEGATIVE_INFINITY) throw new IllegalArgumentException("AI timer");
    out.writeInt(Float.floatToRawIntBits(value));
  }
  static float readTimer(DataInputStream in) throws IOException {
    float value = Float.intBitsToFloat(in.readInt());
    if (Float.isNaN(value) || value == Float.NEGATIVE_INFINITY) throw new IllegalArgumentException("AI timer");
    return value;
  }
}
