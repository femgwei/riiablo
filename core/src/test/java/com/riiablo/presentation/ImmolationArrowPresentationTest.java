package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.d2moo.common.drlg.D2Seed;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.Colors;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.skill.SkillFormula;
import org.junit.jupiter.api.Test;

/** Native 1.10f Immolation Arrow presentation contracts, without a GL context. */
class ImmolationArrowPresentationTest extends RiiabloTest {
  @org.junit.jupiter.api.BeforeAll
  public static void initColors() { Riiablo.colors = new Colors(); }
  @Test void impactUsesNativeSparseFireChildrenAndSkillRadius() {
    Missiles.Entry arrow = Riiablo.files.Missiles.get("immolationarrow");
    Skills.Entry skill = Riiablo.files.skills.get("Immolation Arrow");
    assertNotNull(arrow);
    assertNotNull(skill);
    assertEquals(27, skill.Id);
    assertEquals("immolationarrow", skill.cltmissile);
    assertEquals(12, arrow.pCltHitFunc);
    assertArrayEquals(new String[] {"firewall", "firesmall", "firemedium"},
        java.util.Arrays.copyOf(arrow.CltHitSubMissile, 3));
    assertEquals(0, arrow.cHitPar[0], "zero selects the skill radius calculation");
    assertEquals(75, arrow.cHitPar[1], "each circle cell has a 75% creation gate");
    assertEquals("75", arrow.SHitCalc1);
    assertEquals("75", arrow.CHitCalc1);
    for (int level : new int[] {1, 7, 20}) {
      assertEquals(2, SkillFormula.evaluate(skill.calc1, skill, level));
      assertEquals(75, MissileImpactPresentationSystem.nativeImmolationHitLifetime(
          arrow, new Missile(), level), "fire lifetime is not the arrow's Range=40");
    }
  }

  @Test void arrowKeepsNativeTravelAndSingleImpactSoundWiring() {
    Missiles.Entry arrow = Riiablo.files.Missiles.get("immolationarrow");
    assertNotNull(arrow);
    assertEquals("FireArrow", arrow.CelFile);
    assertEquals("amazon_firearrow_1", arrow.TravelSound);
    assertEquals("explosion_medium_1", arrow.HitSound);
    assertEquals(10, arrow.HitClass);
    assertTrue(arrow.ExplosionMissile == null || arrow.ExplosionMissile.isEmpty());
  }

  @Test void groundFireRetainsNativeSubLoopLightAndLifetime() {
    Missile fire = groundFire();
    Missiles.Entry row = fire.missile;
    assertEquals(230, row.Id);
    assertEquals(5, row.pCltDoFunc);
    assertEquals(75, row.Range);
    assertEquals("groundFireBig", row.CelFile);
    assertEquals(37, row.AnimLen);
    assertEquals(1, row.SubLoop);
    assertEquals(12, row.SubStart);
    assertEquals(36, row.SubStop);
    assertEquals(5, row.Light);
    assertEquals(4, row.Flicker);
    assertEquals("object_fire_loop_magic", row.TravelSound);
    assertTrue(NativeGroundFirePresentation.handles(fire));
    assertFalse(NativeGroundFirePresentation.handles(new Missile()));
    assertFalse(NativeGroundFirePresentation.handles(null));
  }

  @Test void groundFireRandomPhaseUsesClientSeedWithoutConsumingDamageSeed() {
    Missile fire = groundFire();
    fire.nativeSeed = new D2Seed(0x12345678, 666);
    fire.rngState = 0x76543210;
    Animation animation = animationAt(11);
    long oracle = Integer.toUnsignedLong(0x12345678) * 0x6AC690C5L + 666L;
    int selected = 11 + (int) ((oracle & 0xffffffffL) % 24);

    NativeGroundFirePresentation.adjustFrame(fire, animation, 50);

    verify(animation).setFrame(selected);
    assertEquals((int) oracle, fire.clientSeed.getNLowSeed());
    assertEquals((int) (oracle >>> 32), fire.clientSeed.getNHighSeed());
    assertEquals(0x12345678, fire.nativeSeed.getNLowSeed());
    assertEquals(666, fire.nativeSeed.getNHighSeed());
    assertEquals(0x76543210, fire.rngState);
    assertNotSame(fire.nativeSeed, fire.clientSeed);
  }

  @Test void groundFireEndingRewindsFromRemainingLifetime() {
    Missile fire = groundFire();
    Animation endStart = animationAt(30);
    NativeGroundFirePresentation.adjustFrame(fire, endStart, 12);
    verify(endStart).setFrame(9);

    Animation ending = animationAt(9);
    NativeGroundFirePresentation.adjustFrame(fire, ending, 11);
    verify(ending).setFrame(7);

    Animation clamped = animationAt(1);
    NativeGroundFirePresentation.adjustFrame(fire, clamped, 1);
    verify(clamped).setFrame(0);

    Animation steady = animationAt(20);
    NativeGroundFirePresentation.adjustFrame(fire, steady, 13);
    verify(steady, never()).setFrame(anyInt());
    assertNull(fire.clientSeed, "ending/steady branches do not roll random numbers");
  }

  private static Missile groundFire() {
    Missile fire = new Missile();
    fire.missile = Riiablo.files.Missiles.get("immolationfire");
    assertNotNull(fire.missile);
    return fire;
  }

  private static Animation animationAt(int frame) {
    Animation animation = mock(Animation.class);
    when(animation.getNumFramesPerDir()).thenReturn(37);
    when(animation.getFrame()).thenReturn(frame);
    return animation;
  }
}
