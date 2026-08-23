package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.codec.Animation;
import com.riiablo.engine.Direction;
import com.badlogic.gdx.utils.IntIntMap;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

@All({Angle.class, AnimationWrapper.class})
public class DirectionResolver extends IteratingSystem {
  private static final Logger log = LogManager.getLogger(DirectionResolver.class);
  private static final int STABLE_FRAMES_REQUIRED = 2;

  protected ComponentMapper<Angle> mAngle;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;

  private final IntIntMap pendingDirection = new IntIntMap();
  private final IntIntMap pendingFrames = new IntIntMap();

  @Override
  protected void process(int entityId) {
    float radians = mAngle.get(entityId).angle.angleRad();
    Animation animation = mAnimationWrapper.get(entityId).animation;
    int d = Direction.radiansToDirection(radians, animation.getNumDirections());
    int current = animation.getDirection();
    if (d == current) {
      pendingDirection.remove(entityId, -1);
      pendingFrames.remove(entityId, 0);
      return;
    }

    int pending = pendingDirection.get(entityId, -1);
    int frames = pending == d ? pendingFrames.get(entityId, 0) + 1 : 1;
    pendingDirection.put(entityId, d);
    pendingFrames.put(entityId, frames);
    if (frames < STABLE_FRAMES_REQUIRED) return;

    animation.setDirection(d);
    pendingDirection.remove(entityId, -1);
    pendingFrames.remove(entityId, 0);
    log.debug("[DIRECTION] entity={} direction={}->{} radians={} frame={}",
        entityId, current, d, radians, animation.getFrame());
  }
}
