package com.riiablo.engine.client;


import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.codec.DC;
import com.riiablo.codec.DC6;
import com.riiablo.codec.DCC;
import com.riiablo.engine.Dirty;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.CofComponentDescriptors;
import com.riiablo.engine.client.component.CofDirtyComponents;
import com.riiablo.engine.client.component.CofLoadingComponents;
import com.riiablo.engine.client.component.CofWrapper;
import com.riiablo.engine.client.component.AnimationWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.event.CofChangeEvent;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.codec.Animation;

import net.mostlyoriginal.api.event.common.Subscribe;

@All({
    CofDirtyComponents.class, CofWrapper.class, CofReference.class, Class.class,
    CofComponents.class, CofComponentDescriptors.class
})
public class CofLayerLoader extends IteratingSystem {
  private static final String TAG = "CofLayerLoader";
  private static final boolean DEBUG        = !true;
  private static final boolean DEBUG_EVENTS = DEBUG && true;

  private final StringBuilder builder = new StringBuilder(64);

  protected ComponentMapper<Class> mClass;
  protected ComponentMapper<CofWrapper> mCofWrapper;
  protected ComponentMapper<CofReference> mCofReference;
  protected ComponentMapper<CofComponents> mCofComponents;
  protected ComponentMapper<CofDirtyComponents> mCofDirtyComponents;
  protected ComponentMapper<CofLoadingComponents> mCofLoadingComponents;
  protected ComponentMapper<CofComponentDescriptors> mCofComponentDescriptors;
  protected ComponentMapper<AnimationWrapper> mAnimationWrapper;
  protected ComponentMapper<AnimData> mAnimData;
  protected ComponentMapper<Casting> mCasting;

  @Override
  protected void process(int entityId) {}

  @Override
  protected void inserted(int entityId) {
    if (DEBUG_EVENTS) Gdx.app.debug(TAG, "inserted");
    int flags = mCofDirtyComponents.get(entityId).flags;
    int requiresReload = loadDcs(entityId, flags);
    mCofDirtyComponents.remove(entityId);
    if (requiresReload != Dirty.NONE) mCofLoadingComponents.create(entityId).flags |= requiresReload;
  }

  @Subscribe
  public void onCofChanged(CofChangeEvent event) {
    if (DEBUG_EVENTS) Gdx.app.debug(TAG, "onCofChanged");
    if (event instanceof ModeChangeEvent && ((ModeChangeEvent) event).restart) {
      // Repeated attacks use a forced same-mode update only to restart the
      // action. Keep the already resident layers and reset the frame instead
      // of tearing down DCC assets and showing a blank frame while reloading.
      AnimationWrapper wrapper = mAnimationWrapper.has(event.entityId)
          ? mAnimationWrapper.get(event.entityId) : null;
      Animation animation = wrapper == null ? null : wrapper.animation;
      if (animation != null && animation.getNumFramesPerDir() > 0) {
        int frame = 0;
        Casting casting = mCasting.has(event.entityId) ? mCasting.get(event.entityId) : null;
        if (casting != null && casting.strafeInitialized) {
          AnimData anim = mAnimData.get(event.entityId);
          int rollbackPercent = 50;
          com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(casting.skillId);
          if (skill != null && skill.Param != null && skill.Param.length > 5
              && skill.Param[5] > 0) rollbackPercent = skill.Param[5];
          int midpoint = Math.max(0,
              animation.getNumFramesPerDir() * (100 - rollbackPercent) / 100);
          int attackFrame = -1;
          if (anim != null && anim.keyframes != null) {
            for (int i = 0; i < anim.keyframes.length; i++) {
              if (anim.keyframes[i] == com.riiablo.engine.Engine.KEYFRAME_ATK) {
                attackFrame = i;
                break;
              }
            }
          }
          frame = attackFrame >= 0 ? Math.min(midpoint, Math.max(0, attackFrame - 1)) : midpoint;
          frame = Math.min(frame, animation.getNumFramesPerDir() - 1);
          Gdx.app.log(TAG, String.format(
              "[STRAFE_ANIM] phase=client_rollback entity=%d frame=%d attackFrame=%d rollbackPercent=%d",
              event.entityId, frame, attackFrame, rollbackPercent));
        }
        animation.setFrame(frame);
        animation.updateBox();
      }
      if (DEBUG_EVENTS) Gdx.app.debug(TAG, "restart animation without COF reload");
      return;
    }
    mCofDirtyComponents.create(event.entityId).flags |= Dirty.ALL;
  }

