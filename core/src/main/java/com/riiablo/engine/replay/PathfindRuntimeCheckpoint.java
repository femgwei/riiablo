package com.riiablo.engine.replay;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Portable graph-path cursor image. Native path object graphs are rejected. */
public final class PathfindRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 256 * 1024;
  private final byte[] bytes;

  private PathfindRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }

  public static PathfindRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("path runtime length");
    }
    PathfindRuntimeCheckpoint result = new PathfindRuntimeCheckpoint(bytes.clone());
    result.validate();
    return result;
  }

  public static PathfindRuntimeCheckpoint capture(World world, int id) {
    if (world == null || id < 0) throw new IllegalArgumentException("path runtime host");
    Pathfind path = world.getMapper(Pathfind.class).get(id);
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION);
      out.writeBoolean(path != null);
      if (path != null) {
        if (path.nativeRuntime != null) {
          throw new IllegalStateException("native path runtime is not portable");
        }
        writePath(out, path.path);
        out.writeInt(path.checkpointTargetIndex());
        writeVector(out, path.target); writeVector(out, path.destination);
        out.writeBoolean(path.nativeBounded); out.writeInt(path.nativePathType);
        out.writeInt(path.targetEntityId);
        boolean bound = path.targetIdentity != null;
        if (bound && path.targetEntityId < 0) throw new IllegalStateException("path identity without target");
        out.writeBoolean(bound);
        writeVector(out, path.lastTargetPosition);
        CheckpointIO.writeFloat(out, path.repathTimer);
        out.writeInt(path.pendingDirection); out.writeInt(path.pendingDirectionFrames);
        CheckpointIO.writeFloat(out, path.stalledTime);
        out.writeBoolean(path.blockedByDynamic);
        CheckpointIO.writeString(out, path.movementBlockReason);
      }
      out.flush();
      return fromBytes(bytes.toByteArray());
    } catch (IOException e) { throw new IllegalStateException("path runtime capture", e); }
  }

  public void validateHost(World world, int id) {
    Decoded d = decode();
    boolean live = world.getMapper(Pathfind.class).has(id);
    if (d.present != live) throw new IllegalStateException("path component host mismatch: " + id);
    if (d.present && d.identityBound) {
      if (d.targetEntityId < 0 || !world.getMapper(NativePathIdentity.class).has(d.targetEntityId)) {
        throw new IllegalStateException("path target identity host mismatch");
      }
    }
  }

  public static void restore(World world, int id, PathfindRuntimeCheckpoint checkpoint) {
    if (checkpoint == null) return;
    if (world == null || id < 0) throw new IllegalArgumentException("path runtime host");
    Decoded d = checkpoint.decode();
    Pathfind live = world.getMapper(Pathfind.class).get(id);
    if (!d.present) {
      if (live != null) world.getMapper(Pathfind.class).remove(id);
      return;
    }
    if (live == null) throw new IllegalStateException("path component missing: " + id);
    live.restoreCheckpoint(d.path, d.targetIndex);
    live.target.set(d.target); live.destination.set(d.destination);
    live.nativeBounded = d.nativeBounded; live.nativePathType = d.nativePathType;
    live.targetEntityId = d.targetEntityId;
    live.targetIdentity = d.identityBound
        ? world.getMapper(NativePathIdentity.class).get(d.targetEntityId).token() : null;
    live.lastTargetPosition.set(d.lastTargetPosition);
    live.repathTimer = d.repathTimer;
    live.pendingDirection = d.pendingDirection; live.pendingDirectionFrames = d.pendingDirectionFrames;
    live.stalledTime = d.stalledTime; live.blockedByDynamic = d.blockedByDynamic;
    live.movementBlockReason = d.movementBlockReason;
  }

  private void validate() { decode(); }

  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("path runtime version");
      Decoded d = new Decoded(); d.present = CheckpointIO.readBoolean(in);
      if (d.present) {
        d.path = readPath(in); d.targetIndex = in.readInt();
        if (d.path == null && d.targetIndex != -1) throw new IllegalArgumentException("path target index");
        if (d.path != null && (d.targetIndex < -1 || d.targetIndex >= d.path.getCount())) {
          throw new IllegalArgumentException("path target index");
        }
        d.target = readVector(in); d.destination = readVector(in);
        d.nativeBounded = CheckpointIO.readBoolean(in); d.nativePathType = in.readInt();
        d.targetEntityId = in.readInt();
        d.identityBound = CheckpointIO.readBoolean(in);
        if (d.identityBound && d.targetEntityId < 0) throw new IllegalArgumentException("path identity");
        d.lastTargetPosition = readVector(in); d.repathTimer = CheckpointIO.readFloat(in);
        d.pendingDirection = in.readInt(); d.pendingDirectionFrames = in.readInt();
        d.stalledTime = CheckpointIO.readFloat(in); d.blockedByDynamic = CheckpointIO.readBoolean(in);
        d.movementBlockReason = CheckpointIO.readString(in);
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing path runtime");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated path runtime", e); }
  }

  private static void writePath(DataOutputStream out, GraphPath path) throws IOException {
    out.writeBoolean(path != null);
    if (path == null) return;
    int count = path.getCount();
    if (count > 4096) throw new IllegalArgumentException("path count");
    out.writeInt(count);
    for (int i = 0; i < count; i++) { out.writeInt(path.get(i).x); out.writeInt(path.get(i).y); }
  }

  private static GraphPath readPath(DataInputStream in) throws IOException {
    if (!CheckpointIO.readBoolean(in)) return null;
    int count = CheckpointIO.count(in, 4096);
    GraphPath path = new GraphPath();
    for (int i = 0; i < count; i++) path.add(new Point2(in.readInt(), in.readInt()));
    if (count == 0) throw new IllegalArgumentException("empty path");
    return path;
  }

  private static void writeVector(DataOutputStream out, Vector2 value) throws IOException {
    CheckpointIO.writeFloat(out, value.x); CheckpointIO.writeFloat(out, value.y);
  }
  private static Vector2 readVector(DataInputStream in) throws IOException {
    return new Vector2(CheckpointIO.readFloat(in), CheckpointIO.readFloat(in));
  }

  private static final class Decoded {
    boolean present, nativeBounded, identityBound, blockedByDynamic;
    int targetIndex, nativePathType, targetEntityId, pendingDirection, pendingDirectionFrames;
    float repathTimer, stalledTime;
    GraphPath path;
    Vector2 target, destination, lastTargetPosition;
    String movementBlockReason;
  }
}
