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
  static class Probe extends net.mostlyoriginal.api.system.core.PassiveSystem {
    final java.util.List<com.riiablo.engine.server.event.DamageEvent> hits = new java.util.ArrayList<>();
    @net.mostlyoriginal.api.event.common.Subscribe
    public void damage(com.riiablo.engine.server.event.DamageEvent event) { hits.add(event); }
  }

  @Test void deadOwnerNovaCollisionsDamageAndChillOnceAtZeroAndFiftyResistance() {
    for (int resist : new int[] {0, 50}) {
      EntityFactory factory = mock(EntityFactory.class);
      ServerSkillSystem system = new ServerSkillSystem();
      Probe probe = new Probe();
      World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), probe,
          new StateUpdater(), system, new MissileCollisionSystem(), factory)
          .build().register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
      try {
        world.setDelta(1f / 25f);
        int source = world.create(), target = world.create();
        world.getMapper(Position.class).create(source).position.set(10, 10);
        world.getMapper(Position.class).create(target).position.set(10, 10);
        com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("zombie1");
        world.getMapper(Monster.class).create(source).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
        Attributes owner = Attributes.obtainStandard();
        owner.base().put(Stat.level, 20); owner.base().put(Stat.hitpoints, 0); owner.reset();
        world.getMapper(AttributesWrapper.class).create(source).attrs = owner;
        Attributes defender = Attributes.obtainStandard();
        defender.base().put(Stat.hitpoints, 1000); defender.base().put(Stat.maxhp, 1000);
        defender.base().put(Stat.coldresist, resist); defender.reset();
        world.getMapper(AttributesWrapper.class).create(target).attrs = defender;
        world.getMapper(Player.class).create(target);
        world.getMapper(UnitStates.class).create(target).init(world, target);
        when(factory.createMissile(any(), any(), any(), eq(source), eq(10)))
            .thenAnswer(call -> {
              int id = world.create();
              Missiles.Entry missile = call.getArgument(0);
              com.badlogic.gdx.math.Vector2 origin = call.getArgument(2);
              world.getMapper(Missile.class).create(id).set(missile, origin, missile.Range).setOwner(source);
              world.getMapper(Position.class).create(id).position.set(origin);
              world.getMapper(Velocity.class).create(id);
              return id;
            });
        world.process();
        system.resolveColdEnchantedDeath(source);
        world.process();
        assertEquals(1, probe.hits.size(), "64 overlapping nova missiles share the hit gate");
        float damage = probe.hits.get(0).damage;
        assertTrue(damage >= (resist == 0 ? 44 : 22) && damage <= (resist == 0 ? 46 : 23));
        assertEquals(1000 - damage, defender.get(Stat.hitpoints).asFixed(), .001f);
        com.riiablo.engine.server.state.UnitState cold = world.getMapper(UnitStates.class)
            .get(target).stateList.getState(com.riiablo.engine.server.state.StateId.COLD);
        assertNotNull(cold);
        assertEquals(resist == 0 ? 300 : 150, cold.duration);
        for (int i = 0; i < 3; i++) world.process();
        assertEquals(1, probe.hits.size());
      } finally { world.dispose(); }
    }
  }
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
