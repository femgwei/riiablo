package com.riiablo.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regression coverage for the native Missiles.txt SubLoop playback contract. */
class AnimationSubLoopTest extends RiiabloTest {
  @BeforeEach
  void initializeColors() {
    Riiablo.colors = new com.riiablo.Colors();
  }

  @Test
  void loopsSubRangeOnlyAfterTheInitialFullAnimation() {
    Animation animation = Animation.newAnimation();
    animation.numFrames = 37;
    animation.startIndex = 0;
    animation.endIndex = 37;
    animation.setFrameDuration(1f);
    animation.setMode(Animation.Mode.LOOP);
    animation.setSubLoop(12, 36);

    assertEquals(0, animation.getFrame(0f));
    assertEquals(11, animation.getFrame(11.9f));
    assertEquals(12, animation.getFrame(12f));
    assertEquals(36, animation.getFrame(36f));
    assertEquals(12, animation.getFrame(37f));
    assertEquals(35, animation.getFrame(60f));
    assertEquals(12, animation.getFrame(61f));
  }
}
