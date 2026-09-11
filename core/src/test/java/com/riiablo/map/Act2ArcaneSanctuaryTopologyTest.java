package com.riiablo.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class Act2ArcaneSanctuaryTopologyTest {
  @Test
  void nativeRoomCountAndBranchForksArePreserved() {
    Act2ArcaneSanctuaryTopology topology =
        Act2ArcaneSanctuaryTopology.generate(NativeLevelSeed.forLevel(123, 75));
    assertEquals(1 + 4 * 15, topology.rooms().size());
    assertEquals(4 * 15, topology.edges().size());
    Set<Integer> roomIds = new HashSet<>();
    for (Act2ArcaneSanctuaryTopology.Room room : topology.rooms()) {
      assertTrue(roomIds.add(room.id()));
    }
    for (int branch = 0; branch < 4; branch++) {
      int first = 1 + branch * 15;
      int forkAtEight = first + 8;
      int forkAtNine = first + 9;
      int forkAtTwelve = first + 12;
      int forkAtThirteen = first + 13;
      assertTrue(hasEdge(topology, Act2ArcaneSanctuaryTopology.CENTER_ROOM_ID, first));
      assertTrue(hasEdge(topology, first + 7, forkAtEight));
      assertTrue(hasEdge(topology, first + 7, forkAtNine));
      assertTrue(hasEdge(topology, first + 11, forkAtTwelve));
      assertTrue(hasEdge(topology, first + 11, forkAtThirteen));
    }
  }

  @Test
  void selectedSummonerBranchMatchesNativeDirectionMapping() {
    for (int seed = 0; seed < 128; seed++) {
      Act2ArcaneSanctuaryTopology topology =
          Act2ArcaneSanctuaryTopology.generate(seed);
      assertEquals(Act2ArcaneSanctuaryLayout.fromLevelSeed(seed),
          topology.summonerDirection());
      assertEquals(topology.summonerDirection().summonerPresetDef(),
          Act2ArcaneSanctuaryLayout.Direction.values()[topology.summonerBranch()]
              .summonerPresetDef());
    }
  }

  private static boolean hasEdge(Act2ArcaneSanctuaryTopology topology, int from, int to) {
    for (Act2ArcaneSanctuaryTopology.Edge edge : topology.edges()) {
      if (edge.from() == from && edge.to() == to) return true;
    }
    return false;
  }
}
