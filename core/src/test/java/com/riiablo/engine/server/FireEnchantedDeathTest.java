package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.artemis.*;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.monster.MonsterAffix;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class FireEnchantedDeathTest {
  @Test void coldDeathWaitsFourFramesAndRejectsRemovedIdentity() {
    ServerSkillSystem system = spy(new ServerSkillSystem());
    doNothing().when(system).resolveColdEnchantedDeath(anyInt());
    com.riiablo.engine.EntityFactory factory = mock(com.riiablo.engine.EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), system, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
    try {
      world.setDelta(1f / 25f);
      int id = world.create();
      world.getMapper(Position.class).create(id);
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.rank = MonsterRank.UNIQUE;
      monster.affixes = MonsterAffix.COLD_ENCHANTED;
      world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
      world.process();
      system.onColdEnchantedDeath(DeathEvent.obtain(9, id));
      system.onColdEnchantedDeath(DeathEvent.obtain(9, id));
      for (int i = 0; i < 3; i++) world.process();
      verify(system, never()).resolveColdEnchantedDeath(anyInt());
      world.process();
      verify(system).resolveColdEnchantedDeath(id);
      monster.coldDeathNovaScheduled = false;
      system.onColdEnchantedDeath(DeathEvent.obtain(9, id));
      world.getMapper(NativePathIdentity.class).remove(id);
      for (int i = 0; i < 5; i++) world.process();
      verify(system, times(1)).resolveColdEnchantedDeath(id);
    } finally { world.dispose(); }
  }
  @Test void maximumUsesBaseLifeAndDifficultyPercent() {
    assertEquals(750, ServerSkillSystem.fireDeathMaximum(1000, 75));
    assertEquals(329, ServerSkillSystem.fireDeathMaximum(999, 33));
    assertEquals(0, ServerSkillSystem.fireDeathMaximum(1000, 0));
  }

  @Test void deathIsDeferredFourFramesAndDuplicateCannotRearm() {
    ServerSkillSystem system = spy(new ServerSkillSystem());
    doNothing().when(system).resolveFireEnchantedDeath(anyInt());
    com.riiablo.engine.EntityFactory factory = mock(com.riiablo.engine.EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), system, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
    try {
      world.setDelta(1f / 25f);
      int id = world.create();
      world.getMapper(Position.class).create(id);
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.rank = MonsterRank.UNIQUE;
      monster.affixes = MonsterAffix.FIRE_ENCHANTED;
      world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
      world.process();
      system.onFireEnchantedDeath(DeathEvent.obtain(9, id));
      system.onFireEnchantedDeath(DeathEvent.obtain(9, id));
      for (int i = 0; i < 3; i++) world.process();
      verify(system, never()).resolveFireEnchantedDeath(anyInt());
      world.process();
      verify(system, times(1)).resolveFireEnchantedDeath(id);
      system.onFireEnchantedDeath(DeathEvent.obtain(9, id));
      for (int i = 0; i < 5; i++) world.process();
      verify(system, times(1)).resolveFireEnchantedDeath(id);
    } finally { world.dispose(); }
  }
}
