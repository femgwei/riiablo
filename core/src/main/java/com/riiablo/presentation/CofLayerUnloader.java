package com.riiablo.presentation;

import com.artemis.BaseEntitySystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.riiablo.codec.DC;
import com.riiablo.presentation.component.CofComponentDescriptors;

@All(CofComponentDescriptors.class)
public class CofLayerUnloader extends BaseEntitySystem {
  protected ComponentMapper<CofComponentDescriptors> mCofComponentDescriptors;

  private CofLayerLoader layerLoader;

  @Override
  protected void initialize() {
    world.getInvocationStrategy().setEnabled(this, false);
  }

  @Override
  protected void processSystem() {}

  @Override
  protected void dispose() {
    IntBag entities = getEntityIds();
    for (int i = 0, size = entities.size(); i < size; i++) {
      int id = entities.get(i);
      AssetDescriptor<? extends DC>[] descriptors = mCofComponentDescriptors.get(id).descriptors;
      for (int c = 0; c < descriptors.length; c++) {
        layerLoader.unload(c, descriptors);
      }
      for (AssetDescriptor<? extends DC> retired : mCofComponentDescriptors.get(id).retired) {
        CofLayerLoader.releaseAsset(layerLoader.clientContext.assets(), retired);
      }
      mCofComponentDescriptors.get(id).retired.clear();
    }
  }
}
