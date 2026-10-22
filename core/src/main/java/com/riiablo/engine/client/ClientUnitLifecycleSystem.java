package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;

/** Lifecycle intent admission and replica projection; never settles server rules. */
@Wire(failOnNull = false)
public class ClientUnitLifecycleSystem extends BaseSystem {
  public interface LocalAuthority {
    boolean isPlayerDead(int player);
    boolean canRespawnPlayer(int player);
    void respawnPlayerAtTown(int player);
  }

  public interface Presentation {
    void applyLifecycle(int entityId, AuthoritativeUnitLifecycleState.Snapshot snapshot,
        boolean wasDead);
  }

  protected ClientNetworkReceiver receiver;
  protected ClientNetworkSynchronizer network;
  protected ComponentMapper<UnitStates> mStates;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Running> mRunning;
  private final LocalAuthority authority;
  private final Presentation presentation;
  private int requestedPlayer = Engine.INVALID_ENTITY;

  public ClientUnitLifecycleSystem(LocalAuthority authority, Presentation presentation) {
    this.authority = authority;
    this.presentation = presentation;
  }

  public boolean isPlayerDead(int player) {
    if (player < 0) return false;
    if (receiver != null) {
      AuthoritativeUnitLifecycleState.Snapshot snapshot = receiver.authoritativeLifecycle(player);
      return snapshot != null && snapshot.dead();
    }
    return authority != null && authority.isPlayerDead(player);
  }

  public boolean canRespawnPlayer(int player) {
    if (!isPlayerDead(player)) return false;
    if (receiver == null) return authority != null && authority.canRespawnPlayer(player);
    AuthoritativeUnitLifecycleState.Snapshot snapshot = receiver.authoritativeLifecycle(player);
    return snapshot.hasMode && snapshot.mode == Engine.Player.MODE_DD;
  }

  /** Host/UI captures only intent. Validation and execution happen on the next tick. */
  public void requestRespawn(int player) { requestedPlayer = player; }

  @Override protected void processSystem() {
    int player = requestedPlayer;
    requestedPlayer = Engine.INVALID_ENTITY;
    if (!canRespawnPlayer(player)) return;
    if (receiver != null) {
      if (network != null) network.requestPlayerRespawn();
    } else if (authority != null) authority.respawnPlayerAtTown(player);
  }

  /** Called after all components in one accepted packet have been projected. */
  void applySnapshot(int entityId, AuthoritativeUnitLifecycleState.Snapshot snapshot) {
    if (snapshot.type != Class.Type.PLR && snapshot.type != Class.Type.MON) return;
    boolean wasDead = mPlayerCorpse.has(entityId);
    UnitStates states = mStates.create(entityId);
    if (states.stateList == null) states.init(entityId);
    states.snapshotOnly = true;
    if (snapshot.dead()) {
      mPathfind.remove(entityId);
      mCasting.remove(entityId);
      mSequence.remove(entityId);
      mTarget.remove(entityId);
      mRunning.remove(entityId);
      mVelocity.remove(entityId);
      mMovementModes.remove(entityId);
      if (snapshot.type == Class.Type.PLR && !wasDead) {
        PlayerCorpse marker = mPlayerCorpse.create(entityId);
        marker.playerId = entityId;
        if (mPosition.has(entityId)) marker.deathLocation.set(mPosition.get(entityId).position);
      }
    } else if (snapshot.phase == AuthoritativeUnitLifecycleState.Phase.ALIVE) {
      if (snapshot.type == Class.Type.PLR) {
        mPlayerCorpse.remove(entityId);
        mMovementModes.create(entityId).set(
            Engine.Player.MODE_TN, Engine.Player.MODE_TW, Engine.Player.MODE_RN);
      }
      // Vitals/mode snapshots own respawn. No HP/MP/position is computed here.
    }
    if (presentation != null) presentation.applyLifecycle(entityId, snapshot, wasDead);
  }
}
