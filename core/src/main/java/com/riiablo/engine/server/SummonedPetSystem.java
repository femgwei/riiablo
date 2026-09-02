package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Maintains native summon ownership and time-limited pet lifetimes. */
@All(SummonedPet.class)
public class SummonedPetSystem extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(SummonedPetSystem.class);
  private static final float NATIVE_FRAMES_PER_SECOND = 25f;

  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<Player> mPlayer;

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
    if (pet.durationFrames <= 0) return;
    pet.elapsedFrames += Math.max(0f, world.delta) * NATIVE_FRAMES_PER_SECOND;
    if (pet.elapsedFrames < pet.durationFrames) return;
    log.info("[SUMMON_PET] phase=remove entity={} owner={} petType={} reason=expired "
            + "duration={}",
        entityId, pet.ownerId, pet.petType, pet.durationFrames);
    world.delete(entityId);
  }
}
