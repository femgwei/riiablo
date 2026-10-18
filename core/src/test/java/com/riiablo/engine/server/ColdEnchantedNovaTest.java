package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.artemis.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.*;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ColdEnchantedNovaTest extends RiiabloTest {
  @Test void createsSixtyFourTableDamageMissilesAtHalfMonsterLevel() {
    EntityFactory factory = mock(EntityFactory.class);
    ServerSkillSystem system = new ServerSkillSystem();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), system, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
    try {
      int source = world.create();
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Monster.class).create(source);
      Attributes owner = Attributes.obtainStandard();
      owner.base().put(Stat.level, 20); owner.reset();
      world.getMapper(AttributesWrapper.class).create(source).attrs = owner;
      java.util.List<Integer> ids = new java.util.ArrayList<>();
      when(factory.createMissile(any(), any(), any(), eq(source), eq(10)))
          .thenAnswer(call -> {
            int id = world.create(); ids.add(id);
            Missiles.Entry row = call.getArgument(0);
            world.getMapper(Missile.class).create(id).set(row, call.getArgument(2), row.Range).setOwner(source);
            world.getMapper(Velocity.class).create(id);
            return id;
          });
      world.process();
      system.resolveColdEnchantedDeath(source);
      assertEquals(64, ids.size());
      for (int id : ids) {
        Missile missile = world.getMapper(Missile.class).get(id);
        assertEquals("coldunique", missile.missile.Missile);
        assertTrue(missile.damage.get(Stat.coldmindam).asInt() > 0);
        assertTrue(missile.damage.get(Stat.coldlength).asInt() > 0);
        assertEquals(Riiablo.files.Missiles.get("frostnova").Vel,
            world.getMapper(Velocity.class).get(id).velocity.len(), .001f);
      }
      System.out.println("coldunique level=10 min=" + world.getMapper(Missile.class).get(ids.get(0)).damage.get(Stat.coldmindam).asInt()
          + " max=" + world.getMapper(Missile.class).get(ids.get(0)).damage.get(Stat.coldmaxdam).asInt()
          + " length=" + world.getMapper(Missile.class).get(ids.get(0)).damage.get(Stat.coldlength).asInt());
    } finally { world.dispose(); }
  }
}
