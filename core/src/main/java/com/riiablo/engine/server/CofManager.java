package com.riiablo.engine.server;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.riiablo.engine.Dirty;
import com.riiablo.engine.shared.component.CofDirtyComponents;
import com.riiablo.engine.server.component.CofAlphas;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.CofTransforms;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.event.AlphaChangeEvent;
import com.riiablo.engine.server.event.CofChangeEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.event.TransformChangeEvent;
import com.riiablo.engine.server.event.WClassChangeEvent;

import net.mostlyoriginal.api.event.common.EventSystem;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All(CofReference.class)
public class CofManager extends BaseEntitySystem {
  private static final Logger log = LogManager.getLogger(CofManager.class);
  /** Entity ids created from an authoritative checkpoint before insertion is processed. */
  private final java.util.Set<Integer> checkpointInsertions = new java.util.HashSet<>();
  @com.artemis.annotations.Wire(name = "serverContext", failOnNull = false)
  protected ServerGameplayContext serverContext;
  @com.artemis.annotations.Wire(name = "simulationSession", failOnNull = false)
  protected com.riiablo.engine.SimulationSession simulationSession;
  @com.artemis.annotations.Wire(name = "map", failOnNull = false)
  protected com.riiablo.map.Map map;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<NativeObjectState> mNativeObjectState;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<CofDirtyComponents> mCofDirtyComponents;
  protected ComponentMapper<CofAlphas> mCofAlphas;
  protected ComponentMapper<CofTransforms> mCofTransforms;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;

  protected EventSystem event;

  @Override
  protected void inserted(int entityId) {
    CofReference reference = mCofReference.get(entityId);
    // A checkpoint lifecycle creates the native component graph first and the
    // serializer applies the authoritative CofReference before Artemis flushes
    // insertions.  Do not count that insertion as a new animation restart;
    // the serialized restart id must survive the round-trip verbatim.
    boolean checkpointRestore = checkpointInsertions.remove(entityId);
    setMode(entityId, reference.mode, !checkpointRestore);
    setWClass(entityId, reference.wclass, true);
  }

  /** Marks a just-created entity so insertion processing preserves its snapshot restart id. */
  public void markCheckpointInsertion(int entityId) {
    checkpointInsertions.add(entityId);
  }

  /** Cancels a pending checkpoint insertion when creation is rolled back before flush. */
  public void cancelCheckpointInsertion(int entityId) {
    checkpointInsertions.remove(entityId);
  }

  @Override
  protected void removed(int entityId) {
    checkpointInsertions.remove(entityId);
  }

  @Override
  protected void processSystem() {}

  public void setMode(int id, byte mode) {
    setMode(id, mode, false);
  }

  public void setMode(int id, byte mode, boolean force) {
    setMode(id, mode, force, true);
  }

  /** A sequence point changes its displayed COF, not the logical SQ action. */
  public void setSequenceMode(int id, byte mode, boolean force) {
    setMode(id, mode, force, false);
  }

