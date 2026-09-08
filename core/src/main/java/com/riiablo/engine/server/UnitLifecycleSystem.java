package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.BaseSystem;
import com.artemis.BaseEntitySystem;
import com.artemis.annotations.All;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitLifecycle;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import net.mostlyoriginal.api.event.common.Subscribe;

/**
 * Centralizes the non-presentation part of the native unit death boundary.
 * Dedicated corpse/reward systems still own their domain-specific work; this
 * system only makes the boundary idempotent and removes references that would
 * otherwise keep missiles, summons, targets or source-owned states alive.
 */
@All(UnitLifecycle.class)
public class UnitLifecycleSystem extends BaseEntitySystem {
  private static final Logger log = LogManager.getLogger(UnitLifecycleSystem.class);

  protected ComponentMapper<UnitLifecycle> mLifecycle;
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AIWrapper> mAIWrapper;

  private EntitySubscription missiles;
  private EntitySubscription pets;
  private EntitySubscription targets;
  private EntitySubscription unitStates;
  private long deathSequence;

  @Override
  protected void initialize() {
    missiles = world.getAspectSubscriptionManager().get(Aspect.all(Missile.class));
    pets = world.getAspectSubscriptionManager().get(Aspect.all(SummonedPet.class));
    targets = world.getAspectSubscriptionManager().get(Aspect.all(Target.class));
    unitStates = world.getAspectSubscriptionManager().get(Aspect.all(UnitStates.class));
  }

  protected void process(int entityId) {
    // EntityFactory marks newly allocated units as SPAWN.  Advancing one
    // phase per fixed tick makes InsertWorld observable and prevents systems
    // from treating a partially assembled entity as active in its creation
    // tick.  Death/removed phases are terminal and deliberately untouched.
    UnitLifecycle lifecycle = mLifecycle.get(entityId);
    if (lifecycle == null) return;
    if (lifecycle.phase == UnitLifecycle.Phase.SPAWN) {
      lifecycle.transition(UnitLifecycle.Phase.INSERTED);
    } else if (lifecycle.phase == UnitLifecycle.Phase.INSERTED) {
      lifecycle.transition(UnitLifecycle.Phase.ACTIVE);
    }
  }

  @Override
  protected void processSystem() {
    com.artemis.utils.IntBag entities = subscription.getEntities();
    int[] ids = entities.getData();
    for (int i = 0; i < entities.size(); i++) process(ids[i]);
  }

  @Override
  protected void removed(int entityId) {
    // BaseEntitySystem invokes this callback at the deletion boundary.  The
    // component may already be detached, so this is best-effort; the
    // important guarantee is that no deleted entity remains in the active
    // lifecycle subscription.
    if (mLifecycle.has(entityId)) {
      mLifecycle.get(entityId).transition(UnitLifecycle.Phase.DESTROYED);
    }
  }

  @Subscribe
  public void onDeath(DeathEvent event) {
    if (event == null || event.victim < 0 || !world.getEntityManager().isActive(event.victim)) return;
    UnitLifecycle lifecycle = mLifecycle.has(event.victim)
        ? mLifecycle.get(event.victim) : mLifecycle.create(event.victim).reset();
    // DeathEvent may be emitted by both melee and missile paths in one tick.
    if (lifecycle.deathHandled) return;
    lifecycle.deathHandled = true;
    lifecycle.deathKiller = event.killer;
    lifecycle.deathTick = ++deathSequence;
    lifecycle.transition(UnitLifecycle.Phase.DEATH);

    int removedMissiles = cleanupMissiles(event.victim);
    int removedPets = cleanupPets(event.victim);
    int clearedTargets = cleanupTargets(event.victim);
    int removedSourceStates = cleanupSourceStates(event.victim);

    // Missile-like units have no corpse lifecycle and must leave the world at
    // the death boundary. Players/monsters are retained by their corpse
    // systems (MODE_DD / PlayerCorpse) and are never deleted here.
    if (mMissile.has(event.victim)
        || (!mPlayer.has(event.victim) && !mMonster.has(event.victim)
            && !mSummonedPet.has(event.victim))) {
      lifecycle.transition(UnitLifecycle.Phase.REMOVED);
      world.delete(event.victim);
    }
    log.debug("[UNIT_LIFECYCLE] phase=death entity={} killer={} missiles={} pets={} "
            + "targets={} sourceStates={} retained={}",
        event.victim, event.killer, removedMissiles, removedPets, clearedTargets,
        removedSourceStates, world.getEntityManager().isActive(event.victim));
  }

  private int cleanupMissiles(int deadEntityId) {
    int removed = 0;
    if (missiles == null) return 0;
    int[] ids = missiles.getEntities().getData();
    int size = missiles.getEntities().size();
    for (int i = 0; i < size; i++) {
      int id = ids[i];
      Missile missile = mMissile.get(id);
      if (missile == null) continue;
      if (missile.ownerId == deadEntityId || missile.damageOwnerId == deadEntityId
          || missile.attachedEntityId == deadEntityId) {
        world.delete(id);
        removed++;
      } else if (missile.targetId == deadEntityId) {
        // A homing missile loses its target but can finish its native range.
        missile.targetId = Engine.INVALID_ENTITY;
        missile.homing = false;
      }
    }
    return removed;
  }

  private int cleanupPets(int deadEntityId) {
    int removed = 0;
    if (pets == null) return 0;
    int[] ids = pets.getEntities().getData();
    int size = pets.getEntities().size();
    for (int i = 0; i < size; i++) {
      int id = ids[i];
      SummonedPet pet = mSummonedPet.get(id);
      if (pet != null && id != deadEntityId && pet.ownerId == deadEntityId) {
        world.delete(id);
        removed++;
      }
    }
    return removed;
  }

  private int cleanupTargets(int deadEntityId) {
    int cleared = 0;
    if (targets == null) return 0;
    int[] ids = targets.getEntities().getData();
    int size = targets.getEntities().size();
    for (int i = 0; i < size; i++) {
      Target target = mTarget.get(ids[i]);
      if (target != null && target.target == deadEntityId) {
        target.target = Engine.INVALID_ENTITY;
        cleared++;
      }
    }
    return cleared;
  }

  private int cleanupSourceStates(int deadEntityId) {
    int removed = 0;
    if (unitStates == null) return 0;
    int[] ids = unitStates.getEntities().getData();
    int size = unitStates.getEntities().size();
    for (int i = 0; i < size; i++) {
      UnitStates states = mUnitStates.get(ids[i]);
      if (states != null && states.stateList != null) {
        removed += states.stateList.removeStatesFromSource(deadEntityId);
      }
    }
    return removed;
  }
}
