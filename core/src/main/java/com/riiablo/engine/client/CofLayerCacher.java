package com.riiablo.engine.client;

import com.riiablo.presentation.ClientGameplayContext;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.COF;
import com.riiablo.codec.DC;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Dirty;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.client.component.CofComponentDescriptors;
import com.riiablo.engine.client.component.CofLoadingComponents;
import com.riiablo.engine.shared.component.CofDirtyComponents;
import com.riiablo.engine.client.component.CofWrapper;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.ai.Npc;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.event.CofChangeEvent;

import net.mostlyoriginal.api.event.common.Subscribe;

@All({
    CofWrapper.class, CofComponents.class, AnimData.class,
    AnimationWrapper.class, CofComponentDescriptors.class
})
public class CofLayerCacher extends IteratingSystem {
  private static final String TAG = "CofLayerCacher";
  private static final boolean DEBUG        = !true;
  private static final boolean DEBUG_EVENTS = DEBUG && true;

  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<CofWrapper> mCofWrapper;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<CofLoadingComponents> mCofLoadingComponents;
  protected ComponentMapper<CofDirtyComponents> mCofDirtyComponents;
  protected ComponentMapper<CofComponentDescriptors> mCofComponentDescriptors;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<CofReference> mCofReference;

  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext = ClientGameplayContext.fromGlobals();

  protected CofManager cofs;

  @Override
  protected void process(int entityId) {
    // Live animation rates are handled by AnimationStepper. Keep this
    // subscription alive after loading so missed COF changes can recover.
    CofLoadingComponents loading = mCofLoadingComponents.get(entityId);
    // Never bind an old mode's pending DCCs to a freshly resolved COF before
    // CofLayerLoader has published that COF's layer descriptors.
    if (mCofDirtyComponents.has(entityId)) return;
    if (loading == null) {
      // Loading is transient, but the renderer's Animation is persistent.
      // Recover a missed mode refresh even after the last loading marker was
      // consumed; otherwise an idle unit can loop its old RN layers forever.
      Animation animation = mAnimationWrapper.get(entityId).animation;
      if (animation.getCOF() != mCofWrapper.get(entityId).cof) {
        mCofDirtyComponents.create(entityId).flags |= Dirty.ALL;
        if (mPlayer.has(entityId) && Gdx.app != null) {
          CofReference reference = mCofReference.get(entityId);
          Gdx.app.log(TAG, "[PLAYER_ANIM] phase=recover entity=" + entityId
              + " mode=" + (reference == null ? -1 : reference.mode));
        }
      } else {
        // Matching COFs alone do not guarantee a complete resident sprite.
        // A lost/replaced transient layer can otherwise remain invisible
        // indefinitely on an idle shrine, or across a retained corpse revival.
        int missing = 0;
        CofComponentDescriptors owned = mCofComponentDescriptors.get(entityId);
        COF cof = animation.getCOF();
        if (cof == null) return;
        for (int l = 0; l < cof.getNumLayers(); l++) {
          int c = cof.getLayer(l).component;
          AssetDescriptor<? extends DC> descriptor = owned.descriptors[c];
          if (descriptor == null || mCofComponents.get(entityId).component[c]
              == CofComponents.COMPONENT_NIL) continue;
          Animation.Layer layer = animation.getLayer(c);
          if (clientContext.assets() != null && clientContext.assets().isLoaded(descriptor)
              && (layer == null || layer.getDC() != clientContext.assets().get(descriptor))) {
            missing |= 1 << c;
          }
        }
        if (missing != Dirty.NONE) {
          // Rebind this entity's already-owned assets. Do not acquire another
          // reference or reload optional layers absent from the retail MPQ.
          mCofLoadingComponents.create(entityId).flags |= missing;
          if (Gdx.app != null) Gdx.app.log(TAG,
              "[COMPOSITE_RECOVER] entity=" + entityId + " reason=resident_layer_mismatch flags="
                  + Dirty.toString(missing));
        }
      }
      return;
    }
    loading.flags = cacheDcs(entityId, loading.flags);
    if (loading.flags == Dirty.NONE) {
      mCofLoadingComponents.remove(entityId);
      if (mPlayer.has(entityId) && Gdx.app != null) {
        CofReference reference = mCofReference.get(entityId);
        StringBuilder paths = new StringBuilder();
        for (AssetDescriptor<? extends DC> descriptor
            : mCofComponentDescriptors.get(entityId).descriptors) {
          if (descriptor == null) continue;
          if (paths.length() > 0) paths.append('|');
          paths.append(descriptor.fileName);
        }
        Gdx.app.log(TAG, "[PLAYER_ANIM] phase=layers_ready entity=" + entityId
            + " mode=" + (reference == null ? -1 : reference.mode)
            + " layers=" + paths);
      }
    }
  }

  @Subscribe
  public void onCofChanged(CofChangeEvent event) {
    if (DEBUG_EVENTS) Gdx.app.debug(TAG, "onCofChanged");
  }

