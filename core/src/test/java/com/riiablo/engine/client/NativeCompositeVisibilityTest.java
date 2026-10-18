package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.riiablo.Colors;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.*;
import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.client.component.*;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.component.Class;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.loader.COFLoader;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Real retail COF/DCC decoding and ECS; only GPU upload/submission is mocked. */
class NativeCompositeVisibilityTest extends RiiabloTest {
  @Test void shrineBodiesAndResurrectedFallenSubmitVisibleFramesAfterIdReuse() {
    AssetManager previous = Riiablo.assets;
    Colors colors = Riiablo.colors;
    Riiablo.colors = new Colors();
    AssetManager assets = Riiablo.assets = new AssetManager(Riiablo.mpqs, false);
    assets.setLoader(COF.class, new COFLoader(Riiablo.mpqs));
    assets.setLoader(DCC.class, new SynchronousAssetLoader<DCC, AssetLoaderParameters<DCC>>(Riiablo.mpqs) {
      public DCC load(AssetManager manager, String name, FileHandle file, AssetLoaderParameters<DCC> params) {
        return DCC.loadFromFile(file);
      }
      public Array<AssetDescriptor> getDependencies(String name, FileHandle file, AssetLoaderParameters<DCC> params) {
        return null;
      }
    });
    CofManager manager = new CofManager();
    World world = new World(new WorldConfigurationBuilder().with(
        new EventSystem(), manager, new CofUnloader(), new CofResolver(), new CofLoader(),
        new CofLayerLoader(), new CofLayerCacher()).build()
        .register("clientContext", ClientGameplayContext.fromGlobals()));
    try (MockedConstruction<Texture> textures = mockConstruction(Texture.class, (texture, context) -> {
      when(texture.getWidth()).thenReturn(256);
      when(texture.getHeight()).thenReturn(256);
      when(texture.getTextureObjectHandle()).thenReturn(1);
    })) {
      int monster = create(world, Class.Type.MON, "FA", Engine.Monster.MODE_NU);
      settle(world, assets);
      assertDraws(world, monster, "initial Fallen");
      Animation resident = world.getMapper(AnimationWrapper.class).get(monster).animation;
      int body = resident.getCOF().getLayer(0).component;
      String bodyPath = world.getMapper(CofComponentDescriptors.class).get(monster)
          .descriptors[body].fileName;
      int references = assets.getReferenceCount(bodyPath);
      resident.setLayer(body, null, false);
      settle(world, assets);
      assertNotNull(resident.getLayer(body), "recover a lost resident layer without a mode change");
      assertEquals(references, assets.getReferenceCount(bodyPath), "rebind must not acquire another reference");
      for (byte mode : new byte[] {Engine.Monster.MODE_DT, Engine.Monster.MODE_DD,
          Engine.Monster.MODE_S1, Engine.Monster.MODE_NU, Engine.Monster.MODE_A1, Engine.Monster.MODE_A2}) {
        manager.setMode(monster, mode);
        settle(world, assets);
        assertDraws(world, monster, "Fallen mode=" + mode);
      }
      world.delete(monster);
      world.process();
      int checked = 0;
      for (Objects.Entry row : Riiablo.files.objects) {
        if (!ClientEntityFactory.isShrine(row) || !row.Draw) continue;
        String token = Riiablo.files.ObjType.get(row.Id).Token;
        int id = create(world, Class.Type.OBJ, token, Engine.Object.MODE_NU);
        if (checked == 0) assertEquals(monster, id, "actual recycled monster entity id");
        world.getMapper(com.riiablo.engine.server.component.Object.class).create(id).base = row;
        settle(world, assets);
        assertDraws(world, id, "shrine object=" + row.Id + " token=" + token);
        world.delete(id);
        world.process();
        checked++;
      }
      assertTrue(checked > 0);
      System.out.println("[COMPOSITE_VISIBILITY] shrineBodies=" + checked + " fallenModes=7 recycled=true");
    } finally {
      world.dispose();
      assets.dispose();
      Riiablo.assets = previous;
      Riiablo.colors = colors;
    }
  }

  private static int create(World world, Class.Type type, String token, byte mode) {
    int id = world.create();
    world.getMapper(Class.class).create(id).type = type;
    world.getMapper(CofReference.class).create(id).set(token, mode);
    int[] components = world.getMapper(CofComponents.class).create(id).component;
    if (type == Class.Type.OBJ) {
      java.util.Arrays.fill(components, CofComponents.COMPONENT_NULL);
    } else {
      com.riiablo.codec.excel.MonStats.Entry monster = Riiablo.files.monstats.get("fallen1");
      com.riiablo.codec.excel.MonStats2.Entry detail = Riiablo.files.monstats2.get(monster.MonStatsEx);
      for (int c = 0; c < components.length; c++) {
        String values = detail.ComponentV[c];
        if (values != null && !values.isEmpty()) {
          components[c] = Riiablo.files.compcode.index(values.replace("\"", "").split(",")[0]);
        }
      }
    }
    world.getMapper(CofComponentDescriptors.class).create(id);
    world.getMapper(AnimationWrapper.class).create(id);
    world.getMapper(AnimData.class).create(id).speed = 256;
    return id;
  }

  private static void settle(World world, AssetManager assets) {
    for (int i = 0; i < 8; i++) {
      world.process();
      assets.finishLoading();
    }
  }

  private static void assertDraws(World world, int id, String description) {
    Animation animation = world.getMapper(AnimationWrapper.class).get(id).animation;
    assertNotNull(animation.getCOF(), description);
    PaletteIndexedBatch batch = mock(PaletteIndexedBatch.class);
    animation.draw(batch, 0, 0);
    assertTrue(animation.getDrawableLayerCount() > 0, description + " resident texture handle");
    verify(batch, atLeastOnce().description(description)).draw(any(TextureRegion.class), anyFloat(), anyFloat());
  }
}
