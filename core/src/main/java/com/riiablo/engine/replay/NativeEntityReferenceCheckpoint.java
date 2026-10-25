package com.riiablo.engine.replay;

import com.artemis.World;
import com.riiablo.engine.server.component.NativePathIdentity;
import java.io.*;
import java.util.Set;

/** Explicit entity-ID/lifetime semantics, never a serialized World or pooled component/token. */
public final class NativeEntityReferenceCheckpoint {
  private static final int UNGUARDED = 0, CURRENT = 1, RETIRED = 2;
  private final int id, lifetime;

  private NativeEntityReferenceCheckpoint(int id, int lifetime) {
    if (id < -1 || lifetime < UNGUARDED || lifetime > RETIRED || id < 0 && lifetime != UNGUARDED) {
      throw new IllegalArgumentException("native entity reference");
    }
    this.id = id; this.lifetime = lifetime;
  }
  public int entityId() { return id; }

  public static NativeEntityReferenceCheckpoint capture(World world, int id, NativePathIdentity.Token token) {
    if (token != null && world == null) throw new IllegalStateException("native reference requires owning World");
    return new NativeEntityReferenceCheckpoint(id, token == null ? UNGUARDED
        : token.isCurrent(world, id) ? CURRENT : RETIRED);
  }
  public void write(DataOutputStream out) throws IOException { out.writeInt(id); out.writeByte(lifetime); }
  public static NativeEntityReferenceCheckpoint read(DataInputStream in) throws IOException {
    return new NativeEntityReferenceCheckpoint(in.readInt(), in.readUnsignedByte());
  }
  public void validateCoverage(Set<Integer> covered) {
    // A null token still dereferences the current ID in legacy AI; only retired references cannot act.
    if (id >= 0 && lifetime != RETIRED && !covered.contains(id)) {
      throw new IllegalArgumentException("AI entity reference outside checkpoint: " + id);
    }
  }
  public void validateRestore(World world) {
    if (lifetime == UNGUARDED) return;
    if (world == null) throw new IllegalStateException("native reference requires target World");
    if (lifetime == CURRENT) current(world);
  }
  public NativePathIdentity.Token resolve(World world) {
    validateRestore(world);
    if (lifetime == UNGUARDED) return null;
    return lifetime == RETIRED ? NativePathIdentity.retiredToken(world, id) : current(world);
  }
  private NativePathIdentity.Token current(World world) {
    if (!active(world, id)) throw new IllegalStateException("AI current entity missing: " + id);
    NativePathIdentity identity = world.getMapper(NativePathIdentity.class).get(id);
    if (identity == null || identity.token() == null || !identity.token().isCurrent(world, id)) {
      throw new IllegalStateException("AI current lifetime missing: " + id);
    }
    return identity.token();
  }
  private static boolean active(World world, int id) {
    // Artemis isActive alone does not establish that an ID was ever allocated.
    try {
      return world.getEntity(id) != null && world.getEntityManager().isActive(id);
    } catch (IndexOutOfBoundsException unallocated) {
      return false;
    }
  }
}
