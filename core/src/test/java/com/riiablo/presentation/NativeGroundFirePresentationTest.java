package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.d2moo.common.drlg.D2Seed;
import com.riiablo.Riiablo;
import com.riiablo.codec.Animation;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.util.BBox;
import com.riiablo.engine.server.component.Missile;
import org.junit.jupiter.api.Test;

class NativeGroundFirePresentationTest extends com.riiablo.RiiabloTest {
  @org.junit.jupiter.api.BeforeAll
  public static void initColors() { Riiablo.colors = new com.riiablo.Colors(); }

  @Test void handlesEveryCltDoFiveRegardlessOfSpriteNameOrSkill() {
    for (Missiles.Entry row : Riiablo.files.Missiles) {
      Missile fire = new Missile();
      fire.missile = row;
      assertEquals(row.pCltDoFunc == 5, NativeGroundFirePresentation.handles(fire), row.Missile);
    }
    assertFalse(NativeGroundFirePresentation.handles(null));
    assertFalse(NativeGroundFirePresentation.handles(new Missile()));
  }

  @Test void playsGrowthBeforeRandomizedInclusiveLoop() {
    Missile fire = fire(); // immutable seed 0 yields phase 16
    Animation animation = animation();
    MissileLoader.initializePlayback(fire, fire.missile, animation, 30);
    assertEquals(0, animation.getFrame(), "random birth frames must not skip the growth");
    assertEquals(256, animation.getFrameDelta());
    assertFalse(animation.hasSubLoop(), "CltDo5 owns the APK loop directly");
    for (int age = 1; age <= 11; age++) {
      NativeGroundFirePresentation.update(fire, animation, 75, .04f);
      assertEquals(age, animation.getFrame());
    }
    NativeGroundFirePresentation.update(fire, animation, 75, .04f);
    assertEquals(28, animation.getFrame(), "phase is first applied at age 12");
    for (int age = 13; age <= 20; age++)
      NativeGroundFirePresentation.update(fire, animation, 75, .04f);
    assertEquals(36, animation.getFrame(), "loop end is inclusive");
    NativeGroundFirePresentation.update(fire, animation, 75, .04f);
    assertEquals(12, animation.getFrame());
  }

  @Test void randomPhaseIsStableAndNeverConsumesAuthoritativeOrClientRng() {
    Missile fire = fire();
    fire.nativeSeed = new D2Seed(42, 666);
    fire.clientSeed = new D2Seed(9, 666);
    assertEquals(16, NativeGroundFirePresentation.phase(fire));
    java.util.Set<Integer> phases = new java.util.HashSet<>();
    for (int seed = 0; seed < 100; seed++) {
      fire.presentationSeed = seed;
      int phase = NativeGroundFirePresentation.phase(fire);
      assertTrue(phase >= 0 && phase < 25);
      assertEquals(phase, NativeGroundFirePresentation.phase(fire));
      phases.add(phase);
    }
    assertTrue(phases.size() > 1);
    assertEquals(42, fire.nativeSeed.getNLowSeed());
    assertEquals(9, fire.clientSeed.getNLowSeed());
    assertEquals(666, fire.clientSeed.getNHighSeed());
  }

  @Test void lastTenTicksFadeWithoutRewindingAndReplicaClockKeepsAdvancing() {
    Missile fire = fire();
    fire.nativeFrame = 65;
    fire.nativeLifetimeFrames = 75;
    Animation animation = animation();
    MissileLoader.initializePlayback(fire, fire.missile, animation, 0);
    assertEquals(31, animation.getFrame());
    Animation.Layer layer = mock(Animation.Layer.class);
    Animation rendered = mock(Animation.class);
    when(rendered.getNumFramesPerDir()).thenReturn(37);
    when(rendered.getLayer(0)).thenReturn(layer);
    for (int tick = 1; tick <= 10; tick++) {
      NativeGroundFirePresentation.update(fire, rendered, 10, .04f);
      verify(layer).setAlpha((10 - tick) / 10f);
    }
    assertEquals(75, fire.clientFrame);
    assertEquals(65, fire.nativeFrame, "presentation cannot advance snapshot/damage time");
    verify(rendered).setFrame(36);
    verify(rendered).setFrame(12);
    verify(rendered, never()).update(anyFloat());
    assertEquals(1f, NativeGroundFirePresentation.fadeAlpha(50));
    assertEquals(0f, NativeGroundFirePresentation.fadeAlpha(-1));
  }

  private static Missile fire() {
    Missile fire = new Missile();
    fire.missile = new Missiles.Entry();
    fire.missile.pCltDoFunc = 5;
    fire.missile.SubLoop = 1;
    fire.missile.SubStart = 12;
    fire.missile.SubStop = 36;
    fire.presentationSeedKnown = true;
    return fire;
  }

  private static Animation animation() {
    DCC dcc = mock(DCC.class);
    when(dcc.getNumDirections()).thenReturn(1);
    when(dcc.getNumFramesPerDir()).thenReturn(37);
    when(dcc.getBox(anyInt())).thenReturn(new BBox());
    Animation animation = Animation.newAnimation();
    animation.edit().layer(dcc).build();
    animation.setMode(Animation.Mode.LOOP);
    animation.setFrameDelta(128);
    return animation;
  }
}
