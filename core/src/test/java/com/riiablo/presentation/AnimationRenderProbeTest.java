package com.riiablo.presentation;

import com.riiablo.codec.Animation;
import com.riiablo.codec.COF;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnimationRenderProbeTest {
  private com.riiablo.Colors savedColors;
  @org.junit.jupiter.api.BeforeEach void colors() {
    savedColors = com.riiablo.Riiablo.colors;
    com.riiablo.Riiablo.colors = new com.riiablo.Colors();
  }
  @org.junit.jupiter.api.AfterEach void restoreColors() {
    com.riiablo.Riiablo.colors = savedColors;
  }
  @Test void ignoresMissingDrawWrongModeAndStaleComposite() {
    Animation animation = mock(Animation.class);
    COF cof = mock(COF.class);
    when(animation.getCOF()).thenReturn(cof);
    when(animation.getNumFramesPerDir()).thenReturn(2);
    AnimationRenderProbe probe = new AnimationRenderProbe(7, (byte) 9, 1);
    probe.record(7, (byte) 9, 1, cof, animation);
    assertFalse(probe.complete());
    when(animation.getSubmittedLayerCount()).thenReturn(1);
    probe.record(8, (byte) 9, 1, cof, animation);
    probe.record(7, (byte) 5, 1, cof, animation);
    probe.record(7, (byte) 9, 1, mock(COF.class), animation);
    when(animation.getFrame()).thenReturn(1);
    probe.record(7, (byte) 9, 1, cof, animation);
    assertFalse(probe.complete(), "last frame alone is not complete playback");
    when(animation.getFrame()).thenReturn(0);
    probe.record(7, (byte) 9, 1, cof, animation);
    assertTrue(probe.complete());
  }

  @Test void consecutiveActionsCannotCombineIntoOneCompleteAnimation() {
    Animation animation = mock(Animation.class);
    COF cof = mock(COF.class);
    when(animation.getCOF()).thenReturn(cof);
    when(animation.getSubmittedLayerCount()).thenReturn(1);
    when(animation.getNumFramesPerDir()).thenReturn(2);
    AnimationRenderProbe probe = new AnimationRenderProbe(7, (byte) 9, 1);
    probe.record(7, (byte) 9, 1, cof, animation);
    when(animation.getFrame()).thenReturn(1);
    probe.record(7, (byte) 9, 2, cof, animation);
    assertFalse(probe.complete());
  }

  @Test void visibleBodyCannotStandInForMissingRequiredShield() {
    Animation animation = mock(Animation.class);
    COF cof = mock(COF.class);
    when(animation.getCOF()).thenReturn(cof);
    when(animation.getSubmittedLayerCount()).thenReturn(1);
    when(animation.getNumFramesPerDir()).thenReturn(1);
    AnimationRenderProbe probe = new AnimationRenderProbe(7, (byte) 9, 1).requireComponents(16);
    when(animation.getSubmittedComponentMask()).thenReturn(1);
    probe.record(7, (byte) 9, 1, cof, animation);
    assertFalse(probe.complete());
    when(animation.getSubmittedComponentMask()).thenReturn(17);
    probe.record(7, (byte) 9, 1, cof, animation);
    assertTrue(probe.complete());
  }
}
