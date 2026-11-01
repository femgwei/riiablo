package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnimDataResolverTest {
  @Test
  void replicatedHolyShieldStateChangesResolvedBlockButNotAttackSpeed() {
    com.riiablo.codec.D2 previous = com.riiablo.Riiablo.anim;
    com.riiablo.Riiablo.anim = com.riiablo.codec.D2.loadFromStream(
        new java.io.ByteArrayInputStream(new byte[256 * 4]));
    AnimDataResolver resolver = new AnimDataResolver();
    com.artemis.World world = new com.artemis.World(
        new com.artemis.WorldConfigurationBuilder().with(resolver).build());
    try {
      com.artemis.Entity entity = world.createEntity();
      entity.edit().create(com.riiablo.engine.server.component.Class.class).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      com.riiablo.engine.server.component.CofReference cof = entity.edit().create(
          com.riiablo.engine.server.component.CofReference.class);
      cof.set("PA", com.riiablo.engine.Engine.Player.MODE_BL);
      com.riiablo.engine.server.component.UnitStates states = entity.edit().create(
          com.riiablo.engine.server.component.UnitStates.class).init(entity.getId());
      states.snapshotOnly = true;
      world.process();
      com.riiablo.engine.server.event.CofChangeEvent event =
          new com.riiablo.engine.server.event.CofChangeEvent();
      event.entityId = entity.getId();
      resolver.onCofChanged(event);
      assertEquals(128, entity.getComponent(com.riiablo.engine.server.component.AnimData.class).speed);
      states.stateList.addState(com.riiablo.engine.server.state.StateId.HOLYSHIELD, 100, 1, entity.getId());
      resolver.onCofChanged(event);
      assertEquals(256, entity.getComponent(com.riiablo.engine.server.component.AnimData.class).speed);
      states.stateList.removeState(com.riiablo.engine.server.state.StateId.HOLYSHIELD);
      resolver.onCofChanged(event);
      assertEquals(128, entity.getComponent(com.riiablo.engine.server.component.AnimData.class).speed);
      cof.mode = com.riiablo.engine.Engine.Player.MODE_A1;
      resolver.onCofChanged(event);
      assertEquals(256, entity.getComponent(com.riiablo.engine.server.component.AnimData.class).speed);
    } finally {
      world.dispose();
      com.riiablo.Riiablo.anim = previous;
    }
  }

  @Test
  void holyShieldUsesNativeBlockRateBonusInsteadOfUnmodifiedAnimData() {
    assertEquals(128, AnimDataResolver.blockAnimationSpeed(256, 0, false));
    assertEquals(256, AnimDataResolver.blockAnimationSpeed(256, 0, true));
    assertEquals(207, AnimDataResolver.blockAnimationSpeed(256, 42, false));
    assertEquals(335, AnimDataResolver.blockAnimationSpeed(256, 42, true));
  }

  @Test
  void blockRateUsesNativeIntegerDiminishingReturnsAndBounds() {
    assertEquals(281, AnimDataResolver.blockAnimationSpeed(256, 120, false));
    assertEquals(409, AnimDataResolver.blockAnimationSpeed(256, 120, true));
    assertEquals(1, AnimDataResolver.blockAnimationSpeed(0, 0, true));
    assertEquals(0x7fff, AnimDataResolver.blockAnimationSpeed(0x7fff, 120, true));
  }

  @Test
  void usesHitReactionFallbackForMissingBlockMode() {
    assertArrayEquals(new String[] {"GH", "A1"}, AnimDataResolver.fallbackModes("BL"));
  }

  @Test
  void usesAttackFallbackForMissingSpecialMode() {
    assertArrayEquals(new String[] {"A1", "S1"}, AnimDataResolver.fallbackModes("XX"));
  }

  @Test
  void ordinaryModesDoNotInventAnimationFallbacks() {
    assertEquals(0, AnimDataResolver.fallbackModes("DT").length);
  }
}
