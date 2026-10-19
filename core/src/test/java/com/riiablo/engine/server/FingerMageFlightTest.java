package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.missile.FingerMageFlight;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FingerMageFlightTest {
  @Test void repathsOnlyOnCountdownBoundaryAndUsesSignedCoordinates() {
    World world = new World(new WorldConfiguration());
    try {
      int owner = world.create();
      int target = world.create();
      int id = world.create();
      world.getMapper(Target.class).create(owner).target = target;
      world.getMapper(Position.class).create(target).position.set(10, 3);
      world.getMapper(Angle.class).create(id);
      world.process();
      Missile missile = new Missile();
      missile.ownerId = owner;
      missile.nativeLifetimeFrames = 80;
      missile.missile = new Missiles.Entry();
      missile.missile.pSrvDoFunc = 11;
      missile.missile.Param = new int[] {5, 20, 2};
      Vector2 velocity = new Vector2(6, 0);
      FingerMageFlight.update(world, id, missile, Vector2.Zero, velocity, 1);
      assertEquals(0, velocity.y);
      FingerMageFlight.update(world, id, missile, Vector2.Zero, velocity, 5);
      assertEquals(6, velocity.len(), 0.0001);
      assertEquals(velocity.x, velocity.y, 0.0001);
      world.getMapper(Position.class).get(target).position.set(-100, 0);
      Vector2 before = new Vector2(velocity);
      FingerMageFlight.update(world, id, missile, Vector2.Zero, velocity, 10);
      assertEquals(before, velocity);
    } finally { world.dispose(); }
  }
}
