package com.riiablo.engine.server;

import static org.mockito.Mockito.*;
import com.artemis.*;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.ModeChangeEvent;
import com.riiablo.engine.server.monster.*;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class LightningEnchantedTimingTest {
  @Test void missileTableSoftHitReachesLightningCallbackWithoutChangingAction() {
    ServerSkillSystem system = spy(new ServerSkillSystem());
    doNothing().when(system).emitLightningEnchantedBolts(anyInt());
    MissileCollisionSystem collisions = new MissileCollisionSystem();
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        system, collisions, factory).build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(1, 0)));
    try {
      world.setDelta(1f / 25f);
      int id = world.create();
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.rank = MonsterRank.UNIQUE;
      monster.affixes = MonsterAffix.LIGHTNING_ENCHANTED;
      CofReference cof = world.getMapper(CofReference.class).create(id);
      cof.mode = Engine.Monster.MODE_A1;
      world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
      for (int i = 0; i < 10; i++) world.process();
      Missile missile = new Missile();
      missile.missile = new com.riiablo.codec.excel.Missiles.Entry();
      collisions.queueMissileHitReaction(id, missile);
      verify(system, never()).emitLightningEnchantedBolts(anyInt());
      missile.missile.SoftHit = true;
      collisions.queueMissileHitReaction(id, missile);
      world.process();
      verify(system).emitLightningEnchantedBolts(id);
      org.junit.jupiter.api.Assertions.assertEquals(Engine.Monster.MODE_A1, cof.mode);
      org.junit.jupiter.api.Assertions.assertEquals(19,
          world.getMapper(NativeAiState.class).get(id).previousMode);
      for (int i = 0; i < 10; i++) world.process();
      cof.mode = Engine.Monster.MODE_DT;
      collisions.queueMissileHitReaction(id, missile);
      world.process();
      verify(system, times(1)).emitLightningEnchantedBolts(id);
    } finally { world.dispose(); }
  }

  @Test void softHitReleasesImmediatelySharesCooldownAndDoesNotDuplicateGh() {
    ServerSkillSystem system = spy(new ServerSkillSystem());
    doNothing().when(system).emitLightningEnchantedBolts(anyInt());
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), system, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
    try {
      world.setDelta(1f / 25f);
      int id = world.create();
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.rank = MonsterRank.UNIQUE;
      monster.affixes = MonsterAffix.LIGHTNING_ENCHANTED;
      CofReference cof = world.getMapper(CofReference.class).create(id);
      cof.mode = Engine.Monster.MODE_A1;
      world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
      for (int i = 0; i < 10; i++) world.process();
      system.onLightningEnchantedSoftHit(com.riiablo.engine.server.event.MonsterSoftHitEvent.obtain(id));
      verify(system).emitLightningEnchantedBolts(id);
      system.onLightningEnchantedSoftHit(com.riiablo.engine.server.event.MonsterSoftHitEvent.obtain(id));
      verify(system, times(1)).emitLightningEnchantedBolts(id);
      for (int i = 0; i < 10; i++) world.process();
      cof.mode = Engine.Monster.MODE_GH;
      system.onLightningEnchantedSoftHit(com.riiablo.engine.server.event.MonsterSoftHitEvent.obtain(id));
      verify(system, times(1)).emitLightningEnchantedBolts(id);
      cof.mode = Engine.Monster.MODE_A1;
      system.onLightningEnchantedSoftHit(com.riiablo.engine.server.event.MonsterSoftHitEvent.obtain(id));
      verify(system, times(2)).emitLightningEnchantedBolts(id);
      for (int i = 0; i < 10; i++) world.process();
      cof.mode = Engine.Monster.MODE_DT;
      system.onLightningEnchantedSoftHit(com.riiablo.engine.server.event.MonsterSoftHitEvent.obtain(id));
      cof.mode = Engine.Monster.MODE_A1;
      monster.rank = MonsterRank.NORMAL;
      system.onLightningEnchantedSoftHit(com.riiablo.engine.server.event.MonsterSoftHitEvent.obtain(id));
      verify(system, times(2)).emitLightningEnchantedBolts(id);
    } finally { world.dispose(); }
  }
  @Test void getHitDefersTwoFramesAndTenFrameIntervalExcludesOrdinaryModes() {
    ServerSkillSystem system = spy(new ServerSkillSystem());
    doNothing().when(system).emitLightningEnchantedBolts(anyInt());
    EntityFactory factory = mock(EntityFactory.class);
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(), system, factory)
        .build().register("factory", factory).register("map", new com.riiablo.map.Map(1, 0)));
    try {
      world.setDelta(1f / 25f);
      int id = world.create();
      Monster monster = world.getMapper(Monster.class).create(id);
      monster.rank = MonsterRank.UNIQUE;
      monster.affixes = MonsterAffix.LIGHTNING_ENCHANTED;
      world.getMapper(NativePathIdentity.class).create(id).allocated(world, id);
      for (int i = 0; i < 10; i++) world.process();
      system.onLightningEnchantedMode(ModeChangeEvent.obtain(id, Engine.Monster.MODE_NU));
      system.onLightningEnchantedMode(ModeChangeEvent.obtain(id, Engine.Monster.MODE_DT));
      world.process();
      verify(system, never()).emitLightningEnchantedBolts(anyInt());
      system.onLightningEnchantedMode(ModeChangeEvent.obtain(id, Engine.Monster.MODE_GH));
      world.process();
      verify(system, never()).emitLightningEnchantedBolts(anyInt());
      world.process();
      verify(system).emitLightningEnchantedBolts(id);
      system.onLightningEnchantedMode(ModeChangeEvent.obtain(id, Engine.Monster.MODE_GH));
      world.process(); world.process();
      verify(system, times(1)).emitLightningEnchantedBolts(id);
      for (int i = 0; i < 6; i++) world.process();
      system.onLightningEnchantedMode(ModeChangeEvent.obtain(id, Engine.Monster.MODE_GH));
      world.process(); world.process();
      verify(system, times(2)).emitLightningEnchantedBolts(id);
    } finally { world.dispose(); }
  }
}
