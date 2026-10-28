package com.riiablo.engine.server.monster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonsterSpawnerDeterminismTest {
  @Test
  void sameGameSeedProducesTheSamePackAndAdvancesOneNamedStream() {
    List<String> first = spawn(0x1A10F);
    List<String> second = spawn(0x1A10F);
    assertEquals(first, second);
    assertNotEquals(0, first.size());
  }

  @Test
  void differentGameSeedsDoNotShareTheMonsterSpawnSequence() {
    assertNotEquals(spawn(1), spawn(2));
  }

  private static List<String> spawn(int seed) {
    MonsterSpawner spawner = new MonsterSpawner(seed);
    List<String> result = new ArrayList<>();
    spawner.setCreateCallback((monsterId, x, y, rank, affixes, championType, uniqueId) -> {
      result.add(monsterId + ":" + Float.floatToIntBits(x) + ":" + Float.floatToIntBits(y)
          + ":" + rank + ":" + affixes + ":" + championType + ":" + uniqueId);
      return result.size() - 1;
    });
    spawner.setDifficulty(2);
    spawner.spawnPack(1, 10f, 20f, 30);
    result.add("rng=" + spawner.checkpointRng().state() + ":draws="
        + spawner.checkpointRng().drawCount());
    return result;
  }
}
