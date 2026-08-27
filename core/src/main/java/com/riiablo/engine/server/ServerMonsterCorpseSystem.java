package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/**
 * Authoritative monster death/corpse lifecycle shared by local and dedicated
 * server worlds.
 *
 * <p>The client {@code DeathHandler} also owns rendering and Box2D teardown,
 * so it cannot be registered in D2GS.  This system contains only the ECS
 * portion needed by native monster AIs: dispatching {@link AIWrapper#ai}'s
 * death transition and marking the dead monster as a usable {@link Corpse}.
 */
public class ServerMonsterCorpseSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(ServerMonsterCorpseSystem.class);

  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<MovementModes> mMovementModes;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Interactable> mInteractable;

  @Subscribe
  public void onDeath(DeathEvent event) {
    if (event == null || event.victim < 0 || !mMonster.has(event.victim)) return;

    // Local GameScreen still has DeathHandler, so this call is intentionally
    // idempotent: every native AI guards its DEAD state in kill().  D2GS uses
    // this system as the authoritative replacement for DeathHandler's monster
    // branch.
    if (mAIWrapper.has(event.victim) && mAIWrapper.get(event.victim).ai != null) {
      log.debug("[MONSTER_CORPSE] phase=death_event entity={} killer={} ai={}",
          event.victim, event.killer,
          mAIWrapper.get(event.victim).ai.getClass().getSimpleName());
      mAIWrapper.get(event.victim).ai.kill();
    } else {
      log.debug("[MONSTER_CORPSE] phase=death_event entity={} killer={} ai=missing",
          event.victim, event.killer);
    }
  }

  @Subscribe
  public void onModeChanged(ModeChangeEvent event) {
    if (event == null || event.mode != Engine.Monster.MODE_DD
        || !mMonster.has(event.entityId)) return;

    final int entityId = event.entityId;
    if (!mCorpse.has(entityId)) {
      mCorpse.create(entityId);
      Monster monster = mMonster.get(entityId);
      log.info("[MONSTER_CORPSE] phase=created entity={} monster={} usable={} duration={}",
          entityId,
          monster != null && monster.monstats != null ? monster.monstats.Id : "unknown",
          true, Corpse.DEFAULT_DURATION);
    } else {
      // A repeated MODE_DD event must not extend or reset a corpse that a
      // shaman is already using.
      log.debug("[MONSTER_CORPSE] phase=duplicate_mode entity={} corpseExisting=true", entityId);
    }

    // Dead monsters must not keep participating in movement, targeting, or a
    // stale skill cast while they remain as selectable resurrection targets.
    if (mVelocity.has(entityId)) mVelocity.remove(entityId);
    if (mMovementModes.has(entityId)) mMovementModes.remove(entityId);
    if (mPathfind.has(entityId)) mPathfind.remove(entityId);
    if (mCasting.has(entityId)) mCasting.remove(entityId);
    if (mRunning.has(entityId)) mRunning.remove(entityId);
    if (mTarget.has(entityId)) mTarget.remove(entityId);
    if (mInteractable.has(entityId)) mInteractable.remove(entityId);
  }
}
