package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.pet.PetType;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Maintains native summon ownership and time-limited pet lifetimes. */
@All(SummonedPet.class)
public class SummonedPetSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(SummonedPetSystem.class);
  private static final float NATIVE_FRAMES_PER_SECOND = 25f;

  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  protected ComponentMapper<AttributesWrapper> mAttributes;
  protected ComponentMapper<MapWrapper> mMap;
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Size> mSize;
  protected ComponentMapper<Corpse> mCorpse;
  private final Vector2 warpPosition = new Vector2();

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
    // transition. The owner may briefly lack a MapWrapper during login, so
    // only enforce this when both sides are known.
    if (mMap.has(entityId) && mMap.has(pet.ownerId)) {
      MapWrapper petMap = mMap.get(entityId), ownerMap = mMap.get(pet.ownerId);
      if (petMap != null && ownerMap != null && petMap.zone != null && ownerMap.zone != null
          && petMap.zone != ownerMap.zone) {
        if (PetType.warpsWithOwner(pet.petType)
            && mPosition.has(entityId) && mPosition.has(pet.ownerId)) {
          warpPosition.set(mPosition.get(pet.ownerId).position);
          int footprint = mSize.has(entityId) ? Math.max(1, mSize.get(entityId).size) : 1;
          if (ownerMap.zone.findFreeCoordinates(
              warpPosition, footprint, 8, true, warpPosition)) {
            mPosition.get(entityId).position.set(warpPosition);
            petMap.set(ownerMap.map, ownerMap.zone);
            log.info("[SUMMON_PET] phase=warp entity={} owner={} petType={} position=({}, {})",
                entityId, pet.ownerId, pet.petType, warpPosition.x, warpPosition.y);
          } else {
            log.warn("[SUMMON_PET] phase=remove entity={} owner={} petType={} "
                    + "reason=owner_zone_no_free_position",
                entityId, pet.ownerId, pet.petType);
            world.delete(entityId);
            return;
          }
        } else {
          log.info("[SUMMON_PET] phase=remove entity={} owner={} petType={} "
                  + "reason=owner_zone_changed_no_warp",
              entityId, pet.ownerId, pet.petType);
          world.delete(entityId);
          return;
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
}