  private int cacheDcs(int entityId, int flags) {
    COF cof = mCofWrapper.get(entityId).cof;
    int[] component = mCofComponents.get(entityId).component;
    AssetDescriptor<? extends DC>[] descriptors = mCofComponentDescriptors.get(entityId).descriptors;
    Animation animation = mAnimationWrapper.get(entityId).animation;

    // Publish a complete composite, never a new COF with a mixture of old and
    // pending layers. In particular, multi-layer monster attacks can otherwise
    // flash while their DCCs complete on different render frames.
    for (int l = 0; l < cof.getNumLayers(); l++) {
      int c = cof.getLayer(l).component;
      if (!Dirty.isDirty(flags, c) || component[c] == CofComponents.COMPONENT_NIL) continue;
      AssetDescriptor<? extends DC> descriptor = descriptors[c];
      if (descriptor != null && (clientContext.assets() == null
          || !clientContext.assets().isLoaded(descriptor))) return flags;
    }

//    if (cof == null) return;
    // FIXME: logic here needs to be looked into -- should below operations be performed when cof didn't change?
    boolean newCof = animation.setCOF(cof);
    if (mObject.has(entityId)) {
      com.riiablo.engine.server.component.Object object = mObject.get(entityId);
      byte mode = mCofReference.get(entityId).mode;
      if (mode != com.riiablo.engine.Engine.Object.MODE_OP && object != null
          && object.mode >= com.riiablo.engine.Engine.Object.MODE_NU
          && object.mode <= com.riiablo.engine.Engine.Object.MODE_S5) {
        mode = object.mode;
      }
      if (object != null && object.base != null) {
        configureObjectAnimation(animation, object.base, mode);
      } else {
        animation.setFrame(CofLayerLoader.objectAnimationStartFrame(
            null, mode, animation.getNumFramesPerDir()));
      }
    }
    if (newCof && mSummonedPet.has(entityId)) {
      Gdx.app.log(TAG, String.format(
          "[SUMMON_PRESENTATION] phase=cof_ready entity=%d frames=%d layers=%d",
          entityId,
          animation.getNumFramesPerDir(), cof.getNumLayers()));
    }
    if (newCof) {
      AnimData animData = mAnimData.get(entityId);
      animation.setFrameDelta(animationRate(entityId, animData));
//      if (animData.factor > 0) {
//        System.out.println("setFrameDelta 0 " + animData.factor);
//        animation.setFrameDelta(animData.factor);
//      } else if (animData.speed == 0) {
//        System.out.println("setFrameDelta 1 " + cof.getAnimRate());
//        animation.setFrameDelta(cof.getAnimRate());
//      } else {
//        System.out.println("setFrameDelta 2 " + animData.factor);
//        animation.setFrameDelta(animData.speed);
//      }
//      if (mAnimData.get(entityId).speed == 0) {
//        Vector2 velocity = world.getEntity(entityId).getComponent(Velocity.class).velocity;
//        animation.setFrameDelta((int)(16 * velocity.len()));
//      }
    }
//    if (newCof && cofComponent.speed != CofComponent.SPEED_NULL) {
//      anim.setFrameDelta(cofComponent.speed);
//    }

    int alteredLayers = Dirty.NONE;
    for (int l = 0, size = cof.getNumLayers(); l < size; l++) {
      COF.Layer layer = cof.getLayer(l);
      int c = layer.component;
      if (!Dirty.isDirty(flags, c)) continue;
      int flag = (1 << c);
      if (component[c] == CofComponents.COMPONENT_NIL) {
        flags &= ~flag;
        alteredLayers |= flag;
        animation.setLayer(layer, null, false);
        continue;
      }

      AssetDescriptor<? extends DC> descriptor = descriptors[c];
      if (descriptor == null) {
        flags &= ~flag;
        alteredLayers |= flag;
        animation.setLayer(layer, null, false);
      } else if (clientContext.assets() != null && clientContext.assets().isLoaded(descriptor)) {
        flags &= ~flag;
        alteredLayers |= flag;
        if (DEBUG) Gdx.app.debug(TAG, "Loaded[" + Engine.getComposite(c) + "] " + descriptor.fileName);
        DC dc = clientContext.assets().get(descriptor);
        // D2 keeps the active unit graphics ready for all discrete facings.
        // Lazy GL texture creation during a player direction change stalls or
        // briefly blanks a composite layer, which is visible as flashing.
        // Limit eager loading to the local player to keep monster memory use
        // bounded.
        if (mPlayer.has(entityId)) dc.loadDirections();
        // Town NPCs are visible immediately after the loading screen and can
        // start walking on the first simulation tick (Warriv is the common
        // case).  Loading only the current facing leaves the first direction
        // change to create/upload DCC textures on the render thread, which is
        // perceived as the NPC appearing late.  NPC composites are few and
        // persistent for the whole town, so eagerly materialize every facing
        // while the initial presentation pass is still running.  Monsters
        // remain lazy to avoid multiplying their texture footprint.
        if (mAIWrapper.has(entityId) && mAIWrapper.get(entityId).ai instanceof Npc) {
          dc.loadDirections();
        }
        animation.setLayer(layer, dc, false);
      }
    }

    if (alteredLayers != Dirty.NONE) {
      animation.updateBox();
      if (mObject.has(entityId)) {
        com.riiablo.engine.server.component.Object object = mObject.get(entityId);
        // Native door interaction is anchored to Objects.txt's fixed
        // selection rectangle, not to the visible pixels of the current DCC
        // frame. ON door frames can be transparent, so retaining Left/Top /
        // Width/Height keeps the original hover label and click target at the
        // doorway after opening without making the whole door selectable.
        if (object != null && object.base != null && object.base.IsDoor) {
          Objects.Entry base = object.base;
          if (base.Width > 0 && base.Height > 0) {
            animation.getBox().asBox(base.Left, base.Top, base.Width, base.Height);
          } else {
            animation.getBox().set(cof.getBox());
          }
        }
      }
      cofs.updateAlpha(entityId, alteredLayers);
      cofs.updateTransform(entityId, alteredLayers);
    }
    // Keep a compact presentation trace for doors.  Their gameplay state is
    // authoritative in Object.mode, while the visual state is assembled from
    // a COF plus one DCC/DC6 per layer; logging both sides makes a wrong frame
    // or facing immediately distinguishable from a collision bug.
    if (newCof && mObject.has(entityId)) {
      com.riiablo.engine.server.component.Object object = mObject.get(entityId);
      Objects.Entry base = object == null ? null : object.base;
      if (base != null && base.IsDoor) {
        CofReference reference = mCofReference.get(entityId);
        byte mode = reference.mode;
        if (mode != Engine.Object.MODE_OP && object.mode >= Engine.Object.MODE_NU
            && object.mode <= Engine.Object.MODE_S5) {
          mode = object.mode;
        }
        StringBuilder paths = new StringBuilder();
        for (AssetDescriptor<? extends DC> descriptor : descriptors) {
          if (descriptor != null) {
            if (paths.length() > 0) paths.append('|');
            paths.append(descriptor.fileName);
          }
        }
        int startFrame = CofLayerLoader.objectAnimationStartFrame(
            base, mode, animation.getNumFramesPerDir());
        Gdx.app.log(TAG, String.format(
            "[DOOR_PRESENTATION] entity=%d objectId=%d token=%s orientation=%d "
                + "objectMode=%d cofMode=%d cof=%s directions=%d frames=%d "
                + "nativeFrames=%d start=%d animationDirection=%d animationFrame=%d animationMode=%s "
                + "cycle=%s dcc=%s",
            entityId, base.Id, base.Token, base.Orientation, object.mode, reference.mode,
            reference.effectiveToken() + com.riiablo.engine.server.component.Class.Type.OBJ
                .getMode(mode) + Engine.getWClass(reference.effectiveWClass()),
            cof.getNumDirections(), cof.getNumFramesPerDir(),
            CofLayerLoader.objectAnimationFrameCount(base, mode, cof.getNumFramesPerDir()),
            startFrame,
            animation.getDirection(), animation.getFrame(), animation.getMode(),
            base.CycleAnim != null && mode >= 0 && mode < base.CycleAnim.length
                ? base.CycleAnim[mode] : false,
            paths));
      }
    }
    if (DEBUG) Gdx.app.debug(TAG, "Remaining layers: " + Dirty.toString(flags));
    if (flags == Dirty.NONE) {
      CofComponentDescriptors owned = mCofComponentDescriptors.get(entityId);
      for (AssetDescriptor<? extends DC> retired : owned.retired) {
        CofLayerLoader.releaseAsset(clientContext.assets(), retired);
      }
      owned.retired.clear();
    }
    return flags;
  }

