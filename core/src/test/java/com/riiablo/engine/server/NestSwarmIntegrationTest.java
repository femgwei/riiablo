package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import org.junit.jupiter.api.Test;

/** Data and decision tests for the native Swarm/FoulCrowNest AI bindings. */
class NestSwarmIntegrationTest extends RiiabloTest {
  @Test
  void nativeRowsResolveToDedicatedAiAndSpawnChain() {
    assertTrue(Riiablo.files.monstats.get("swarm1") != null);
    assertTrue(Riiablo.files.monstats.get("crownest3") != null);
    assertEquals("Swarm", Riiablo.files.monstats.get("swarm1").AI);
    assertEquals("FoulCrowNest", Riiablo.files.monstats.get("crownest3").AI);
  }

  @Test
  void nestSpawnsOnlyWhenInRangeAfterIntervalAndCapacity() {
    assertTrue(com.riiablo.engine.server.ai.FoulCrowNest.shouldSpawn(10f, 0, 2, 25f, 20, true));
    assertTrue(!com.riiablo.engine.server.ai.FoulCrowNest.shouldSpawn(25f, 0, 2, 25f, 20, true));
    assertTrue(!com.riiablo.engine.server.ai.FoulCrowNest.shouldSpawn(10f, 2, 2, 25f, 20, true));
    assertTrue(!com.riiablo.engine.server.ai.FoulCrowNest.shouldSpawn(10f, 0, 2, 5f, 20, true));
  }

  @Test
  void nestPositionSearchFallsBackAroundBlockedCenter() {
    Vector2 out = new Vector2();
    Vector2 result = Actioneer.findNestSpawnPosition(10, 10, out,
        (x, y) -> !(x == 10 && y == 10));
    assertEquals(9f, result.x);
    assertEquals(9f, result.y);
  }
}
