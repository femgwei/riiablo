package com.riiablo.engine.client;

import com.badlogic.gdx.utils.IntMap;
import com.google.flatbuffers.Table;
import com.riiablo.net.packet.d2gs.AngleP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.PositionP;
import com.riiablo.net.packet.d2gs.VelocityP;

/**
 * Committed server transforms, distinct from the client's mutable prediction.
 * Only the receiver package can commit/remove an entry. Readers receive a
 * detached immutable value, never a FlatBuffers table or an ECS vector.
 */
public final class AuthoritativeTransformState {
  private final IntMap<Snapshot> snapshots = new IntMap<>();
  // Legacy zero-tick packets may replace values, but must not erase the
  // monotonic watermark established by a stamped packet.
  private final IntMap<AuthoritativeSnapshotTimeline> timelines = new IntMap<>();

  public Snapshot get(int entityId) {
    return snapshots.get(entityId);
  }

  boolean commit(int entityId, EntitySync data) {
    Snapshot previous = snapshots.get(entityId);
    AuthoritativeSnapshotTimeline timeline = timelines.get(entityId);
    if (timeline == null) {
      timeline = new AuthoritativeSnapshotTimeline();
      timelines.put(entityId, timeline);
    }
    if (!timeline.accept(data.tick(), data.serverTimeMillis())) return false;
    PositionP position = find(data, ComponentP.PositionP, new PositionP());
    VelocityP velocity = find(data, ComponentP.VelocityP, new VelocityP());
    AngleP angle = find(data, ComponentP.AngleP, new AngleP());
    if (position == null && velocity == null && angle == null) return true;
    snapshots.put(entityId, new Snapshot(data, previous, position, velocity, angle));
    return true;
  }

  void remove(int entityId) {
    snapshots.remove(entityId);
    timelines.remove(entityId);
  }

  void clear() {
    snapshots.clear();
    timelines.clear();
  }

  private static <T extends Table> T find(EntitySync data, byte type, T table) {
    for (int i = 0; i < data.componentLength(); i++) {
      if (data.componentType(i) == type) {
        data.component(table, i);
        return table;
      }
    }
    return null;
  }

  public static final class Snapshot {
    public final long tick;
    public final long serverTimeMillis;
    public final boolean hasPosition, hasVelocity, hasAngle;
    public final float x, y, velocityX, velocityY, angleX, angleY;

    private Snapshot(EntitySync data, Snapshot previous,
        PositionP position, VelocityP velocity, AngleP angle) {
      tick = data.tick();
      serverTimeMillis = data.serverTimeMillis();
      hasPosition = position != null || previous != null && previous.hasPosition;
      hasVelocity = velocity != null || previous != null && previous.hasVelocity;
      hasAngle = angle != null || previous != null && previous.hasAngle;
      x = position != null ? position.x() : previous == null ? 0f : previous.x;
      y = position != null ? position.y() : previous == null ? 0f : previous.y;
      velocityX = velocity != null ? velocity.x() : previous == null ? 0f : previous.velocityX;
      velocityY = velocity != null ? velocity.y() : previous == null ? 0f : previous.velocityY;
      angleX = angle != null ? angle.x() : previous == null ? 0f : previous.angleX;
      angleY = angle != null ? angle.y() : previous == null ? 0f : previous.angleY;
    }
  }
}
