package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
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
import com.riiablo.loader.COFLoader;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class CofModeTransitionTest {
  @Test void missingChestCofDoesNotBlockPlayerIdleLoad() {
    missingCofDoesNotBlockPlayer(false);
  }

  @Test void missingBlockPrewarmDoesNotBlockPlayerIdleLoad() {
    missingCofDoesNotBlockPlayer(true);
  }

  private void missingCofDoesNotBlockPlayer(boolean missingBlock) {
    com.badlogic.gdx.assets.loaders.FileHandleResolver resolver = name -> {
      if (name.contains("boOP") || (missingBlock && name.contains("AMBL"))) return null;
      return new FileHandle(name) {
        @Override public boolean exists() { return true; }
        @Override public java.io.InputStream read() {
          return new java.io.ByteArrayInputStream(cofBytes());
        }
      };
    };
    missingCofDoesNotBlockPlayer(resolver, missingBlock);
  }

  static void missingCofDoesNotBlockPlayer(
      com.badlogic.gdx.assets.loaders.FileHandleResolver resolver, boolean missingBlock) {
    AssetManager saved = Riiablo.assets;
    Colors savedColors = Riiablo.colors;
    Riiablo.colors = new Colors();
    AssetManager assets = Riiablo.assets = new AssetManager(resolver);
    assets.setLoader(COF.class, new COFLoader(resolver));
    CofManager cofs = new CofManager();
    World world = new World(new WorldConfigurationBuilder().with(
        new EventSystem(), cofs, new CofUnloader(), new CofResolver(), new CofLoader(),
        new CofLayerLoader(), new CofLayerCacher()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int player = createComposite(world, Class.Type.PLR, "AM", Engine.Player.MODE_RN);
      if (missingBlock) world.getSystem(CofResolver.class).inserted(player);
      assertTrue(pump(world, assets), "initial running COF must load");
      COF run = world.getMapper(AnimationWrapper.class).get(player).animation.getCOF();
      assertNotNull(run);
      if (!missingBlock) {
        int chest = createComposite(world, Class.Type.OBJ, "bo", Engine.Object.MODE_NU);
        assertTrue(pump(world, assets));
        cofs.setMode(chest, Engine.Object.MODE_OP);
      }
      cofs.setMode(player, Engine.Player.MODE_TN);
      assertTrue(pump(world, assets), "missing optional COF must not stall the global asset queue");
      COF idle = assets.get("data/global/CHARS/AM/cof/AMTNHTH.cof", COF.class);
      assertNotSame(run, idle);
      assertSame(idle, world.getMapper(AnimationWrapper.class).get(player).animation.getCOF());
      // Repeat after the missing chest transition, as in the user's log.
      cofs.setMode(player, Engine.Player.MODE_RN);
      assertTrue(pump(world, assets));
      assertSame(assets.get("data/global/CHARS/AM/cof/AMRNHTH.cof", COF.class),
          world.getMapper(AnimationWrapper.class).get(player).animation.getCOF());
      cofs.setMode(player, Engine.Player.MODE_TN);
      assertTrue(pump(world, assets));
      assertSame(assets.get("data/global/CHARS/AM/cof/AMTNHTH.cof", COF.class),
          world.getMapper(AnimationWrapper.class).get(player).animation.getCOF());
    } finally {
      try {
        world.dispose();
        assets.dispose();
      } finally {
        Riiablo.assets = saved;
        Riiablo.colors = savedColors;
      }
    }
  }

  private static int createComposite(World world, Class.Type type, String token, byte mode) {
    int id = world.create();
    world.getMapper(Class.class).create(id).type = type;
    world.getMapper(CofReference.class).create(id).set(token, mode);
    world.getMapper(CofComponents.class).create(id);
    world.getMapper(CofComponentDescriptors.class).create(id);
    world.getMapper(AnimationWrapper.class).create(id);
    world.getMapper(AnimData.class).create(id).speed = 256;
    return id;
  }

  private static boolean pump(World world, AssetManager assets) {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
    do {
      assets.update();
      world.process();
      if (assets.getQueuedAssets() == 0) {
        world.process();
        return true;
      }
      Thread.yield();
    } while (System.nanoTime() < deadline);
    return false;
  }

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
        new CofLayerLoader(), new CofLayerCacher()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
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
        new CofLayerLoader(), new CofLayerCacher()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
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
    return COF.loadFromStream(new java.io.ByteArrayInputStream(cofBytes()));
  }

  private static byte[] cofBytes() {
    java.nio.ByteBuffer bytes = java.nio.ByteBuffer.allocate(28 + 9 + 8 + 16 * 8)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN);
    bytes.put((byte) 1).put((byte) 8).put((byte) 16).put((byte) 20);
    bytes.position(24);
    bytes.putShort((short) 256).putShort((short) 0);
    bytes.position(33);
    bytes.put(new byte[] {'H', 'T', 'H', 0});
    return bytes.array();
  }
}
