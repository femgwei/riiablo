package com.riiablo.engine.replay;

import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.World;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.component.NativeTargetNode;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.pet.PetType;
import java.io.*;
import java.util.*;

/** Raw summon controllers and World quota high-water mark, not Item graphs or live actions. */
public final class SummonRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 32 * 1024 * 1024;
  private final byte[] bytes;
  private SummonRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public Set<Integer> entityIds() { return Collections.unmodifiableSet(decode().pets.keySet()); }
  // Fresh decoded values for package-owned preflight only, never live components.
  java.util.Map<Integer, SummonedPet> entries() { return decode().pets; }
  public static SummonRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("summon runtime length");
    }
    SummonRuntimeCheckpoint result = new SummonRuntimeCheckpoint(bytes.clone());
    result.decode(); return result;
  }

  public static SummonRuntimeCheckpoint capture(World world, int[] ids) {
    NativePlayerTargetSlots slots = world.getSystem(NativePlayerTargetSlots.class);
    if (slots == null) throw new IllegalStateException("summon World order owner missing");
    EntityAllocatorCheckpoint allocator = EntityAllocatorCheckpoint.capture(world);
    Set<Integer> covered = new TreeSet<>();
    for (int id : ids) if (!allocator.active(id) || !covered.add(id)) {
      throw new IllegalArgumentException("summon capture host");
    }
    for (int id : allocator.activeIds()) if (!covered.contains(id) && world.getMapper(SummonedPet.class).has(id)) {
      throw new IllegalStateException("local summon requires owner checkpoint");
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION); out.writeLong(slots.checkpointPetSpawnSequence()); out.writeInt(covered.size());
      for (int id : covered) {
        SummonedPet pet = world.getMapper(SummonedPet.class).get(id);
        out.writeInt(id); out.writeBoolean(pet != null);
        if (pet == null) continue;
        if (pet.sourceItem != null) throw new IllegalStateException("Iron Golem Item graph requires owner checkpoint");
        NativeTargetNode node = world.getMapper(NativeTargetNode.class).get(id);
        if (pet.hasDetachedTargetOrder() || pet.checkpointTargetNode() != node
            || node == null || !node.boundTo(slots)) {
          throw new IllegalStateException("detached summon target membership");
        }
        for (int reference : new int[] {pet.bladeMissileId, pet.infernoTargetId, pet.deathLastCorpseId, pet.controllerId}) {
          if (reference >= 0 && allocator.active(reference) && !covered.contains(reference)) {
            throw new IllegalStateException("live summon reference outside checkpoint");
          }
        }
        writePet(out, pet);
      }
      out.flush(); SummonRuntimeCheckpoint result = fromBytes(bytes.toByteArray());
      result.validateRestore(world); return result;
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  public void validateRestore(World world) {
    Decoded d = decode();
    if (world.getSystem(NativePlayerTargetSlots.class) == null) {
      throw new IllegalStateException("summon World order owner missing");
    }
    for (int id : EntityAllocatorCheckpoint.capture(world).activeIds()) {
      if (!d.pets.containsKey(id) && world.getMapper(SummonedPet.class).has(id)) {
        throw new IllegalStateException("cannot rewind quota around an uncovered live summon: " + id);
      }
    }
    for (int id : d.pets.keySet()) {
      if (!world.getEntityManager().isActive(id)) throw new IllegalStateException("summon host missing: " + id);
      SummonedPet current = world.getMapper(SummonedPet.class).get(id);
      if (current != null && current.sourceItem != null) {
        throw new IllegalStateException("cannot overwrite uncovered Iron Golem Item graph");
      }
    }
  }

  /** TARGETS rebinds the shared node after this raw overlay; never calls set or consumes quota. */
  public void restore(World world) {
    validateRestore(world); Decoded d = decode();
    for (Map.Entry<Integer, SummonedPet> entry : d.pets.entrySet()) {
      if (entry.getValue() == null) world.getMapper(SummonedPet.class).remove(entry.getKey());
      else copy(entry.getValue(), world.getMapper(SummonedPet.class).create(entry.getKey()));
    }
    world.getSystem(NativePlayerTargetSlots.class).restorePetSpawnSequence(d.sequence);
  }

  /** Check the actual unsigned-short projection, including its derived unsummon flag. */
  public void validateProjections(java.util.Map<Integer, SummonedPet> projections, Set<Integer> unsummonable) {
    Decoded d = decode();
    if (!d.pets.keySet().equals(projections.keySet())) throw new IllegalArgumentException("summon projection coverage mismatch");
    for (int id : d.pets.keySet()) {
      SummonedPet p = d.pets.get(id), projection = projections.get(id);
      if ((p != null) != (projection != null) || p != null && (p.ownerId != projection.ownerId
          || !Objects.equals(p.petType, projection.petType) || (Math.max(0, p.skillId) & 0xffff) != projection.skillId
          || PetType.canBeUnsummoned(p.petType) != unsummonable.contains(id))) {
        throw new IllegalArgumentException("summon network/runtime mismatch: " + id);
      }
    }
  }

  private static void writePet(DataOutputStream o, SummonedPet p) throws IOException {
    o.writeInt(p.ownerId); CheckpointIO.writeString(o, p.petType); o.writeInt(p.skillId); o.writeInt(p.skillLevel);
    o.writeBoolean(p.passive); o.writeInt(p.durationFrames); CheckpointIO.writeFloat(o, p.elapsedFrames);
    o.writeLong(p.spawnOrder); o.writeBoolean(p.unsummonPending); CheckpointIO.writeString(o, p.unsummonReason);
    o.writeBoolean(p.networkReplica); o.writeInt(p.maxShots); o.writeInt(p.shotsFired); o.writeInt(p.attackCooldownFrames);
    o.writeBoolean(p.bladeSentinel); o.writeBoolean(p.hasTrapTarget);
    CheckpointIO.writeFloat(o, p.trapTargetX); CheckpointIO.writeFloat(o, p.trapTargetY);
    CheckpointIO.writeFloat(o, p.bladeOriginX); CheckpointIO.writeFloat(o, p.bladeOriginY);
    o.writeBoolean(p.bladeMovingToTarget); o.writeInt(p.bladeMissileId); o.writeBoolean(p.infernoChanneling);
    o.writeInt(p.infernoFrame); o.writeInt(p.infernoSequencePoint); o.writeInt(p.infernoEndFrame);
    o.writeInt(p.infernoRemainingFrames); o.writeInt(p.infernoPulseFrames); o.writeInt(p.infernoPulseCooldownFrames);
    o.writeInt(p.infernoTargetId); o.writeInt(p.deathLastCorpseId); o.writeBoolean(p.deathPending);
    CheckpointIO.writeFloat(o, p.deadFrames); o.writeBoolean(p.boneWall); o.writeInt(p.controllerId);
  }
  private static SummonedPet readPet(DataInputStream in) throws IOException {
    SummonedPet p = new SummonedPet();
    p.ownerId = in.readInt(); p.petType = CheckpointIO.readString(in); p.skillId = in.readInt(); p.skillLevel = in.readInt();
    p.passive = CheckpointIO.readBoolean(in); p.durationFrames = in.readInt(); p.elapsedFrames = CheckpointIO.readFloat(in);
    p.spawnOrder = in.readLong(); p.unsummonPending = CheckpointIO.readBoolean(in); p.unsummonReason = CheckpointIO.readString(in);
    p.networkReplica = CheckpointIO.readBoolean(in); p.maxShots = in.readInt(); p.shotsFired = in.readInt(); p.attackCooldownFrames = in.readInt();
    p.bladeSentinel = CheckpointIO.readBoolean(in); p.hasTrapTarget = CheckpointIO.readBoolean(in);
    p.trapTargetX = CheckpointIO.readFloat(in); p.trapTargetY = CheckpointIO.readFloat(in);
    p.bladeOriginX = CheckpointIO.readFloat(in); p.bladeOriginY = CheckpointIO.readFloat(in);
    p.bladeMovingToTarget = CheckpointIO.readBoolean(in); p.bladeMissileId = in.readInt(); p.infernoChanneling = CheckpointIO.readBoolean(in);
    p.infernoFrame = in.readInt(); p.infernoSequencePoint = in.readInt(); p.infernoEndFrame = in.readInt();
    p.infernoRemainingFrames = in.readInt(); p.infernoPulseFrames = in.readInt(); p.infernoPulseCooldownFrames = in.readInt();
    p.infernoTargetId = in.readInt(); p.deathLastCorpseId = in.readInt(); p.deathPending = CheckpointIO.readBoolean(in);
    p.deadFrames = CheckpointIO.readFloat(in); p.boneWall = CheckpointIO.readBoolean(in); p.controllerId = in.readInt();
    if (p.ownerId < -1 || p.skillId < -1 || p.skillLevel < 0 || p.durationFrames < 0 || p.elapsedFrames < 0
        || p.spawnOrder < 0 || p.maxShots < 0 || p.shotsFired < 0
        || p.bladeMissileId < -1 || p.infernoEndFrame < -1
        || p.infernoPulseFrames < 0 || p.infernoTargetId < -1
        || p.deathLastCorpseId < -1 || p.deadFrames < 0 || p.controllerId < -1) {
      throw new IllegalArgumentException("summon runtime scalar");
    }
    return p;
  }
  private static void copy(SummonedPet a, SummonedPet b) {
    b.ownerId = a.ownerId; b.petType = a.petType; b.skillId = a.skillId; b.skillLevel = a.skillLevel;
    b.passive = a.passive; b.durationFrames = a.durationFrames; b.elapsedFrames = a.elapsedFrames;
    b.spawnOrder = a.spawnOrder; b.unsummonPending = a.unsummonPending; b.unsummonReason = a.unsummonReason;
    b.networkReplica = a.networkReplica; b.maxShots = a.maxShots; b.shotsFired = a.shotsFired;
    b.attackCooldownFrames = a.attackCooldownFrames; b.bladeSentinel = a.bladeSentinel; b.hasTrapTarget = a.hasTrapTarget;
    b.trapTargetX = a.trapTargetX; b.trapTargetY = a.trapTargetY; b.bladeOriginX = a.bladeOriginX; b.bladeOriginY = a.bladeOriginY;
    b.bladeMovingToTarget = a.bladeMovingToTarget; b.bladeMissileId = a.bladeMissileId;
    b.infernoChanneling = a.infernoChanneling; b.infernoFrame = a.infernoFrame; b.infernoSequencePoint = a.infernoSequencePoint;
    b.infernoEndFrame = a.infernoEndFrame; b.infernoRemainingFrames = a.infernoRemainingFrames;
    b.infernoPulseFrames = a.infernoPulseFrames; b.infernoPulseCooldownFrames = a.infernoPulseCooldownFrames;
    b.infernoTargetId = a.infernoTargetId; b.deathLastCorpseId = a.deathLastCorpseId;
    b.deathPending = a.deathPending; b.deadFrames = a.deadFrames; b.boneWall = a.boneWall; b.controllerId = a.controllerId;
    b.sourceItem = null;
  }
  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("summon runtime version");
      Decoded d = new Decoded(); d.sequence = in.readLong();
      if (d.sequence < 0) throw new IllegalArgumentException("summon spawn sequence");
      int count = CheckpointIO.count(in, 1000000), previous = -1; Set<Long> orders = new HashSet<>();
      for (int i = 0; i < count; i++) {
        int id = in.readInt();
        if (id <= previous) throw new IllegalArgumentException("summon host identity/order");
        previous = id; SummonedPet p = CheckpointIO.readBoolean(in) ? readPet(in) : null;
        if (p != null && (p.spawnOrder > d.sequence || p.spawnOrder != 0 && !orders.add(p.spawnOrder))) {
          throw new IllegalArgumentException("summon quota order");
        }
        d.pets.put(id, p);
      }
      for (SummonedPet p : d.pets.values()) if (p != null && p.ownerId >= 0 && !d.pets.containsKey(p.ownerId)) {
        throw new IllegalArgumentException("summon owner outside checkpoint");
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing summon runtime");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated summon runtime", e); }
  }
  private static final class Decoded {
    long sequence;
    final Map<Integer, SummonedPet> pets = new TreeMap<>();
  }
}
