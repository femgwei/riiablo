package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Direction;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.client.component.BBoxWrapper;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.graphics.BlendMode;

@All(Missile.class)
@Exclude({AnimationWrapper.class, BBoxWrapper.class})
public class MissileLoader extends IteratingSystem {
  protected ComponentMapper<Missile> mMissile;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<BBoxWrapper> mBBoxWrapper;
  protected ComponentMapper<Angle> mAngle;

  @Override
  protected void process(int entityId) {
    Missile missile = mMissile.get(entityId);
    if (!Riiablo.assets.isLoaded(missile.missileDescriptor)) return;
    DCC celFile = Riiablo.assets.get(missile.missileDescriptor);

    Missiles.Entry entry = missile.missile;

    int blendMode;
    switch (entry.Trans) {
      case 0:  blendMode = BlendMode.ID; break;
      // Ground-fire DCCs use the native luminosity mask, but their
      // Light/Flicker fields modulate that mask over time. Keep source
      // palette RGB values intact instead of recolouring the sprite.
      case 1:  blendMode = isPoisonMissile(entry) ? BlendMode.SCREEN
          : isGroundFire(entry) ? BlendMode.LUMINOSITY_FLICKER : BlendMode.LUMINOSITY; break;
      default: blendMode = BlendMode.ID; break;
    }

    Animation animation = mAnimationWrapper.create(entityId).animation;
    animation.edit()
        .layer(celFile, blendMode)
        .build();
    animation.setMode(entry.LoopAnim > 0 ? Animation.Mode.LOOP : Animation.Mode.CLAMP); // TODO: Some are 2 -- special case?
    if (entry.SubLoop > 0 && animation.getMode() == Animation.Mode.LOOP) {
      animation.setSubLoop(entry.SubStart, entry.SubStop);
    }
    int initialFrame = entry.RandStart;
    // PoisonSmokePuff is spawned as a client-only child by the native poison
    // javelin callback.  Its table RandStart is empty, but D2 seeds each new
    // puff at a different animation frame; starting every child at frame 0
    // produces an obvious row of synchronized tiny sparks.  Derive a stable
    // per-entity phase from the missile seed/position so local and network
    // clients get varied, reproducible large/small puffs without global RNG.
    if (isPoisonSmokePuff(entry) && animation.getNumFramesPerDir() > 0) {
      initialFrame += poisonPuffPhase(missile, entityId,
          animation.getNumFramesPerDir());
    }
    if (isGroundFire(entry) && animation.getNumFramesPerDir() > 0) {
      initialFrame += groundFirePhase(missile, animation.getNumFramesPerDir());
    }
    if (animation.getNumFramesPerDir() > 0) {
      animation.setFrame(Math.floorMod(initialFrame, animation.getNumFramesPerDir()));
    }
    // D2Common initializes missile wAnimSpeed as
    //   (animrate << 8) / 1024.
    // Animation.setFrameDelta uses the same 8.8 fixed-point unit, so keeping
    // the table value here preserves per-missile impact/child timing instead
    // of forcing every DCC to 25 frames per second.
    int nativeFrameDelta = entry.animrate > 0 ? (entry.animrate >> 2) : 256;
    animation.setFrameDelta(Math.max(1, nativeFrameDelta));
    
    // Set direction based on Angle component if available
    if (mAngle.has(entityId)) {
      float radians = mAngle.get(entityId).angle.angleRad();
      int direction = Direction.radiansToDirection(radians, animation.getNumDirections());
      animation.setDirection(direction);
    } else {
      animation.setDirection(0);
    }

    mBBoxWrapper.create(entityId).box = animation.getBox();
  }

  /** Ground-fire rows share the native groundFireBig DCC and flicker fields. */
  static boolean isGroundFire(Missiles.Entry entry) {
    return entry != null
        && entry.CelFile != null
        && "groundFireBig".equalsIgnoreCase(entry.CelFile)
        && entry.Light > 0
        && entry.Flicker > 0;
  }

  static boolean isPoisonSmokePuff(Missiles.Entry entry) {
    return entry != null && entry.CelFile != null
        && "PoisonSmokePuff".equalsIgnoreCase(entry.CelFile);
  }

  static int poisonPuffPhase(Missile missile, int entityId, int frameCount) {
    if (frameCount <= 0) return 0;
    int x = missile != null ? Math.round(missile.start.x * 16f) : 0;
    int y = missile != null ? Math.round(missile.start.y * 16f) : 0;
    int seed = missile != null ? missile.rngState : 0;
    int hash = seed ^ entityId * 0x9E3779B9 ^ x * 0x45D9F3B ^ y * 0x27D4EB2D;
    hash ^= hash >>> 16;
    hash *= 0x7FEB352D;
    hash ^= hash >>> 15;
    return Math.floorMod(hash, frameCount);
  }

  /** Native poison/plague missiles use PL2 Screen instead of alpha-over. */
  static boolean isPoisonMissile(Missiles.Entry entry) {
    if (entry == null) return false;
    String type = entry.EType;
    String missile = entry.Missile;
    String skill = entry.Skill;
    return "pois".equalsIgnoreCase(type) || "poison".equalsIgnoreCase(type)
        || missile != null && (missile.toLowerCase(java.util.Locale.ROOT).contains("poison")
            || missile.toLowerCase(java.util.Locale.ROOT).contains("plague"))
        || skill != null && (skill.toLowerCase(java.util.Locale.ROOT).contains("poison")
            || skill.toLowerCase(java.util.Locale.ROOT).contains("plague"));
  }

  /** Stable world-cell phase, so neighbouring fire cells do not animate in lockstep. */
  static int groundFirePhase(Missile missile, int frameCount) {
    if (frameCount <= 0 || missile == null) return 0;
    int x = Math.round(missile.start.x);
    int y = Math.round(missile.start.y);
    int hash = x * 0x45D9F3B ^ y * 0x27D4EB2D;
    return Math.floorMod(hash, frameCount);
  }
}
