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

  @Test void allFourFireLayersAccumulateInsteadOfOccludingTheirNeighbours() {
    for (String name : new String[] {"immolationfire", "firewall", "firesmall", "firemedium"}) {
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      assertEquals(1, row.Trans);
      assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE,
          MissileLoader.nativeBlendMode(row), name);
    }
  }

  @Test void realImpactKeepsTheCircularLayoutLifetimeAndEachChildsBirthSeed() {
    com.artemis.World[] worlds = new com.artemis.World[1];
    java.util.List<Integer> created = new java.util.ArrayList<>();
    com.riiablo.engine.EntityFactory factory = mock(com.riiablo.engine.EntityFactory.class,
        CALLS_REAL_METHODS);
    doAnswer(call -> {
      com.artemis.World world = worlds[0];
      int id = world.create();
      Missiles.Entry row = Riiablo.files.Missiles.get((Integer) call.getArgument(0));
      Missile child = world.getMapper(Missile.class).create(id);
      child.set(row, call.getArgument(2), row.Range);
      child.presentationSeedKnown = true;
      child.presentationSeed = 1000 + created.size();
      world.getMapper(com.riiablo.engine.server.component.Position.class).create(id)
          .position.set((com.badlogic.gdx.math.Vector2) call.getArgument(2));
      world.getMapper(com.riiablo.engine.server.component.Velocity.class).create(id);
      created.add(id);
      return id;
    }).when(factory).createMissile(anyInt(), any(com.badlogic.gdx.math.Vector2.class),
        any(com.badlogic.gdx.math.Vector2.class), anyInt());
    MissileImpactPresentationSystem system = new MissileImpactPresentationSystem();
    com.artemis.World world = worlds[0] = new com.artemis.World(
        new com.artemis.WorldConfigurationBuilder().with(
            new net.mostlyoriginal.api.event.common.EventSystem(), system,
            new OverlayManager(), factory).build()
            .register("factory", factory)
            .register("map", new com.riiablo.map.Map(0, 0))
            .register("clientContext", ClientGameplayContext.fromGlobals()));
    try {
      int sourceId = world.create();
      Missile source = world.getMapper(Missile.class).create(sourceId);
      source.missile = Riiablo.files.Missiles.get("immolationarrow");
      source.skillId = 27;
      source.damageLevel = 7;
      source.presentationSeedKnown = true;
      source.presentationSeed = 0x12345678;
      source.nativeSeed = new D2Seed(42, 666);
      com.riiablo.engine.server.event.MissileImpactEvent event =
          com.riiablo.engine.server.event.MissileImpactEvent.obtain(sourceId,
              source.missile.Id, -1, -1, new com.badlogic.gdx.math.Vector2(10.75f, 20.25f));
      system.onMissileImpact(event);
      long low = Integer.toUnsignedLong(source.presentationSeed), high = 666;
      int accepted = 0;
      for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) {
        if (x * x + y * y > 4) continue;
        long bits = low * 0x6AC690C5L + high;
        low = bits & 0xffffffffL; high = bits >>> 32;
        int ttl = 63 + (int) (low % 25);
        bits = low * 0x6AC690C5L + high;
        low = bits & 0xffffffffL; high = bits >>> 32;
        int childType = (int) (low % 3);
        bits = low * 0x6AC690C5L + high;
        low = bits & 0xffffffffL; high = bits >>> 32;
        if (low % 100 >= 75) continue;
        int id = created.get(accepted);
        Missile fire = world.getMapper(Missile.class).get(id);
        assertEquals(source.missile.CltHitSubMissile[childType], fire.missile.Missile);
        assertEquals(ttl, fire.nativeLifetimeFrames);
        assertEquals(1000 + accepted, fire.presentationSeed,
            "the impact must not replace the child's distinct allocation seed");
        assertTrue(fire.presentationOnly);
        assertFalse(fire.authoritative);
        assertEquals(27, fire.skillId);
        assertEquals(7, fire.damageLevel);
        com.badlogic.gdx.math.Vector2 position = world.getMapper(
            com.riiablo.engine.server.component.Position.class).get(id).position;
        assertEquals(10 + x, position.x, .0001f);
        assertEquals(20 + y, position.y, .0001f);
        accepted++;
      }
      assertEquals(accepted, created.size());
      assertTrue(accepted > 3);
      assertEquals(42, source.nativeSeed.getNLowSeed());
      assertEquals(666, source.nativeSeed.getNHighSeed());
    } finally { world.dispose(); }
  }

  @Test void groundFireUsesReferenceGrowthPhaseAndFade() {
    Missile fire = groundFire();
    fire.presentationSeedKnown = true;
    fire.presentationSeed = 0;
    assertEquals(0, NativeGroundFirePresentation.frameAt(fire, 37, 0));
    assertEquals(11, NativeGroundFirePresentation.frameAt(fire, 37, 11));
    assertEquals(28, NativeGroundFirePresentation.frameAt(fire, 37, 12));
    assertEquals(36, NativeGroundFirePresentation.frameAt(fire, 37, 20));
    assertEquals(12, NativeGroundFirePresentation.frameAt(fire, 37, 21));
    assertEquals(.5f, NativeGroundFirePresentation.fadeAlpha(5));
    assertEquals(0f, NativeGroundFirePresentation.fadeAlpha(0));
  }

  private static Missile groundFire() {
    Missile fire = new Missile();
    fire.missile = Riiablo.files.Missiles.get("immolationfire");
    assertNotNull(fire.missile);
    return fire;
  }

}
