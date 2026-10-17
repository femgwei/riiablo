package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.Networked;
import com.riiablo.engine.server.component.Velocity;
import org.junit.jupiter.api.Test;

class IndependentSystemsTest {
  @Test void ecsSystemsProcessWithoutCoreOrAClient() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new AngularVelocity(), new ServerNetworkIdManager()).build());
    try {
      int id = world.create();
      Angle angle = world.getMapper(Angle.class).create(id);
      angle.angle.set(1, 0);
      angle.target.set(0, 1);
      Networked networked = world.getMapper(Networked.class).create(id);
      world.setDelta(1f);
      world.process();
      assertEquals(id, networked.serverId);
      assertTrue(angle.angle.epsilonEquals(angle.target));
      assertThrows(ClassNotFoundException.class, () -> Class.forName("com.riiablo.Riiablo"));
    } finally { world.dispose(); }
  }

  @Test void movementAdapterAppliesStateAndLockWithoutMutatingTheRequest() {
    Velocity requested = new Velocity();
    requested.velocity.set(8, 4);
    requested.stateSpeedMultiplier = 0.5f;
    Vector2 out = Box2DSynchronizerPre.resolveMovementVelocity(requested, new Vector2());
    assertEquals(new Vector2(4, 2), out);
    assertEquals(new Vector2(8, 4), requested.velocity);
    requested.stateMovementLocked = true;
    assertTrue(Box2DSynchronizerPre.resolveMovementVelocity(requested, out).isZero());
  }
}
