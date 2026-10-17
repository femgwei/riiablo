package com.riiablo.map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MonsterSpawnIntegerSelectionTest {
  @Test void floatIntegerValuesDoNotEstablishProvenance() {
    Map.RoomEx room = new Map.RoomEx(0, 0, 0, 100, 100);
    room.addMonsterSpawn(1, 10, 20, 7, false);
    Map.MonsterSpawn spawn = room.getPendingMonsterSpawns().first();
    assertFalse(spawn.hasIntegerSelection());
    assertThrows(IllegalStateException.class, spawn::integerSelectionX);
    assertThrows(IllegalStateException.class, spawn::integerSelectionY);
  }

  @Test void explicitSelectionPreservesExactIntsEvenBeyondFloatAndRawDomains() {
    Map.RoomEx room = new Map.RoomEx(0, 0, 0, 100, 100);
    room.addIntegerMonsterSpawn(1, 16777217, -3, 7, true);
    Map.MonsterSpawn spawn = room.getPendingMonsterSpawns().first();
    assertTrue(spawn.hasIntegerSelection());
    assertEquals(16777217, spawn.integerSelectionX());
    assertEquals(-3, spawn.integerSelectionY());
    assertEquals(7, spawn.packId);
    assertTrue(spawn.minion);
    // Selection provenance is not permission to initialize uint16 native raw.
    assertNotEquals((double) spawn.integerSelectionX(), (double) spawn.x);
  }

  @Test void claimedPopulationCannotQueueAgain() {
    Map.RoomEx room = new Map.RoomEx(0, 0, 0, 100, 100);
    assertTrue(room.claimMonsterPopulation());
    room.addIntegerMonsterSpawn(1, 10, 20, 7, false);
    assertEquals(0, room.getPendingMonsterSpawns().size);
    assertFalse(room.claimMonsterPopulation());
  }
}
