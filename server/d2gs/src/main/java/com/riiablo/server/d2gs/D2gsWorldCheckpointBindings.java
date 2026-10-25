package com.riiablo.server.d2gs;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.google.flatbuffers.ByteBufferUtil;
import com.riiablo.engine.replay.NativeUnitRandomRegistry;
import com.riiablo.engine.replay.CheckpointRngRegistry;
import com.riiablo.engine.replay.SimulationCheckpoint;
import com.riiablo.engine.server.NativeUnitRandom;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.MissileCollisionSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.UnitRandomSeed;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.save.CharData;
import com.riiablo.save.D2SCheckpointRegistry;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owner-thread bindings to live authoritative players and D2Common unit seeds. */
final class D2gsWorldCheckpointBindings {
  static final String UNIT_STREAM = "unit";
  private final World world;
  private final int gameSeed;
  private final D2SCheckpointRegistry d2s;
  private final Set<Integer> players = new HashSet<>();
  private NativeUnitRandomRegistry nativeRng;
  private CheckpointRngRegistry compatibilityRng = new CheckpointRngRegistry();

  D2gsWorldCheckpointBindings(World world, int gameSeed) {
    this.world = world;
    this.gameSeed = gameSeed;
    d2s = new D2SCheckpointRegistry((entityId, entry, decoded) -> binding(entityId));
    nativeRng = newNativeRegistry();
    refreshCompatibilityRng();
  }

  D2SCheckpointRegistry d2sRegistry() { return d2s; }
  NativeUnitRandomRegistry nativeRegistry() { return nativeRng; }
  CheckpointRngRegistry compatibilityRegistry() { return compatibilityRng; }

  CheckpointRngRegistry prepareCompatibilityRestore() {
    // A legacy archive may remove this stream from the registry. Rebind the
    // actual system-owned object before restoring/rolling back, never a copy
    // reconstructed by the registry and detached from combat consumers.
    compatibilityRng = new CheckpointRngRegistry();
    refreshCompatibilityRng();
    return compatibilityRng;
  }

  void refreshCompatibilityRng() {
    MissileCollisionSystem missiles = world.getSystem(MissileCollisionSystem.class);
    NativeRng rng = missiles == null ? null : missiles.checkpointCombatRng();
    if (rng != null && compatibilityRng.get(rng.streamName(), rng.contextId()) == null) {
      compatibilityRng.register(rng);
    }
  }

  void refreshPlayers() {
    Set<Integer> live = new HashSet<>();
    for (int id : networkedIds()) {
      Player player = world.getMapper(Player.class).get(id);
      if (player == null || player.data == null) continue;
      live.add(id);
      if (d2s.get(id) == null) d2s.register(id, binding(id));
    }
    for (int id : players) if (!live.contains(id)) d2s.unregister(id);
    players.clear();
    players.addAll(live);
  }

  NativeUnitRandomRegistry captureNativeRegistry() {
    // Rebind wrappers every time: Artemis can recycle a component/id, and
    // retaining an old NativeUnitRandom would capture a detached seed object.
    nativeRng = newNativeRegistry();
    for (int id : networkedIds()) {
      Monster monster = world.getMapper(Monster.class).get(id);
      Missile missile = world.getMapper(Missile.class).get(id);
      UnitRandomSeed unit = world.getMapper(UnitRandomSeed.class).get(id);
      if (monster != null || missile != null
          || (monster == null && missile == null && unit != null && unit.seed != null)) {
        nativeRng.register(UNIT_STREAM, id, NativeUnitRandom.unit(world, gameSeed, id));
      }
    }
    return nativeRng;
  }

  void validateNativeStates(D2gsCheckpointSnapshot checkpoint) {
    Set<Integer> entities = checkpoint.payloads().keySet();
    for (SimulationCheckpoint.NativeUnitRngState state : checkpoint.summary().nativeUnitRngStates()) {
      if (!UNIT_STREAM.equals(state.stream) || state.contextId < 0
          || state.contextId > Integer.MAX_VALUE
          || !entities.contains((int) state.contextId)) {
        throw new IllegalArgumentException("native RNG is not bound to a checkpoint unit");
      }
    }
  }

