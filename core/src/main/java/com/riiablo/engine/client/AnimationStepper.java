package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IntervalIteratingSystem;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.client.component.AnimationWrapper;

@All(AnimationWrapper.class)
public class AnimationStepper extends IntervalIteratingSystem {
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;

  public AnimationStepper() {
    super(null, SimulationClock.STEP_SECONDS);
  }

  @Override
  protected void process(int entityId) {
    //mAnimationWrapper.get(entityId).animation.update(world.delta);
    mAnimationWrapper.get(entityId).animation.update(SimulationClock.STEP_SECONDS);
  }
}
