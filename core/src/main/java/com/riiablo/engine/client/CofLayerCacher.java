package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
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
    CofLoadingComponents.class, CofWrapper.class, CofComponents.class, AnimData.class
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
  protected ComponentMapper<CofComponentDescriptors> mCofComponentDescriptors;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<SummonedPet> mSummonedPet;
  protected ComponentMapper<com.riiablo.engine.server.component.Object> mObject;
  protected ComponentMapper<CofReference> mCofReference;

  protected CofManager cofs;

  @Override
  protected void process(int entityId) {
    // Animation speed is authoritative in AnimData.  Repeated Strafe actions
    // update override for each attack cycle (including weapon IAS); keep the
    // already-loaded client animation in lockstep instead of applying the
    // rate only when a COF is first loaded.
    AnimationWrapper wrapper = mAnimationWrapper.has(entityId)
        ? mAnimationWrapper.get(entityId) : null;
    AnimData currentAnim = mAnimData.get(entityId);
    if (wrapper != null && wrapper.animation != null && currentAnim != null) {
      int expectedRate = currentAnim.override >= 0 ? currentAnim.override : currentAnim.speed;
      if (expectedRate > 0 && wrapper.animation.getFrameDelta() != expectedRate) {
        wrapper.animation.setFrameDelta(expectedRate);
      }
    }
    CofLoadingComponents loading = mCofLoadingComponents.get(entityId);
    // A mode/COF change can remove the transient loading component while this
    // subscription is being iterated (notably during the initial town
    // presentation pass).  Artemis may still hand the stale entity id to
    // this system for the current pass; do not turn that benign race into a
    // LWJGL-thread crash that aborts the game before summons are presented.
    if (loading == null) return;
    loading.flags = cacheDcs(entityId, loading.flags);
    if (loading.flags == Dirty.NONE) mCofLoadingComponents.remove(entityId);
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
        int nativeFrameCount = CofLayerLoader.objectAnimationFrameCount(
            object.base, mode, animation.getNumFramesPerDir());
        animation.setFrameRange(0, nativeFrameCount);
        animation.setMode(CofLayerLoader.objectAnimationMode(object.base, mode));
        animation.setFrame(CofLayerLoader.objectAnimationStartFrame(
            object.base, mode, nativeFrameCount));
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
      if (animData.override >= 0) {
        animation.setFrameDelta(animData.override);
      } else {
        animation.setFrameDelta(animData.speed);
      }
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
      if (Riiablo.assets.isLoaded(descriptor)) {
        flags &= ~flag;
        alteredLayers |= flag;
        if (DEBUG) Gdx.app.debug(TAG, "Loaded[" + Engine.getComposite(c) + "] " + descriptor.fileName);
        DC dc = Riiablo.assets.get(descriptor);
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
    return flags;
  }
}
