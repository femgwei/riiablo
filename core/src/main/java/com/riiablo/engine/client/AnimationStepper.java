package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IntervalIteratingSystem;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Sequence;

@All(AnimationWrapper.class)
public class AnimationStepper extends IntervalIteratingSystem {
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Sequence> mSequence;

  public AnimationStepper() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  @Override
  protected void process(int entityId) {
    AnimationWrapper wrapper = mAnimationWrapper.get(entityId);
    if (wrapper == null || wrapper.animation == null) return;

    Sequence sequence = mSequence.get(entityId);
    AnimData animData = mAnimData.get(entityId);
    if (sequence != null && sequence.nativeJab && animData != null) {
      // Player SQ animation is authoritative on the sequence timeline.  The
      // server's AnimStepper resolves each monseq point to the selected A1/A2
      // mode and source COF frame; do not run the resident COF as an ordinary
      // looping animation or it will ignore that source-frame mapping.
      int frameCount = wrapper.animation.getNumFramesPerDir();
      if (frameCount > 0) {
        int frame = Math.max(0, Math.min(frameCount - 1, animData.frame >>> 8));
        wrapper.animation.setFrame(frame);
      }
      return;
    }

    //mAnimationWrapper.get(entityId).animation.update(world.delta);
    wrapper.animation.update(SimulationClock.STEP_SECONDS);
  }
}
