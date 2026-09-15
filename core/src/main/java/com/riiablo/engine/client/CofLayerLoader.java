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
        animation.setFrame(0);
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
      if (Riiablo.mpqs.contains(path)) {
        descriptor = descriptors[c] = new AssetDescriptor<>(path, DCC.class);
      } else {
        path = builder.replace(start + 16, start + 19, DC6.EXT).toString();
        assert Riiablo.mpqs.contains(path) : "Failed to locate " + path + " after looking for DCC and DC6";
        descriptor = descriptors[c] = new AssetDescriptor<>(path, DC6.class);
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
