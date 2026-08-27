package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;

import net.mostlyoriginal.api.event.common.Subscribe;

@All({CofReference.class, Sequence.class, AnimData.class})
public class SequenceHandler extends IteratingSystem {
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<Sequence> mSequence;
  protected ComponentMapper<AnimData> mAnimData;

  protected CofManager cofs;

  @Subscribe
  public void onAnimDataFinished(AnimDataFinishedEvent event) {
    if (!mSequence.has(event.entityId)) return;
    Sequence sequence = mSequence.get(event.entityId);
    // Log sequence transition for debugging
    com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(SequenceHandler.class);
    log.trace("Sequence finished for entity {}: mode1={} -> mode2={}", event.entityId, sequence.mode1, sequence.mode2);
    log.info("=== SequenceHandler.onAnimDataFinished ===");
    log.info("Entity: {}, Removing Sequence, mode1={} -> mode2={}", event.entityId, sequence.mode1, sequence.mode2);
    cofs.setMode(event.entityId, sequence.mode2);
    mAnimData.get(event.entityId).override = -1;
    mSequence.remove(event.entityId);
    log.info("After removal: Has Sequence: {}", mSequence.has(event.entityId));
  }

  @Override
  protected void process(int entityId) {
    Sequence sequence = mSequence.get(entityId);
    if (!sequence.started) {
      sequence.started = true;
      // D2 starts each action at frame zero. Force the COF event even when a
      // repeated action uses the same mode (for example consecutive Throws),
      // otherwise it inherits the previous frame and can skip its keyframe.
      cofs.setMode(entityId, sequence.mode1, true);
      mAnimData.get(entityId).override = -1;
    } else if (mCofReference.get(entityId).mode != sequence.mode1) {
      // Log sequence start for debugging
      com.riiablo.logger.Logger log = com.riiablo.logger.LogManager.getLogger(SequenceHandler.class);
      log.trace("Starting sequence for entity {}: setting mode to {}", entityId, sequence.mode1);
      cofs.setMode(entityId, sequence.mode1);
      mAnimData.get(entityId).override = -1;
    }
  }
}
