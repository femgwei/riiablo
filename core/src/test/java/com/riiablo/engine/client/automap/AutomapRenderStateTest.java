package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AutomapRenderStateTest {
  @Test
  void entersAndLeavesSpritePassInOrder() {
    AutomapRenderState.Phase phase = AutomapRenderState.Phase.SHAPES;
    phase = AutomapRenderState.enterSprites(phase);
    assertEquals(AutomapRenderState.Phase.SPRITES, phase);
    phase = AutomapRenderState.leaveSprites(phase);
    assertEquals(AutomapRenderState.Phase.SHAPES, phase);
  }

  @Test
  void rejectsNestedSpritePass() {
    assertThrows(IllegalStateException.class,
        () -> AutomapRenderState.enterSprites(AutomapRenderState.Phase.SPRITES));
  }

  @Test
  void rejectsLeavingWithoutSpritePass() {
    assertThrows(IllegalStateException.class,
        () -> AutomapRenderState.leaveSprites(AutomapRenderState.Phase.SHAPES));
  }

  @Test
  void exceptionPathLeavesRendererInShapesPhase() {
    AutomapRenderState.Phase phase = AutomapRenderState.Phase.SHAPES;
    try {
      phase = AutomapRenderState.enterSprites(phase);
      throw new RuntimeException("simulated native sprite failure");
    } catch (RuntimeException expected) {
      // Mirrors renderNativeEntitySprites' finally transition.
      phase = AutomapRenderState.leaveSprites(phase);
    }
    assertEquals(AutomapRenderState.Phase.SHAPES, phase);
  }
}
