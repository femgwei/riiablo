package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.Test;

class PotionRecoverySystemTest extends RiiabloTest {
  @Test
  void healingPotionRestoresLifeOverItsNativeDurationOnly() {
    Fixture fixture = fixture(10f, 20f);
    Item potion = beltPotion(fixture.character, "hp1", 1);

    assertTrue(fixture.character.useBeltPotion(0));
    assertFalse(fixture.character.getItems().contains(potion));
    assertEquals(10f, value(fixture.attributes, Stat.hitpoints), 0.0001f);
    assertEquals(20f, value(fixture.attributes, Stat.mana), 0.0001f);

    process(fixture.world, 1);
    assertTrue(value(fixture.attributes, Stat.hitpoints) > 10f);
    assertTrue(value(fixture.attributes, Stat.hitpoints) < 55f);
    assertEquals(20f, value(fixture.attributes, Stat.mana), 0.0001f);

    process(fixture.world, 191);
    assertEquals(55f, value(fixture.attributes, Stat.hitpoints), 0.0001f);
    assertEquals(20f, value(fixture.attributes, Stat.mana), 0.0001f);
    fixture.world.dispose();
  }

  @Test
  void manaPotionUsesManaTableAmountAndNativeDurationOnly() {
    Fixture fixture = fixture(10f, 20f);
    beltPotion(fixture.character, "mp1", 2);

    assertTrue(fixture.character.useBeltPotion(0));
    process(fixture.world, 128);

    assertEquals(10f, value(fixture.attributes, Stat.hitpoints), 0.0001f);
    // Amazon receives the native 1.5x class bonus: 20 base mana becomes 30.
    assertEquals(50f, value(fixture.attributes, Stat.mana), 0.0001f);
    fixture.world.dispose();
  }

  @Test
  void rejuvenationPotionRestoresBothResourcesImmediately() {
    Fixture fixture = fixture(10f, 20f);
    beltPotion(fixture.character, "rvs", 3);

    assertTrue(fixture.character.useBeltPotion(0));

    assertEquals(45f, value(fixture.attributes, Stat.hitpoints), 0.0001f);
    assertEquals(55f, value(fixture.attributes, Stat.mana), 0.0001f);
    fixture.world.dispose();
  }

  @Test
  void repeatedHealingPotionsMergeRemainingAmountAndDuration() {
    Fixture fixture = fixture(10f, 20f);
    beltPotion(fixture.character, "hp1", 4);
    assertTrue(fixture.character.useBeltPotion(0));
    process(fixture.world, 96);

    beltPotion(fixture.character, "hp1", 5);
    assertTrue(fixture.character.useBeltPotion(0));
    process(fixture.world, 288);

    assertEquals(100f, value(fixture.attributes, Stat.hitpoints), 0.0001f);
    fixture.world.dispose();
  }

  private static Fixture fixture(float life, float mana) {
    CharData character = CharData.obtain().set(
        Riiablo.NORMAL, false, "PotionHero", Riiablo.AMAZON);
    Attributes attributes = character.getStats();
    attributes.base().put(Stat.hitpoints, life);
    attributes.base().put(Stat.maxhp, 100f);
    attributes.base().put(Stat.mana, mana);
    attributes.base().put(Stat.maxmana, 100f);
    attributes.base().put(Stat.vitality, 0);
    attributes.base().put(Stat.energy, 0);
    attributes.reset();

    World world = new World(new WorldConfigurationBuilder()
        .with(new PotionRecoverySystem())
        .build());
    int entityId = world.create();
    world.getMapper(Player.class).create(entityId).data = character;
    world.getMapper(AttributesWrapper.class).create(entityId).attrs = attributes;
    world.process();
    return new Fixture(world, character, attributes);
  }

  private static Item beltPotion(CharData character, String code, int id) {
    Item potion = new ItemGenerator().generate(code);
    potion.id = id;
    assertTrue(character.getItems().addPotionToBelt(potion));
    return potion;
  }

  private static void process(World world, int frames) {
    for (int i = 0; i < frames; i++) world.process();
  }

  private static float value(Attributes attributes, short stat) {
    return attributes.get(stat).asFixed();
  }

  private static final class Fixture {
    final World world;
    final CharData character;
    final Attributes attributes;

    Fixture(World world, CharData character, Attributes attributes) {
      this.world = world;
      this.character = character;
      this.attributes = attributes;
    }
  }
}
