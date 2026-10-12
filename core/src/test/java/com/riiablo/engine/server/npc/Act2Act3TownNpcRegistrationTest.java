package com.riiablo.engine.server.npc;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

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
    int[] ids = {257, 405, 406};
    for (int id : ids) assertNotNull(manager.getNpcDefinition(id), "missing A4 NPC " + id);
  }

  @Test void registersAct5TownNpcIds() {
    NpcDialogManager manager = new NpcDialogManager();
    int[] ids = {511, 512, 513, 514};
    for (int id : ids) assertNotNull(manager.getNpcDefinition(id), "missing A5 NPC " + id);
  }
}
