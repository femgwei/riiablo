package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.Box2DBody;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Running;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Target;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.pet.PetType;
import com.riiablo.engine.server.state.StateId;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Maintains native summon ownership and time-limited pet lifetimes. */
@All(SummonedPet.class)
public class SummonedPetSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(SummonedPetSystem.class);
  private static final float NATIVE_FRAMES_PER_SECOND = 25f;
  /** D2MOO NecroPet switches to PetMove mode 3 beyond 50 subtiles. */
  static final float NATIVE_TELEPORT_DISTANCE = 50f;
  /** D2MOO begins following the owner's stored path beyond 28 subtiles. */
  static final float NATIVE_OWNER_TRAIL_DISTANCE = 28f;
  private static final float FAILED_REGROUP_RETRY_SECONDS = 1f;

  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<MapWrapper> mMap;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Pathfind> mPathfind;
  protected ComponentMapper<PathWrapper> mPathWrapper;
  protected ComponentMapper<Target> mTarget;
  protected ComponentMapper<Casting> mCasting;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<Running> mRunning;
  protected ComponentMapper<Velocity> mVelocity;
  protected ComponentMapper<Box2DBody> mBox2DBody;
  protected ComponentMapper<UnitStates> mUnitStates;
  private final Vector2 warpPosition = new Vector2();
  private final Vector2 landingProbe = new Vector2();
  private final IntMap<Float> regroupRetry = new IntMap<>();

  @Override
  protected void process(int entityId) {
    SummonedPet pet = mSummonedPet.get(entityId);
    if (pet == null) return;
    if (pet.ownerId < 0 || !mPlayer.has(pet.ownerId)) {
      log.info("[SUMMON_PET] phase=remove entity={} owner={} reason=owner_missing",
          entityId, pet.ownerId);
      world.delete(entityId);
      return;
    }
    // D2Game_KillPlayerPets removes every non-hireling summon when its owner
    // dies.  Do this from authoritative HP rather than presentation state so
    // local and multiplayer worlds follow the same rule.
    // ServerPlayerDeathSystem places PlayerCorpse on the owner before the
    // death event is broadcast. This is the authoritative death marker and
    // avoids depending on an optional/late attributes snapshot.
    if (mPlayerCorpse.has(pet.ownerId)) {
      log.info("[SUMMON_PET] phase=remove entity={} owner={} reason=owner_corpse_marker",
          entityId, pet.ownerId);
      world.delete(entityId);
      return;
    }
    if (mAttributes.has(pet.ownerId) && mAttributes.get(pet.ownerId).attrs != null) {
      StatRef ownerHp = mAttributes.get(pet.ownerId).attrs.get(Stat.hitpoints, StatRef.obtain());
      if (ownerHp != null && ownerHp.asFixed() <= 0f) {
        log.info("[SUMMON_PET] phase=remove entity={} owner={} reason=owner_dead", entityId, pet.ownerId);
        world.delete(entityId);
        return;
      }
    }
    // Native wall-maker segments are minions of the first wall unit. The
    // MONUMOD_KILLMINIONSDEATH link removes the remaining line/ring when that
    // controller dies; ownerId still points at the casting player for damage
    // credit and multiplayer hostility.
    if (pet.boneWall && pet.controllerId >= 0 && pet.controllerId != entityId
        && !isLivingController(pet.controllerId)) {
      log.info("[NECRO_BONE_WALL] phase=remove entity={} owner={} controller={} "
              + "reason=controller_dead_or_missing",
          entityId, pet.ownerId, pet.controllerId);
      world.delete(entityId);
      return;
    }
    // PetType.txt decides whether a pet follows its owner across a level
    // transition. Native NecroPet also switches to PetMove mode 3 beyond 50
    // subtiles. D2Game can first follow the player's 20-entry coordinate
    // trail across RoomEx boundaries; Java has no equivalent trail yet, so a
    // missing path between non-adjacent rooms uses the same mode-3 regroup as
    // a deterministic fallback.
    if (mMap.has(entityId) && mMap.has(pet.ownerId)) {
      MapWrapper petMap = mMap.get(entityId), ownerMap = mMap.get(pet.ownerId);
      if (petMap != null && ownerMap != null && petMap.zone != null && ownerMap.zone != null) {
        boolean sameMap = petMap.map == null || ownerMap.map == null || petMap.map == ownerMap.map;
        boolean sameZone = sameMap && petMap.zone == ownerMap.zone;
        if (!sameZone && !PetType.warpsWithOwner(pet.petType)) {
          log.info("[SUMMON_PET] phase=remove entity={} owner={} petType={} "
                  + "reason=owner_zone_changed_no_warp",
              entityId, pet.ownerId, pet.petType);
          world.delete(entityId);
          return;
        }

        if (mPosition.has(entityId) && mPosition.has(pet.ownerId)) {
          Vector2 petPosition = mPosition.get(entityId).position;
          Vector2 ownerPosition = mPosition.get(pet.ownerId).position;
          int footprint = mSize.has(entityId) ? Math.max(1, mSize.get(entityId).size) : 1;
          int distance = nativeOwnerDistance(petPosition, ownerPosition, footprint);
          boolean roomsAdjacent = sameZone && (!ownerMap.zone.hasNativeRoomTopology()
              || ownerMap.zone.areRoomsAdjacent(
                  petPosition.x, petPosition.y, ownerPosition.x, ownerPosition.y));
          boolean hasPath = mPathfind.has(entityId);
          boolean regroup = PetType.warpsWithOwner(pet.petType)
              && shouldRegroup(sameZone, distance, roomsAdjacent, hasPath,
                  isMobileFollower(pet));
          if (regroup && readyToRegroup(entityId, !sameZone)) {
            if (findOwnerLanding(ownerMap.zone, ownerPosition, footprint,
                warpPosition, landingProbe)) {
              relocate(entityId, pet, petMap, ownerMap, warpPosition, distance,
                  sameZone ? "regroup" : "warp");
            } else if (!sameZone) {
              log.warn("[SUMMON_PET] phase=remove entity={} owner={} petType={} "
                      + "reason=owner_zone_no_free_position searchRadius=24",
                  entityId, pet.ownerId, pet.petType);
              world.delete(entityId);
              return;
            } else {
              regroupRetry.put(entityId, FAILED_REGROUP_RETRY_SECONDS);
              log.debug("[SUMMON_PET] phase=regroup_deferred entity={} owner={} petType={} "
                      + "distance={} reason=no_free_position searchRadius=24",
                  entityId, pet.ownerId, pet.petType, distance);
            }
          }
        }
      }
    }
    // D2MOO keeps the dead unit through its DT/DD sequence, then removes it
    // from PLAYERPETS. Give clients one native second (25 frames) to observe
    // the death state while preventing immortal summon corpses.
    if (mCorpse.has(entityId)) {
      pet.deathPending = true;
      pet.deadFrames += Math.max(0f, world.delta) * NATIVE_FRAMES_PER_SECOND;
      if (pet.deadFrames >= 25f) {
        log.info("[SUMMON_PET] phase=remove entity={} owner={} petType={} reason=dead_complete",
            entityId, pet.ownerId, pet.petType);
        world.delete(entityId);
        return;
      }
    }
    if (pet.durationFrames <= 0) return;
    pet.elapsedFrames += Math.max(0f, world.delta) * NATIVE_FRAMES_PER_SECOND;
    if (pet.elapsedFrames < pet.durationFrames) return;
    log.info("[SUMMON_PET] phase=remove entity={} owner={} petType={} reason=expired "
            + "duration={}",
        entityId, pet.ownerId, pet.petType, pet.durationFrames);
    world.delete(entityId);
  }

  private boolean isLivingController(int entityId) {
    if (!world.getEntityManager().isActive(entityId) || mCorpse.has(entityId)) return false;
    if (!mAttributes.has(entityId) || mAttributes.get(entityId).attrs == null) return true;
    StatRef hp = mAttributes.get(entityId).attrs.get(Stat.hitpoints, StatRef.obtain());
    return hp == null || hp.asFixed() > 0f;
  }

  @Override
  protected void removed(int entityId) {
    regroupRetry.remove(entityId);
  }

  private boolean readyToRegroup(int entityId, boolean crossZone) {
    if (crossZone) return true;
    float remaining = regroupRetry.get(entityId, 0f);
    if (remaining <= 0f) return true;
    remaining -= Math.max(0f, world.getDelta());
    if (remaining <= 0f) {
      regroupRetry.remove(entityId);
      return true;
    }
    regroupRetry.put(entityId, remaining);
    return false;
  }

  private void relocate(int entityId, SummonedPet pet, MapWrapper petMap,
      MapWrapper ownerMap, Vector2 destination, float oldDistance, String phase) {
    Vector2 position = mPosition.get(entityId).position;
    float oldX = position.x, oldY = position.y;

    // A PetMove mode-3 relocation starts a fresh neutral think. No old-world
    // target, attack sequence or path is allowed to run at the new position.
    if (mPathfind.has(entityId)) mPathfind.remove(entityId);
    if (mPathWrapper.has(entityId)) mPathWrapper.remove(entityId);
    if (mTarget.has(entityId)) mTarget.remove(entityId);
    if (mCasting.has(entityId)) mCasting.remove(entityId);
    if (mSequence.has(entityId)) mSequence.remove(entityId);
    if (mRunning.has(entityId)) mRunning.remove(entityId);
    if (mVelocity.has(entityId)) {
      mVelocity.get(entityId).velocity.setZero();
      mVelocity.get(entityId).clearModeSpeedBonus();
    }
    if (mAIWrapper.has(entityId) && mAIWrapper.get(entityId).ai != null) {
      mAIWrapper.get(entityId).ai.onOwnerWarp();
    }

    position.set(destination);
    petMap.set(ownerMap.map, ownerMap.zone);
    com.riiablo.map.Map.RoomEx room = ownerMap.zone.findRoomEx(destination.x, destination.y);
    petMap.roomId = room != null ? room.id : -1;
    if (mBox2DBody.has(entityId) && mBox2DBody.get(entityId).body != null) {
      mBox2DBody.get(entityId).body.setTransform(destination, 0f);
      mBox2DBody.get(entityId).body.setLinearVelocity(0f, 0f);
    }
    if (mUnitStates.has(entityId)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states.stateList == null) states.init(entityId);
      states.stateList.addState(StateId.SYNC_WARPED, 2, 1, entityId);
    }
    regroupRetry.remove(entityId);
    log.info("[SUMMON_PET] phase={} entity={} owner={} petType={} from=({}, {}) "
            + "to=({}, {}) distance={} level={} room={}",
        phase, entityId, pet.ownerId, pet.petType, oldX, oldY,
        destination.x, destination.y, oldDistance,
        ownerMap.zone.level != null ? ownerMap.zone.level.Id : -1, petMap.roomId);
  }

  static boolean shouldRegroup(boolean sameZone, float distance,
      boolean roomsAdjacent, boolean hasPath, boolean mobileFollower) {
    if (!sameZone) return true;
    if (!mobileFollower) return false;
    if (distance > NATIVE_TELEPORT_DISTANCE) return true;
    return distance > NATIVE_OWNER_TRAIL_DISTANCE && !roomsAdjacent && !hasPath;
  }

  /** D2Game {@code AIUTIL_GetDistanceToCoordinates_FullUnitSize}. */
  static int nativeOwnerDistance(Vector2 pet, Vector2 owner, int petSize) {
    if (pet == null || owner == null) return Integer.MAX_VALUE;
    int size = Math.max(0, petSize);
    int dx = Math.abs(Math.abs(Math.round(pet.x) - Math.round(owner.x)) - size);
    int dy = Math.abs(Math.abs(Math.round(pet.y) - Math.round(owner.y)) - size);
    int min = Math.min(dx, dy), max = Math.max(dx, dy);
    return (min + 2 * max) / 2;
  }

  private static boolean isMobileFollower(SummonedPet pet) {
    if (pet == null || pet.passive || pet.boneWall) return false;
    switch (PetType.canonical(pet.petType)) {
      case "assassintrap":
      case "pettrap":
      case "hydra":
        return false;
      default:
        return true;
    }
  }

  /**
   * Deterministic projection of PetMove mode 3. Search begins outside the
   * owner's collision footprint and expands through 8, 16 and 24 subtiles.
   */
  static boolean findOwnerLanding(com.riiablo.map.Map.Zone zone, Vector2 owner,
      int footprint, Vector2 out, Vector2 probe) {
    if (zone == null || owner == null || out == null || probe == null) return false;
    int centerX = Math.round(owner.x), centerY = Math.round(owner.y);
    int minimumRadius = Math.max(2, footprint + 1);
    int searchedRadius = minimumRadius - 1;
    int[] limits = {8, 16, 24};
    for (int limit : limits) {
      for (int radius = searchedRadius + 1; radius <= limit; radius++) {
        for (int dy = -radius; dy <= radius; dy++) {
          for (int dx = -radius; dx <= radius; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
            probe.set(centerX + dx, centerY + dy);
            if (zone.findFreeCoordinates(probe, footprint, 0, true, out)) return true;
          }
        }
      }
      searchedRadius = Math.max(searchedRadius, limit);
    }
    return false;
  }
}
