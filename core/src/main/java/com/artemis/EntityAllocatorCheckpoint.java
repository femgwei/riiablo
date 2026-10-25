package com.artemis;

import com.artemis.utils.BitVector;
import com.artemis.utils.IntBag;
import com.artemis.utils.IntDeque;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Owner-thread allocator boundary for pinned artemis-odb 2.3.0.
 * Only three private fields are accessed; no Unsafe, component object graph,
 * replacement EntityManager or simulation tick. Fail closed on layout drift.
 * Capture requires completed deletions; pending insertions are allowed.
 */
public final class EntityAllocatorCheckpoint {
  public static final int MAX_NEXT_ID = CheckpointEntityAllocation.MAX_ENTITY_ID + 1;
  public static final int MAX_BYTES = 12 + MAX_NEXT_ID * 4;
  private static final Field NEXT = field("nextId", int.class, false);
  private static final Field LIMBO = field("limbo", IntDeque.class, true);
  private static final Field RECYCLED = field("recycled", BitVector.class, true);
  private final int nextId;
  private final int[] freeIds;
  private final BitSet free;

  private EntityAllocatorCheckpoint(int nextId, int[] freeIds) {
    if (nextId < 0 || nextId > MAX_NEXT_ID || freeIds.length > nextId) {
      throw new IllegalArgumentException("allocator bounds");
    }
    this.nextId = nextId;
    this.freeIds = freeIds.clone();
    free = new BitSet(nextId);
    for (int id : freeIds) {
      if (id < 0 || id >= nextId || free.get(id)) throw new IllegalArgumentException("allocator free id");
      free.set(id);
    }
  }

  public static EntityAllocatorCheckpoint of(int nextId, int[] freeIds) {
    return new EntityAllocatorCheckpoint(nextId, Objects.requireNonNull(freeIds));
  }

  public int nextId() { return nextId; }
  public int[] freeIds() { return freeIds.clone(); }
  public boolean active(int id) { return id >= 0 && id < nextId && !free.get(id); }
  public int[] activeIds() {
    int[] ids = new int[nextId - freeIds.length];
    for (int id = 0, i = 0; id < nextId; id++) if (!free.get(id)) ids[i++] = id;
    return ids;
  }

  public byte[] bytes() {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(1); out.writeInt(nextId); out.writeInt(freeIds.length);
      for (int id : freeIds) out.writeInt(id);
      return bytes.toByteArray();
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  public static EntityAllocatorCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length < 12 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("allocator length");
    }
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != 1) throw new IllegalArgumentException("allocator version");
      int next = in.readInt(), count = in.readInt();
      if (next < 0 || next > MAX_NEXT_ID || count < 0 || count > next || bytes.length != 12 + count * 4) {
        throw new IllegalArgumentException("allocator count");
      }
      int[] free = new int[count];
      for (int i = 0; i < count; i++) free[i] = in.readInt();
      return of(next, free);
    } catch (IOException e) { throw new IllegalArgumentException("truncated allocator", e); }
  }

  public static EntityAllocatorCheckpoint capture(World world) {
    Objects.requireNonNull(world);
    EntityManager manager = world.getEntityManager();
    try {
      int next = NEXT.getInt(manager);
      IntDeque queue = (IntDeque) LIMBO.get(manager);
      if (manager.getClass() != EntityManager.class || next < 0 || next > MAX_NEXT_ID || queue.size() > next) {
        throw new IllegalStateException("unsupported Artemis allocator implementation/bounds");
      }
      if (next != manager.entities.size()) throw new IllegalStateException("allocator entity store mismatch");
      int[] free = new int[queue.size()];
      for (int i = 0; i < free.length; i++) free[i] = queue.get(i);
      EntityAllocatorCheckpoint state = of(next, free);
      for (int id = 0; id < next; id++) {
        if (manager.entities.get(id) == null || manager.isActive(id) != state.active(id)) {
          throw new IllegalStateException("allocator recycled/free-list mismatch");
        }
        if (world.batchProcessor.isDeleted(id)) throw new IllegalStateException("allocator deletion batch is pending");
        if (state.free.get(id) && world.compositionId(id) != 0) {
          throw new IllegalStateException("allocator free id still has components");
        }
      }
      return state;
    } catch (IllegalAccessException e) { throw new IllegalStateException("Artemis allocator access unavailable", e); }
  }

  /** Local/non-networked entities must already have the exact archived id set. */
  public void validateRestore(World world, Set<Integer> incoming, Set<Integer> currentManaged) {
    EntityAllocatorCheckpoint current = capture(world);
    Set<Integer> expectedLocals = new TreeSet<>(), actualLocals = new TreeSet<>();
    for (int id : incoming) if (!active(id)) throw new IllegalArgumentException("payload uses free/unallocated id");
    for (int id : activeIds()) if (!incoming.contains(id)) expectedLocals.add(id);
    for (int id : current.activeIds()) if (!currentManaged.contains(id)) actualLocals.add(id);
    if (!expectedLocals.equals(actualLocals)) throw new IllegalStateException("allocator local entity set mismatch");
  }

  /** Publish structural changes and finish native purge, never invoke a system's process(). */
  public static void flushStructure(World world) {
    world.batchProcessor.update();
    IntBag purge = world.batchProcessor.getPendingPurge();
    if (!purge.isEmpty()) {
      world.getComponentManager().clean(purge);
      world.getEntityManager().clean(purge);
      world.batchProcessor.purgeComponents();
    }
  }

  /** Apply only after native set reconciliation, scope closure and structural purge. */
  public void restore(World world) {
    EntityAllocatorCheckpoint current = capture(world);
    if (!Arrays.equals(activeIds(), current.activeIds())) {
      throw new IllegalStateException("allocator active entity set mismatch");
    }
    EntityManager manager = world.getEntityManager();
    try {
      // Grow using Artemis itself, so all registered entity/component stores
      // retain their normal capacity guarantees. Raw reservations are unpublished.
      while (NEXT.getInt(manager) < nextId) manager.create();
      while (manager.entities.size() > nextId) manager.entities.removeLast();
      IntDeque queue = (IntDeque) LIMBO.get(manager);
      BitVector recycled = (BitVector) RECYCLED.get(manager);
      queue.clear(); recycled.clear();
      for (int id : freeIds) { queue.add(id); recycled.unsafeSet(id); }
      NEXT.setInt(manager, nextId);
    } catch (IllegalAccessException e) { throw new IllegalStateException("Artemis allocator access unavailable", e); }
  }

  private static Field field(String name, Class<?> type, boolean isFinal) {
    try {
      Field field = EntityManager.class.getDeclaredField(name);
      int modifiers = field.getModifiers();
      if (field.getType() != type || !Modifier.isPrivate(modifiers)
          || Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers) != isFinal) {
        throw new IllegalStateException("unsupported Artemis allocator layout: " + name);
      }
      field.setAccessible(true);
      return field;
    } catch (ReflectiveOperationException | SecurityException e) {
      throw new IllegalStateException("unsupported Artemis allocator layout: " + name, e);
    }
  }
}
