package com.riiablo.engine.client;

import com.badlogic.gdx.Gdx;
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
    // One-shot impact DCCs are created and rendered in the same simulation
    // tick.  Preload their sole direction here instead of relying on the
    // lazy Layer.draw path, which can otherwise submit a region before the
    // GL texture has been created (the entity then expires with no visible
    // pixels even though the animation and bounds are valid).
    if (entry != null && "iceexplode".equalsIgnoreCase(entry.Missile)) {
      celFile.loadDirection(0);
    }

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
    // IceArrowExplode.dcc is a fully-coloured impact sprite.  Its native
    // Trans=1 row is intended for D2's separate luminosity-mask pipeline,
    // but the indexed shader derives alpha from the decoded palette RGB and
    // makes this blue effect effectively transparent. Keep the source palette
    // alpha for this one-shot impact so the native 16-frame burst is visible.
    if ("iceexplode".equalsIgnoreCase(entry.Missile)) {
      blendMode = BlendMode.ID;
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
      int phase = poisonPuffPhase(missile, entityId, animation.getNumFramesPerDir());
      initialFrame += phase;
      Gdx.app.debug("MissileLoader", String.format(
          "[MISSILE_ANIM_PHASE] entity=%d missile=%s phase=%d frames=%d initial=%d",
          entityId, entry.Missile, phase, animation.getNumFramesPerDir(),
          Math.floorMod(initialFrame, animation.getNumFramesPerDir())));
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

  /**
   * Loads a newly-created presentation missile immediately when its native
   * impact event is dispatched during the simulation tick.  Normally Artemis
   * invokes {@link #process(int)} on the next pass, but one-shot impact DCCs
   * must be visible in the same render frame in which they are spawned.
   */
  public void loadNow(int entityId) {
    if (!mMissile.has(entityId)) return;
    // Entity ids are recycled by Artemis. Rebuild an inherited wrapper in
    // place instead of removing and recreating the component: RenderSystem is
    // subscribed to AnimationWrapper and a same-tick remove/create can leave
    // a recycled impact entity out of its render subscription. process()'
    // Animation.Builder.reset() replaces the old DCC layer, so retaining the
    // wrapper does not retain the previous missile's animation.
    process(entityId);
    Missiles.Entry entry = mMissile.get(entityId).missile;
    if (entry != null && ("iceexplode".equalsIgnoreCase(entry.Missile)
        || "fireexplosion2".equalsIgnoreCase(entry.Missile))) {
      int frames = mAnimationWrapper.has(entityId)
          ? mAnimationWrapper.get(entityId).animation.getNumFramesPerDir() : 0;
      Animation loaded = mAnimationWrapper.has(entityId)
          ? mAnimationWrapper.get(entityId).animation : null;
      int loadedBlend = loaded != null && loaded.getLayer(0) != null
          ? loaded.getLayer(0).getBlendMode() : -1;
      String pixels = "n/a";
      if ("iceexplode".equalsIgnoreCase(entry.Missile)) {
        com.badlogic.gdx.graphics.Pixmap pixmap = celFile.getPixmap(0, 0);
        if (pixmap != null) {
          int nonZero = 0;
          int first = pixmap.getPixel(0, 0);
          for (int py = 0; py < pixmap.getHeight(); py++) {
            for (int px = 0; px < pixmap.getWidth(); px++) {
              if (pixmap.getPixel(px, py) != 0) nonZero++;
            }
          }
          pixels = String.format("%dx%d nonZero=%d first=0x%08x texture=%s",
              pixmap.getWidth(), pixmap.getHeight(), nonZero, first,
              celFile.getTexture(0, 0) != null
                  ? celFile.getTexture(0, 0).getTexture().getTextureObjectHandle()
                  : "null");
        }
      }
      Gdx.app.debug("MissileLoader", String.format(
          "[MISSILE_ANIM_LOAD] entity=%d missile=%s loaded=%s frames=%d dirs=%d "
              + "frame=%d dir=%d trans=%d blend=%d box=%s pixels=%s",
          entityId, entry.Missile, loaded != null, frames,
          loaded != null ? loaded.getNumDirections() : 0,
          loaded != null ? loaded.getFrame() : -1,
          loaded != null ? loaded.getDirection() : -1,
          entry.Trans, loadedBlend,
          loaded != null ? loaded.getBox() : null, pixels));
    }
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
