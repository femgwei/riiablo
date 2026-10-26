package com.riiablo.engine.replay;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.NativeDiabRun;
import com.riiablo.engine.server.NativeMonInferno;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Leap;
import com.riiablo.engine.server.component.Sequence;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Checkpoint image for transient action components. Network EntitySync does not
 * carry these components, so restoring only the ECS baseline would otherwise
 * resume a cast, animation sequence, or Leap with a different phase.
 */
public final class ActionRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 128 * 1024;
  private static final int CASTING = 1;
  private static final int SEQUENCE = 2;
  private static final int LEAP = 3;
  private static final int BOOLEAN = 1;
  private static final int BYTE = 2;
  private static final int SHORT = 3;
  private static final int CHAR = 4;
  private static final int INT = 5;
  private static final int LONG = 6;
  private static final int FLOAT = 7;
  private static final int DOUBLE = 8;
  private static final int VECTOR = 9;
  private static final int INT_ARRAY = 10;
  private static final int MON_INFERNO = 11;
  private static final int DIAB_RUN = 12;

  private final byte[] bytes;

  private ActionRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }

  public byte[] bytes() { return bytes.clone(); }

  /** Validates that restore will find the same transient component set. */
  public void validateHost(World world, int entityId) {
    if (world == null || entityId < 0) throw new IllegalArgumentException("action runtime host");
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      int version = in.readInt();
      if (version != VERSION) throw new IllegalArgumentException("action runtime version");
      for (int expected = CASTING; expected <= LEAP; expected++) {
        int kind = in.readInt();
        if (kind != expected) throw new IllegalArgumentException("action component order");
        boolean present = CheckpointIO.readBoolean(in);
        boolean live = componentPresent(world, entityId, kind);
        if (present != live) throw new IllegalStateException("action component host mismatch: " + entityId);
        if (present) {
          int count = CheckpointIO.count(in, 256);
          for (int i = 0; i < count; i++) {
            in.readUTF();
            skipValue(in, in.readUnsignedByte());
          }
        }
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing action runtime");
    } catch (IOException e) {
      throw new IllegalArgumentException("truncated action runtime", e);
    }
  }

  public static ActionRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("action runtime length");
    }
    ActionRuntimeCheckpoint result = new ActionRuntimeCheckpoint(bytes.clone());
    result.validate();
    return result;
  }

  public static ActionRuntimeCheckpoint capture(World world, int entityId) {
    if (world == null || entityId < 0) throw new IllegalArgumentException("action runtime host");
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION);
      writeComponent(out, CASTING, world.getMapper(Casting.class).get(entityId));
      writeComponent(out, SEQUENCE, world.getMapper(Sequence.class).get(entityId));
      writeComponent(out, LEAP, world.getMapper(Leap.class).get(entityId));
      out.flush();
      return fromBytes(bytes.toByteArray());
    } catch (IOException e) {
      throw new IllegalStateException("action runtime capture", e);
    }
  }

  public static void restore(World world, int entityId, ActionRuntimeCheckpoint checkpoint) {
    if (checkpoint == null) return;
    if (world == null || entityId < 0) throw new IllegalArgumentException("action runtime host");
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(checkpoint.bytes));
      int version = in.readInt();
      if (version != VERSION) throw new IllegalArgumentException("action runtime version");
      readComponent(in, CASTING, world, entityId);
      readComponent(in, SEQUENCE, world, entityId);
      readComponent(in, LEAP, world, entityId);
      if (in.read() != -1) throw new IllegalArgumentException("trailing action runtime");
    } catch (IOException e) {
      throw new IllegalArgumentException("truncated action runtime", e);
    }
  }

  private void validate() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      int version = in.readInt();
      if (version != VERSION) throw new IllegalArgumentException("action runtime version");
      skipComponent(in); skipComponent(in); skipComponent(in);
      if (in.read() != -1) throw new IllegalArgumentException("trailing action runtime");
    } catch (IOException e) {
      throw new IllegalArgumentException("truncated action runtime", e);
    }
  }

  private static void writeComponent(DataOutputStream out, int kind, Object component)
      throws IOException {
    out.writeInt(kind);
    out.writeBoolean(component != null);
    if (component == null) return;
    rejectUnsupportedActiveFields(component);
    List<Field> fields = fields(component.getClass());
    out.writeInt(fields.size());
    for (Field field : fields) {
      int tag = tag(field.getType());
      if (tag == 0) continue;
      out.writeUTF(field.getName());
      out.writeByte(tag);
      try {
        writeValue(out, tag, field.get(component));
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("action field access: " + field, e);
      }
    }
  }

  /**
   * The action components intentionally contain native object graphs (combat
   * records, Items and native sequence/path state) which are not portable
   * checkpoint values.  A live value must fail capture instead of being
   * silently dropped and resuming a different action phase.
   */
  private static void rejectUnsupportedActiveFields(Object component) {
    for (Field field : component.getClass().getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || tag(field.getType()) != 0) {
        continue;
      }
      field.setAccessible(true);
      try {
        if (field.get(component) != null) {
          throw new IllegalStateException("unsupported action runtime field: "
              + component.getClass().getSimpleName() + "." + field.getName());
        }
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("action field access: " + field, e);
      }
    }
  }

  private static void readComponent(DataInputStream in, int expectedKind, World world, int id)
      throws IOException {
    int kind = in.readInt();
    if (kind != expectedKind) throw new IllegalArgumentException("action component order");
    boolean present = CheckpointIO.readBoolean(in);
    Object component;
    if (kind == CASTING) component = world.getMapper(Casting.class).get(id);
    else if (kind == SEQUENCE) component = world.getMapper(Sequence.class).get(id);
    else component = world.getMapper(Leap.class).get(id);
    int count = 0;
    if (present) {
      count = CheckpointIO.count(in, 256);
      if (component == null) throw new IllegalStateException("action component missing: " + id);
      resetComponent(kind, component);
    } else {
      if (component != null) removeComponent(kind, world, id);
    }
    String previousName = null;
    for (int i = 0; i < count; i++) {
      String name = in.readUTF();
      if (previousName != null && name.compareTo(previousName) <= 0) {
        throw new IllegalArgumentException("action field order");
      }
      previousName = name;
      int tag = in.readUnsignedByte();
      Field field = field(component.getClass(), name);
      if (field == null || tag(field.getType()) != tag) {
        throw new IllegalArgumentException("unknown action field: " + name);
      }
      try {
        readValue(in, tag, field, component);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("action field access: " + field, e);
      }
    }
  }

  private static void skipComponent(DataInputStream in) throws IOException {
    int kind = in.readInt();
    if (kind < CASTING || kind > LEAP) throw new IllegalArgumentException("action component kind");
    if (!CheckpointIO.readBoolean(in)) return;
    int count = CheckpointIO.count(in, 256);
    for (int i = 0; i < count; i++) {
      in.readUTF();
      int tag = in.readUnsignedByte();
      skipValue(in, tag);
    }
  }

  private static void resetComponent(int kind, Object component) {
    if (kind == CASTING) {
      ((Casting) component).set(-1, Engine.INVALID_ENTITY, new Vector2());
    } else if (kind == SEQUENCE) {
      ((Sequence) component).sequence((byte) 0, (byte) 0);
    } else {
      Leap leap = (Leap) component;
      leap.set(new Vector2(), new Vector2(), 0.001f, Engine.INVALID_ENTITY);
    }
  }

  private static void removeComponent(int kind, World world, int id) {
    if (kind == CASTING) world.getMapper(Casting.class).remove(id);
    else if (kind == SEQUENCE) world.getMapper(Sequence.class).remove(id);
    else world.getMapper(Leap.class).remove(id);
  }

  private static boolean componentPresent(World world, int id, int kind) {
    if (kind == CASTING) return world.getMapper(Casting.class).has(id);
    if (kind == SEQUENCE) return world.getMapper(Sequence.class).has(id);
    return world.getMapper(Leap.class).has(id);
  }

  private static List<Field> fields(Class<?> type) {
    List<Field> result = new ArrayList<>();
    for (Field field : type.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || tag(field.getType()) == 0) continue;
      field.setAccessible(true);
      result.add(field);
    }
    result.sort(Comparator.comparing(Field::getName));
    return result;
  }

  private static Field field(Class<?> type, String name) {
    try {
      Field field = type.getDeclaredField(name);
      if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic() || tag(field.getType()) == 0) return null;
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException e) {
      return null;
    }
  }

  private static int tag(Class<?> type) {
    if (type == boolean.class) return BOOLEAN;
    if (type == byte.class) return BYTE;
    if (type == short.class) return SHORT;
    if (type == char.class) return CHAR;
    if (type == int.class) return INT;
    if (type == long.class) return LONG;
    if (type == float.class) return FLOAT;
    if (type == double.class) return DOUBLE;
    if (type == Vector2.class) return VECTOR;
    if (type == IntArray.class) return INT_ARRAY;
    if (type == NativeMonInferno.class) return MON_INFERNO;
    if (type == NativeDiabRun.class) return DIAB_RUN;
    return 0;
  }

  private static void writeValue(DataOutputStream out, int tag, Object value) throws IOException,
      IllegalAccessException {
    switch (tag) {
      case BOOLEAN: out.writeBoolean((Boolean) value); break;
      case BYTE: out.writeByte((Byte) value); break;
      case SHORT: out.writeShort((Short) value); break;
      case CHAR: out.writeChar((Character) value); break;
      case INT: out.writeInt((Integer) value); break;
      case LONG: out.writeLong((Long) value); break;
      case FLOAT: out.writeFloat((Float) value); break;
      case DOUBLE: out.writeDouble((Double) value); break;
      case VECTOR: Vector2 v = (Vector2) value; out.writeFloat(v.x); out.writeFloat(v.y); break;
      case INT_ARRAY:
        IntArray a = (IntArray) value; out.writeInt(a.size); for (int i = 0; i < a.size; i++) out.writeInt(a.get(i)); break;
      case MON_INFERNO:
        NativeMonInferno inferno = (NativeMonInferno) value;
        out.writeInt(inferno.deadline); out.writeInt(inferno.interval); out.writeInt(inferno.tailFrames);
        out.writeInt(inferno.frame); out.writeInt(inferno.nextUpdate); out.writeInt(inferno.endFrame);
        out.writeInt(inferno.missiles); out.writeInt(inferno.lastMissileId);
        out.writeBoolean(inferno.started); out.writeBoolean(inferno.horrorSequence);
        out.writeBoolean(inferno.impInferno); out.writeInt(inferno.sequenceCursor);
        break;
      case DIAB_RUN:
        NativeDiabRun run = (NativeDiabRun) value;
        out.writeInt(run.targetId); out.writeFloat(run.destination.x); out.writeFloat(run.destination.y);
        out.writeInt(run.cursor); out.writeInt(run.remaining); out.writeBoolean(run.moving);
        out.writeBoolean(run.pathStarted); out.writeBoolean(run.tail); out.writeBoolean(run.hitProcessed);
        out.writeBoolean(run.damageProcessed); out.writeInt(run.loops);
        break;
      default: throw new IllegalArgumentException("action field tag");
    }
  }

  private static void readValue(DataInputStream in, int tag, Field field, Object target)
      throws IOException, IllegalAccessException {
    switch (tag) {
      case BOOLEAN: field.setBoolean(target, in.readBoolean()); break;
      case BYTE: field.setByte(target, in.readByte()); break;
      case SHORT: field.setShort(target, in.readShort()); break;
      case CHAR: field.setChar(target, in.readChar()); break;
      case INT: field.setInt(target, in.readInt()); break;
      case LONG: field.setLong(target, in.readLong()); break;
      case FLOAT: field.setFloat(target, in.readFloat()); break;
      case DOUBLE: field.setDouble(target, in.readDouble()); break;
      case VECTOR:
        Vector2 v = (Vector2) field.get(target); v.set(in.readFloat(), in.readFloat()); break;
      case INT_ARRAY:
        IntArray a = (IntArray) field.get(target); a.clear(); int size = CheckpointIO.count(in, 256);
        for (int i = 0; i < size; i++) a.add(in.readInt()); break;
      case MON_INFERNO:
        field.set(target, readInferno(in)); break;
      case DIAB_RUN:
        field.set(target, readDiabRun(in)); break;
      default: throw new IllegalArgumentException("action field tag");
    }
  }

  private static NativeMonInferno readInferno(DataInputStream in) throws IOException {
    int deadline = in.readInt(), interval = in.readInt(), tailFrames = in.readInt();
    int frame = in.readInt(), nextUpdate = in.readInt(), endFrame = in.readInt();
    int missiles = in.readInt(), lastMissileId = in.readInt();
    boolean started = CheckpointIO.readBoolean(in), horror = CheckpointIO.readBoolean(in);
    boolean imp = CheckpointIO.readBoolean(in); int cursor = in.readInt();
    NativeMonInferno value = new NativeMonInferno(deadline, interval, tailFrames, horror, imp);
    value.frame = frame; value.nextUpdate = nextUpdate; value.endFrame = endFrame;
    value.missiles = missiles; value.lastMissileId = lastMissileId;
    value.started = started; value.sequenceCursor = cursor; return value;
  }

  private static NativeDiabRun readDiabRun(DataInputStream in) throws IOException {
    NativeDiabRun value = new NativeDiabRun(in.readInt(),
        new Vector2(in.readFloat(), in.readFloat()), true);
    value.cursor = in.readInt(); value.remaining = in.readInt(); value.moving = CheckpointIO.readBoolean(in);
    value.pathStarted = CheckpointIO.readBoolean(in); value.tail = CheckpointIO.readBoolean(in);
    value.hitProcessed = CheckpointIO.readBoolean(in); value.damageProcessed = CheckpointIO.readBoolean(in);
    value.loops = in.readInt(); return value;
  }

  private static void skipValue(DataInputStream in, int tag) throws IOException {
    switch (tag) {
      case BOOLEAN: in.readBoolean(); break; case BYTE: in.readByte(); break;
      case SHORT: in.readShort(); break; case CHAR: in.readChar(); break;
      case INT: in.readInt(); break; case LONG: in.readLong(); break;
      case FLOAT: in.readFloat(); break; case DOUBLE: in.readDouble(); break;
      case VECTOR: in.readFloat(); in.readFloat(); break;
      case INT_ARRAY: int size = CheckpointIO.count(in, 256); for (int i = 0; i < size; i++) in.readInt(); break;
      case MON_INFERNO:
        for (int i = 0; i < 8; i++) in.readInt();
        in.readBoolean(); in.readBoolean(); in.readBoolean(); in.readInt(); break;
      case DIAB_RUN:
        in.readInt(); in.readFloat(); in.readFloat(); in.readInt(); in.readInt();
        in.readBoolean(); in.readBoolean(); in.readBoolean(); in.readBoolean(); in.readBoolean(); in.readInt(); break;
      default: throw new IllegalArgumentException("action field tag");
    }
  }
}
