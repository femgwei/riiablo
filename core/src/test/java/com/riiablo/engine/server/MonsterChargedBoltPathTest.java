package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.artemis.*;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class MonsterChargedBoltPathTest extends RiiabloTest {
  @Test void scarabAndEnchantedUseCardinalPairsWithChargedSeeds() {
    for (boolean enchanted : new boolean[] {false, true}) {
      EntityFactory factory = mock(EntityFactory.class);
      ServerSkillSystem skills = new ServerSkillSystem();
      World world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), skills, new MissileCollisionSystem(), factory).build().register("factory", factory)
          .register("map", new com.riiablo.map.Map(1, 0)));
      try {
        int source = world.create();
        world.getMapper(Monster.class).create(source);
        world.getMapper(Position.class).create(source).position.set(10.75f, -7.25f);
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        java.util.List<Vector2> directions = new java.util.ArrayList<>();
        when(factory.createMissile(any(), any(), any(), eq(source), eq(1)))
            .thenAnswer(call -> {
              int id = world.create(); ids.add(id);
              directions.add(new Vector2((Vector2) call.getArgument(1)));
              Missiles.Entry row = call.getArgument(0);
              Vector2 origin = call.getArgument(2);
              assertEquals(new Vector2(10, -8), origin);
              world.getMapper(Missile.class).create(id).set(row, origin, row.Range);
              world.getMapper(Position.class).create(id).position.set(origin);
              world.getMapper(Velocity.class).create(id).velocity.set(call.getArgument(1)).scl(5);
              return id;
            });
        if (enchanted) skills.emitLightningEnchantedBolts(source);
        else skills.emitScarabChargedBolts(source);
        assertEquals(8, ids.size());
        int[] dx = {0, 1, 0, -1}, dy = {-1, 0, 1, 0};
        for (int i = 0; i < 8; i++) {
          Missile bolt = world.getMapper(Missile.class).get(ids.get(i));
          assertEquals(enchanted ? "lightunique" : "buglightning", bolt.missile.Missile);
          assertTrue(bolt.chargedBoltPath);
          assertEquals(10 + dx[i / 2], bolt.chargedBoltTargetX);
          assertEquals(-8 + dy[i / 2], bolt.chargedBoltTargetY);
          int main = AssassinTrapSystem.chargedBoltMainDirection(10, -8,
              bolt.chargedBoltTargetX, bolt.chargedBoltTargetY);
          assertEquals(main, bolt.chargedBoltMainDirection);
          long seed = AssassinTrapSystem.chargedBoltRoll(10 + i % 2, 666);
          assertEquals((int) seed, bolt.chargedBoltSeedLow);
          assertEquals((int) (seed >>> 32), bolt.chargedBoltSeedHigh);
          Vector2 expected = AssassinTrapSystem.chargedBoltVector(
              AssassinTrapSystem.chargedBoltDirection(main, (int) seed), new Vector2());
          assertTrue(expected.epsilonEquals(directions.get(i), .0001f));
          assertEquals(2f, bolt.chargedBoltNextTurnDistance);
          assertEquals(Missile.chargedBoltLifetimeFrames(bolt.missile, 1), bolt.nativeLifetimeFrames);
        }
        // Exercise the production turn handler after crossing a two-subtile segment.
        Missile first = world.getMapper(Missile.class).get(ids.get(0));
        long next = AssassinTrapSystem.chargedBoltRoll(
            first.chargedBoltSeedLow, first.chargedBoltSeedHigh);
        first.distanceTraveled = 2f;
        world.setDelta(1f / 25f);
        world.process();
        assertEquals((int) next, first.chargedBoltSeedLow);
        assertEquals((int) (next >>> 32), first.chargedBoltSeedHigh);
        assertEquals(4f, first.chargedBoltNextTurnDistance);
        Vector2 turned = AssassinTrapSystem.chargedBoltVector(
            AssassinTrapSystem.chargedBoltDirection(first.chargedBoltMainDirection, (int) next),
            new Vector2());
        assertTrue(turned.epsilonEquals(world.getMapper(Velocity.class)
            .get(ids.get(0)).velocity.cpy().nor(), .0001f));
      } finally { world.dispose(); }
    }
  }
}
