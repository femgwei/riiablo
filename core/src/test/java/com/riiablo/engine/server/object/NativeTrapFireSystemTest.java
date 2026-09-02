package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.NativeTrapFire;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.map.Map;

import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

class NativeTrapFireSystemTest extends RiiabloTest {
  @Test
  void damagesNearbyPlayerAndDeletesFireAtEndOfLifetime() {
    DamageProbe probe = new DamageProbe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), probe, new NativeTrapFireSystem())
        .build());
    try {
      Map.Zone zone = mock(Map.Zone.class);
      int fireId = world.create();
      world.getMapper(Position.class).create(fireId).position.set(10, 10);
      world.getMapper(MapWrapper.class).create(fireId).set(null, zone);
      world.getMapper(NativeTrapFire.class).create(fireId).reset(0.5f, 3f, 100, 1);

      int playerId = world.create();
      world.getMapper(Player.class).create(playerId);
      world.getMapper(Position.class).create(playerId).position.set(11, 10);
      world.getMapper(MapWrapper.class).create(playerId).set(null, zone);
      Attributes attrs = Attributes.obtainLarge();
      attrs.base().put(Stat.hitpoints, 100f);
      attrs.base().put(Stat.level, 1);
      attrs.base().put(Stat.dexterity, 0);
      attrs.base().put(Stat.armorclass, 0);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(playerId).attrs = attrs;

      world.setDelta(0.1f);
      world.process();
      StatRef hp = attrs.get(Stat.hitpoints, StatRef.obtain());
      assertTrue(hp.asFixed() < 100f);
      assertTrue(probe.damageEvents > 0);

      for (int i = 0; i < 6; i++) world.process();
      assertFalse(world.getEntityManager().isActive(fireId));
    } finally {
      world.dispose();
    }
  }

  private static final class DamageProbe extends PassiveSystem {
    int damageEvents;

    @Subscribe
    public void onDamage(DamageEvent event) {
      damageEvents++;
    }
  }
}
