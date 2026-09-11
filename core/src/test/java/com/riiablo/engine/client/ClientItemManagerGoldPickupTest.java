package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Item;
import com.riiablo.save.CharData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ClientItemManagerGoldPickupTest extends RiiabloTest {
  @AfterEach
  void clearCharacter() {
    Riiablo.charData = null;
  }

  @Test
  void localGoldPickupCreditsWalletWithoutUsingCursor() {
    Riiablo.charData = character("LocalGold", 10, 1);
    ClientItemManager manager = new ClientItemManager();
    World world = world(manager);
    try {
      int entity = groundGold(world, 42, 25);
      manager.groundToCursor(entity);
      world.process();

      assertEquals(35, Riiablo.charData.getStats().get(Stat.gold).asInt());
      assertNull(Riiablo.charData.getItems().getCursor());
      assertFalse(world.getEntityManager().isActive(entity));
    } finally {
      world.dispose();
    }
  }

  @Test
  void localPartialGoldPickupKeepsRemainderOnGround() {
    Riiablo.charData = character("LocalCap", 9_990, 1);
    ClientItemManager manager = new ClientItemManager();
    World world = world(manager);
    try {
      int entity = groundGold(world, 43, 25);
      manager.groundToCursor(entity);
      world.process();

      assertEquals(10_000, Riiablo.charData.getStats().get(Stat.gold).asInt());
      assertNull(Riiablo.charData.getItems().getCursor());
      assertTrue(world.getEntityManager().isActive(entity));
      assertEquals(15, world.getMapper(Item.class).get(entity).item.attrs
          .base().get(Stat.quantity).asInt());
    } finally {
      world.dispose();
    }
  }

  @Test
  void localFullWalletDoesNotConsumeGroundGold() {
    Riiablo.charData = character("LocalFull", 10_000, 1);
    ClientItemManager manager = new ClientItemManager();
    World world = world(manager);
    try {
      int entity = groundGold(world, 44, 25);
      manager.groundToCursor(entity);
      world.process();

      assertEquals(10_000, Riiablo.charData.getStats().get(Stat.gold).asInt());
      assertTrue(world.getEntityManager().isActive(entity));
      assertEquals(25, world.getMapper(Item.class).get(entity).item.attrs
          .base().get(Stat.quantity).asInt());
    } finally {
      world.dispose();
    }
  }

  private static World world(ClientItemManager manager) {
    TestFactory factory = new TestFactory();
    return new World(new WorldConfigurationBuilder().with(manager, factory).build()
        .register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static CharData character(String name, int carriedGold, int level) {
    CharData character = CharData.obtain().set(Riiablo.NORMAL, false, name, Riiablo.AMAZON);
    character.level = (byte) level;
    character.getStats().base().put(Stat.level, level);
    character.getStats().aggregate().put(Stat.level, level);
    character.getStats().base().put(Stat.gold, carriedGold);
    character.getStats().aggregate().put(Stat.gold, carriedGold);
    return character;
  }

  private static int groundGold(World world, int itemId, int quantity) {
    com.riiablo.item.Item gold = new com.riiablo.item.Item();
    gold.id = itemId;
    gold.code = "gld";
    gold.attrs = Attributes.obtainStandard();
    gold.attrs.base().put(Stat.quantity, quantity);
    gold.attrs.aggregate().put(Stat.quantity, quantity);
    int entity = world.create();
    world.getMapper(Item.class).create(entity).item = gold;
    world.process();
    return entity;
  }

  private static final class TestFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return -1; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return -1; }
  }
}
