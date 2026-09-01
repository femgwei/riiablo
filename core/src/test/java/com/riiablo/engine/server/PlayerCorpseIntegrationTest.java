package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.client.DeathHandler;
import com.riiablo.engine.Engine;
import com.riiablo.CharacterClass;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.PlayerCorpse;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.camera.IsometricCamera;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless authority test for D2-style player death, corpse loot and respawn. */
class PlayerCorpseIntegrationTest extends RiiabloTest {
  @Test
  void corpsePermissionIsExplicitAndNotImpliedByAnotherPlayer() {
    PlayerCorpse corpse = new PlayerCorpse();
    corpse.playerId = 7;
    assertTrue(corpse.canRetrieve(7));
    assertFalse(corpse.canRetrieve(9));
    corpse.grantLootPermission(9);
    assertTrue(corpse.canRetrieve(9));
    corpse.revokeLootPermission(9);
    assertFalse(corpse.canRetrieve(9));
  }

  @Test
  void deathDetachesEquipmentRespawnRestoresStateAndCorpseRetrievalRestoresItems() {
    EventSystem events = new EventSystem();
    TestMap map = new TestMap();
    DeathHandler death = new DeathHandler();
    PlayerCorpseRetrievalSystem retrieval = new PlayerCorpseRetrievalSystem();
    CofManager cofs = new CofManager();
    World world = new World(new WorldConfigurationBuilder()
        .with(events, cofs, death, retrieval)
        .build()
        .register("map", map)
        .register("iso", new IsometricCamera()));
    try {
      CharData data = CharData.obtain().clear()
          .set(Riiablo.NORMAL, false, "CorpseHero", Riiablo.AMAZON);
      data.initializeStartItems(CharacterClass.AMAZON.entry());
      data.getStats().base().put(Stat.hitpoints, 60f);
      data.getStats().base().put(Stat.maxhp, 60f);
      data.getStats().base().put(Stat.mana, 20f);
      data.getStats().base().put(Stat.maxmana, 20f);
      data.getStats().reset();

      int playerId = world.create();
      world.getMapper(Player.class).create(playerId).data = data;
      world.getMapper(Position.class).create(playerId).position.set(12, 34);
      world.getMapper(AttributesWrapper.class).create(playerId).attrs = data.getStats();
      world.getMapper(CofReference.class).create(playerId).set("AM", Engine.Player.MODE_NU);
      world.process(); // wire systems and CofReference before dispatching death

      Item right = data.getItems().getSlot(BodyLoc.RARM);
      Item left = data.getItems().getSlot(BodyLoc.LARM);
      assertNotNull(right);
      assertNotNull(left);
      System.out.println("[PLAYER_CORPSE_CHAIN] phase=before_death player=" + playerId
          + " right=" + right.code + " left=" + left.code + " position=(12,34)");

      events.dispatch(DeathEvent.obtain(playerId, playerId));
      PlayerCorpse marker = world.getMapper(PlayerCorpse.class).get(playerId);
      assertNotNull(marker, "death must attach authoritative PlayerCorpse marker");
      assertNull(data.getItems().getSlot(BodyLoc.RARM), "right-hand item must leave player on death");
      assertNull(data.getItems().getSlot(BodyLoc.LARM), "left-hand item must leave player on death");
      assertEquals(0f, data.getStats().aggregate().getValue(Stat.hitpoints, 0f));
      assertEquals(2, marker.equippedItems.size, "both starting weapons must be stored on corpse");
      Vector2 deathLocation = new Vector2(marker.deathLocation);

      // The independent corpse is the object that remains at the death location.
      int corpseId = world.create();
      PlayerCorpse corpse = world.getMapper(PlayerCorpse.class).create(corpseId);
      corpse.playerId = playerId;
      corpse.deathLocation.set(deathLocation);
      corpse.equippedItems.putAll(marker.equippedItems);
      System.out.println("[PLAYER_CORPSE_CHAIN] phase=corpse_created entity=" + corpseId
          + " items=" + corpse.equippedItems.size + " location=" + corpse.deathLocation);

      death.respawnPlayerAtTown(playerId);
      assertFalse(death.isPlayerDead(playerId), "ESC respawn must clear the dead marker");
      assertEquals(new Vector2(99, 101), world.getMapper(Position.class).get(playerId).position);
      assertEquals(60f, data.getStats().aggregate().getValue(Stat.hitpoints, 0f));
      assertNotNull(world.getMapper(com.riiablo.engine.server.component.Velocity.class).get(playerId));
      assertNull(data.getItems().getSlot(BodyLoc.RARM), "items remain on corpse after respawn");

      // Walking back to the saved death location retrieves both items.
      world.getMapper(Position.class).get(playerId).position.set(deathLocation);
      world.process();
      assertEquals(right, data.getItems().getSlot(BodyLoc.RARM));
      assertEquals(left, data.getItems().getSlot(BodyLoc.LARM));
      assertFalse(world.getMapper(PlayerCorpse.class).has(corpseId),
          "fully looted corpse entity must be deleted after retrieval");
      System.out.println("[PLAYER_CORPSE_CHAIN] phase=summary detached=2 restored=2 respawn=(99,101)");
    } finally {
      world.dispose();
    }
  }

  /** Map fixture with a deterministic town entry and no generated/rendered zones. */
  private static final class TestMap extends Map {
    TestMap() { super(0, 0); }

    @Override public Vector2 find(int id) {
      return new Vector2(99, 101);
    }
  }

}
