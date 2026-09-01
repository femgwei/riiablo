package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * System that manages corpse lifetimes and removes them after a duration.
 * This prevents corpses from accumulating indefinitely and impacting performance.
 */
@All(Corpse.class)
public class CorpseManager extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(CorpseManager.class);

  protected ComponentMapper<Corpse> mCorpse;
  protected ComponentMapper<PlayerCorpse> mPlayerCorpse;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;

  @Override
  protected void process(int entityId) {
    // Player corpses own recoverable equipment and persist until retrieval.
    if (mPlayerCorpse.has(entityId)) return;

    Corpse corpse = mCorpse.get(entityId);
    float delta = world.getDelta();

    if (corpse.fading) {
      // Handle fade out
      corpse.fadeTime += delta;
      
      // Apply alpha fade to the animation if available
      if (mAnimationWrapper.has(entityId)) {
        float alpha = 1.0f - (corpse.fadeTime / Corpse.FADE_DURATION);
        if (alpha < 0) alpha = 0;
        // Note: Animation alpha handling would need to be implemented
        // For now, we just delete when fade completes
      }
      
      if (corpse.fadeTime >= Corpse.FADE_DURATION) {
        // Fade complete, delete the entity
        log.debug("Corpse {} fade complete, removing entity", entityId);
        world.delete(entityId);
      }
    } else if (Float.isFinite(corpse.timeRemaining)) {
      // Count down the corpse timer
      corpse.timeRemaining -= delta;
      
      if (corpse.timeRemaining <= 0) {
        // Start fading out
        corpse.fading = true;
        corpse.usable = false; // Can no longer be used by skills
        log.debug("Corpse {} starting to fade", entityId);
      }
    }
  }
}
