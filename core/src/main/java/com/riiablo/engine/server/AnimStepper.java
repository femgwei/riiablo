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
    animData.frame += animData.override >= 0 ? animData.override : animData.speed;
    if (animData.frame >= animData.numFrames) {
      animData.frame -= animData.numFrames;
      events.dispatch(AnimDataFinishedEvent.obtain(entityId));
    }

    if (animData.keyframes == null || animData.keyframes.length == 0) {
      return;
    }

    // 计算关键帧索引（frame 是 24.8 固定点数，右移 8 位获取整数部分）
    final int keyframeIndex = animData.frame >>> 8;
    
    // 边界检查：防止数组越界
    if (keyframeIndex < 0 || keyframeIndex >= animData.keyframes.length) {
      log.warn("Keyframe index out of bounds: frame={}, index={}, keyframes.length={}, entityId={}", 
          animData.frame, keyframeIndex, animData.keyframes.length, entityId);
      return;
    }

    final byte keyframe = animData.keyframes[keyframeIndex];
    if (keyframe > Engine.KEYFRAME_NIL) {
      log.debug("broadcasting AnimDataKeyframeEvent({},{})", entityId, Engine.getKeyframe(keyframe));
      events.dispatch(AnimDataKeyframeEvent.obtain(entityId, keyframe));
    }
  }
}
