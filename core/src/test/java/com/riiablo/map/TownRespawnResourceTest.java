package com.riiablo.map;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Validates retained resurrection coordinates against the actual 1.10f towns. */
class TownRespawnResourceTest extends RiiabloTest {
  @Test void actTwoThreeAndFiveRetainPlayerSpawnsInsideTheirTown() {
    for (int act : new int[] {1, 2, 4}) {
      Map map = new Map(127897359, 0);
      map.generate(act);
      int townId = act == 1 ? 40 : act == 2 ? 75 : 109;
      Vector2 spawn = map.townPlayerSpawn();
      assertNotNull(spawn, "Act " + (act + 1));
      Map.Zone town = map.getZone(spawn);
      assertNotNull(town, "spawn " + spawn);
      assertEquals(townId, town.levelId(), "spawn " + spawn);
      assertEquals(town.townPlayerSpawn(), spawn, "native anchor is used");
      System.out.println("TOWN_RESPAWN act=" + (act + 1) + " world=" + spawn
          + " local=" + spawn.cpy().sub(town.x(), town.y())
          + " flags=" + town.nativeCollisionFlagsWorld((int) spawn.x, (int) spawn.y));
    }
  }
}
