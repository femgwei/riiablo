package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Act5BaalSpawnLayoutTest {
  @Test
  void summonPointMatchesNativeBaalMonsterSpawnTarget() {
    Vector2 point = Act5BaalSpawnLayout.summonPoint(100f, 200f, new Vector2());
    assertEquals(100f, point.x);
    assertEquals(213f, point.y);
    assertEquals(0f, Act5BaalSpawnLayout.SUMMON_OFFSET_X);
    assertEquals(13f, Act5BaalSpawnLayout.SUMMON_OFFSET_Y);
  }

  @Test
  void candidateOffsetsAreStableAndNonOverlapping() {
    Set<String> offsets = new HashSet<>();
    for (int i = 0; i < Act5BaalSpawnLayout.candidateCount(); i++) {
      float x = Act5BaalSpawnLayout.candidateX(i);
      float y = Act5BaalSpawnLayout.candidateY(i);
      assertTrue(Math.hypot(x, y) >= 2.5d);
      assertTrue(offsets.add(x + ":" + y));
    }
    assertEquals(16, offsets.size());
  }

  @Test
  void candidateOrderWrapsForAllNativeWaveGroupSizes() {
    assertEquals(Act5BaalSpawnLayout.candidateX(0),
        Act5BaalSpawnLayout.candidateX(Act5BaalSpawnLayout.candidateCount()));
    assertEquals(Act5BaalSpawnLayout.candidateY(0),
        Act5BaalSpawnLayout.candidateY(Act5BaalSpawnLayout.candidateCount()));
    assertEquals(-1f, Act5BaalSpawnLayout.FACING_Y);
  }
}
