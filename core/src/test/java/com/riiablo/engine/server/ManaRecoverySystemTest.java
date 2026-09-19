package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import org.junit.jupiter.api.Test;

class ManaRecoverySystemTest extends RiiabloTest {
  @Test
  void usesNativeFixedPointRecoveryRate() {
    int maximum = 100 * 256;
    assertEquals(3, ManaRecoverySystem.recoveryPerTickEncoded(maximum, 300, 0, 0));
    assertEquals(6, ManaRecoverySystem.recoveryPerTickEncoded(maximum, 300, 100, 0));
  }

  @Test
  void alwaysRecoversAtLeastOneFixedPointUnit() {
    assertEquals(1, ManaRecoverySystem.recoveryPerTickEncoded(10 * 256, 300, 0, 0));
  }

  @Test
  void addsFlatRecoveryInTheEncodedDomain() {
    assertEquals(67, ManaRecoverySystem.recoveryPerTickEncoded(
        100 * 256, 300, 100, 61));
  }

  @Test
  void systemRecoversLivingPlayerAndClampsAtMaximum() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new ManaRecoverySystem())
        .build());
    try {
      Attributes attrs = createPlayer(world, 9.996f, 10f, 1f);
      world.process(); // submit the newly composed entity to aspect subscriptions
      world.process();
      assertEquals(10f, attrs.get(Stat.mana).asFixed(), 0.0001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void deadPlayerDoesNotRecoverMana() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new ManaRecoverySystem())
        .build());
    try {
      Attributes attrs = createPlayer(world, 0f, 10f, 0f);
      world.process(); // submit the newly composed entity to aspect subscriptions
      world.process();
      assertEquals(0f, attrs.get(Stat.mana).asFixed(), 0.0001f);
    } finally {
      world.dispose();
    }
  }

  private static Attributes createPlayer(
      World world, float mana, float maximumMana, float hitpoints) {
    int entityId = world.create();
    world.getMapper(Player.class).create(entityId);
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.mana, mana);
    attrs.base().put(Stat.maxmana, maximumMana);
    attrs.base().put(Stat.hitpoints, hitpoints);
    attrs.reset();
    world.getMapper(AttributesWrapper.class).create(entityId).attrs = attrs;
    return attrs;
  }
}
