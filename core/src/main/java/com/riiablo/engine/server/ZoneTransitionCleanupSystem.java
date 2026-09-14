package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import com.artemis.utils.IntBag;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.ZoneChangeEvent;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.Map;

import net.mostlyoriginal.api.event.common.Subscribe;

/**
 * Clears level-scoped effects when an owner changes zones.
 *
 * <p>D2MOO treats missiles and source-owned periodic states as children of the
 * level in which they were created.  A warp must therefore not carry an
 * in-flight projectile, an attached controller, or a DOT layer into the new
 * level.  This listener runs after the warp transaction has rebound the owner
 * to its destination zone and queues stale entities for the normal Artemis
 * deletion/snapshot path.</p>
 */
public final class ZoneTransitionCleanupSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(ZoneTransitionCleanupSystem.class);

  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<MapWrapper> mMapWrapper;
  protected ComponentMapper<UnitStates> mUnitStates;

  @Subscribe
  public void onZoneChanged(ZoneChangeEvent event) {
    if (event == null || event.zone == null || world == null) return;
    final int ownerId = event.entityId;
    final Map.Zone destination = event.zone;
    int removedMissiles = 0;
    int removedStates = 0;

    IntBag missileEntities = world.getAspectSubscriptionManager()
        .get(Aspect.all(Missile.class)).getEntities();
    int[] missileIds = missileEntities.getData();
    for (int i = 0; i < missileEntities.size(); i++) {
      int entityId = missileIds[i];
      Missile missile = mMissile.get(entityId);
      if (missile == null || !referencesOwner(missile, ownerId)) continue;
      MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
      if (wrapper != null && sameLevel(wrapper.zone, destination)) continue;
      // No wrapper is treated as stale: an owner-bound missile without a
      // level cannot be safely serialized into the destination baseline.
      world.delete(entityId);
      removedMissiles++;
      log.debug("[ZONE_CLEANUP] deleted missile={} owner={} fromLevel={} toLevel={}",
          entityId, ownerId, levelId(wrapper == null ? null : wrapper.zone),
          levelId(destination));
    }

    IntBag stateEntities = world.getAspectSubscriptionManager()
        .get(Aspect.all(UnitStates.class)).getEntities();
    int[] stateIds = stateEntities.getData();
    for (int i = 0; i < stateEntities.size(); i++) {
      int entityId = stateIds[i];
      UnitStates unitStates = mUnitStates.get(entityId);
      StateList states = unitStates == null ? null : unitStates.stateList;
      if (states == null || states.isEmpty()) continue;
      MapWrapper wrapper = mMapWrapper.has(entityId) ? mMapWrapper.get(entityId) : null;
      if (wrapper != null && sameLevel(wrapper.zone, destination)) continue;
      int removed = states.removeStatesFromSource(ownerId);
      if (removed > 0) {
        removedStates += removed;
        log.debug("[ZONE_CLEANUP] removed states={} target={} source={} fromLevel={} toLevel={}",
            removed, entityId, ownerId, levelId(wrapper == null ? null : wrapper.zone),
            levelId(destination));
      }
    }

    if (removedMissiles > 0 || removedStates > 0) {
      log.info("[ZONE_CLEANUP] owner={} destinationLevel={} missiles={} states={}",
          ownerId, levelId(destination), removedMissiles, removedStates);
    }
  }

  private static boolean referencesOwner(Missile missile, int ownerId) {
    return missile.ownerId == ownerId || missile.damageOwnerId == ownerId
        || missile.attachedEntityId == ownerId || missile.rabiesSourceId == ownerId;
  }

  private static boolean sameLevel(Map.Zone first, Map.Zone second) {
    return first != null && second != null && levelId(first) >= 0
        && levelId(first) == levelId(second);
  }

  private static int levelId(Map.Zone zone) {
    return zone == null || zone.level == null ? -1 : zone.level.Id;
  }
}
