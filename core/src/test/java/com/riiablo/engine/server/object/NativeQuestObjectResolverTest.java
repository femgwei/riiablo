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
