package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.artemis.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.*;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DamageEvent;
import net.mostlyoriginal.api.event.common.*;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class FireEnchantedDamageIntegrationTest extends RiiabloTest {
  static class Probe extends PassiveSystem {
    DamageEvent hit;
    @Subscribe public void damage(DamageEvent event) { hit = event; }
  }
  @Test void actualExplosionAppliesBothChannelsAndFireResistanceOnce() {
    EntityFactory factory = mock(EntityFactory.class);
    ServerSkillSystem system = new ServerSkillSystem();
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), system, probe, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
    try {
      int source = world.create(), target = world.create();
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Position.class).create(target).position.set(11, 10);
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("zombie1");
      Monster monster = world.getMapper(Monster.class).create(source);
      monster.monstats = row;
      monster.rngState = 123;
      Attributes owner = Attributes.obtainStandard();
      owner.base().put(Stat.level, 20); owner.reset();
      world.getMapper(AttributesWrapper.class).create(source).attrs = owner;
      Attributes defender = Attributes.obtainStandard();
      defender.base().put(Stat.hitpoints, 10000); defender.base().put(Stat.maxhp, 10000);
      defender.base().put(Stat.fireresist, 50); defender.reset();
      world.getMapper(AttributesWrapper.class).create(target).attrs = defender;
      world.getMapper(Player.class).create(target);
      when(factory.createMissile(any(), any(), any(), eq(source), eq(1)))
          .thenAnswer(call -> world.create());
      world.process();
      MonsterStatsCalculator.MonsterStatsInit base = new MonsterStatsCalculator.MonsterStatsInit();
      assertTrue(MonsterStatsCalculator.calculateMonsterStatsByLevel(Riiablo.files,
          row.hcIdx, 1, 0, 20, (short) 1, base));
      int maximum = base.maxHP * Riiablo.files.DifficultyLevels.get(0).MonsterCEDamagePercent / 100;
      int minimum = maximum * 60 / 100;
      int roll = (123 * 1103515245 + 12345) & 0x7fffffff;
      int channel = (minimum + (maximum > minimum ? roll % (maximum - minimum) : 0)) / 4;
      system.resolveFireEnchantedDeath(source);
      assertNotNull(probe.hit);
      assertEquals(channel, probe.hit.physicalDamage, .001f);
      assertEquals(channel / 2, probe.hit.fireDamage, .001f);
      assertEquals(10000 - channel - channel / 2,
          defender.get(Stat.hitpoints).asFixed(), .001f);
      verify(factory, times(1)).createMissile(any(), any(), any(), eq(source), eq(1));
    } finally { world.dispose(); }
  }
}
