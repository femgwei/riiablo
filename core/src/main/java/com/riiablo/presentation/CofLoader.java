package com.riiablo.presentation;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.loaders.AssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.riiablo.presentation.component.CofDescriptor;
import com.riiablo.presentation.component.CofWrapper;
import com.riiablo.engine.server.event.CofChangeEvent;
import com.riiablo.codec.COF;

import net.mostlyoriginal.api.event.common.Subscribe;

@All(CofDescriptor.class)
@Exclude(CofWrapper.class)
public class CofLoader extends IteratingSystem {
  private static final String TAG = "CofLoader";
  private static final boolean DEBUG        = !true;
  private static final boolean DEBUG_EVENTS = DEBUG && true;

  protected ComponentMapper<CofWrapper> mCofWrapper;
  protected ComponentMapper<CofDescriptor> mCofDescriptor;
  @com.artemis.annotations.Wire(name = "clientContext")
  protected ClientGameplayContext clientContext ;

  @Override
  protected void process(int entityId) {
    checkLoaded(entityId);
  }

  @Subscribe
  public void onCofChanged(CofChangeEvent event) {
    if (DEBUG_EVENTS) Gdx.app.debug(TAG, "inserted");
    
    CofDescriptor descriptor = mCofDescriptor.get(event.entityId);
    if (descriptor == null) {
      if (DEBUG) Gdx.app.debug(TAG, "CofDescriptor not found for entity " + event.entityId);
      return;
    }

    // A null COF is not a completed asynchronous load in LibGDX: it keeps
    // the task at the head of the global queue forever. Optional object
    // modes (e.g. boOPHTH in 1.10f) must never enter that queue. Keep the
    // resident Animation until a later, available mode replaces it.
    if (!isAvailable(clientContext, descriptor.descriptor)) {
      if (Gdx.app != null) Gdx.app.error(TAG,
          "[COF_MISSING] entity=" + event.entityId + " path="
              + descriptor.descriptor.fileName + " action=skip_load");
      mCofDescriptor.remove(event.entityId);
      return;
    }

    clientContext.assets().load(descriptor.descriptor);
    if (DEBUG) Gdx.app.debug(TAG, "Loading " + descriptor.descriptor.fileName);
    checkLoaded(event.entityId);
  }

  @Override
  protected void inserted(int entityId) {
  }

  static boolean isAvailable(ClientGameplayContext context, AssetDescriptor<COF> descriptor) {
    if (context == null || context.assets() == null) return false;
    AssetLoader<COF, ?> loader = context.assets().getLoader(COF.class, descriptor.fileName);
    // Leave missing-loader validation to AssetManager.load itself.
    if (loader == null) return true;
    FileHandle file = descriptor.file != null ? descriptor.file : loader.resolve(descriptor.fileName);
    return file != null && file.exists();
  }

  private void checkLoaded(int entityId) {
    AssetDescriptor<COF> descriptor = mCofDescriptor.get(entityId).descriptor;
    if (!clientContext.assets().isLoaded(descriptor)) return;
    COF cof = clientContext.assets().get(descriptor);
    // D2MOD: COF can be null if file doesn't exist, system continues with null COF
    if (cof == null) {
      if (DEBUG) Gdx.app.debug(TAG, "COF is null for " + descriptor.fileName + ", skipping wrapper creation");
      return;  // Don't create wrapper if COF is null
    }
    mCofWrapper.create(entityId).cof = cof;
    if (DEBUG) Gdx.app.debug(TAG, "Loaded " + descriptor.fileName);
  }
}

