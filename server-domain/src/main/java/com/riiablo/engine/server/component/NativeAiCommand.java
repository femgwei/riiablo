package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.Engine;
import com.artemis.World;
import java.util.ArrayDeque;

/** Current native command plus commands displaced by CopyAiCommand. */
@Transient
@PooledWeaver
public class NativeAiCommand extends PooledComponent {
  public static final int NONE = 0;
  public static final int ATTACK = 1;
  public static final int MOVE = 14;

  @EntityId public int ownerId = Engine.INVALID_ENTITY;
  @EntityId public int targetId = Engine.INVALID_ENTITY;
  public int command;
  public long expiresAtFrame = Long.MAX_VALUE;
  public NativePathIdentity.Token targetIdentity;
  public NativePathIdentity.Token ownerIdentity;
  private final ArrayDeque<Saved> pending = new ArrayDeque<>();

  public NativeAiCommand set(int ownerId, int command, int targetId) {
    pending.clear();
    return assign(ownerId, command, targetId, null);
  }

  private NativeAiCommand assign(int ownerId, int command, int targetId,
      NativePathIdentity.Token identity) {
    this.ownerId = ownerId;
    this.command = command;
    this.targetId = targetId;
    this.targetIdentity = identity;
    this.ownerIdentity = null;
    this.expiresAtFrame = Long.MAX_VALUE;
    return this;
  }

  /** CopyAiCommand inserts before the current command, then selects the copy. */
  public NativeAiCommand copy(World world, int ownerId, int command, int targetId) {
    if (this.command != NONE) pending.addFirst(new Saved(this));
    NativePathIdentity target = targetId < 0 ? null
        : world.getMapper(NativePathIdentity.class).get(targetId);
    assign(ownerId, command, targetId, target == null ? null : target.token());
    NativePathIdentity owner = ownerId < 0 ? null
        : world.getMapper(NativePathIdentity.class).get(ownerId);
    ownerIdentity = owner == null ? null : owner.token();
    return this;
  }

  /** Free only the current command. Returns whether another command remains. */
  public boolean pop() {
    Saved next = pending.pollFirst();
    if (next == null) { assign(Engine.INVALID_ENTITY, NONE, Engine.INVALID_ENTITY, null); return false; }
    assign(next.owner, next.command, next.target, next.identity);
    expiresAtFrame = next.expiry;
    ownerIdentity = next.ownerIdentity;
    return true;
  }

  public boolean targetIsCurrent(World world) {
    return targetIdentity == null || targetIdentity.isCurrent(world, targetId);
  }
  public boolean ownerIsCurrent(World world) {
    return ownerIdentity == null || ownerIdentity.isCurrent(world, ownerId);
  }

  private static final class Saved {
    final int owner, command, target;
    final NativePathIdentity.Token identity;
    final NativePathIdentity.Token ownerIdentity;
    final long expiry;
    Saved(NativeAiCommand source) {
      owner = source.ownerId; command = source.command; target = source.targetId;
      identity = source.targetIdentity;
      ownerIdentity = source.ownerIdentity;
      expiry = source.expiresAtFrame;
    }
  }

  public boolean isAttack() {
    return command == ATTACK;
  }

  @Override protected void reset() {
    pending.clear();
    assign(Engine.INVALID_ENTITY, NONE, Engine.INVALID_ENTITY, null);
  }
}
