package com.riiablo.engine.client;

import com.badlogic.gdx.utils.IntMap;
import com.google.flatbuffers.Table;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Class;
import com.riiablo.net.packet.d2gs.CofReferenceP;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.StateP;
import com.riiablo.net.packet.d2gs.VitalsP;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server lifecycle values, isolated from buffers, ECS replicas and presentation. */
public final class AuthoritativeUnitLifecycleState {
  public enum Phase { UNKNOWN, ALIVE, DEATH, CORPSE }
  private final IntMap<Snapshot> snapshots = new IntMap<>();
  private final IntMap<AuthoritativeSnapshotTimeline> timelines = new IntMap<>();

  public Snapshot get(int entityId) { return snapshots.get(entityId); }

  // Only the receiver commits. Omitted fields survive partial updates; an
  // explicitly empty StateP clears the state list.
  boolean commit(int entityId, EntitySync data) {
    AuthoritativeSnapshotTimeline timeline = timelines.get(entityId);
    if (timeline == null) {
      timeline = new AuthoritativeSnapshotTimeline();
      timelines.put(entityId, timeline);
    }
    if (!timeline.accept(data.tick(), data.serverTimeMillis())) return false;
    snapshots.put(entityId, new Snapshot(data, snapshots.get(entityId)));
    return true;
  }

  void remove(int entityId) { snapshots.remove(entityId); timelines.remove(entityId); }
  void clear() { snapshots.clear(); timelines.clear(); }

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
    public final long tick, serverTimeMillis;
    public final Class.Type type;
    public final boolean hasMode, hasStates;
    public final int mode;
    public final Vitals vitals;
    public final List<State> states;
    public final Phase phase;

    private Snapshot(EntitySync data, Snapshot previous) {
      tick = data.tick();
      serverTimeMillis = data.serverTimeMillis();
      type = Class.Type.valueOf(data.type());
      CofReferenceP cof = find(data, ComponentP.CofReferenceP, new CofReferenceP());
      StateP state = find(data, ComponentP.StateP, new StateP());
      VitalsP resources = find(data, ComponentP.VitalsP, new VitalsP());
      hasMode = cof != null || previous != null && previous.hasMode;
      mode = cof != null ? cof.mode() : previous == null ? -1 : previous.mode;
      hasStates = state != null || previous != null && previous.hasStates;
      if (state != null) {
        List<State> copy = new ArrayList<>(state.stateIdLength());
        for (int i = 0; i < state.stateIdLength(); i++) copy.add(new State(state, i));
        states = Collections.unmodifiableList(copy);
      } else states = previous == null ? Collections.emptyList() : previous.states;
      vitals = resources != null ? new Vitals(resources) : previous == null ? null : previous.vitals;
      int deathMode = type == Class.Type.PLR ? Engine.Player.MODE_DT : Engine.Monster.MODE_DT;
      int corpseMode = type == Class.Type.PLR ? Engine.Player.MODE_DD : Engine.Monster.MODE_DD;
      boolean unit = type == Class.Type.PLR || type == Class.Type.MON;
      // The independent wire fields can arrive in either order. Fresh vitals
      // decide death first; a later mode-only packet refines DT versus DD.
      if (unit && resources != null) {
        if (!resources.dead()) phase = Phase.ALIVE;
        else phase = hasMode && mode == corpseMode ? Phase.CORPSE : Phase.DEATH;
      } else if (unit && cof != null) {
        phase = mode == corpseMode ? Phase.CORPSE : mode == deathMode ? Phase.DEATH : Phase.ALIVE;
      } else if (previous != null) phase = previous.phase;
      else phase = Phase.UNKNOWN;
    }

    public boolean dead() { return phase == Phase.DEATH || phase == Phase.CORPSE; }
  }

  /** Resolved server values; no local maximum/resource calculation. */
  public static final class Vitals {
    public final float hitpoints, maxHitpoints, mana, maxMana, stamina, maxStamina;
    public final boolean dead;
    private Vitals(VitalsP data) {
      maxHitpoints = sanitize(data.maxHitpoints());
      hitpoints = Math.min(sanitize(data.hitpoints()), maxHitpoints);
      maxMana = sanitize(data.maxMana());
      mana = Math.min(sanitize(data.mana()), maxMana);
      maxStamina = sanitize(data.maxStamina());
      stamina = Math.min(sanitize(data.stamina()), maxStamina);
      dead = data.dead();
    }
    private static float sanitize(float value) {
      return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }
  }

  /** Detached StateP entry, including all periodic/presentation metadata. */
  public static final class State {
    public final int id, duration, level, velocityModifier, runtimeValue;
    public final int animationRateModifier, skillModifier, maxLifeModifier;
    public final int maxManaModifier, maxStaminaModifier, sourceEntityId, skillId;
    public final int periodicDelayFrames, periodicCountdownFrames;
    private State(StateP data, int i) {
      id = data.stateId(i);
      duration = i < data.durationLength() ? data.duration(i) : 0;
      level = i < data.levelLength() ? data.level(i) : 1;
      velocityModifier = i < data.velocityModifierLength() ? data.velocityModifier(i) : 0;
      runtimeValue = i < data.runtimeValueLength() ? data.runtimeValue(i) : 0;
      animationRateModifier = i < data.animationRateModifierLength() ? data.animationRateModifier(i) : 0;
      skillModifier = i < data.skillModifierLength() ? data.skillModifier(i) : 0;
      maxLifeModifier = i < data.maxLifeModifierLength() ? data.maxLifeModifier(i) : 0;
      maxManaModifier = i < data.maxManaModifierLength() ? data.maxManaModifier(i) : 0;
      maxStaminaModifier = i < data.maxStaminaModifierLength() ? data.maxStaminaModifier(i) : 0;
      sourceEntityId = i < data.sourceEntityIdLength() ? data.sourceEntityId(i) : -1;
      skillId = i < data.skillIdLength() ? data.skillId(i) : -1;
      periodicDelayFrames = i < data.periodicDelayFramesLength() ? data.periodicDelayFrames(i) : 0;
      periodicCountdownFrames = i < data.periodicCountdownFramesLength() ? data.periodicCountdownFrames(i) : -1;
    }
  }
}
