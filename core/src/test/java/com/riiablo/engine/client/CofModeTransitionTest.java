package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.codec.COF;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.*;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.AnimData;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.CofComponents;
import com.riiablo.engine.server.component.CofReference;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class CofModeTransitionTest {
  @Test void stopDuringPendingWalkLoadReplacesTheResidentRunCof() {
    AssetManager saved = Riiablo.assets;
    Colors savedColors = Riiablo.colors;
    Riiablo.colors = new Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    COF run = cof();
    COF idle = cof();
    java.util.Set<String> pending = new java.util.HashSet<>();
    when(assets.isLoaded(any(AssetDescriptor.class))).thenAnswer(call ->
        !pending.contains(((AssetDescriptor<?>) call.getArgument(0)).fileName));
    when(assets.get(any(AssetDescriptor.class))).thenAnswer(call -> {
      AssetDescriptor<?> descriptor = call.getArgument(0);
      return descriptor.fileName.contains("RN") ? run : idle;
    });
    CofManager cofs = new CofManager();
    World world = new World(new WorldConfigurationBuilder().with(
        new EventSystem(), cofs, new CofUnloader(), new CofResolver(), new CofLoader(),
        new CofLayerLoader(), new CofLayerCacher()).build());
    try {
      int id = world.create();
      world.getMapper(Class.class).create(id).type = Class.Type.PLR;
      world.getMapper(CofReference.class).create(id).set("AM", Engine.Player.MODE_RN);
      world.getMapper(CofComponents.class).create(id);
      world.getMapper(CofComponentDescriptors.class).create(id);
      world.getMapper(AnimationWrapper.class).create(id);
      world.getMapper(AnimData.class).create(id).speed = 256;
      world.process();
      assertSame(run, world.getMapper(AnimationWrapper.class).get(id).animation.getCOF());
      pending.add("data\\global\\CHARS\\AM\\cof\\AMWLHTH.cof");
      cofs.setMode(id, Engine.Player.MODE_WL);
      world.process();
      cofs.setMode(id, Engine.Player.MODE_NU);
      for (int i = 0; i < 5; i++) world.process();
      assertSame(idle, world.getMapper(AnimationWrapper.class).get(id).animation.getCOF());
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      Riiablo.colors = savedColors;
    }
  }

  @Test void residentRunCofIsReplacedWhenPlayerStopsRepeatedly() {
    AssetManager saved = Riiablo.assets;
    Colors savedColors = Riiablo.colors;
    Riiablo.colors = new Colors();
    AssetManager assets = Riiablo.assets = mock(AssetManager.class);
    COF run = cof();
    COF idle = cof();
    when(assets.isLoaded(any(AssetDescriptor.class))).thenReturn(true);
    when(assets.get(any(AssetDescriptor.class))).thenAnswer(call -> {
      AssetDescriptor<?> descriptor = call.getArgument(0);
      return descriptor.fileName.contains("RN") ? run : idle;
    });
    CofManager cofs = new CofManager();
    World world = new World(new WorldConfigurationBuilder().with(
        new EventSystem(), cofs, new CofUnloader(), new CofResolver(), new CofLoader(),
        new CofLayerLoader(), new CofLayerCacher()).build());
    try {
      int id = world.create();
      world.getMapper(Class.class).create(id).type = Class.Type.PLR;
      world.getMapper(CofReference.class).create(id).set("AM", Engine.Player.MODE_NU);
      world.getMapper(CofComponents.class).create(id);
      world.getMapper(CofComponentDescriptors.class).create(id);
      world.getMapper(AnimationWrapper.class).create(id);
      world.getMapper(AnimData.class).create(id).speed = 256;
      world.process();
      for (int i = 0; i < 5; i++) {
        cofs.setMode(id, Engine.Player.MODE_RN);
        world.process();
        world.process();
        assertSame(run, world.getMapper(AnimationWrapper.class).get(id).animation.getCOF(), "run " + i);
        cofs.setMode(id, Engine.Player.MODE_NU);
        world.process();
        world.process();
        assertSame(idle, world.getMapper(AnimationWrapper.class).get(id).animation.getCOF(), "idle " + i);
      }
      // Reproduce a missed presentation refresh: the unit and resolved COF
      // already say idle, but the transient loading marker is gone and the
      // renderer still owns its previous running animation.
      world.getMapper(AnimationWrapper.class).get(id).animation.setCOF(run);
      assertFalse(world.getMapper(CofLoadingComponents.class).has(id));
      for (int i = 0; i < 5; i++) world.process();
      assertSame(idle, world.getMapper(AnimationWrapper.class).get(id).animation.getCOF(),
          "resident running animation must recover without another movement event");
    } finally {
      world.dispose();
      Riiablo.assets = saved;
      Riiablo.colors = savedColors;
    }
  }

  private static COF cof() {
    java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(28 + 9 + 8 + 16 * 8)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    bytes.put((byte) 1).put((byte) 8).put((byte) 16).put((byte) 20);
    bytes.position(24);
    bytes.putShort((short) 256).putShort((short) 0);
    bytes.position(33);
    bytes.put(new byte[] {'H', 'T', 'H', 0});
    return COF.loadFromStream(new java.io.ByteArrayInputStream(bytes.array()));
  }
}