  private void setMode(int id, byte mode, boolean force, boolean refreshDamage) {
    CofReference reference = mCofReference.get(id);
    boolean restart = reference.mode == mode;
    if (restart && !force) return;
    if (force) reference.animationRestartId++;
    Monster monster = mMonster.get(id);
    if (monster != null && monster.monstats != null
        && "catapult1".equals(monster.monstats.BaseId)
        && (mode == com.riiablo.engine.Engine.Monster.MODE_DT
            || mode == com.riiablo.engine.Engine.Monster.MODE_DD)) {
      com.riiablo.engine.server.component.MapWrapper wrapper = world.getMapper(
          com.riiablo.engine.server.component.MapWrapper.class).get(id);
      com.riiablo.engine.server.component.Position position = world.getMapper(
          com.riiablo.engine.server.component.Position.class).get(id);
      if (wrapper != null && wrapper.zone != null && position != null) {
        com.riiablo.map.Map.RoomEx room = wrapper.zone.findRoomEx(position.position.x, position.position.y);
        if (room != null) room.recordDeadCatapult(monster.monstats.hcIdx);
      }
    }
    if (monster != null) world.getMapper(
        com.riiablo.engine.server.component.NativeAiState.class).create(id)
        .onModeChange(reference.mode);
    if (monster != null && (Monster.isMeleeMode(reference.mode) || Monster.isMeleeMode(mode)
        || (mSequence.has(id) && Monster.isMeleeMode(mSequence.get(id).mode1)))) {
      AnimData anim = mAnimData.get(id);
      Casting casting = mCasting.get(id);
      Sequence sequence = mSequence.get(id);
      log.info("[MONSTER_MELEE] phase=mode_change entity={} monster={} previousMode={} "
              + "requestedMode={} force={} restart={} sequence={} sequenceStarted={} "
              + "skill={} target={} animFrame={} animFrames={} keyframes={}",
          id, monster.monstats != null ? monster.monstats.Id : "unknown",
          Monster.modeName(reference.mode), Monster.modeName(mode), force, restart,
          sequence != null ? Monster.modeName(sequence.mode1) : "none",
          sequence != null && sequence.started,
          casting != null ? casting.skillId : -1,
          casting != null ? casting.targetId : -1,
          anim != null ? anim.frame : -1,
          anim != null ? anim.numFrames : -1,
          anim != null && anim.keyframes != null ? anim.keyframes.length : 0);
    }
    NativeObjectState state = mNativeObjectState.get(id);
    if (state != null && state.kind == com.riiablo.map.NativePresetObjectResolver.Kind.SHRINE) {
      log.info("[SHRINE_ANIM] phase=set_mode entity={} requested={} previous={} force={} "
              + "restart={} activated={} persistentMode={} sequence={}",
          id, mode, reference.mode, force, restart, state.activated,
          state.currentMode,
          world.getMapper(com.riiablo.engine.server.component.Sequence.class).has(id));
    }
    reference.mode = mode;
    if (refreshDamage) {
      Sequence sequence = mSequence.get(id);
      refreshMonsterElements(id, sequence != null && sequence.nativeMonster != null
          && mode == sequence.mode1 ? com.riiablo.engine.Engine.Monster.MODE_XX : mode);
    }
    // Publish the live mode before COF listeners resolve Object.mode. The
    // map-owned NativeObjectState separately retains the restorable end mode.
    com.riiablo.engine.server.component.Object object = mObject.get(id);
    if (object != null) object.mode = mode;
    // A forced same-mode update is used by repeated attacks to restart the
    // animation at frame zero.  Mark it explicitly so the client can reset
    // the existing Animation without unloading/reloading every DCC layer.
    event.dispatch(ModeChangeEvent.obtain(id, mode, restart));
  }

  /** MonsterMode::sub_6FC627B0 runs before the mode callback (and point zero).
   * Replicas must never consume the authoritative monster seed. */
  private void refreshMonsterElements(int id, int mode) {
    if (mode == com.riiablo.engine.Engine.Monster.MODE_GH
        || (simulationSession != null && !simulationSession.mode().isAuthoritative())) return;
    Monster monster = mMonster.get(id);
    if (monster == null || monster.monstats == null) return;
    com.riiablo.Files files = ServerGameplayContext.files(serverContext);
    if (files == null) return;
    com.riiablo.engine.server.component.AttributesWrapper wrapper = world.getMapper(
        com.riiablo.engine.server.component.AttributesWrapper.class).get(id);
    // Some construction paths publish COF before Attributes. Do not consume a
    // level-one selection that will then be reused by the first real action.
    if (wrapper == null || wrapper.attrs == null) { monster.modeElements = null; return; }
    int level = wrapper.attrs.getValue(com.riiablo.attributes.Stat.level, 1);
    int difficulty = map == null ? 0 : map.getDifficulty();
    int players = MonsterStatsCalculator.nativePlayerCount(monster.monstats,
        world.getAspectSubscriptionManager().get(com.artemis.Aspect.all(
            com.riiablo.engine.server.component.Player.class)).getEntities().size());
    NativeUnitRandom rng = NativeUnitRandom.monster(monster,
        serverContext == null ? 0 : serverContext.gameSeed(), id);
    boolean expansion = serverContext != null
        ? serverContext.combatContext(difficulty).expansion()
        : ServerGameplayContext.legacyCombatContext(difficulty).expansion();
    monster.modeElements = com.riiablo.engine.server.combat.MonsterModeDamageResolver.resolveElements(
        files, rng::nextInt, monster.monstats, level, difficulty, mode, players, expansion);
    monster.rngState = rng.state();
  }