  NativeUnitRandomRegistry prepareNativeRestore(List<SimulationCheckpoint.NativeUnitRngState> states) {
    Set<Integer> restored = new HashSet<>();
    for (SimulationCheckpoint.NativeUnitRngState state : states) restored.add((int) state.contextId);
    for (int id : networkedIds()) {
      if (restored.contains(id)) continue;
      Monster monster = world.getMapper(Monster.class).get(id);
      Missile missile = world.getMapper(Missile.class).get(id);
      UnitRandomSeed unit = world.getMapper(UnitRandomSeed.class).get(id);
      if (monster != null) monster.nativeSeed = null;
      if (missile != null) missile.nativeSeed = null;
      if (unit != null) { unit.seed = null; unit.low = 0; }
    }
    nativeRng = newNativeRegistry();
    return nativeRng;
  }

  /** D2S does not carry runtime revisions; the ECS envelope remains authoritative for them. */
  void restorePlayerMetadata(D2gsCheckpointSnapshot checkpoint) {
    if (checkpoint.d2sCheckpoint() == null) return;
    Map<Integer, byte[]> payloads = checkpoint.payloads();
    for (int id : checkpoint.d2sCheckpoint().entries().keySet()) {
      byte[] bytes = payloads.get(id);
      if (bytes == null) throw new IllegalArgumentException("D2S player missing from ECS checkpoint");
      com.riiablo.net.packet.d2gs.D2GS packet = com.riiablo.net.packet.d2gs.D2GS.getRootAsD2GS(
          ByteBufferUtil.removeSizePrefix(ByteBuffer.wrap(bytes)));
      EntitySync sync = (EntitySync) packet.data(new EntitySync());
      Player component = requirePlayer(id);
      boolean playerMetadata = false;
      for (int i = 0; i < sync.componentTypeLength(); i++) {
        if (sync.componentType(i) == ComponentP.PlayerP) {
          PlayerP player = (PlayerP) sync.component(new PlayerP(), i);
          if (!component.data.name.equals(player.charName())
              || component.data.charClass != player.charClass()) {
            throw new IllegalArgumentException("D2S/ECS player identity mismatch");
          }
          component.data.restoreProgressionRevision(player.progressionRevision());
          component.data.getItems().restoreEquipmentRevision(player.equipmentRevision());
          playerMetadata = true;
        } else if (sync.componentType(i) == ComponentP.VitalsP) {
          VitalsSerializer.apply(world.getMapper(AttributesWrapper.class).get(id),
              (VitalsP) sync.component(new VitalsP(), i));
        }
      }
      if (!playerMetadata) throw new IllegalArgumentException("D2S player has no ECS metadata");
    }
    refreshPlayers();
  }

  private NativeUnitRandomRegistry newNativeRegistry() {
    return new NativeUnitRandomRegistry(state -> {
      if (!UNIT_STREAM.equals(state.stream) || state.contextId < 0
          || state.contextId > Integer.MAX_VALUE) throw new IllegalArgumentException("native unit stream");
      int id = (int) state.contextId;
      if (!D2gsCheckpointSnapshot.isActive(world, id) || !world.getMapper(Networked.class).has(id)) {
        throw new IllegalStateException("native RNG entity is not active: " + id);
      }
      return NativeUnitRandom.unit(world, gameSeed, id);
    });
  }

  private D2SCheckpointRegistry.Binding binding(final int entityId) {
    return new D2SCheckpointRegistry.Binding() {
      @Override public CharData get() { return requirePlayer(entityId).data; }
      @Override public int difficulty() { return get().getDifficulty(); }
      @Override public void set(CharData data) {
        Player player = requirePlayer(entityId);
        if (data == null || !player.data.name.equals(data.name)
            || player.data.charClass != data.charClass) {
          throw new IllegalArgumentException("D2S player identity mismatch");
        }
        data.update(); // Complete all potentially failing work before publishing either pointer.
        AttributesWrapper attributes = world.getMapper(AttributesWrapper.class).get(entityId);
        if (attributes == null) throw new IllegalStateException("player attributes missing");
        player.data = data;
        attributes.attrs = data.getStats();
      }
    };
  }

  private Player requirePlayer(int id) {
    Player player = D2gsCheckpointSnapshot.isActive(world, id) ? world.getMapper(Player.class).get(id) : null;
    if (player == null || player.data == null) throw new IllegalStateException("checkpoint player missing: " + id);
    return player;
  }

  private int[] networkedIds() {
    IntBag ids = world.getAspectSubscriptionManager().get(Aspect.all(Networked.class)).getEntities();
    return java.util.Arrays.copyOf(ids.getData(), ids.size());
  }
}
