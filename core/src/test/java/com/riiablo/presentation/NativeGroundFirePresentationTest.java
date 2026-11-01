package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.codec.Animation;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.codec.excel.Missiles;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

class NativeGroundFirePresentationTest extends com.riiablo.RiiabloTest {
  @org.junit.jupiter.api.BeforeAll
  public static void initColors() { com.riiablo.Riiablo.colors = new com.riiablo.Colors(); }
  @Test
  void handlesOnlyCltDoFive() {
    Missile missile = missile(5, 12, 36);
    assertTrue(NativeGroundFirePresentation.handles(missile));
    missile.missile.pCltDoFunc = 4;
    assertTrue(!NativeGroundFirePresentation.handles(missile));
  }

  @Test
  void entersSubLoopAtNativeRandomizedFrame() throws Exception {
    Missile missile = missile(5, 12, 36);
    missile.presentationSeed = 1;
    missile.presentationSeedKnown = true;
    Animation animation = animationAt(11);

    NativeGroundFirePresentation.adjustFrame(missile, animation, 60);

    // The native expression is (SubStart - 1) + roll(24), i.e. 11..34.
    verify(animation).setFrame(anyInt());
  }

  @Test
  void rewindsAtSubStartAndDuringFinalFrames() throws Exception {
    Missile missile = missile(5, 12, 36);
    Animation animation = animationAt(20);

    NativeGroundFirePresentation.adjustFrame(missile, animation, 12);
    verify(animation).setFrame(9);

    Animation ending = animationAt(10);
    NativeGroundFirePresentation.adjustFrame(missile, ending, 11);
    verify(ending).setFrame(8);
  }

  private static Missile missile(int callback, int subStart, int subStop) {
    Missile missile = new Missile();
    missile.missile = new Missiles.Entry();
    missile.missile.pCltDoFunc = callback;
    missile.missile.SubStart = subStart;
    missile.missile.SubStop = subStop;
    return missile;
  }

  private static Animation animationAt(int frame) {
    Animation animation = mock(Animation.class);
    when(animation.getNumFramesPerDir()).thenReturn(64);
    when(animation.getFrame()).thenReturn(frame);
    return animation;
  }
}
