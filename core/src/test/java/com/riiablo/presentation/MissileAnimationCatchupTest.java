package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.server.component.Missile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Asset publication follows the simulation clock without restarting a native DCC. */
class MissileAnimationCatchupTest extends RiiabloTest {
  @BeforeEach void initializeColors() { Riiablo.colors = new com.riiablo.Colors(); }

  @Test void delayedMonsterProjectileRetainsFractionalNativeProgress() {
    Missile missile = missile(5);
    Animation animation = animation(8, 128, Animation.Mode.LOOP);

    MissileLoader.initializePlayback(missile, missile.missile, animation, 1);

    assertEquals(3, animation.getFrame()); // 1 + 5 * 128/256 = 3.5
    animation.update(1f / 25f);
    assertEquals(4, animation.getFrame(), "the half-frame remainder survives publication");
    assertEquals(5, missile.nativeFrame, "loading never advances the simulation");
  }

  @Test void agedLoopAndOneShotUseTheirExistingPlaybackModes() {
    Missile missile = missile(12);
    Animation looping = animation(8, 256, Animation.Mode.LOOP);
    MissileLoader.initializePlayback(missile, missile.missile, looping, 1);
    assertEquals(5, looping.getFrame());

    Animation impact = animation(8, 256, Animation.Mode.CLAMP);
    MissileLoader.initializePlayback(missile, missile.missile, impact, 1);
    assertEquals(7, impact.getFrame(), "an old impact cannot become a fresh explosion");
  }

  @Test void delayedSubLoopRetainsUnwrappedPlaybackTime() {
    Missile missile = missile(15);
    Animation animation = animation(12, 256, Animation.Mode.LOOP);
    animation.setSubLoop(4, 8);
    MissileLoader.initializePlayback(missile, missile.missile, animation, 0);
    assertEquals(7, animation.getFrame());
    animation.update(1f / 25f);
    assertEquals(4, animation.getFrame());
  }

  @Test void groundFireKeepsItsCallbackControlledRandomPhase() {
    Missile missile = missile(100);
    missile.missile.CelFile = "groundFireBig";
    missile.missile.Light = 1;
    missile.missile.Flicker = 1;
    Animation animation = animation(37, 256, Animation.Mode.LOOP);
    MissileLoader.initializePlayback(missile, missile.missile, animation, 11);
    assertEquals(11, animation.getFrame());
  }

  @Test void cltDoFiveReloadRetainsReferenceLoopAgeInsteadOfRestartingGrowth() {
    Missile missile = missile(40);
    missile.missile.pCltDoFunc = 5;
    missile.missile.SubLoop = 1;
    missile.missile.SubStart = 12;
    missile.missile.SubStop = 36;
    missile.presentationSeedKnown = true;
    Animation animation = animation(37, 128, Animation.Mode.LOOP);
    MissileLoader.initializePlayback(missile, missile.missile, animation, 30);
    assertEquals(31, animation.getFrame());
    assertEquals(256, animation.getFrameDelta());
    NativeGroundFirePresentation.update(missile, animation, 35, .04f);
    assertEquals(32, animation.getFrame());
    MissileLoader.initializePlayback(missile, missile.missile, animation, 0);
    assertEquals(32, animation.getFrame(), "sparse snapshots/reloads cannot rewind local age");
  }

  private static Missile missile(int age) {
    Missile missile = new Missile();
    missile.missile = new Missiles.Entry();
    missile.nativeFrame = age;
    return missile;
  }

  private static Animation animation(int frames, int rate, Animation.Mode mode) {
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(frames);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    Animation animation = Animation.newAnimation();
    animation.edit().layer(dcc).build();
    animation.setMode(mode);
    animation.setFrameDelta(rate);
    return animation;
  }
}
