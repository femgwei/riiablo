package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;

import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.client.event.ClientCorpseLifecycleEvent;
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
  protected ComponentMapper<com.riiablo.engine.server.component.Networked> mNetworked;

  @Override
  protected void process(int entityId) {
    // Server deletion/resurrection owns a network corpse's lifetime.
    if (mNetworked.has(entityId)) return;
    // Player corpses own recoverable equipment and persist until retrieval.
    if (mPlayerCorpse.has(entityId)) return;

    Corpse corpse = mCorpse.get(entityId);
    float delta = world.getDelta();

    if (corpse.fading) {
      // Handle fade out
      corpse.fadeTime += delta;
      
      if (corpse.fadeTime >= Corpse.FADE_DURATION) {
        // Fade complete, delete the entity
        log.debug("Corpse {} fade complete, removing entity", entityId);
        dispatch(ClientCorpseLifecycleEvent.removed(entityId));
        world.delete(entityId);
      }
    } else if (Float.isFinite(corpse.timeRemaining)) {
      // Count down the corpse timer
      corpse.timeRemaining -= delta;
      
      if (corpse.timeRemaining <= 0) {
        // Start fading out
        corpse.fading = true;
        corpse.usable = false; // Can no longer be used by skills
        dispatch(ClientCorpseLifecycleEvent.fading(entityId));
        log.debug("Corpse {} starting to fade", entityId);
      }
    }
  }

  private void dispatch(ClientCorpseLifecycleEvent event) {
    net.mostlyoriginal.api.event.common.EventSystem events = world.getSystem(
        net.mostlyoriginal.api.event.common.EventSystem.class);
    if (events != null) events.dispatch(event);
  }
}
