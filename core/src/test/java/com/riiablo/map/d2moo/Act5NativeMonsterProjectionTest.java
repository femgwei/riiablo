package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.utils.Array;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class Act5NativeMonsterProjectionTest {
  @Test
  void queuesNativeMonsterIntoItsProjectedRoom() {
    Map.Zone zone = new Map.Zone();
    zone.setPosition(100, 200);
    Map.RoomEx room = zone.addRoomEx(100, 200, 80, 80);
    Array<Map.NativeMonster> monsters = new Array<>();
    monsters.add(new Map.NativeMonster(room.id, 42, 0, 12, 18, 7, "Super", false));

    assertEquals(1, Act5D2MOOLayoutBridge.queueNativeMonsters(zone, monsters));
    assertEquals(1, room.getPendingMonsterSpawns().size);
    Map.MonsterSpawn spawn = room.getPendingMonsterSpawns().first();
    assertEquals(42, spawn.monsterId);
    assertEquals(112f, spawn.x);
    assertEquals(218f, spawn.y);
    assertEquals(7, spawn.superUniqueId);
    assertEquals("Super", spawn.superUniqueKey);
  }

  @Test
  void fallsBackToCoordinateRoomAndSkipsAlreadySpawnedUnits() {
    Map.Zone zone = new Map.Zone();
    zone.setPosition(50, 75);
    Map.RoomEx room = zone.addRoomEx(50, 75, 40, 40);
    Array<Map.NativeMonster> monsters = new Array<>();
    monsters.add(new Map.NativeMonster(99, 11, 0, 10, 10, -1, null, false));
    monsters.add(new Map.NativeMonster(99, 12, 0, 11, 11, -1, null, true));

    assertEquals(1, Act5D2MOOLayoutBridge.queueNativeMonsters(zone, monsters));
    assertEquals(1, room.getPendingMonsterSpawns().size);
    assertEquals(11, room.getPendingMonsterSpawns().first().monsterId);
  }
}