  static void configureObjectAnimation(Animation animation, Objects.Entry base, byte mode) {
    int frames = animation.getNumFramesPerDir();
    if (frames <= 0) return;
    // Objects.txt Start is an offset into the sprite, not into FrameCnt.
    // Barrel/urn ON uses one frame at Start2=9/8 in a shared 10/9-frame COF.
    int start = CofLayerLoader.objectAnimationStartFrame(base, mode, frames);
    int count = CofLayerLoader.objectAnimationFrameCount(base, mode, frames);
    animation.setFrameRange(start, Math.min(frames, start + count));
    animation.setMode(CofLayerLoader.objectAnimationMode(base, mode));
    animation.restart();
  }

  private int animationRate(int entityId, AnimData animData) {
    int fallback = animData.override >= 0 ? animData.override : animData.speed;
    if (!mObject.has(entityId)) return fallback;
    com.riiablo.engine.server.component.Object object = mObject.get(entityId);
    CofReference reference = mCofReference.get(entityId);
    if (object == null || object.base == null || reference == null) return fallback;
    byte mode = reference.mode;
    if (mode != Engine.Object.MODE_OP && object.mode >= Engine.Object.MODE_NU
        && object.mode <= Engine.Object.MODE_S5) {
      mode = object.mode;
    }
    return CofLayerLoader.objectAnimationRate(object.base, mode, fallback, entityId);
  }
}

