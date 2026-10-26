package com.riiablo.engine.replay;

import com.artemis.EntityAllocatorCheckpoint;
import com.artemis.World;
import com.artemis.annotations.Wire;
import com.riiablo.engine.server.combat.CombatPositionHistory;
import com.riiablo.engine.server.combat.CombatPositionCheckpoint;
import com.riiablo.engine.server.combat.CombatPositionCaptureSystem;
import com.riiablo.engine.server.MercenaryFollowSystem;
import com.riiablo.engine.server.MercenarySkillSystem;
import com.riiablo.engine.server.SummonedPetSystem;
import com.riiablo.engine.server.component.*;
import java.io.*;
import java.util.*;

/** Hireling/summon components and world-owned companion retry clocks; nested in TARGETS v3.
 * Includes shared owner/combat position history, not equipment Item references or actions.
 */
public final class CompanionRuntimeCheckpoint {
  private static final int VERSION = 4;
  public static final int MAX_BYTES = 48 * 1024 * 1024;
  private final byte[] bytes;
  private CompanionRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public Set<Integer> entityIds() { return Collections.unmodifiableSet(decode().mercenaries.keySet()); }
  /** Null for companion v1: old archives did not cover summon controllers or quota. */
  public SummonRuntimeCheckpoint summonRuntime() { return decode().summons; }
  /** Null when absent or uncovered; hasPositionHistoryCoverage distinguishes old archives. */
  public CombatPositionCheckpoint positionHistory() { return decode().history; }
  public boolean hasPositionHistoryCoverage() { return decode().historyCovered; }
  public MercenaryServiceCheckpoint mercenaryService() { return decode().service; }
  public boolean hasMercenaryServiceCoverage() { return decode().serviceCovered; }

  private static final class Binding {
    @Wire(name = "combatPositionHistory", failOnNull = false) CombatPositionHistory history;
    @Wire(name = "map", failOnNull = false) com.riiablo.map.Map map;
  }
  private static Binding binding(World world) {
    Binding b = new Binding(); world.inject(b);
    CombatPositionCaptureSystem capture = world.getSystem(CombatPositionCaptureSystem.class);
    if (b.history != null && b.history.checkpointMap() != b.map
        || capture != null && (b.history == null || capture.checkpointHistory() != b.history)) {
      throw new IllegalStateException("position history owner binding mismatch");
    }
    return b;
  }

