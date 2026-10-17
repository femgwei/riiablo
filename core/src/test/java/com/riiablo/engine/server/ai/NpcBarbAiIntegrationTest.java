package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.NativeTargetNode;
import org.junit.jupiter.api.Test;

class NpcBarbAiIntegrationTest extends RiiabloTest {
  private String row() {
    for (MonStats.Entry row : Riiablo.files.monstats) if ("NpcBarb".equals(row.AI)) return row.Id;
    throw new AssertionError("Missing native NpcBarb row");
  }

  @Test void nativeDefenderRegistersOnceAndTargetsMonstersInsteadOfPlayers() {
    try (Act1NativeAiIntegrationTest.Fixture f = new Act1NativeAiIntegrationTest.Fixture(row(), 0, 1)) {
      assertTrue(f.ai instanceof NpcBarb);
      NpcBarb ai = (NpcBarb) f.ai;
      assertFalse(ai.isValidEnemyTarget(f.target));
      int evil = f.ally("fallen1", 100, 11, 10);
      f.world.process();
      assertTrue(ai.isValidEnemyTarget(evil));
      ai.update(.04f);
      NativeTargetNode node = f.world.getMapper(NativeTargetNode.class).get(f.actor);
      assertTrue(node.isNode8());
      long order = node.order();
      ai.beforeDecision();
      assertEquals(order, node.order());
      assertNotNull(f.casting());
      assertEquals(evil, f.casting().targetId);
    }
  }

  @Test void combatKeepsAttackAndRunUsesStrictDistanceBoundary() {
    try (Act1NativeAiIntegrationTest.Fixture f = new Act1NativeAiIntegrationTest.Fixture(row(), 0, 10)) {
      NpcBarb ai = (NpcBarb) f.ai;
      ai.think(f.target, 1, true);
      assertNotNull(f.casting());
      f.finishAction();
      ai.params[1] = 100; ai.params[2] = 20;
      clearInvocations(f.paths);
      ai.think(f.target, 19, false);
      verify(f.paths).findPath(eq(f.actor), any(), eq(true), eq(f.target));
      clearInvocations(f.paths);
      ai.think(f.target, 20, false);
      verify(f.paths).findPath(eq(f.actor), any(), eq(false), eq(-1));
    }
  }

  @Test void failedWanderTriesThreePathsAndIdles() {
    try (Act1NativeAiIntegrationTest.Fixture f = new Act1NativeAiIntegrationTest.Fixture(row(), 0, 10)) {
      when(f.paths.findPath(anyInt(), any(), anyBoolean(), anyInt())).thenReturn(false);
      ((NpcBarb) f.ai).thinkWithoutTarget();
      verify(f.paths, times(3)).findPath(eq(f.actor), any(), eq(false), eq(-1));
      assertNull(f.casting());
    }
  }
}