  private int loadDcs(int entityId, int flags) {
    int requiresReload = Dirty.NONE;

    Class.Type logicalType = mClass.get(entityId).type;
    CofReference reference = mCofReference.get(entityId);
    Class.Type type = reference.effectiveType(logicalType);
    String token = reference.effectiveToken();
    byte mode = reference.effectiveMode(logicalType);
    COF cof = mCofWrapper.get(entityId).cof;
    int[] component = mCofComponents.get(entityId).component;
    AssetDescriptor<? extends DC>[] descriptors = mCofComponentDescriptors.get(entityId).descriptors;

    // Object OP is an operation transition (for example the ten-frame shrine
    // activation animation), not a persistent idle animation.  Animation's
    // default mode is LOOP, which made a stale/missed OP snapshot visibly loop
    // forever even after the authoritative object state had settled.  Clamp
    // object operation animations to their final frame; the following ON/NU
    // mode change still replaces the COF normally.  Unit animations retain
    // their normal looping behavior.
    if (mAnimationWrapper.has(entityId)) {
      Animation animation = mAnimationWrapper.get(entityId).animation;
      if (logicalType == Class.Type.OBJ) {
        animation.setMode(mode == Engine.Object.MODE_OP
            ? Animation.Mode.CLAMP : Animation.Mode.LOOP);
      }
    }

    // data\global\monsters\FK\rh\FKRHFBLA11HS.dcc
    final int start = type.PATH.length() + 4; // start after token
    builder.setLength(0);
    builder
        .append(type.PATH).append('\\')
        .append(token).append('\\')
        .append("AA").append('\\')
        .append(token).append("AABBB").append(type.getMode(mode)).append("CCC").append('.');
    for (int l = 0, size = cof.getNumLayers(); l < size; l++) {
      COF.Layer layer = cof.getLayer(l);
      int c = layer.component;
      if (!Dirty.isDirty(flags, c)) continue;
      if (component[c] == CofComponents.COMPONENT_NIL) {
        requiresReload |= (1 << c);
        unload(c, descriptors);
        continue;
      } else if (component[c] == CofComponents.COMPONENT_NULL) {
        component[c] = CofComponents.COMPONENT_LIT;
      }

      String composite = Engine.getComposite(c);
      builder
          .replace(start     , start +  2, composite)
          .replace(start +  5, start +  7, composite)
          .replace(start +  7, start + 10, type.COMP[component[c]])
          .replace(start + 12, start + 15, layer.weaponClass);

      if (!isLocalPlayer(entityId)) unload(c, descriptors);
      AssetDescriptor<? extends DC> descriptor = descriptors[c];
      String path = builder.replace(start + 16, start + 19, DCC.EXT).toString();
      // Standalone summon rows such as Valkyrie ship one-frame COFs but only
      // a NU body DCC.  Their WL/GH/etc. COFs intentionally reuse that static
      // sprite; requiring a mode-specific DCC clears the only layer as soon
      // as the pet starts moving.  Try the native NU body only for one-frame
      // COFs, preserving strict mode lookup for animated monsters.
      if (!Riiablo.mpqs.contains(path) && cof.getNumFramesPerDir() <= 1
          && mode != Engine.Monster.MODE_NU) {
        String fallback = new StringBuilder(builder)
            .replace(start + 10, start + 12, type.getMode(Engine.Monster.MODE_NU))
            .replace(start + 16, start + 19, DCC.EXT)
            .toString();
        if (Riiablo.mpqs.contains(fallback)) {
          Gdx.app.debug(TAG, "Reusing static NU COF layer for " + path);
          path = fallback;
        }
      }
      if (Riiablo.mpqs.contains(path)) {
        descriptor = descriptors[c] = new AssetDescriptor<>(path, DCC.class);
      } else {
        path = builder.replace(start + 16, start + 19, DC6.EXT).toString();
        if (!Riiablo.mpqs.contains(path) && cof.getNumFramesPerDir() <= 1
            && mode != Engine.Monster.MODE_NU) {
          String fallback = new StringBuilder(builder)
              .replace(start + 10, start + 12, type.getMode(Engine.Monster.MODE_NU))
              .replace(start + 16, start + 19, DC6.EXT)
              .toString();
          if (Riiablo.mpqs.contains(fallback)) {
            Gdx.app.debug(TAG, "Reusing static NU COF layer for " + path);
            path = fallback;
          }
        }
        if (Riiablo.mpqs.contains(path)) {
          descriptor = descriptors[c] = new AssetDescriptor<>(path, DC6.class);
        } else {
          // Some MonStats/COF rows advertise a component for which the
          // installed MPQ has no DCC or DC6 (for example a vine's TR layer in
          // older 1.10 data).  Do not enqueue a descriptor for a nonexistent
          // file: DC6.loadFromFile would receive a null FileHandle and crash
          // the LWJGL thread.  The remaining COF layers can still render.
          unload(c, descriptors);
          Gdx.app.error(TAG, "Missing COF component; skipping layer "
              + Engine.getComposite(c) + " for " + path);
          continue;
        }
      }

      if (DEBUG) Gdx.app.log(TAG, "Loading[" + Engine.getComposite(c) + "] " + path);
      // AssetManager keeps a reference count per load call.  Every entity
      // needs its own reference, even when another entity (for example the
      // cached local player) already loaded the same DCC.  Skipping load when
      // isLoaded() is true lets a monster release the shared asset out from
      // under the player and leaves revived monsters with missing layers.
      Riiablo.assets.load(descriptor);
      requiresReload |= (1 << c);
    }

    return requiresReload;
  }

