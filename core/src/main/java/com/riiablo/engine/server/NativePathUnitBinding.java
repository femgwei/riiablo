package com.riiablo.engine.server;

import com.artemis.World;
import com.d2moo.common.path.D2PathUnits;
import com.riiablo.engine.server.component.NativePathIdentity;
import java.util.Objects;

/** Binds resolved native data to actual host owner/target lifetimes.
 * Capture/use on the serialized game thread. Does not infer native metadata,
 * fp16 or direction from ECS floats, allocate identities, or install movement. */
public final class NativePathUnitBinding {
  public interface Reader {
    /** Return a complete immutable native-data snapshot, not an ECS component. */
    D2PathUnits.Unit read(World world, int entityId);
  }

  private final World world;
  private final int ownerId, targetId;
  private final NativePathIdentity.Token ownerIdentity, targetIdentity;
  private final Reader reader;

  /** A fresh target selection. Saved requests must pass their original tokens below. */
  public NativePathUnitBinding(World world, int ownerId, int targetId, Reader reader) {
    this(world, ownerId, identity(world, ownerId), targetId, identity(world, targetId), reader);
  }

  /** Resume a saved selection without silently looking up a replacement at the same ID. */
  public NativePathUnitBinding(World world, int ownerId, NativePathIdentity.Token ownerIdentity,
      int targetId, NativePathIdentity.Token targetIdentity, Reader reader) {
    this.world = Objects.requireNonNull(world);
    this.ownerId = ownerId;
    this.targetId = targetId;
    this.reader = Objects.requireNonNull(reader);
    this.ownerIdentity = Objects.requireNonNull(ownerIdentity);
    this.targetIdentity = Objects.requireNonNull(targetIdentity);
    requireCurrent();
  }

  /** Recapture after movement/metadata changes; the old observation retains its data.
   * Both identity checks precede native data reads and are repeated after them. */
  public D2PathUnits.Observation observe(int speed) {
    if (speed < 0 || speed > 255) throw new IllegalArgumentException("native unsigned speed byte required");
    requireCurrent();
    D2PathUnits.Unit owner = Objects.requireNonNull(reader.read(world, ownerId), "owner snapshot");
    D2PathUnits.Unit target = ownerId == targetId ? owner
        : Objects.requireNonNull(reader.read(world, targetId), "target snapshot");
    requireCurrent();
    return D2PathUnits.observation(owner, target, speed, this::requireCurrent);
  }

  public void requireCurrent() {
    if (!ownerIdentity.isCurrent(world, ownerId) || !targetIdentity.isCurrent(world, targetId)) {
      throw new IllegalStateException("owner/target lifetime changed; bind a new path target");
    }
  }

  private static NativePathIdentity.Token identity(World world, int id) {
    Objects.requireNonNull(world);
    NativePathIdentity component = id < 0 ? null : world.getMapper(NativePathIdentity.class).get(id);
    if (component == null || component.token() == null) {
      throw new IllegalArgumentException("known factory allocation identity required");
    }
    return component.token();
  }
}
