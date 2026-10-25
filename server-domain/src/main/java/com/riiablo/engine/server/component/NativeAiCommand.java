package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.Engine;
import com.artemis.World;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Current native command plus commands displaced by CopyAiCommand. */
@Transient
@PooledWeaver
public class NativeAiCommand extends PooledComponent {
  public static final int NONE = 0;
  public static final int ATTACK = 1;
  public static final int MOVE = 14;
  public static final int MAX_CHECKPOINT_ENTRIES = 65536;

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

  /** Immutable value image, current command first, followed by pop order. No RNG or query repair. */
  public List<Checkpoint> captureCheckpoint(World world) {
    if (pending.size() >= MAX_CHECKPOINT_ENTRIES) throw new IllegalStateException("AI command stack length");
    List<Checkpoint> result = new ArrayList<>();
    result.add(new Checkpoint(world, new Saved(this)));
    for (Saved saved : pending) result.add(new Checkpoint(world, saved));
    return Collections.unmodifiableList(result);
  }

  /** Preflight the complete stack before changing even its current command. */
  public static void validateCheckpoint(World world, List<Checkpoint> image) {
    if (image == null || image.isEmpty() || image.size() > MAX_CHECKPOINT_ENTRIES) {
      throw new IllegalArgumentException("AI command stack length");
    }
    for (Checkpoint entry : image) {
      if (entry == null) throw new IllegalArgumentException("null AI command");
      if (entry.ownerLifetime == 1) entry.token(world, entry.ownerId, 1);
      if (entry.targetLifetime == 1) entry.token(world, entry.targetId, 1);
    }
  }

  public void restoreCheckpoint(World world, List<Checkpoint> image) {
    validateCheckpoint(world, image);
    pending.clear();
    Checkpoint first = image.get(0);
    assign(first.ownerId, first.command, first.targetId,
        first.token(world, first.targetId, first.targetLifetime));
    ownerIdentity = first.token(world, first.ownerId, first.ownerLifetime);
    expiresAtFrame = first.expiresAtFrame;
    for (int i = 1; i < image.size(); i++) {
      Checkpoint e = image.get(i);
      pending.addLast(new Saved(e.ownerId, e.command, e.targetId, e.expiresAtFrame,
          e.token(world, e.targetId, e.targetLifetime), e.token(world, e.ownerId, e.ownerLifetime)));
    }
  }

  /** Lifetime: 0=null (legacy unguarded), 1=current, 2=stale; never serialize Java tokens. */
  public static final class Checkpoint {
    public final int ownerId, command, targetId, ownerLifetime, targetLifetime;
    public final long expiresAtFrame;

    public Checkpoint(int ownerId, int command, int targetId, long expiresAtFrame,
        int ownerLifetime, int targetLifetime) {
      if (ownerId < -1 || targetId < -1 || ownerLifetime < 0 || ownerLifetime > 2
          || targetLifetime < 0 || targetLifetime > 2
          || ownerLifetime != 0 && ownerId < 0 || targetLifetime != 0 && targetId < 0) {
        throw new IllegalArgumentException("AI command identity/lifetime");
      }
      this.ownerId = ownerId; this.command = command; this.targetId = targetId;
      this.expiresAtFrame = expiresAtFrame;
      this.ownerLifetime = ownerLifetime; this.targetLifetime = targetLifetime;
    }

    private Checkpoint(World world, Saved s) {
      this(s.owner, s.command, s.target, s.expiry,
          lifetime(world, s.owner, s.ownerIdentity), lifetime(world, s.target, s.identity));
    }
    private static int lifetime(World world, int id, NativePathIdentity.Token token) {
      return token == null ? 0 : token.isCurrent(world, id) ? 1 : 2;
    }
    private NativePathIdentity.Token token(World world, int id, int lifetime) {
      if (lifetime == 0) return null;
      if (lifetime == 2) return NativePathIdentity.retiredToken(world, id);
      NativePathIdentity identity = world.getMapper(NativePathIdentity.class).get(id);
      if (identity == null || identity.token() == null || !identity.token().isCurrent(world, id)) {
        throw new IllegalStateException("AI command current lifetime missing: " + id);
      }
      return identity.token();
    }
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
    Saved(int owner, int command, int target, long expiry,
        NativePathIdentity.Token identity, NativePathIdentity.Token ownerIdentity) {
      this.owner = owner; this.command = command; this.target = target; this.expiry = expiry;
      this.identity = identity; this.ownerIdentity = ownerIdentity;
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
