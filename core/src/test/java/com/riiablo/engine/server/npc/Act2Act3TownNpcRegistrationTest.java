package com.riiablo.engine.server.npc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.engine.server.monster.MonsterType;

class Act2Act3TownNpcRegistrationTest {
  @Test void registersAct2TownNpcIds() {
    NpcDialogManager manager = new NpcDialogManager();
    int[] ids = {175, 176, 177, 178, 198, 199, 200, 201, 202, 210, 244};
    for (int id : ids) assertNotNull(manager.getNpcDefinition(id), "missing A2 NPC " + id);
  }

  @Test void registersAct3TownNpcIds() {
    NpcDialogManager manager = new NpcDialogManager();
    int[] ids = {245, 252, 253, 254, 255, 264, 297};
    for (int id : ids) assertNotNull(manager.getNpcDefinition(id), "missing A3 NPC " + id);
  }

  @Test void registersAct4TownNpcIds() {
    NpcDialogManager manager = new NpcDialogManager();
    int[] ids = {MonsterType.HALBU, MonsterType.JAMELLA, MonsterType.TYRAEL2};
    for (int id : ids) assertNotNull(manager.getNpcDefinition(id), "missing A4 NPC " + id);

    assertEquals("Halbu", manager.getNpcDefinition(MonsterType.HALBU).name);
    assertEquals("Jamella", manager.getNpcDefinition(MonsterType.JAMELLA).name);
    assertEquals("Tyrael", manager.getNpcDefinition(MonsterType.TYRAEL2).name);
  }

  @Test void registersAct5TownNpcIds() {
    NpcDialogManager manager = new NpcDialogManager();
    int[] ids = {
        MonsterType.LARZUK,
        MonsterType.DREHYA,
        MonsterType.MALAH,
        MonsterType.NIHLATHAK,
        MonsterType.QUAL_KEHK
    };
    for (int id : ids) assertNotNull(manager.getNpcDefinition(id), "missing A5 NPC " + id);

    NpcDialogManager.NpcDefinition anya = manager.getNpcDefinition(MonsterType.DREHYA);
    assertEquals("Anya", anya.name);
    assertTrue(anya.isVendor);
    assertTrue(anya.canGamble);

    NpcDialogManager.NpcDefinition nihlathak = manager.getNpcDefinition(MonsterType.NIHLATHAK);
    assertEquals("Nihlathak", nihlathak.name);
    assertEquals(NpcDialogManager.NPC_TYPE_GAMBLER, nihlathak.npcType);
    assertTrue(nihlathak.canGamble);

    NpcDialogManager.NpcDefinition qualKehk = manager.getNpcDefinition(MonsterType.QUAL_KEHK);
    assertEquals(NpcDialogManager.NPC_TYPE_HIRELING, qualKehk.npcType);
    assertTrue(qualKehk.canHire);
  }
}
