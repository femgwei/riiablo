package com.riiablo.server.d2gs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ItemInteractor;
import com.riiablo.engine.server.ItemManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.SerializationManager;
import com.riiablo.engine.server.ServerEntityFactory;
import com.riiablo.engine.server.WarpInteractor;
import com.riiablo.engine.server.NativePlayerTargetSlots;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.portal.TownPortalRegistry;
import com.riiablo.map.Map;
import com.riiablo.mpq.MPQFileHandleResolver;
import java.io.File;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Assume;
import org.junit.Test;

/** Real ServerEntityFactory + SerializationManager checkpoint round-trip. */
public class ServerEntityLifecycleTest {
  @BeforeClass public static void load110f() {
    String home = System.getProperty("d2Home");
    if (home == null || home.isEmpty()) home = System.getenv("D2_HOME");
    if (home == null || home.isEmpty()) home = "G:\\BaiduNetdiskDownload\\Diablo II 1.10F";
    Assume.assumeTrue("1.10f installation is required", new File(home, "d2data.mpq").isFile());
    Gdx.app = new HeadlessApplication(new ApplicationAdapter() {});
    Riiablo.home = Gdx.files.absolute(home);
    Riiablo.mpqs = new MPQFileHandleResolver();
    Riiablo.files = new Files();
  }

  @AfterClass public static void unload110f() {
    if (Gdx.app != null) Gdx.app.exit();
  }

  @Test public void realFactoryRestoresDeletedPlayerAndStableCheckpointDigest() {
    ServerEntityFactory factory = new ServerEntityFactory();
    SerializationManager serializer = new SerializationManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(new net.mostlyoriginal.api.event.common.EventSystem(), new ItemManager(),
            new ObjectInteractor(), new WarpInteractor(), new ItemInteractor(),
            factory, new NativePlayerTargetSlots(), new CofManager(), serializer)
        .build()
        .register("factory", factory)
        .register("map", new Map(0, 0))
        .register("townPortalRegistry", new TownPortalRegistry()));
    try {
      int entity = factory.createPlayer("CheckpointHero", Riiablo.AMAZON, 10f, 10f);
      world.process();
      Player player = world.getMapper(Player.class).get(entity);
      player.data.markProgressionCommitted();
      player.data.getItems().restoreEquipmentRevision(7L);
      D2gsCheckpointSnapshot checkpoint = D2gsCheckpointSnapshot.capture(world, serializer, 77L);
      world.getMapper(Position.class).get(entity).position.set(99f, 99f);
      world.delete(entity);
      world.process();

      ServerEntityLifecycle lifecycle = new ServerEntityLifecycle(world, factory, Riiablo.files);
      D2gsCheckpointSnapshot.RestoreResult result =
          checkpoint.restoreExisting(world, serializer, lifecycle);
      world.process();
      D2gsCheckpointSnapshot restored = D2gsCheckpointSnapshot.capture(world, serializer, 77L);

      assertTrue(result.success());
      assertEquals(java.util.Arrays.asList(entity), result.createdEntities);
      assertEquals(checkpoint.summary().canonical(), restored.summary().canonical());
      assertEquals(10f, world.getMapper(Position.class).get(entity).position.x, 0f);
      assertEquals(10f, world.getMapper(Position.class).get(entity).position.y, 0f);
    } finally {
      world.dispose();
    }
  }
}
