package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

class NativeDeathMaulTest {
  @Test void nativeIntegerDistanceUsesFloorAndHalfOfSmallerAxis() {
    assertEquals(12, NativeDeathMaul.distance(new Vector2(10.9f,10.9f), new Vector2(21.1f,12.1f)));
    assertEquals(12, NativeDeathMaul.distance(new Vector2(21.1f,12.1f), new Vector2(10.9f,10.9f)));
    assertEquals(1, NativeDeathMaul.distance(new Vector2(-.1f,-.1f), Vector2.Zero));
  }
  @Test void preservesNativeTruncationAndNineToTenDiscontinuity() {
    assertEquals(32, NativeDeathMaul.lifetime(60, 0));
    assertEquals(32, NativeDeathMaul.lifetime(60, 1));
    assertEquals(52, NativeDeathMaul.lifetime(60, 9));
    assertEquals(50, NativeDeathMaul.lifetime(60, 10));
    assertEquals(500, NativeDeathMaul.lifetime(60, 100));
  }
  @Test void differentMaulsShareTargetNextDelay() {
    com.riiablo.codec.excel.Missiles.Entry row = new com.riiablo.codec.excel.Missiles.Entry();
    row.NextHit = true; row.NextDelay = 10;
    com.riiablo.engine.server.component.Missile first = new com.riiablo.engine.server.component.Missile();
    com.riiablo.engine.server.component.Missile second = new com.riiablo.engine.server.component.Missile();
    first.missile = second.missile = row;
    first.deathMaul = second.deathMaul = true;
    com.riiablo.engine.server.state.StateList target = new com.riiablo.engine.server.state.StateList(7);
    assertTrue(MissileCollisionSystem.claimTargetHit(first, 7, target));
    assertFalse(MissileCollisionSystem.claimTargetHit(second, 7, target));
    for (int i = 0; i < 9; i++) target.update();
    assertFalse(MissileCollisionSystem.claimTargetHit(first, 7, target));
    target.update();
    assertTrue(MissileCollisionSystem.claimTargetHit(first, 7, target));
  }
}
