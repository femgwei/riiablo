package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

class NativeJabSequenceTest {
  @Test
  void oneHandThrustUsesTheNativeEighteenPointSequence() {
    assertEquals(18, NativeJabSequence.frameCount(Engine.WEAPON_1HT));
    assertEquals(Engine.Player.MODE_A1, NativeJabSequence.mode(Engine.WEAPON_1HT, 0));
    assertEquals(5, NativeJabSequence.sourceFrame(Engine.WEAPON_1HT, 0));
    assertEquals(NativeJabSequence.EVENT_MELEE_ATTACK,
        NativeJabSequence.event(Engine.WEAPON_1HT, 3));
    assertEquals(NativeJabSequence.EVENT_MELEE_ATTACK,
        NativeJabSequence.event(Engine.WEAPON_1HT, 9));
    assertEquals(NativeJabSequence.EVENT_MELEE_ATTACK,
        NativeJabSequence.event(Engine.WEAPON_1HT, 15));
  }

  @Test
  void twoHandThrustUsesTheNativeTwentyOnePointSequence() {
    assertEquals(21, NativeJabSequence.frameCount(Engine.WEAPON_2HT));
    assertEquals(2, NativeJabSequence.sourceFrame(Engine.WEAPON_2HT, 0));
    assertEquals(4, NativeJabSequence.sourceFrame(Engine.WEAPON_2HT, 7));
    assertEquals(NativeJabSequence.EVENT_MELEE_ATTACK,
        NativeJabSequence.event(Engine.WEAPON_2HT, 3));
    assertEquals(NativeJabSequence.EVENT_MELEE_ATTACK,
        NativeJabSequence.event(Engine.WEAPON_2HT, 10));
    assertEquals(NativeJabSequence.EVENT_MELEE_ATTACK,
        NativeJabSequence.event(Engine.WEAPON_2HT, 16));
  }
}
