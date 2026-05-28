package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.annotations.Exclude;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.riiablo.Riiablo;
import com.riiablo.engine.client.component.CofDescriptor;
import com.riiablo.engine.client.component.CofWrapper;
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
    
    Riiablo.assets.load(descriptor.descriptor);
    if (DEBUG) Gdx.app.debug(TAG, "Loading " + descriptor.descriptor.fileName);
    checkLoaded(event.entityId);
  }

  @Override
  protected void inserted(int entityId) {
  }

  private void checkLoaded(int entityId) {
    AssetDescriptor<COF> descriptor = mCofDescriptor.get(entityId).descriptor;
    if (!Riiablo.assets.isLoaded(descriptor)) return;
    COF cof = Riiablo.assets.get(descriptor);
    // D2MOD: COF can be null if file doesn't exist, system continues with null COF
    if (cof == null) {
      if (DEBUG) Gdx.app.debug(TAG, "COF is null for " + descriptor.fileName + ", skipping wrapper creation");
      return;  // Don't create wrapper if COF is null
    }
    mCofWrapper.create(entityId).cof = cof;
    if (DEBUG) Gdx.app.debug(TAG, "Loaded " + descriptor.fileName);
  }
}
