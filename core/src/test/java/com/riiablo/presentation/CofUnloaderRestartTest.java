package com.riiablo.presentation;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.presentation.component.CofDescriptor;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CofUnloaderRestartTest {
  @Test void restartRetainsOwnershipButModeChangeReleasesIt() {
    AssetManager saved = Riiablo.assets;
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    CofUnloader unloader = new CofUnloader();
    World world = new World(new WorldConfigurationBuilder().with(
        new EventSystem(), unloader).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int entity = world.create();
      AssetDescriptor<COF> descriptor = new AssetDescriptor<>("block.cof", COF.class);
      world.getMapper(CofDescriptor.class).create(entity).descriptor = descriptor;
      world.process();
      when(assets.contains(descriptor.fileName)).thenReturn(true);
      unloader.onCofChanged(ModeChangeEvent.obtain(entity, (byte) 9, true));
      verify(assets, never()).unload(anyString());
      assertSame(descriptor, world.getMapper(CofDescriptor.class).get(entity).descriptor);
      unloader.onCofChanged(ModeChangeEvent.obtain(entity, (byte) 1, false));
      verify(assets).unload(descriptor.fileName);
    } finally { world.dispose(); Riiablo.assets = saved; }
  }
}
