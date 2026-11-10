package com.riiablo.presentation;

import com.badlogic.gdx.Gdx;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.annotations.Wire;
import com.artemis.systems.IteratingSystem;

import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Direction;
import com.riiablo.presentation.component.AnimationWrapper;
import com.riiablo.presentation.component.BBoxWrapper;
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

  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext ;

  @Override
  protected void process(int entityId) {
    Missile missile = mMissile.get(entityId);
    if (world.getSystem(ReferenceElementalPresentationSystem.class) != null
        && ReferenceElementalPresentationSystem.isBlazeTrail(missile)) return;
    // Fire Wall makers are invisible native path controllers.  Their row uses
    // groundFireBig as a callback carrier, but drawing that row produces the
    // oversized continuous wall/loose flying flame; only their firewall child
    // segments are part of the original visual.
    if (missile.missile != null && "firewallmaker".equalsIgnoreCase(missile.missile.Missile)) {
      return;
    }
    // cursecenter is a native callback carrier and deliberately has no CelFile.
    // Its CltDo17 child pulses create the actual rendered curse-area cells.
    if (missile.missile == null || missile.missile.CelFile == null
        || missile.missile.CelFile.isEmpty()) return;
    // Network and replay reconstruction can restore the native missile row
    // without the transient AssetDescriptor. Recreate it before consulting
    // AssetManager; otherwise isLoaded(null) crashes the render thread.
    if (missile.missileDescriptor == null) {
      missile.missileDescriptor = new com.badlogic.gdx.assets.AssetDescriptor<>(
          com.riiablo.engine.server.component.Class.Type.MIS.PATH
              + '\\' + missile.missile.CelFile + ".dcc", DCC.class);
    }
    Missiles.Entry visualRow = presentationVisualRow(clientContext, missile);
    if (visualRow != missile.missile && visualRow != null) {
      String path = com.riiablo.engine.server.component.Class.Type.MIS.PATH
          + '\\' + visualRow.CelFile + ".dcc";
      if (!path.equals(missile.missileDescriptor.fileName)) {
        missile.missileDescriptor = new com.badlogic.gdx.assets.AssetDescriptor<>(path, DCC.class);
      }
      if (clientContext.assets() != null
          && !clientContext.assets().contains(path)) {
        clientContext.assets().load(missile.missileDescriptor);
      }
    }
    if (clientContext.assets() == null || !clientContext.assets().isLoaded(missile.missileDescriptor)) return;
    DCC celFile = clientContext.assets().get(missile.missileDescriptor);

    Missiles.Entry entry = visualRow;
    // CltDo3's presentation-only plaguejavcloud is a callback carrier: its
    // PoisonSparks base layer is the extra spear-like sprite seen travelling
    // with the javelin. Keep the entity alive so MissileImpactPresentationSystem
    // can run CltDo4 and emit PoisonSmokePuff, but do not attach this base DCC.
    if (missile.presentationOnly && entry != null
        && "plaguejavcloud".equalsIgnoreCase(entry.Missile)) return;
    // One-shot impact DCCs are created and rendered in the same simulation
    // tick.  Preload their sole direction here instead of relying on the
    // lazy Layer.draw path, which can otherwise submit a region before the
    // GL texture has been created (the entity then expires with no visible
    // pixels even though the animation and bounds are valid).
    if (entry != null && "iceexplode".equalsIgnoreCase(entry.Missile)) {
      celFile.loadDirection(0);
      com.badlogic.gdx.graphics.Pixmap pixmap = celFile.getPixmap(0, 0);
      if (pixmap != null) {
        int firstPixel = pixmap.getPixel(0, 0);
        int distinct = 0;
        int minLow = 255;
        int maxLow = 0;
        int minHigh = 255;
        int maxHigh = 0;
        int previous = Integer.MIN_VALUE;
        for (int py = 0; py < pixmap.getHeight(); py++) {
          for (int px = 0; px < pixmap.getWidth(); px++) {
            int pixel = pixmap.getPixel(px, py);
            int low = pixel & 0xff;
            int high = pixel >>> 24;
            minLow = Math.min(minLow, low);
            maxLow = Math.max(maxLow, low);
            minHigh = Math.min(minHigh, high);
            maxHigh = Math.max(maxHigh, high);
            if (pixel != previous) distinct++;
            previous = pixel;
          }
        }
        com.badlogic.gdx.graphics.g2d.TextureRegion region = celFile.getTexture(0, 0);
        Gdx.app.debug("MissileLoader", String.format(
            "[MISSILE_DCC] entity=%d size=%dx%d first=0x%08x distinct=%d "
                + "low=%d..%d high=%d..%d texture=%s",
            entityId, pixmap.getWidth(), pixmap.getHeight(), firstPixel, distinct,
            minLow, maxLow, minHigh, maxHigh,
            region != null ? region.getTexture().getTextureObjectHandle() : "null"));
      }
    }

    int blendMode = nativeBlendMode(entry);
    // IceArrowExplode.dcc is a fully-coloured impact sprite, but its native
    // Trans=1 row still uses the same additive missile compositing as
    // FreezeExplodeCenter/Ejecta. Keep the explicit branch as documentation
    // because this one-shot layer was previously forced through a different
    // indexed-alpha fallback while diagnosing the missing animation.
    if ("iceexplode".equalsIgnoreCase(entry.Missile)) {
      blendMode = BlendMode.ADDITIVE;
    }

    Animation animation = mAnimationWrapper.create(entityId).animation;
    animation.edit()
        .layer(celFile, blendMode)
        .build();
    animation.setMode(entry.LoopAnim > 0 ? Animation.Mode.LOOP : Animation.Mode.CLAMP); // TODO: Some are 2 -- special case?
    if (!NativeGroundFirePresentation.handles(missile)
        && entry.SubLoop > 0 && animation.getMode() == Animation.Mode.LOOP) {
      animation.setSubLoop(entry.SubStart, entry.SubStop);
    }
    int initialFrame = entry.RandStart;
    // CltDo5 initializes growth separately below. Keep the legacy random
    // startup only for groundFireBig rows using a different client callback.
    if (!NativeGroundFirePresentation.handles(missile)
        && isGroundFire(entry) && animation.getNumFramesPerDir() > 1) {
      initialFrame += MissileImpactPresentationSystem.nextClientRandom(missile,
          animation.getNumFramesPerDir());
    }
    // D2Common initializes missile wAnimSpeed as
    //   (animrate << 8) / 1024.
    // Animation.setFrameDelta uses the same 8.8 fixed-point unit, so keeping
    // the table value here preserves per-missile impact/child timing instead
    // of forcing every DCC to 25 frames per second.
    int nativeFrameDelta = nativeFrameDelta(clientContext, missile, visualRow);
    animation.setFrameDelta(Math.max(1, nativeFrameDelta));
    initializePlayback(missile, entry, animation, initialFrame);
    
    // Set direction based on Angle component if available
    if (mAngle.has(entityId)) {
      float radians = mAngle.get(entityId).angle.angleRad();
      int direction = Direction.radiansToDirection(radians, animation.getNumDirections());
      animation.setDirection(direction);
    } else {
      animation.setDirection(0);
    }

    ReferenceElementalPresentationSystem referenceElemental =
        world.getSystem(ReferenceElementalPresentationSystem.class);
    if (referenceElemental != null) referenceElemental.refreshLoadedEffect(entityId);
    mBBoxWrapper.create(entityId).box = animation.getBox();
  }

  static int nativeFrameDelta(ClientGameplayContext context, Missile missile) {
    return nativeFrameDelta(context, missile, missile == null ? null : missile.missile);
  }

  static int nativeFrameDelta(ClientGameplayContext context, Missile missile,
      Missiles.Entry visualRow) {
    if (missile.skillId == 308 && missile.missile.Id == 502) {
      if (context == null || context.files() == null || context.files().skills == null) return 256;
      int speed = com.riiablo.engine.server.skill.SkillFormula.evaluate(
          context.files().skills.get(308).calc1, context.files().skills.get(308), missile.damageLevel);
      if (speed > 0) return speed;
    }
    // SKILLS_UpdateInfernoAnimationParameters replaces the ordinary missile
    // animation rate along with TotalFrames, not just its travel distance.
    if ((missile.skillId == 41 || missile.skillId == 178 || missile.skillId == 319 || missile.skillId == 317
        || missile.skillId == 193 || missile.skillId == 230)
        && missile.nativeLifetimeFrames > 0) {
        int animLen = visualRow != null && visualRow.AnimLen > 0
            ? visualRow.AnimLen : missile.missile.AnimLen;
        return (animLen << 8) / missile.nativeLifetimeFrames;
    }
    return missile.missile.animrate > 0 ? missile.missile.animrate >> 2 : 256;
  }

  /** 1.10f CltDo24 (6FACA1D0) selects A OR B; it does not double damage. */
  static Missiles.Entry infernoVisualRow(ClientGameplayContext context, Missile missile) {
    if (missile == null || missile.skillId != 41 || missile.missile == null
        || !"infernoflame1".equalsIgnoreCase(missile.missile.Missile)
        || context == null || context.files() == null) {
      return missile == null ? null : missile.missile;
    }
    // Immutable allocation seed keeps retries during asynchronous DCC loading
    // stable and never consumes the server's damage stream.
    int seed = missile.presentationSeedKnown ? missile.presentationSeed : missile.rngState;
    com.d2moo.common.drlg.D2Seed visualSeed = new com.d2moo.common.drlg.D2Seed(seed, 666);
    if (com.d2moo.common.seed.Seed.rollLimitedRandomNumber(visualSeed, 2) == 0)
      return missile.missile;
    Missiles.Entry alternate = context.files().Missiles.get("infernoflame2");
    return alternate != null ? alternate : missile.missile;
  }

  /** Fire Wall grows from the centre: large core, medium inner cells, small ends. */
  static Missiles.Entry presentationVisualRow(ClientGameplayContext context, Missile missile) {
    Missiles.Entry row = infernoVisualRow(context, missile);
    if (missile == null || row == null
        || missile.skillId != com.riiablo.engine.server.skill.SkillId.FIRE_WALL
        || !"firewall".equalsIgnoreCase(row.Missile)
        || context == null || context.files() == null) return row;
    int distance = Math.abs(missile.fireWallVisualIndex);
    if (distance == 0) return row;
    String variant = distance <= 2 ? "firemedium" : "firesmall";
    Missiles.Entry alternate = context.files().Missiles.get(variant);
    return alternate != null ? alternate : row;
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
      Gdx.app.debug("MissileLoader", String.format(
          "[MISSILE_ANIM_LOAD] entity=%d missile=%s loaded=%s frames=%d dirs=%d "
              + "frame=%d dir=%d trans=%d blend=%d box=%s",
          entityId, entry.Missile, loaded != null, frames,
          loaded != null ? loaded.getNumDirections() : 0,
          loaded != null ? loaded.getFrame() : -1,
          loaded != null ? loaded.getDirection() : -1,
          entry.Trans, loadedBlend,
          loaded != null ? loaded.getBox() : null));
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

  /** DCC publication must not restart a missile whose simulation already advanced. */
  static void initializePlayback(Missile missile, Missiles.Entry entry,
      Animation animation, int initialFrame) {
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    if (NativeGroundFirePresentation.handles(missile)) {
      NativeGroundFirePresentation.initialize(missile, animation);
      return;
    }
    int start = Math.floorMod(initialFrame, frames);
    animation.setFrame(start);
    // CltDo5 above owns reference-style growth, phased looping and alpha fade.
    // Ordinary missiles, including reconstructed monster projectiles, retain
    // fractional native animation progress across asynchronous asset loading.
    if (!isGroundFire(entry) && missile.nativeFrame > 0) {
      animation.seek(start * animation.getFrameDuration()
          + missile.nativeFrame / 25f);
    }
  }

  /** Cold-loaded or reconstructed smoke starts at its actual simulation age. */
  static int poisonSmokeFrame(Missile missile, int frames) {
    if (frames <= 0) return 0;
    long frame = missile.missile.RandStart
        + (long) Math.max(0, missile.nativeFrame) * nativeFrameDelta(null, missile) / 256;
    return (int) Math.max(0, Math.min(frames - 1, frame));
  }

  static int nativeBlendMode(Missiles.Entry entry) {
    if (entry == null || entry.Trans != 1) return BlendMode.ID;
    // Restore the visually verified indexed-renderer policy. A native draw
    // mode number alone does not establish equivalence with this shader's
    // palette, tint and alpha handling. Do not replace it without GPU QA.
    if (isPoisonMissile(entry)) return BlendMode.SCREEN;
    // D2Client 1.10f 0x6FB5B011..25 selects draw mode 3 for Trans=1.
    // There is no ground-fire exception. Alpha-over makes each big flame
    // cover its neighbours instead of accumulating their bright fire core.
    return BlendMode.ADDITIVE;
  }

  static boolean isPoisonMissile(Missiles.Entry entry) {
    if (entry == null) return false;
    return "pois".equalsIgnoreCase(entry.EType) || "poison".equalsIgnoreCase(entry.EType)
        || containsPoison(entry.Missile) || containsPoison(entry.Skill);
  }

  private static boolean containsPoison(String value) {
    if (value == null) return false;
    String lower = value.toLowerCase(java.util.Locale.ROOT);
    return lower.contains("poison") || lower.contains("plague");
  }

}

