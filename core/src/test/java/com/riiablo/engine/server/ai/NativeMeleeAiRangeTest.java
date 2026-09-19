package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import org.junit.jupiter.api.Test;

class NativeMeleeAiRangeTest {
  @Test
  void footprintRangeAllowsAttackBeforeCentersOverlap() {
    Position source = new Position();
    Position target = new Position();
    source.position.set(10f, 10f);
    target.position.set(12f, 10f);

    assertTrue(AI.isInNativeMeleeRange(
        source, Size.MEDIUM, target, Size.MEDIUM, 0),
        "touching collision footprints must count as native melee range");
    target.position.set(14f, 10f);
    assertFalse(AI.isInNativeMeleeRange(
        source, Size.MEDIUM, target, Size.MEDIUM, 0));
  }
}
