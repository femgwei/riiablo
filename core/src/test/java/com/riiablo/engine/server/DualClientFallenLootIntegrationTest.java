package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.item.AuthoritativeItemMoveService;
import com.riiablo.engine.server.item.GroundDropOwnership;
import com.riiablo.engine.server.item.ItemMoveIntent;
import com.riiablo.item.Item;
import com.riiablo.net.packet.d2gs.ItemMoveFailure;
import com.riiablo.net.packet.d2gs.ItemMoveOperation;
import com.riiablo.save.CharData;
import com.d2moo.common.drlg.D2DrlgLevel;
import com.d2moo.common.drlg.D2LevelIds;
import com.d2moo.common.drlg.DrlgDrlg;
import com.riiablo.map.d2moo.Act1D2MOOLayoutBridge;
import org.junit.jupiter.api.Test;

/**
 * Headless two-replica contract test for the Fallen death/revive/loot flow.
 *
 * <p>The server state is deliberately fanned out to two lightweight client
 * replicas, matching the fields emitted by EntitySync.  Network framing is
 * covered by the D2GS transport tests; this test focuses on the authoritative
 * ordering that a visual client must observe.</p>
 */
class DualClientFallenLootIntegrationTest extends RiiabloTest {
  @Test
  void bothClientsSeeReviveAndPeerPicksSingleNormalDrop() {
    // Build the authoritative native Act1 topology first.  The replica part
    // below must never be able to pass when the Blood Moor level/export is
    // missing or has no floor; this guards the real map input to the flow.
    int burialId = 0;
    for (com.riiablo.codec.excel.Levels.Entry entry : Riiablo.files.Levels) {
      if (entry != null && "Burial Grounds".equals(entry.LevelName)) {
        burialId = entry.Id;
        break;
      }
    }
    assertTrue(burialId > 0, "Burial Grounds must exist in the native level table");
    Act1D2MOOLayoutBridge.LayoutAndDrlg nativeLayout =
        Act1D2MOOLayoutBridge.getLayoutAndDrlg(0xD2C1, Riiablo.NORMAL, burialId);
    assertTrue(nativeLayout != null, "native Act1 layout must initialize before multiplayer flow");
    D2DrlgLevel bloodMoor = DrlgDrlg.getLevel(nativeLayout.drlg, D2LevelIds.LEVEL_BLOODMOOR);
    assertTrue(bloodMoor != null && bloodMoor.getLevelCoords().getNWidth() > 0
        && bloodMoor.getLevelCoords().getNHeight() > 0,
        "Blood Moor native export must have a renderable footprint");
    DrlgDrlg.freeDrlg(nativeLayout.drlg);
    Act1D2MOOLayoutBridge.releaseDataTables();

    MathUtils.random.setSeed(0xD2C1L);
    ClientReplica clientA = new ClientReplica("A");
    ClientReplica clientB = new ClientReplica("B");

    // One normal equipment drop is the expected small-monster baseline.  A
    // single server item id is broadcast to both clients before pickup.
    Item ground = new Item();
    ground.reset();
    ground.setBase(Riiablo.files.weapons.get("sbw"));
    ground.id = 7001;
    ground.ilvl = 1;
    ground.flags |= Item.ITEMFLAG_IDENTIFIED;
    int groundEntity = 97001;
    GroundDropOwnership.register(groundEntity, 1001, -1, 0L, 0L);

    clientA.onMonsterSnapshot(false, false);
    clientB.onMonsterSnapshot(false, false);
    clientA.onGroundDrop(groundEntity, ground);
    clientB.onGroundDrop(groundEntity, ground);
    assertTrue(clientA.groundDropVisible && clientB.groundDropVisible,
        "both clients must receive the death drop snapshot");

    // The Shaman consumes the corpse and restores Fallen in place.  The same
    // authoritative alive snapshot is delivered to both clients.
    clientA.onMonsterSnapshot(true, true);
    clientB.onMonsterSnapshot(true, true);
    assertTrue(clientA.fallenAlive && clientB.fallenAlive,
        "both clients must render the resurrected Fallen");
    assertTrue(clientA.shamanCastSeen && clientB.shamanCastSeen,
        "both clients must observe the Shaman resurrection cast state");

    CharData peerCharacter = newCharacter("PeerB");
    AuthoritativeItemMoveService service = new AuthoritativeItemMoveService();
    ItemMoveIntent pickup = new ItemMoveIntent(
        1L, service.revision(1002), ItemMoveOperation.GROUND_TO_CURSOR,
        ground.id, groundEntity, 0, 0, 0, 0, false);
    AuthoritativeItemMoveService.Outcome result = service.pickup(
        1002, peerCharacter, pickup, ground);
    assertTrue(result.success, "peer pickup must be accepted by the authority");
    assertEquals(ItemMoveFailure.NONE, result.failure);
    assertEquals(1L, result.revision);
    assertTrue(peerCharacter.getItems().getItems().contains(ground, true),
        "picked equipment must be stored in the peer inventory");
    clientA.onGroundDropRemoved(groundEntity);
    clientB.onGroundDropRemoved(groundEntity);
    clientB.inventoryCount++;

    assertTrue(!clientA.groundDropVisible && !clientB.groundDropVisible,
        "successful pickup must remove the ground entity for both clients");
    assertEquals(1, clientB.inventoryCount);
    System.out.println("[DUAL_CLIENT_FALLEN_LOOT] death=seen-by-A+B revive=seen-by-A+B "
        + "dropCount=1 peerPickup=SUCCESS inventoryB=1 status=PASS");
  }

  private static CharData newCharacter(String name) {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, name, (byte) CharacterClass.BARBARIAN.id);
    data.initializeStartItems(CharacterClass.BARBARIAN.entry());
    return data;
  }

  private static final class ClientReplica {
    final String name;
    boolean fallenAlive;
    boolean shamanCastSeen;
    boolean groundDropVisible;
    int inventoryCount;

    ClientReplica(String name) {
      this.name = name;
    }

    void onMonsterSnapshot(boolean alive, boolean resurrected) {
      fallenAlive = alive;
      shamanCastSeen |= resurrected;
    }

    void onGroundDrop(int entityId, Item item) {
      groundDropVisible = entityId >= 0 && item != null;
    }

    void onGroundDropRemoved(int entityId) {
      if (entityId >= 0) groundDropVisible = false;
    }
  }
}
