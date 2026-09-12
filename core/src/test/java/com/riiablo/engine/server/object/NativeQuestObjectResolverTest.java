package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Objects;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.event.QuestObjectInteractionEvent;
import com.riiablo.map.NativePresetObjectResolver;

class NativeQuestObjectResolverTest {
  @Test
  void classifiesAct1QuestObjectsByNativeClassAndInitFunction() {
    assertEquals(NativeQuestObjectResolver.Type.TOWER_TOME, resolve(8, 4));
    for (int id = 17; id <= 22; id++) {
      assertEquals(NativeQuestObjectResolver.Type.CAIRN_STONE, resolve(id, 6));
    }
    assertEquals(NativeQuestObjectResolver.Type.CAIN_GIBBET, resolve(26, 7));
    assertEquals(NativeQuestObjectResolver.Type.INIFUSS_TREE, resolve(30, 9));
    assertEquals(NativeQuestObjectResolver.Type.HORADRIC_MALUS, resolve(108, 15));
    assertEquals(NativeQuestObjectResolver.Type.TAINTED_SUN_ALTAR, resolve(149, 24));
    assertEquals(NativeQuestObjectResolver.Type.HORADRIC_ORIFICE, resolve(152, 21));
    assertEquals(NativeQuestObjectResolver.Type.ARCANE_SANCTUARY_TOME, resolve(357, 42));
    assertEquals(NativeQuestObjectResolver.Type.GIDBINN_DECOY, resolve(252, 31));
    assertEquals(NativeQuestObjectResolver.Type.KHALIM_CHEST, resolve(405, 57));
    assertEquals(NativeQuestObjectResolver.Type.KHALIM_CHEST, resolve(406, 58));
    assertEquals(NativeQuestObjectResolver.Type.KHALIM_CHEST, resolve(407, 59));
    assertEquals(NativeQuestObjectResolver.Type.COMPELLING_ORB, resolve(404, 53));
    assertEquals(NativeQuestObjectResolver.Type.DIABLO_SEAL, resolve(392, 34));
    assertEquals(NativeQuestObjectResolver.Type.DIABLO_SEAL, resolve(396, 38));
    assertEquals(NativeQuestObjectResolver.Type.HELLFORGE, resolve(376, 49));
    assertEquals(NativeQuestObjectResolver.Type.CAGED_SOLDIER, resolve(473, 71));
    assertEquals(NativeQuestObjectResolver.Type.FROZEN_ANYA, resolve(558, 71));
    for (int id = 474; id <= 476; id++) {
      assertEquals(NativeQuestObjectResolver.Type.ANCIENT_STATUE, resolve(id, 71));
    }
    assertEquals(NativeQuestObjectResolver.Type.BAAL_PORTAL, resolve(563, 71));
    assertEquals(NativeQuestObjectResolver.Type.LAST_PORTAL, resolve(565, 71));
    assertEquals(NativeQuestObjectResolver.Type.COUNTESS_CHEST, resolve(500, 47));
    assertEquals(NativeQuestObjectResolver.Type.NONE, resolve(5, 3));
  }

  @Test
  void questAwareLifecyclePreventsTowerTomeFromOrdinaryDrops() {
    Objects.Entry tome = object(8, 4);
    tome.OperateFn = 6;
    assertEquals(NativeObjectOperateTable.Lifecycle.ANIMATED_CONTAINER,
        NativeObjectOperateTable.resolve(6, false,
            NativePresetObjectResolver.Kind.ORDINARY));
    assertEquals(NativeObjectOperateTable.Lifecycle.QUEST_OBJECT,
        NativeObjectOperateTable.resolve(tome,
            NativePresetObjectResolver.Kind.ORDINARY));
  }

  @Test
  void onlySafeQuestObjectHasDefaultActivation() {
    QuestObjectInteractionEvent tome = QuestObjectInteractionEvent.obtain(
        1, 2, 8, NativeQuestObjectResolver.Type.TOWER_TOME);
    assertTrue(tome.accepted);
    assertTrue(tome.oneShot);
    assertEquals(Engine.Object.MODE_ON, tome.targetMode);

    QuestObjectInteractionEvent cairn = QuestObjectInteractionEvent.obtain(
        1, 3, 17, NativeQuestObjectResolver.Type.CAIRN_STONE);
    assertFalse(cairn.accepted);
    cairn.accept(Engine.Object.MODE_OP);
    assertTrue(cairn.accepted);
    assertEquals(Engine.Object.MODE_OP, cairn.targetMode);
  }

  @Test
  void sanctuaryTomeUsesQuestOwnedOperateLifecycle() {
    Objects.Entry tome = object(357, 42);
    assertEquals(NativeObjectOperateTable.Lifecycle.QUEST_OBJECT,
        NativeObjectOperateTable.resolve(tome,
            NativePresetObjectResolver.Kind.ORDINARY));
  }

  private static NativeQuestObjectResolver.Type resolve(int id, int initFn) {
    return NativeQuestObjectResolver.resolve(object(id, initFn));
  }

  private static Objects.Entry object(int id, int initFn) {
    Objects.Entry object = new Objects.Entry();
    object.Id = id;
    object.InitFn = initFn;
    return object;
  }
}
