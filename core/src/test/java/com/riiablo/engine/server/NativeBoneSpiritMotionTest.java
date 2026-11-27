package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.server.missile.NativeBoneSpiritMotion;
import org.junit.jupiter.api.Test;

/** Native movement contract recovered from the minimized 1.10f Bone Spirit trace. */
class NativeBoneSpiritMotionTest extends RiiabloTest {
  @Test
  void usesNativeSeventyFivePercentVelocityAndQuantizedDirection() {
    Missiles.Entry row = Riiablo.files.Missiles.get("bonespirit");
    assertTrue(NativeBoneSpiritMotion.handles(row));

    Vector2 velocity = NativeBoneSpiritMotion.initialVelocity(
        row, 20, 0, new Vector2(1f, 0f), new Vector2());
    assertEquals(14.0625f, velocity.len(), 0.0001f,
        "MISSILES_CreateMissileFromParams applies 75% before native fp16 conversion");
    assertEquals(0f, velocity.y, 0.0001f);
  }
}
