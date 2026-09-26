package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;

import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.graphics.BlendMode;

/** Applies the client-only visual portion of native ground-fire missiles. */
@All({Missile.class, AnimationWrapper.class})
public class MissileFirePresentationSystem extends IteratingSystem {
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;

  @Override
  protected void process(int entityId) {
    Missile missile = mMissile.get(entityId);
    Missiles.Entry entry = missile.missile;
    if (!MissileLoader.isGroundFire(entry)) return;

    Animation.Layer layer = mAnimationWrapper.get(entityId).animation.getLayer(0);
    if (layer == null) return;

    // Native Flicker is a frame interval. Hold one deterministic value for
    // that interval instead of making every render frame independent noise.
    int interval = Math.max(1, entry.Flicker);
    Animation animation = mAnimationWrapper.get(entityId).animation;
    int phase = MissileLoader.groundFirePhase(
        missile, Math.max(1, animation.getNumFramesPerDir()));
    int bucket = Math.floorDiv(animation.getFrame() + phase, interval);
    float noise = hash(bucket ^ phase ^ Math.round(missile.start.x) * 31
        ^ Math.round(missile.start.y) * 131);

    float base = MathUtils.clamp(0.78f + entry.Light * 0.025f, 0.78f, 1f);
    float amplitude = MathUtils.clamp(entry.Flicker * 0.0125f, 0.02f, 0.16f);
    float alpha = MathUtils.clamp(base - amplitude * 0.5f + amplitude * noise, 0.7f, 1f);
    if (layer.getBlendMode() != BlendMode.LUMINOSITY_FLICKER) {
      layer.setBlendMode(BlendMode.LUMINOSITY_FLICKER);
    }
    layer.setAlpha(alpha);
  }

  private static float hash(int value) {
    int x = value * 0x45D9F3B;
    x ^= x >>> 16;
    x *= 0x45D9F3B;
    x ^= x >>> 16;
    return (x & 0x7fffffff) / (float) Integer.MAX_VALUE;
  }
}