  public static CompanionRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("companion runtime length");
    }
    CompanionRuntimeCheckpoint result = new CompanionRuntimeCheckpoint(bytes.clone());
    result.decode(); return result;
  }

  public static CompanionRuntimeCheckpoint capture(World world, int[] ids) {
    EntityAllocatorCheckpoint allocator = EntityAllocatorCheckpoint.capture(world);
    Set<Integer> covered = new TreeSet<>();
    for (int id : ids) if (!allocator.active(id) || !covered.add(id)) {
      throw new IllegalArgumentException("companion capture host");
    }
    for (int id : allocator.activeIds()) if (!covered.contains(id)
        && world.getMapper(Mercenary.class).has(id)) {
      throw new IllegalStateException("local hireling requires owner checkpoint");
    }
    MercenaryFollowSystem follow = world.getSystem(MercenaryFollowSystem.class);
    MercenarySkillSystem skills = world.getSystem(MercenarySkillSystem.class);
    SummonedPetSystem pets = world.getSystem(SummonedPetSystem.class);
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION);
      writeTimers(out, follow == null ? null : follow.checkpointRepathCooldowns());
      writeTimers(out, skills == null ? null : skills.checkpointSkillCooldowns());
      writeTimers(out, pets == null ? null : pets.checkpointRegroupCooldowns());
      out.writeInt(covered.size());
      for (int id : covered) {
        out.writeInt(id); Mercenary merc = world.getMapper(Mercenary.class).get(id);
        out.writeBoolean(merc != null);
        if (merc != null) {
          if (merc.hasDetachedTargetOrder() || merc.checkpointTargetNode()
              != world.getMapper(NativeTargetNode.class).get(id)) {
            throw new IllegalStateException("detached hireling target membership");
          }
          if (merc.targetId >= 0 && allocator.active(merc.targetId) && !covered.contains(merc.targetId)) {
            throw new IllegalStateException("hireling target outside checkpoint");
          }
          writeMercenary(out, merc);
        }
      }
      byte[] summons = SummonRuntimeCheckpoint.capture(world, ids).bytes();
      out.writeInt(summons.length); out.write(summons);
      Binding b = binding(world);
      out.writeBoolean(b.history != null);
      if (b.history != null) {
        byte[] history = CombatPositionCheckpoint.capture(b.history).bytes();
        out.writeInt(history.length); out.write(history);
      }
      CombatPositionCaptureSystem capture = world.getSystem(CombatPositionCaptureSystem.class);
      out.writeBoolean(capture != null);
      if (capture != null) out.writeLong(capture.checkpointTick());
      boolean service = world.getSystem(com.riiablo.engine.server.quest.NativeMercenaryRewardSystem.class) != null;
      out.writeBoolean(service);
      if (service) {
        byte[] image = MercenaryServiceCheckpoint.capture(world, ids).bytes();
        out.writeInt(image.length); out.write(image);
      }
      out.flush(); CompanionRuntimeCheckpoint result = fromBytes(bytes.toByteArray());
      result.validateRestore(world); return result;
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  /** The network projection must agree with the authoritative overlay, including absence. */
  public void validateMercenaryProjections(Map<Integer, Mercenary> projections) {
    Decoded d = decode();
    if (!d.mercenaries.keySet().equals(projections.keySet())) {
      throw new IllegalArgumentException("hireling projection coverage mismatch");
    }
    for (int id : d.mercenaries.keySet()) validateProjection(id, d.mercenaries.get(id), projections.get(id));
  }

  private static void validateProjection(int id, Mercenary m, Mercenary projection) {
    if ((m == null) != (projection == null) || m != null && (m.ownerId != projection.ownerId
        || m.mercType != projection.mercType || m.hirelingId() != projection.hirelingId
        || m.level != projection.level || m.seed != projection.seed || m.nameId != projection.nameId
        || m.experience != projection.experience || m.flags != projection.flags
        || m.progressionRevision != projection.progressionRevision || m.equipmentRevision != projection.equipmentRevision)) {
      throw new IllegalArgumentException("hireling network/runtime mismatch: " + id);
    }
  }

  /** Validate every dependency before TARGETS rewinds its sequence or applies any node. */
  public void validateRestore(World world) {
    Decoded d = decode();
    if ((d.follow != null) != (world.getSystem(MercenaryFollowSystem.class) != null)
        || (d.skills != null) != (world.getSystem(MercenarySkillSystem.class) != null)
        || (d.pets != null) != (world.getSystem(SummonedPetSystem.class) != null)) {
      throw new IllegalStateException("companion system topology mismatch");
    }
    for (int id : d.mercenaries.keySet()) if (!world.getEntityManager().isActive(id)) {
      throw new IllegalStateException("companion host missing: " + id);
    }
    if (d.summons != null) d.summons.validateRestore(world);
    if (d.serviceCovered) {
      if ((d.service != null) != (world.getSystem(com.riiablo.engine.server.quest.NativeMercenaryRewardSystem.class) != null)) {
        throw new IllegalStateException("hireling service topology mismatch");
      }
      if (d.service != null) d.service.validateRestore(world);
    }
    if (d.historyCovered) {
      Binding b = binding(world);
      if ((b.history != null) != (d.history != null)
          || (world.getSystem(CombatPositionCaptureSystem.class) != null) != (d.captureTick != null)) {
        throw new IllegalStateException("position history system topology mismatch");
      }
      if (d.history != null) d.history.validateRestore(b.history);
    }
    Map<Integer, SummonedPet> restoredPets = d.summons == null ? null : d.summons.entries();
    if (d.follow != null) for (int id : d.follow.keySet()) {
      SummonedPet pet = restoredPets != null ? restoredPets.get(id) : world.getMapper(SummonedPet.class).get(id);
      boolean valkyrie = pet != null && !pet.passive && !pet.boneWall && "valkyrie".equalsIgnoreCase(pet.petType);
      if ((d.mercenaries.get(id) == null && !valkyrie)
          || !world.getMapper(Position.class).has(id) || !world.getMapper(MapWrapper.class).has(id)) {
        throw new IllegalStateException("follow timer host missing: " + id);
      }
    }
    if (d.skills != null) for (int id : d.skills.keySet()) {
      if (d.mercenaries.get(id) == null || !world.getMapper(Monster.class).has(id)
          || !world.getMapper(Position.class).has(id)) {
        throw new IllegalStateException("skill timer host missing: " + id);
      }
    }
    if (d.pets != null) for (int id : d.pets.keySet()) {
      if (!(restoredPets != null ? restoredPets.get(id) != null : world.getMapper(SummonedPet.class).has(id))) {
        throw new IllegalStateException("summon regroup timer requires covered controller: " + id);
      }
    }
  }

  /** Raw assignments only: never set(), insert a target node, seed RNG or tick a system. */
  public void restore(World world) {
    validateRestore(world); Decoded d = decode();
    if (d.summons != null) d.summons.restore(world);
    if (d.service != null) d.service.restore(world);
    for (Map.Entry<Integer, Mercenary> entry : d.mercenaries.entrySet()) {
      int id = entry.getKey(); Mercenary m = entry.getValue();
      if (m == null) world.getMapper(Mercenary.class).remove(id);
      else copy(m, world.getMapper(Mercenary.class).create(id));
    }
    if (d.follow != null) world.getSystem(MercenaryFollowSystem.class).restoreRepathCooldowns(d.follow);
    if (d.skills != null) world.getSystem(MercenarySkillSystem.class).restoreSkillCooldowns(d.skills);
    if (d.pets != null) world.getSystem(SummonedPetSystem.class).restoreRegroupCooldowns(d.pets);
    if (d.historyCovered) {
      if (d.history != null) d.history.restore(binding(world).history);
      if (d.captureTick != null) world.getSystem(CombatPositionCaptureSystem.class).restoreCheckpointTick(d.captureTick);
    }
  }

  private static void writeTimers(DataOutputStream out, Map<Integer, Float> timers) throws IOException {
    out.writeBoolean(timers != null);
    if (timers == null) return;
    out.writeInt(timers.size());
    for (Map.Entry<Integer, Float> entry : new TreeMap<>(timers).entrySet()) {
      out.writeInt(entry.getKey()); CheckpointIO.writeFloat(out, entry.getValue());
    }
  }
  private static Map<Integer, Float> readTimers(DataInputStream in) throws IOException {
    if (!CheckpointIO.readBoolean(in)) return null;
    Map<Integer, Float> result = new TreeMap<>(); int count = CheckpointIO.count(in, 1000000), previous = -1;
    for (int i = 0; i < count; i++) {
      int id = in.readInt(); float value = CheckpointIO.readFloat(in);
      if (id <= previous || value < 0f) throw new IllegalArgumentException("companion timer identity/value");
      previous = id; result.put(id, value);
    }
    return result;
  }
  private static void writeMercenary(DataOutputStream o, Mercenary m) throws IOException {
    o.writeInt(m.ownerId); o.writeInt(m.mercType); o.writeInt(m.hirelingId); o.writeInt(m.level);
    o.writeInt(m.seed); o.writeInt(m.nameId); o.writeLong(m.experience); o.writeInt(m.flags);
    o.writeLong(m.progressionRevision); o.writeLong(m.equipmentRevision);
    for (int value : m.skills) o.writeInt(value);
    for (int value : m.skillLevels) o.writeInt(value);
    o.writeInt(m.aiRngState); o.writeInt(m.aiChanceParam); o.writeInt(m.targetId);
  }
  private static Mercenary readMercenary(DataInputStream in) throws IOException {
    Mercenary m = new Mercenary();
    m.ownerId = in.readInt(); m.mercType = in.readInt(); m.hirelingId = in.readInt(); m.level = in.readInt();
    m.seed = in.readInt(); m.nameId = in.readInt(); m.experience = in.readLong(); m.flags = in.readInt();
    m.progressionRevision = in.readLong(); m.equipmentRevision = in.readLong();
    for (int i = 0; i < m.skills.length; i++) m.skills[i] = in.readInt();
    for (int i = 0; i < m.skillLevels.length; i++) m.skillLevels[i] = in.readInt();
    m.aiRngState = in.readInt(); m.aiChanceParam = in.readInt(); m.targetId = in.readInt();
    if (m.ownerId < -1 || m.mercType < 0 || m.hirelingId < -1 || m.level < 0 || m.experience < 0
        || m.progressionRevision < 0 || m.equipmentRevision < 0 || m.targetId < -1) {
      throw new IllegalArgumentException("hireling runtime scalar");
    }
    for (int skill : m.skills) if (skill < -1) throw new IllegalArgumentException("hireling skill");
    for (int level : m.skillLevels) if (level < 0) throw new IllegalArgumentException("hireling skill level");
    return m;
  }
  private static void copy(Mercenary a, Mercenary b) {
    b.ownerId = a.ownerId; b.mercType = a.mercType; b.hirelingId = a.hirelingId; b.level = a.level;
    b.seed = a.seed; b.nameId = a.nameId; b.experience = a.experience; b.flags = a.flags;
    b.progressionRevision = a.progressionRevision; b.equipmentRevision = a.equipmentRevision;
    System.arraycopy(a.skills, 0, b.skills, 0, a.skills.length);
    System.arraycopy(a.skillLevels, 0, b.skillLevels, 0, a.skillLevels.length);
    b.aiRngState = a.aiRngState; b.aiChanceParam = a.aiChanceParam; b.targetId = a.targetId;
  }
  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      int version = in.readInt();
      if (version < 1 || version > VERSION) throw new IllegalArgumentException("companion runtime version");
      Decoded d = new Decoded(); d.follow = readTimers(in); d.skills = readTimers(in); d.pets = readTimers(in);
      int count = CheckpointIO.count(in, 1000000), previous = -1;
      for (int i = 0; i < count; i++) {
        int id = in.readInt();
        if (id <= previous) throw new IllegalArgumentException("companion host identity/order");
        previous = id; d.mercenaries.put(id, CheckpointIO.readBoolean(in) ? readMercenary(in) : null);
      }
      for (Mercenary m : d.mercenaries.values()) if (m != null && m.ownerId >= 0 && !d.mercenaries.containsKey(m.ownerId)) {
        throw new IllegalArgumentException("hireling owner outside checkpoint");
      }
      for (Map<Integer, Float> timers : Arrays.asList(d.follow, d.skills, d.pets)) {
        if (timers != null && !d.mercenaries.keySet().containsAll(timers.keySet())) {
          throw new IllegalArgumentException("companion timer outside checkpoint");
        }
      }
      if (version >= 2) {
        int length = CheckpointIO.count(in, SummonRuntimeCheckpoint.MAX_BYTES);
        if (length == 0 || length > in.available()) throw new IllegalArgumentException("summon runtime length");
        byte[] image = new byte[length]; in.readFully(image); d.summons = SummonRuntimeCheckpoint.fromBytes(image);
        if (!d.summons.entityIds().equals(d.mercenaries.keySet())) {
          throw new IllegalArgumentException("summon host coverage mismatch");
        }
      }
      if (version >= 3) {
        d.historyCovered = true;
        if (CheckpointIO.readBoolean(in)) {
          int length = CheckpointIO.count(in, CombatPositionCheckpoint.MAX_BYTES);
          if (length == 0 || length > in.available()) throw new IllegalArgumentException("position history length");
          byte[] image = new byte[length]; in.readFully(image); d.history = CombatPositionCheckpoint.fromBytes(image);
        }
        if (CheckpointIO.readBoolean(in)) {
          d.captureTick = in.readLong();
          if (d.captureTick < 0 || d.history == null) throw new IllegalArgumentException("position capture tick/history");
        }
      }
      if (version >= 4) {
        d.serviceCovered = true;
        if (CheckpointIO.readBoolean(in)) {
          int length = CheckpointIO.count(in, MercenaryServiceCheckpoint.MAX_BYTES);
          if (length == 0 || length > in.available()) throw new IllegalArgumentException("hireling service length");
          byte[] image = new byte[length]; in.readFully(image); d.service = MercenaryServiceCheckpoint.fromBytes(image);
          d.service.validateComponents(d.mercenaries);
        }
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing companion runtime");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated companion runtime", e); }
  }
  private static final class Decoded {
    boolean serviceCovered;
    MercenaryServiceCheckpoint service;
    boolean historyCovered;
    CombatPositionCheckpoint history;
    Long captureTick;
    SummonRuntimeCheckpoint summons;
    Map<Integer, Float> follow, skills, pets;
    final Map<Integer, Mercenary> mercenaries = new TreeMap<>();
  }
}
