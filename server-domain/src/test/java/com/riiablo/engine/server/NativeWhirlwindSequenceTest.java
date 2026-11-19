package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.riiablo.engine.Engine;
import org.junit.jupiter.api.Test;

class NativeWhirlwindSequenceTest {
  @Test
  void matchesD2CommonSequenceTbls() {
    assertEquals(8, NativeWhirlwindSequence.frameCount());
    assertArrayEquals(new byte[] {0, 1, 2, 3, 3, 4, 5, 6}, sourceFrames());
    assertArrayEquals(new byte[] {0, 0, 0, 1, 0, 0, 0, 1}, events());
    for (int point = 0; point < 8; point++)
      assertEquals(Engine.Player.MODE_A1, NativeWhirlwindSequence.mode(point));
  }

  @Test
  void rejectsPointsOutsideTheNativeTable() {
    assertThrows(IndexOutOfBoundsException.class,
        () -> NativeWhirlwindSequence.sourceFrame(-1));
    assertThrows(IndexOutOfBoundsException.class,
        () -> NativeWhirlwindSequence.event(8));
  }

  private static byte[] sourceFrames() {
    byte[] result = new byte[NativeWhirlwindSequence.frameCount()];
    for (int i = 0; i < result.length; i++) result[i] = NativeWhirlwindSequence.sourceFrame(i);
    return result;
  }

  private static byte[] events() {
    byte[] result = new byte[NativeWhirlwindSequence.frameCount()];
    for (int i = 0; i < result.length; i++) result[i] = NativeWhirlwindSequence.event(i);
    return result;
  }
}