  void unload(int c, AssetDescriptor[] descriptors) {
    AssetDescriptor descriptor = descriptors[c];
    if (descriptor == null) return;
    descriptors[c] = null;
    releaseAsset(Riiablo.assets, descriptor);
    if (DEBUG) Gdx.app.debug(TAG, "Unloading[" + Engine.getComposite(c) + "] " + descriptor.fileName);
  }

  /** Idempotently releases a descriptor, including an asynchronously queued asset. */
  static void releaseAsset(com.badlogic.gdx.assets.AssetManager assets,
      AssetDescriptor descriptor) {
    if (descriptor == null) return;
    // A descriptor can outlive its AssetManager entry when another entity
    // releases the same shared DCC while this entity is still being refreshed.
    // AssetManager.unload throws for that stale state (and used to crash the
    // LWJGL application thread).  Treat release as idempotent: queued assets
    // are still present in contains() and are cancelled; already-removed
    // assets simply have no reference left to release.
    if (assets != null && assets.contains(descriptor.fileName)) {
      try {
        assets.unload(descriptor.fileName);
      } catch (RuntimeException ex) {
        // Asset loading is asynchronous; a completion/failure callback may
        // remove the entry between contains() and unload().  Do not let a
        // presentation resource race terminate the game loop.
        if (DEBUG_EVENTS && Gdx.app != null) {
          Gdx.app.debug(TAG, "Ignoring stale COF asset release " + descriptor.fileName, ex);
        }
      }
    }
  }

  private static boolean isLocalPlayer(int entityId) {
    return Riiablo.game != null && Riiablo.game.player == entityId;
  }
}
