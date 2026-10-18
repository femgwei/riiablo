package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.*;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DamageEvent;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class ScarabNativeAbilityTest {
  @Test void ordinaryScarabDamageReachesEmitterWithoutUniqueAffixes() {
    ServerSkillSystem skills = spy(new ServerSkillSystem());
    doNothing().when(skills).emitScarabChargedBolts(anyInt());
    AIStepper ai = new AIStepper();
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), skills, ai, factory).build()
        .register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
    try {
      int id = world.create();
      MonStats.Entry row = new MonStats.Entry();
      row.BaseId = "scarab1";
      Monster monster = world.getMapper(Monster.class).create(id).set(row, null);
      monster.setRank(0, 0L, -1, -1);
      world.getMapper(AIWrapper.class).create(id);
      assertTrue(monster.nativeChargedBolts);
      assertEquals(0L, monster.affixes);
      ai.onNativeDamage(DamageEvent.obtain(123, id, 9f));
      verify(skills).emitScarabChargedBolts(id);
      ai.onNativeDamage(DamageEvent.obtain(123, id, 9f));
      verify(skills, times(1)).emitScarabChargedBolts(id);
      row.BaseId = "zombie1";
      monster.set(row, null);
      assertFalse(monster.nativeChargedBolts);
      ai.onNativeDamage(DamageEvent.obtain(123, id, 9f));
      verify(skills, times(1)).emitScarabChargedBolts(id);
    } finally { world.dispose(); }
  }
}
