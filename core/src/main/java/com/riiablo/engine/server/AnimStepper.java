package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IntervalIteratingSystem;
import net.mostlyoriginal.api.event.common.EventSystem;

import com.riiablo.codec.Animation;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.event.AnimDataFinishedEvent;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All(AnimData.class)
public class AnimStepper extends IntervalIteratingSystem {
  private static final Logger log = LogManager.getLogger(AnimStepper.class);

  protected ComponentMapper<AnimData> mAnimData;

  protected EventSystem events;

  public AnimStepper() {
    super(null, Animation.FRAME_DURATION);
  }

  @Override
  protected void process(int entityId) {
    AnimData animData = mAnimData.get(entityId);
    if (animData.numFrames <= 0) return;

    int delta = animData.override >= 0 ? animData.override : animData.speed;
    if (delta < 0) delta = 0;
    int nextFrame = animData.frame + delta;
    while (nextFrame >= animData.numFrames) {
      nextFrame -= animData.numFrames;
      dispatchKeyframes(entityId, animData, true);
      events.dispatch(AnimDataFinishedEvent.obtain(entityId));
      animData.lastKeyframeIndex = -1;
    }
    animData.frame = nextFrame;

    if (animData.keyframes == null || animData.keyframes.length == 0) {
      return;
    }
    dispatchKeyframes(entityId, animData, false);
  }

  private void dispatchKeyframes(int entityId, AnimData animData, boolean beforeWrap) {
    if (animData.keyframes == null || animData.keyframes.length == 0) return;
    int currentIndex = animData.frame >>> 8;
    int maxIndex = animData.keyframes.length - 1;
    if (currentIndex > maxIndex) currentIndex = maxIndex;

    int start = animData.lastKeyframeIndex + 1;
    if (start < 0) start = 0;
    if (start <= currentIndex) {
      dispatchRange(entityId, animData.keyframes, start, currentIndex);
    } else if (beforeWrap) {
      dispatchRange(entityId, animData.keyframes, start, maxIndex);
    }
    animData.lastKeyframeIndex = currentIndex;
  }

  private void dispatchRange(int entityId, byte[] keyframes, int start, int end) {
    if (start > end) return;
    for (int i = start; i <= end; i++) {
      byte keyframe = keyframes[i];
      if (keyframe > Engine.KEYFRAME_NIL) {
        log.debug("broadcasting AnimDataKeyframeEvent({},{})", entityId, Engine.getKeyframe(keyframe));
        events.dispatch(AnimDataKeyframeEvent.obtain(entityId, keyframe));
      }
    }
  }
}
