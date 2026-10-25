package com.riiablo.engine.server.ai;

import com.badlogic.gdx.ai.fsm.State;
import com.artemis.World;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.riiablo.engine.replay.CheckpointIO;
import com.riiablo.engine.replay.NativeEntityReferenceCheckpoint;
import java.io.*;
import java.util.*;

/** Explicit, exact-class AI schemas. Never serialize injected services or arbitrary object graphs. */
public final class AiRuntimeState {
  public static final int MAX_BYTES = 16384;
  private final byte[] bytes;
  private final String type, configuration;
  private final int owner;
  private final NativeEntityReferenceCheckpoint reference;
  private AiRuntimeState(byte[] bytes, String type, String configuration, int owner, NativeEntityReferenceCheckpoint reference) {
    this.bytes = bytes; this.type = type; this.configuration = configuration; this.owner = owner;
    this.reference = reference;
  }
  public byte[] bytes() { return bytes.clone(); }
  public int owner() { return owner; }
  public String type() { return type; }

  public static AiRuntimeState capture(AI ai, int id) {
    return capture(null, ai, id);
  }
  public static AiRuntimeState capture(World world, AI ai, int id) {
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
      if (ai != AI.IDLE) { ai.writeRuntime(out); writeSpecific(ai, out, world); }
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
      NativeEntityReferenceCheckpoint reference = null;
      if (prototype != AI.IDLE) { prototype.readRuntime(in, false); reference = readSpecific(prototype, in, false, null); }
      if (in.read() != -1) throw new IllegalArgumentException("trailing AI runtime");
      return new AiRuntimeState(copy, type, configuration, id, reference);
    } catch (IOException e) { throw new IllegalArgumentException("truncated AI runtime", e); }
  }

  public void validateRestore(AI ai, int id) {
    validateRestore(null, ai, id);
  }
  public void validateCoverage(Set<Integer> ids) {
    if (reference != null) reference.validateCoverage(ids);
  }
  public void validateRestore(World world, AI ai, int id) {
    if (ai == null || id != owner || ai != AI.IDLE && ai.entityId != id
        || ai.getClass() != schema(type, configuration, id).getClass()
        || !Objects.equals(configuration, ai.checkpointConfiguration())
        || ai instanceof Idle && ai != AI.IDLE) throw new IllegalStateException("AI runtime implementation/owner mismatch: " + id);
    validateMachineOwner(ai); read(ai, false, world);
    if (reference != null) reference.validateRestore(world);
  }
  public void restore(AI ai, int id) { restore(null, ai, id); }
  public void restore(World world, AI ai, int id) { validateRestore(world, ai, id); read(ai, true, world); }
  private void read(AI ai, boolean apply, World world) {
    if (ai == AI.IDLE) return; // Shared sentinel has no entity-owned state; never mutate it.
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      in.readInt(); in.readInt(); CheckpointIO.readString(in); CheckpointIO.readString(in);
      ai.readRuntime(in, apply); readSpecific(ai, in, apply, world);
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
      case "BaalCrab": return new BaalCrab(id);
      case "Diablo": return new Diablo(id);
      case "Mephisto": return new Mephisto(id);
      case "Duriel": return new Duriel(id);
      case "Summoner": return new Summoner(id);
      case "OblivionKnight": return new OblivionKnight(id);
      case "AbyssKnight": return new AbyssKnight(id);
      case "ArcaneTower": return new ArcaneTower(id);
      case "TrapMissile": return new TrapMissile(id);
      case "TrapNova": return new TrapNova(id);
      case "TrapRightArrow": return new TrapRightArrow(id);
      case "TrappedSoul": return new TrappedSoul(id);
      case "ThornHulk": return new ThornHulk(id);
      case "VileMother": return new VileMother(id);
      case "ZakarumPriest": return new ZakarumPriest(id);
      case "ZakarumZealot": return new ZakarumZealot(id);
      case "TrapPoison": return new TrapPoison(id);
      case "TrapMelee": return new TrapMelee(id);
      case "TrapLeftArrow": return new TrapLeftArrow(id);
      case "BaalCrabClone": return new BaalCrabClone(id);
      case "BaalTaunt": return new BaalTaunt(id);
      case "BaalTentacle": return new BaalTentacle(id);
      case "BaalThrone": return new BaalThrone(id);
      case "Tentacle": return new Tentacle(id);
      case "TentacleHead": return new TentacleHead(id);
      case "SuicideMinion": return new SuicideMinion(id);
      case "WillOWisp": return new WillOWisp(id);
      case "SevenTIllusion": return new SevenTIllusion(id);
      case "Spirit": return new Spirit(id);
      case "SandMaggotQueen": return new SandMaggotQueen(id);
      case "Sarcophagus": return new Sarcophagus(id);
      case "Ancient": return new Ancient(id);
      case "SiegeTower": return new SiegeTower(id);
      case "SiegeBeast": return new SiegeBeast(id);
      case "Succubus": return new Succubus(id);
      case "SuccubusWitch": return new SuccubusWitch(id);
      case "BatDemon": return new BatDemon(id);
      case "CatapultSpotter": return new CatapultSpotter(id);
      case "ClawViperEx": return new ClawViperEx(id);
      case "DesertTurret": return new DesertTurret(id);
      case "ElementalBeast": return new ElementalBeast(id);
      case "FetishBlowgun": return new FetishBlowgun(id);
      case "FingerMage": return new FingerMage(id);
      case "GenericSpawner": return new GenericSpawner(id);
      case "GreaterMummy": return new GreaterMummy(id);
      case "HighPriest": return new HighPriest(id);
      case "InvisoSpawner": return new InvisoSpawner(id);
      case "Izual": return new Izual(id);
      case "MaggotEgg": return new MaggotEgg(id);
      case "Megademon": return new Megademon(id);
      case "MinionSpawner": return new MinionSpawner(id);
      case "Mosquito": return new Mosquito(id);
      case "Overseer": return new Overseer(id);
      case "BaalToStairs": return new BaalToStairs(id);
      case "PantherJavelin": return new PantherJavelin(id);
      case "QuillMother": return new QuillMother(id);
      case "BloodLord": return new BloodLord(id);
      case "Catapult": return new Catapult(id);
      case "DeathMauler": return new DeathMauler(id);
      case "FrozenHorror": return new FrozenHorror(id);
      case "Griswold": return new Griswold(id);
      case "HellMeteor": return new HellMeteor(id);
      case "Minion": return new Minion(id);
      case "Smith": return new Smith(id);
      case "DarkWanderer": return new DarkWanderer(id);
      case "Imp": return new Imp(id);
      case "Vulture": return new Vulture(id);
      case "Nihlathak": return new Nihlathak(id);
      case "Arach": return new Arach(id);
      case "AssassinSentry": return new AssassinSentry(id);
      case "BonePrison": return new BonePrison(id);
      case "FoulCrowNest": return new FoulCrowNest(id);
      case "GargoyleTrap": return new GargoyleTrap(id);
      case "Vines": return new Vines(id);
      case "Regurgitator": return new Regurgitator(id);
      case "BloodRaven": return new BloodRaven(id);
      case "FrogDemon": return new FrogDemon(id);
      case "CycleOfLife": return new CycleOfLife(id);
      case "DeathSentry": return new DeathSentry(id);
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

  private static void writeSpecific(AI ai, DataOutputStream out, World world) throws IOException {
    if (ai instanceof QuillRat) { QuillRat a = (QuillRat) ai; fsm(out, a.stateMachine, a.nextAction, a.time); }
    else if (ai instanceof Scarab) { Scarab a = (Scarab) ai; fsm(out, a.stateMachine, a.nextAction, a.time); out.writeInt(a.aiParam0); }
    else if (ai instanceof Fallen) { Fallen a = (Fallen) ai; fsm(out, a.stateMachine, a.nextAction, a.time); out.writeBoolean(a.aiParam0); }
    else if (ai instanceof Brute) { Brute a = (Brute) ai; fsm(out, a.stateMachine, a.nextAction, a.time); }
    else if (ai instanceof Zombie) { Zombie a = (Zombie) ai; fsm(out, a.stateMachine, a.nextAction, a.time); }
    else if (ai instanceof Skeleton) { Skeleton a = (Skeleton) ai; fsm(out, a.stateMachine, a.nextAction, a.time); out.writeInt(a.targetId); }
    else if (ai instanceof Regurgitator) ((Regurgitator) ai).checkpointFood(world).write(out);
  }
  private static NativeEntityReferenceCheckpoint readSpecific(AI ai, DataInputStream in, boolean apply, World world) throws IOException {
    if (ai instanceof QuillRat) { QuillRat a = (QuillRat) ai; float[] t = fsm(in, a.stateMachine, QuillRat.State.values(), apply); if (apply) { a.nextAction = t[0]; a.time = t[1]; } }
    else if (ai instanceof Scarab) { Scarab a = (Scarab) ai; float[] t = fsm(in, a.stateMachine, Scarab.State.values(), apply); int p = in.readInt(); if (apply) { a.nextAction = t[0]; a.time = t[1]; a.aiParam0 = p; } }
    else if (ai instanceof Fallen) { Fallen a = (Fallen) ai; float[] t = fsm(in, a.stateMachine, Fallen.State.values(), apply); boolean p = CheckpointIO.readBoolean(in); if (apply) { a.nextAction = t[0]; a.time = t[1]; a.aiParam0 = p; } }
    else if (ai instanceof Brute) { Brute a = (Brute) ai; float[] t = fsm(in, a.stateMachine, Brute.State.values(), apply); if (apply) { a.nextAction = t[0]; a.time = t[1]; } }
    else if (ai instanceof Zombie) { Zombie a = (Zombie) ai; float[] t = fsm(in, a.stateMachine, Zombie.State.values(), apply); if (apply) { a.nextAction = t[0]; a.time = t[1]; } }
    else if (ai instanceof Skeleton) { Skeleton a = (Skeleton) ai; float[] t = fsm(in, a.stateMachine, Skeleton.State.values(), apply); int target = in.readInt(); if (target < -1) throw new IllegalArgumentException("AI target"); if (apply) { a.nextAction = t[0]; a.time = t[1]; a.targetId = target; } }
    else if (ai instanceof Regurgitator) {
      NativeEntityReferenceCheckpoint reference = NativeEntityReferenceCheckpoint.read(in);
      if (apply) ((Regurgitator) ai).restoreFood(world, reference);
      return reference;
    }
    return null;
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
    // Positive infinity is a legacy sleep sentinel, unlike the local frame clocks below.
    if (Float.isNaN(value) || value == Float.NEGATIVE_INFINITY) throw new IllegalArgumentException("AI timer");
    out.writeInt(Float.floatToRawIntBits(value));
  }
  static float readTimer(DataInputStream in) throws IOException {
    float value = Float.intBitsToFloat(in.readInt());
    if (Float.isNaN(value) || value == Float.NEGATIVE_INFINITY) throw new IllegalArgumentException("AI timer");
    return value;
  }
  static void writeFiniteDouble(DataOutputStream out, double value) throws IOException {
    if (!Double.isFinite(value)) throw new IllegalArgumentException("AI frame clock");
    out.writeDouble(value);
  }
  static double readFiniteDouble(DataInputStream in) throws IOException {
    double value = in.readDouble();
    if (!Double.isFinite(value)) throw new IllegalArgumentException("AI frame clock");
    return value;
  }
}
