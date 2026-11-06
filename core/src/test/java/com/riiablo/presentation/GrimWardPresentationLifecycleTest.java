package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.MissileImpactEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrimWardPresentationLifecycleTest extends RiiabloTest {
  @Test void moltenEmergeReusesAuthoritativeRollingChild() {
    try (Fixture f = new Fixture()) {
      Missiles.Entry emerge = Riiablo.files.Missiles.get("moltenboulderemerge");
      assertEquals(53, emerge.pCltHitFunc);
      assertEquals(48, emerge.pSrvHitFunc);
      f.impact(emerge);
      assertTrue(f.created.isEmpty());
      Missiles.Entry rolling = Riiablo.files.Missiles.get("moltenboulder");
      assertEquals(52, rolling.pCltHitFunc);
      f.impact(rolling);
      assertFalse(f.created.isEmpty(), "rolling boulder still emits its client explosion");
    }
  }

  @Test void authoritativeWardReplacesStartAndClientStillCreatesStopForEachSize() {
    try (Fixture f = new Fixture()) {
      for (String size : new String[] {"small", "medium", "large"}) {
        String name = "grimward" + size;
        Missiles.Entry start = Riiablo.files.Missiles.get(name + "start");
        Missiles.Entry loop = Riiablo.files.Missiles.get(name);
        assertEquals(29, start.pCltHitFunc);
        assertEquals(26, start.pSrvHitFunc);
        assertEquals(loop.Missile, start.CltHitSubMissile[0]);
        assertEquals(loop.Missile, start.HitSubMissile[0]);
        f.impact(start);
        assertTrue(f.created.isEmpty(), "the authoritative loop must not get a client duplicate");

        assertEquals(29, loop.pCltHitFunc);
        assertEquals(27, loop.pSrvHitFunc);
        f.impact(loop);
        assertEquals(1, f.created.size());
        Missile stop = f.world.getMapper(Missile.class).get(f.created.get(0));
        assertEquals(name + "stop", stop.missile.Missile);
        assertTrue(stop.presentationOnly);
        assertFalse(stop.authoritative);
        assertEquals(-1, stop.ownerId);
        assertFalse(stop.persistent);
        assertEquals(9, stop.nativeLifetimeFrames);
        f.created.clear();
      }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final EntityFactory factory = mock(EntityFactory.class);
    final MissileImpactPresentationSystem system = new MissileImpactPresentationSystem();
    final List<Integer> created = new ArrayList<>();
    final World world;

    Fixture() {
      world = new World(new WorldConfigurationBuilder().with(system, new OverlayManager(), factory).build()
          .register("factory", factory)
          .register("map", new com.riiablo.map.Map(0, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(-1))).thenAnswer(call -> {
            int id = world.create();
            world.getMapper(Missile.class).create(id).missile = call.getArgument(0);
            world.getMapper(Position.class).create(id).position.set(call.getArgument(2));
            world.getMapper(Velocity.class).create(id);
            created.add(id);
            return id;
          });
    }

    void impact(Missiles.Entry row) {
      system.onMissileImpact(MissileImpactEvent.obtain(-1, row.Id, -1, -1, Vector2.Zero));
    }

    @Override public void close() { world.dispose(); }
  }
}
