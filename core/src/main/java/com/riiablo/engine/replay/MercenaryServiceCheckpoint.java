package com.riiablo.engine.replay;

import com.artemis.World;
import com.riiablo.engine.shared.component.Selectable;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.pet.MercenaryManager;
import com.riiablo.engine.server.pet.MercenaryManager.ActiveMercenary;
import com.riiablo.engine.server.pet.MercenaryManagerCheckpoint;
import com.riiablo.engine.server.quest.NativeMercenaryRewardSystem;
import java.io.*;
import java.util.*;

/** Manager/service overlay. Item graphs and pending quest/NPC transactions remain separate owners. */
public final class MercenaryServiceCheckpoint {
  public static final int MAX_BYTES = 16 * 1024 * 1024;
  private final byte[] bytes;
  private MercenaryServiceCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public MercenaryManagerCheckpoint manager() { return decode().manager; }
  public static MercenaryServiceCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("hireling service length");
    MercenaryServiceCheckpoint result = new MercenaryServiceCheckpoint(bytes.clone()); result.decode(); return result;
  }
  private static NativeMercenaryRewardSystem service(World world) {
    NativeMercenaryRewardSystem service = world.getSystem(NativeMercenaryRewardSystem.class);
    if (service == null || service.checkpointManager().checkpointCallback() != service) {
      throw new IllegalStateException("hireling callback owner mismatch");
    }
    return service;
  }
  public static MercenaryServiceCheckpoint capture(World world, int[] ids) {
    NativeMercenaryRewardSystem service = service(world);
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(1); out.writeInt(service.checkpointDifficulty());
      byte[] image = MercenaryManagerCheckpoint.capture(service.checkpointManager()).bytes();
      out.writeInt(image.length); out.write(image);
      java.util.Map<Integer, ActiveMercenary> activeRecords = service.checkpointManager().checkpointActiveMercenaries();
      out.writeInt(activeRecords.size());
      for (ActiveMercenary active : activeRecords.values()) {
        Player owner = world.getMapper(Player.class).get(active.ownerId);
        if (owner == null || owner.data == null) throw new IllegalStateException("hireling D2S owner missing");
        com.riiablo.save.CharData.MercData saved = owner.data.getMerc();
        out.writeInt(active.ownerId); out.writeLong(saved.getItems().equipmentRevision());
        if (active.entityId < 0) {
          byte[] attributes = UnitStateRuntimeCheckpoint.captureAttributesImage(saved.getStats());
          out.writeInt(attributes.length); out.write(attributes);
        }
      }
      out.flush();
      MercenaryServiceCheckpoint result = fromBytes(bytes.toByteArray());
      java.util.Map<Integer, Mercenary> components = new TreeMap<>();
      for (int id : ids) components.put(id, world.getMapper(Mercenary.class).get(id));
      result.validateComponents(components); result.validateRestore(world);
      result.validateBindings(world, true); return result;
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }
  public void validateComponents(java.util.Map<Integer, Mercenary> components) {
    MercenaryManagerCheckpoint image = manager();
    Set<Integer> managed = new HashSet<>();
    if (!components.keySet().containsAll(image.rosterOwners())) throw new IllegalArgumentException("hireling roster owner outside checkpoint");
    for (ActiveMercenary active : image.activeRecords().values()) {
      if (!components.containsKey(active.ownerId)) throw new IllegalArgumentException("hireling owner outside checkpoint");
      if (active.entityId < 0) continue; // Saved dead record without a level-scoped corpse.
      Mercenary m = components.get(active.entityId);
      managed.add(active.entityId);
      if (m == null || m.ownerId != active.ownerId || m.mercType != active.definition.mercType
          || m.hirelingId() != active.definition.hirelingId() || m.seed != active.seed || m.nameId != active.nameId) {
        throw new IllegalArgumentException("hireling manager/component identity mismatch");
      }
    }
    for (java.util.Map.Entry<Integer, Mercenary> e : components.entrySet()) if (e.getValue() != null && !managed.contains(e.getKey())) {
      throw new IllegalArgumentException("hireling component missing service record");
    }
  }
  public void validateRestore(World world) {
    Decoded d = decode(); d.manager.validateRestore(service(world).checkpointManager());
    Set<Integer> owners = new TreeSet<>(d.manager.rosterOwners()); owners.addAll(d.manager.activeRecords().keySet());
    for (int id : owners) {
      Player owner = world.getMapper(Player.class).get(id);
      if (!world.getEntityManager().isActive(id) || owner == null || owner.data == null) {
        throw new IllegalStateException("hireling player host missing");
      }
      if (owner.data.getMerc().getItems().getItems().size != 0) throw new IllegalStateException("hireling Item graph uncovered");
    }
    for (ActiveMercenary active : d.manager.activeRecords().values()) if (active.entityId >= 0
        && (!world.getEntityManager().isActive(active.entityId)
            || !world.getMapper(AttributesWrapper.class).has(active.entityId))) {
      throw new IllegalStateException("hireling entity host missing");
    }
    // Do not silently overwrite an equipped record which is absent from the incoming image either.
    for (ActiveMercenary active : service(world).checkpointManager().checkpointActiveMercenaries().values()) {
      Player owner = world.getMapper(Player.class).get(active.ownerId);
      if (owner != null && owner.data != null && owner.data.getMerc().getItems().getItems().size != 0) {
        throw new IllegalStateException("cannot overwrite uncovered hireling Item graph");
      }
    }
  }
  /** Living/corpse entities share MercData stats; only entity-less records carry a private image. */
  public void validateUnitStateCoverage(java.util.Map<Integer, UnitStateRuntimeCheckpoint> states) {
    for (ActiveMercenary active : manager().activeRecords().values()) if (active.entityId >= 0) {
      UnitStateRuntimeCheckpoint state = states == null ? null : states.get(active.entityId);
      if (state == null || state.attributesType() != com.riiablo.attributes.Attributes.Type.LARGE) {
        throw new IllegalArgumentException("hireling shared attributes require unit-state coverage");
      }
    }
  }
  public void restore(World world) {
    validateRestore(world); Decoded d = decode(); NativeMercenaryRewardSystem service = service(world);
    d.manager.restore(service.checkpointManager()); service.restoreCheckpointDifficulty(d.difficulty);
  }

  /** Complete the real service's post-monster-factory host shape, without hiring or payment. */
  public void prepareEntityHosts(World world) {
    validateRestore(world);
    // Companion decoding has already checked the incoming component graph. Network deserialization
    // deliberately does not install Mercenary controller images; TARGETS does that after this stage.
    for (ActiveMercenary active : manager().activeRecords().values()) if (active.entityId >= 0) {
      // NativeMercenaryRewardSystem.createMercenaryEntity removes these hostile-monster hosts.
      world.getMapper(AIWrapper.class).remove(active.entityId);
      world.getMapper(Interactable.class).remove(active.entityId);
      world.getMapper(Selectable.class).remove(active.entityId);
      world.getMapper(AttributesWrapper.class).get(active.entityId).attrs =
          world.getMapper(Player.class).get(active.ownerId).data.getMerc().getStats();
    }
  }

  /** Call only after D2S publication, before unit-state attributes are reapplied. */
  public void rebindAfterD2s(World world) {
    validateBindings(world, false);
    Decoded d = decode();
    for (java.util.Map.Entry<Integer, byte[]> e : d.attributes.entrySet()) {
      UnitStateRuntimeCheckpoint.validateAttributesImage(world.getMapper(Player.class).get(e.getKey()).data.getMerc().getStats(), e.getValue());
    }
    for (ActiveMercenary active : d.manager.activeRecords().values()) {
      com.riiablo.save.CharData.MercData saved = world.getMapper(Player.class).get(active.ownerId).data.getMerc();
      saved.getItems().restoreEquipmentRevision(d.equipmentRevisions.get(active.ownerId));
      if (active.entityId >= 0) world.getMapper(AttributesWrapper.class).get(active.entityId).attrs = saved.getStats();
      else UnitStateRuntimeCheckpoint.restoreAttributesImage(saved.getStats(), d.attributes.get(active.ownerId));
    }
  }
  private void validateBindings(World world, boolean capture) {
    for (ActiveMercenary active : manager().activeRecords().values()) {
      Player owner = world.getMapper(Player.class).get(active.ownerId);
      if (owner == null || owner.data == null) throw new IllegalStateException("hireling D2S owner missing");
      com.riiablo.save.CharData.MercData saved = owner.data.getMerc();
      if (saved.getItems().getItems().size != 0) throw new IllegalStateException("hireling Item graph uncovered");
      if (!owner.data.hasMerc() || saved.seed != active.seed || (saved.type & 0xffff) != active.definition.hirelingId()
          || (saved.name & 0xffff) != active.nameId || saved.xp != active.experience
          || ((saved.flags & MercenaryManager.FLAG_DEAD) != 0) != (active.state == MercenaryManager.STATE_DEAD)) {
        throw new IllegalStateException("hireling manager/D2S identity mismatch");
      }
      if (active.entityId >= 0) {
        if (world.getMapper(AIWrapper.class).has(active.entityId)
            || world.getMapper(Interactable.class).has(active.entityId)
            || world.getMapper(Selectable.class).has(active.entityId)) {
          throw new IllegalStateException("hireling service entity host shape mismatch");
        }
        AttributesWrapper wrapper = world.getMapper(AttributesWrapper.class).get(active.entityId);
        if (wrapper == null || wrapper.attrs == null || wrapper.attrs.type() != saved.getStats().type()
            || capture && wrapper.attrs != saved.getStats()) throw new IllegalStateException("hireling shared attributes binding mismatch");
      }
    }
  }
  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != 1) throw new IllegalArgumentException("hireling service version");
      Decoded d = new Decoded(); d.difficulty = in.readInt();
      if (d.difficulty < -1 || d.difficulty > 2) throw new IllegalArgumentException("hireling service difficulty");
      int length = CheckpointIO.count(in, MercenaryManagerCheckpoint.MAX_BYTES);
      if (length == 0 || length > in.available()) throw new IllegalArgumentException("hireling manager length");
      byte[] manager = new byte[length]; in.readFully(manager); d.manager = MercenaryManagerCheckpoint.fromBytes(manager);
      java.util.Map<Integer, ActiveMercenary> active = d.manager.activeRecords();
      int count = CheckpointIO.count(in, 100000), previous = -1;
      if (count != active.size()) throw new IllegalArgumentException("hireling saved owner count");
      for (int i = 0; i < count; i++) {
        int owner = in.readInt(); long revision = in.readLong();
        if (owner <= previous || !active.containsKey(owner) || revision < 0) throw new IllegalArgumentException("hireling saved owner/revision");
        previous = owner; d.equipmentRevisions.put(owner, revision);
        if (active.get(owner).entityId < 0) {
          int size = CheckpointIO.count(in, UnitStateRuntimeCheckpoint.MAX_ATTRIBUTES_BYTES);
          if (size == 0 || size > in.available()) throw new IllegalArgumentException("saved hireling attributes length");
          byte[] image = new byte[size]; in.readFully(image); UnitStateRuntimeCheckpoint.validateAttributesImage(image);
          d.attributes.put(owner, image);
        }
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing hireling service"); return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated hireling service", e); }
  }
  private static final class Decoded {
    int difficulty; MercenaryManagerCheckpoint manager;
    final java.util.Map<Integer, Long> equipmentRevisions = new TreeMap<>();
    final java.util.Map<Integer, byte[]> attributes = new TreeMap<>();
  }
}
