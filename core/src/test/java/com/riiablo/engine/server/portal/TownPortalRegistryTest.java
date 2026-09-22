package com.riiablo.engine.server.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class TownPortalRegistryTest {
  @Test
  void replacingOneOwnerLeavesOnlyTheNewestPair() {
    TownPortalRegistry registry = new TownPortalRegistry();
    Map.Zone source = openZone();
    Map.Zone town = openZone();

    TownPortalRegistry.Pair first = registry.replace(10, null, source, new Vector2(1, 1),
        101, 102, town, new Vector2(2, 2), 103, 104);
    TownPortalRegistry.Pair second = registry.replace(10, null, source, new Vector2(3, 3),
        201, 202, town, new Vector2(4, 4), 203, 204);

    assertEquals(1, registry.size());
    assertNotSame(first, second);
    assertEquals(201, registry.get(10).sourceVisual);
    assertEquals(204, registry.get(10).townWarp);
  }

  @Test
  void differentOwnersRemainParallelAndTownAnchorsDoNotOverlap() {
    TownPortalRegistry registry = new TownPortalRegistry();
    Map.Zone town = openZone();
    Map.Zone source = openZone();
    registry.replace(10, null, source, new Vector2(1, 1), 101, 102,
        town, new Vector2(0, 0), 103, 104);
    registry.replace(20, null, source, new Vector2(3, 3), 201, 202,
        town, new Vector2(6, 6), 203, 204);

    Vector2 result = new Vector2();
    assertTrue(registry.findFreeTownPosition(town, new Vector2(0, 0), 1, 8, result));
    assertTrue(result.dst2(new Vector2(0, 0)) >= 9f);
    assertEquals(2, registry.size());
  }

  private static Map.Zone openZone() {
    return new Map.Zone() {
      @Override
      public void addWarp(int entityId) {}

      @Override
      public boolean findFreeCoordinates(Vector2 origin, int unitSize, int searchRadius,
          boolean allowNeighborRooms, Vector2 out) {
        out.set(origin);
        return true;
      }
    };
  }
}