  /**
   * Applies an authoritative mode snapshot received from the server.  A
   * network restart id must be copied verbatim; calling {@link #setMode(int,
   * byte, boolean)} here would increment the local counter and make the next
   * snapshot look stale.  A changed id still dispatches the same forced
   * restart event used by local sequence code.
   */
  public void applyNetworkMode(int id, byte mode, int animationRestartId) {
    CofReference reference = mCofReference.get(id);
    boolean modeChanged = reference.mode != mode;
    boolean restartChanged = reference.animationRestartId != animationRestartId;
    reference.animationRestartId = animationRestartId;
    if (modeChanged) {
      setMode(id, mode, false, false);
    } else if (restartChanged) {
      event.dispatch(ModeChangeEvent.obtain(id, mode, true));
    }
  }

  public void setWClass(int id, byte wclass) {
    setWClass(id, wclass, false);
  }

  public void setWClass(int id, byte wclass, boolean force) {
    CofReference reference = mCofReference.get(id);
    if (reference.wclass == wclass && !force) return;
    reference.wclass = wclass;
    event.dispatch(WClassChangeEvent.obtain(id, wclass));
  }

  public void setVisualOverride(
      int id, com.riiablo.engine.server.component.Class.Type type,
      String token, byte wclass) {
    setVisualOverride(id, type, token, wclass, null);
  }

  public void setVisualOverride(
      int id, com.riiablo.engine.server.component.Class.Type type,
      String token, byte wclass, boolean[] supportedModes) {
    CofReference reference = mCofReference.get(id);
    if (reference.visualType == type
        && java.util.Objects.equals(reference.visualToken, token)
        && reference.visualWClass == wclass
        && reference.visualModes == supportedModes) return;
    reference.setVisualOverride(type, token, wclass, supportedModes);
    CofChangeEvent changed = new CofChangeEvent();
    changed.entityId = id;
    event.dispatch(changed);
  }

  public void clearVisualOverride(int id) {
    CofReference reference = mCofReference.get(id);
    if (reference.visualType == null && reference.visualToken == null
        && reference.visualWClass < 0) return;
    reference.clearVisualOverride();
    CofChangeEvent changed = new CofChangeEvent();
    changed.entityId = id;
    event.dispatch(changed);
  }

  public int setComponent(int id, int c, int code) {
    int[] component = mCofComponents.get(id).component;
    if (component[c] == code) return Dirty.NONE;
    if (code == CofComponents.COMPONENT_NULL && component[c] == CofComponents.COMPONENT_LIT) return Dirty.NONE;
    component[c] = code;
    return mCofDirtyComponents.create(id).flags |= (1 << c);
  }

  public int setAlpha(int id, int c, float a) {
    float[] alpha = mCofAlphas.get(id).alpha;
    if (alpha[c] == a) return Dirty.NONE;
    alpha[c] = a;
    return 1 << c;
  }

  public void updateAlpha(int id, int flags) {
    if (flags == Dirty.NONE) return;
    event.dispatch(AlphaChangeEvent.obtain(id, flags));
  }

  public int setTransform(int id, int c, byte packedTransform) {
    byte[] transform = mCofTransforms.get(id).transform;
    if (transform[c] == packedTransform) return Dirty.NONE;
    transform[c] = packedTransform;
    return 1 << c;
  }

  public void updateTransform(int id, int flags) {
    if (flags == Dirty.NONE) return;
    event.dispatch(TransformChangeEvent.obtain(id, flags));
  }
}
