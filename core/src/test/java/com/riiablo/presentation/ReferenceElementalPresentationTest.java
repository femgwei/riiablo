package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.MissileImpactEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.presentation.component.ReferenceElementalEffect;
import com.riiablo.util.EngineUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceElementalPresentationTest extends RiiabloTest {
  @Test void referenceSpritesExistInTheReal110fMpqs() {
    String[] names = {"firestorm", "moltenboulderfirepath", "moltenboulder-flyingrocks",
        "erruption crack 1", "erruption smoke 1", "volcano", "volcano overlay fire",
        "volcano debris 2", "volcano explosion", "volcano small fire", "armageddontail",
        "armageddonrock", "armageddonexplosion", "hurricaneswoosh", "hurricanerock",
        "hurricanetree", "meteor", "meteortail", "meteorexplode", "blizzard1", "blizzard4"};
    for (String name : names) {
      Missiles.Entry row = Riiablo.files.Missiles.get(name);
      assertNotNull(row, name);
      String path = "data\\global\\missiles\\" + row.CelFile + ".dcc";
      assertTrue(Riiablo.mpqs.contains(path), path);
      DCC dcc = DCC.loadFromFile(Riiablo.mpqs.resolve(path));
      ReferenceElementalEffect profile = new ReferenceElementalEffect();
      ReferenceElementalPresentationSystem.configure(name, profile);
      assertTrue(dcc.getNumFramesPerDir() > profile.loopEnd, name);
      if ("erruption crack 1".equals(name)) assertEquals(4, dcc.getNumDirections());
      if ("hurricaneswoosh".equals(name)) assertEquals(8, dcc.getNumDirections());
    }
  }

  @Test void pixelOffsetsRoundTripWithoutReversingTheFall() {
    Vector2 at = new Vector2(23, 17);
    Vector2 offset = ReferenceElementalPresentationSystem.pixelOffset(at, -140, -400, new Vector2());
    Vector2 screen = EngineUtils.worldToScreenCoords(offset, new Vector2())
        .sub(EngineUtils.worldToScreenCoords(at, new Vector2()));
    assertEquals(-140, screen.x, .001f);
    assertEquals(400, screen.y, .001f);
  }

  @Test void meteorRockAndTailShareOneFixedFallAndKeepServerImpactTiming() {
    try (Harness h = new Harness()) {
      int center = h.root("meteorcenter", SkillId.METEOR);
      Missile authority = h.missile(center);
      authority.nativeLifetimeFrames = 60;
      authority.damageLevel = 12;
      authority.rngState = 765;
      h.tick(0);
      assertEquals(0, h.created.size(), "hold the target marker until the final 0.6 seconds");
      h.tick(1.8f);
      assertEquals(2, h.created.size());
      for (int id : h.created) {
        ReferenceElementalEffect effect = h.effect(id);
        assertEquals(.6f, effect.duration, .0001f);
        assertEquals(new Vector2(-140, 356), effect.offset);
        assertEquals(new Vector2(0, -44), effect.endOffset);
        assertEquals(Vector2.Zero, h.world.getMapper(Velocity.class).get(id).velocity);
      }
      h.tick(.2f);
      assertEquals(2, h.created.size(), "never respawn a pair on every controller tick");
      assertTrue(h.effect(h.created.get(0)).offset.y < 356);
      assertEquals(765, authority.rngState);
      assertEquals(60, authority.nativeLifetimeFrames);
      assertEquals(12, authority.damageLevel);
      assertTrue(h.system.impact(authority.missile, h.impact(center)));
      assertEquals("meteorexplode", h.missile(h.created.get(2)).missile.Missile);
    }
  }

  @Test void blizzardAlternatesStationaryDccsAcrossAnEllipseWithoutDuplicateHits() {
    try (Harness h = new Harness()) {
      int center = h.root("blizzardcenter", SkillId.BLIZZARD);
      h.tick(0);
      for (int i = 0; i < 59; i++) h.tick(.04f);
      assertEquals(24, h.created.size());
      Vector2 at = h.world.getMapper(Position.class).get(center).position;
      Vector2 screenAt = EngineUtils.worldToScreenCoords(at, new Vector2());
      for (int i = 0; i < h.created.size(); i++) {
        // Deleted effects can reuse IDs, so birth records preserve the contract.
        assertEquals(i % 3 == 2 ? "blizzard4" : "blizzard1", h.names.get(i));
        Vector2 delta = EngineUtils.worldToScreenCoords(h.births.get(i), new Vector2()).sub(screenAt);
        assertTrue(delta.x * delta.x / (110 * 110) + delta.y * delta.y / (82.5f * 82.5f) <= 1.001);
      }
      assertTrue(h.system.impact(Riiablo.files.Missiles.get("blizzard1"), h.impact(center)));
      assertEquals(24, h.created.size());
    }
  }

  @Test void fissureCreatesCrackAndSmokeAtTheSamePointWithRandomCrackDirections() {
    try (Harness h = new Harness()) {
      h.root("erruption center", SkillId.FISSURE);
      h.tick(0);
      for (int i = 0; i < 10; i++) h.tick(.04f);
      assertEquals(10, h.created.size());
      for (int i = 0; i < h.created.size(); i += 2) {
        assertEquals("erruption crack 1", h.names.get(i));
        assertEquals("erruption smoke 1", h.names.get(i + 1));
        assertEquals(h.births.get(i), h.births.get(i + 1));
        assertEquals(.9f, h.effect(h.created.get(i)).duration);
        assertTrue(h.effect(h.created.get(i)).direction < 4);
        assertTrue(h.effect(h.created.get(i)).ground);
      }
    }
  }

  @Test void volcanoHasTwoResidentLayersAndRocksLandWithExplosionAndLava() {
    try (Harness h = new Harness()) {
      int root = h.root("volcano", SkillId.VOLCANO);
      // The controller can arrive with only one authoritative frame left;
      // the reference client still keeps the authored 5.5s volcano layers.
      h.missile(root).nativeLifetimeFrames = 1;
      h.tick(0);
      assertEquals(2, h.created.size());
      assertEquals("volcano", h.names.get(0));
      assertEquals("volcano overlay fire", h.names.get(1));
      assertEquals(5.5f, h.effect(h.created.get(0)).duration, .0001f);
      assertEquals(5.5f, h.effect(h.created.get(1)).duration, .0001f);
      assertTrue(h.effect(h.created.get(0)).ground);
      for (int i = 0; i < 8; i++) h.tick(.04f);
      assertEquals("volcano debris 2", h.names.get(2));
      ReferenceElementalEffect rock = h.effect(h.created.get(2));
      assertEquals(.45f, rock.duration);
      assertEquals("volcano explosion", rock.landing);
      for (int i = 0; i < 12; i++) h.tick(.04f);
      assertTrue(h.names.contains("volcano explosion"));
      assertTrue(h.names.contains("volcano small fire"));
      assertTrue(h.system.hideOriginal(root));
    }
    try (Harness h = new Harness()) {
      // Authoritative eruption children carry the parent skill id for damage;
      // they must not be mistaken for another volcano controller.
      h.root("volcano debris 2", SkillId.VOLCANO);
      h.tick(0);
      assertEquals(0, h.created.size());
    }
  }

  @Test void firestormAndBoulderMakeReferenceTrailsAndEightRadialFragments() {
    try (Harness h = new Harness()) {
      int root = h.root("firestormmaker", SkillId.FIRESTORM);
      h.tick(0);
      h.tick(.04f); h.tick(.04f); h.tick(.04f);
      assertEquals(2, h.created.size());
      assertEquals("firestorm", h.names.get(0));
      ReferenceElementalEffect fire = h.effect(h.created.get(0));
      assertEquals(1.2f, fire.duration);
      assertTrue(fire.fade);
      assertFalse(h.system.hideOriginal(root));
    }
    try (Harness h = new Harness()) {
      int root = h.root("moltenboulder", SkillId.MOLTEN_BOULDER);
      h.tick(0);
      assertEquals("moltenboulderfirepath", h.names.get(0));
      assertEquals(2f, h.effect(h.created.get(0)).duration);
      assertTrue(h.system.impact(h.missile(root).missile, h.impact(root)));
      assertEquals(9, h.created.size());
      for (int i = 1; i <= 8; i++) {
        ReferenceElementalEffect fragment = h.effect(h.created.get(i));
        assertEquals(i - 1, fragment.direction);
        assertEquals(.5f, fragment.duration);
        assertTrue(fragment.endOffset.len() >= 44);
      }
    }
  }

  @Test void firestormTrailsFanIntoReferenceStreamsInsteadOfOverlapping() {
    try (Harness h = new Harness()) {
      int[] roots = new int[3];
      int[] seeds = {101, 202, 303};
      for (int i = 0; i < roots.length; i++) {
        roots[i] = h.root("firestormmaker", SkillId.FIRESTORM);
        h.missile(roots[i]).presentationSeedKnown = true;
        h.missile(roots[i]).presentationSeed = seeds[i];
        h.world.getMapper(Velocity.class).get(roots[i]).velocity.set(320, 0);
      }
      h.tick(0);
      assertEquals(3, h.created.size());
      h.tick(.12f);
      assertEquals(6, h.created.size());
      assertNotEquals(h.births.get(3), h.births.get(4));
      assertNotEquals(h.births.get(4), h.births.get(5));
      assertNotEquals(h.births.get(3), h.births.get(5));
    }
  }

  @Test void armageddonLayersScatterAroundTheCastCenterAndExplodeAfterTheFall() {
    try (Harness h = new Harness()) {
      int caster = h.world.create();
      h.world.getMapper(Position.class).create(caster).position.set(10, 20);
      h.world.getMapper(UnitStates.class).create(caster).init(caster).stateList
          .addState(StateId.ARMAGEDDON, 250, 1, caster);
      int first = h.root("armageddoncontrol", SkillId.ARMAGEDDON);
      h.missile(first).ownerId = caster;
      h.missile(first).presentationSeedKnown = true;
      h.missile(first).presentationSeed = 101;
      int second = h.root("armageddoncontrol", SkillId.ARMAGEDDON);
      h.missile(second).ownerId = caster;
      h.missile(second).presentationSeedKnown = true;
      h.missile(second).presentationSeed = 202;
      h.tick(0);
      assertEquals(4, h.created.size());
      assertEquals(h.effect(h.created.get(0)).offset, h.effect(h.created.get(1)).offset);
      assertEquals(330, h.effect(h.created.get(0)).offset.y);
      assertEquals(h.births.get(0), h.births.get(1));
      assertEquals(h.births.get(2), h.births.get(3));
      assertNotEquals(h.births.get(0), h.births.get(2),
          "each strike must land at a different point in the cast area");
      Vector2 center = h.world.getMapper(Position.class).get(caster).position;
      Vector2 screenCenter = EngineUtils.worldToScreenCoords(center, new Vector2());
      for (int i : new int[] {0, 2}) {
        Vector2 delta = EngineUtils.worldToScreenCoords(h.births.get(i), new Vector2())
            .sub(screenCenter);
        assertTrue(delta.x * delta.x / (220 * 220)
            + delta.y * delta.y / (149.6f * 149.6f) <= 1.001f);
      }
      h.world.getMapper(Position.class).get(caster).position.set(100, 80);
      int third = h.root("armageddoncontrol", SkillId.ARMAGEDDON);
      h.missile(third).ownerId = caster;
      h.missile(third).presentationSeedKnown = true;
      h.missile(third).presentationSeed = 303;
      h.tick(0);
      assertEquals(6, h.created.size());
      Vector2 movedCenter = EngineUtils.worldToScreenCoords(
          h.world.getMapper(Position.class).get(caster).position, new Vector2());
      Vector2 movedDelta = EngineUtils.worldToScreenCoords(h.births.get(4), new Vector2())
          .sub(movedCenter);
      assertTrue(movedDelta.x * movedDelta.x / (220 * 220)
          + movedDelta.y * movedDelta.y / (149.6f * 149.6f) <= 1.001f);
      assertNotEquals(h.births.get(0), h.births.get(4),
          "later strikes must follow the caster's moved center");
      for (int i = 0; i < 12; i++) h.tick(.04f);
      assertTrue(h.names.contains("armageddonexplosion"));
      assertEquals(3, h.names.stream().filter("armageddonexplosion"::equals).count());
    }
  }

  @Test void hurricaneUsesStateNotTargetMissileAndHasBoundedOrbitingDebris() {
    try (Harness h = new Harness()) {
      int caster = h.world.create();
      h.world.getMapper(Position.class).create(caster).position.set(10, 20);
      UnitStates states = h.world.getMapper(UnitStates.class).create(caster).init(caster);
      states.stateList.addState(StateId.HURRICANE, 250, 1, caster);
      h.tick(.04f);
      assertEquals(9, h.created.size());
      for (int id : h.created) {
        ReferenceElementalEffect effect = h.effect(id);
        assertTrue(effect.orbit);
        assertTrue(effect.spin < 0);
        assertTrue(effect.lift >= 0 && effect.lift < 75);
        assertEquals(new Vector2(10, 20), effect.origin);
      }
      for (int i = 0; i < 35; i++) h.tick(.04f);
      assertTrue(h.names.size() >= 320 && h.names.size() <= 325);
      long live = h.world.getAspectSubscriptionManager().get(
          com.artemis.Aspect.all(ReferenceElementalEffect.class)).getEntities().size();
      assertTrue(live > 150 && live < 210, "expired orbiters must be removed: " + live);
    }
  }

  @Test void growthAndLoopsRespectTheReferenceMetadataAndExcludedSkillsStayUntouched() {
    ReferenceElementalEffect fire = new ReferenceElementalEffect();
    ReferenceElementalPresentationSystem.configure("firestorm", fire);
    fire.phase = 19;
    fire.age = 11f / 25f;
    assertEquals(11, ReferenceElementalPresentationSystem.frameAt(fire, 37));
    fire.age = 12f / 25f;
    assertEquals(31, ReferenceElementalPresentationSystem.frameAt(fire, 37));
    ReferenceElementalEffect swoosh = new ReferenceElementalEffect();
    ReferenceElementalPresentationSystem.configure("hurricaneswoosh", swoosh);
    swoosh.age = .4f;
    assertEquals(5, ReferenceElementalPresentationSystem.frameAt(swoosh, 11));
    for (int skill : new int[] {SkillId.CYCLONE_ARMOR, SkillId.ARCTIC_BLAST,
        SkillId.TWISTER, SkillId.TORNADO}) {
      Missile missile = new Missile();
      missile.skillId = skill;
      String name = skill == SkillId.ARCTIC_BLAST ? "arcticblast1"
          : skill == SkillId.TWISTER ? "twister" : "tornado";
      missile.missile = Riiablo.files.Missiles.get(name);
      assertFalse(ReferenceElementalPresentationSystem.replaces(missile));
    }
  }

  @Test void replacementAndLegacySystemsDoNotAdvanceTheSameEffectTwice() {
    try (Harness h = new Harness()) {
      h.root("blizzardcenter", SkillId.BLIZZARD);
      h.tick(0);
      int fall = h.created.get(0);
      h.tick(.04f);
      assertEquals(.04f, h.effect(fall).age, .00001f);
      assertEquals(1, h.missile(fall).nativeFrame);
      assertEquals(Vector2.Zero, h.world.getMapper(Velocity.class).get(fall).velocity);
      assertTrue(h.missile(fall).presentationOnly);
      assertFalse(h.missile(fall).authoritative);
      assertFalse(h.missile(fall).damageSnapshot);
      assertEquals(-1, h.missile(fall).ownerId);
    }
  }

  @Test void coldDccPublishesAtItsCurrentAgeAndSurvivesItsParent() {
    com.riiablo.Colors previous = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    try (Harness h = new Harness()) {
      int maker = h.root("firestormmaker", SkillId.FIRESTORM);
      h.tick(0);
      int fire = h.created.get(0);
      h.world.delete(maker);
      h.tick(.52f);
      ReferenceElementalEffect effect = h.effect(fire);
      assertNotNull(effect, "a short-lived carrier must not kill its visual trail");
      assertEquals(.52f, effect.age, .0001f);
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(1);
      when(dcc.getNumFramesPerDir()).thenReturn(37);
      when(dcc.getBox(anyInt())).thenReturn(new com.riiablo.codec.util.BBox());
      com.riiablo.codec.Animation animation = h.world.getMapper(
          com.riiablo.presentation.component.AnimationWrapper.class).create(fire).animation;
      animation.edit().layer(dcc, com.riiablo.graphics.BlendMode.ID).build();
      h.system.refreshLoadedEffect(fire);
      assertEquals(ReferenceElementalPresentationSystem.frameAt(effect, 37), animation.getFrame());
      assertEquals(com.riiablo.graphics.BlendMode.ADDITIVE, animation.getLayer(0).getBlendMode());
      assertTrue(animation.getFrame() >= 12, "cold decoding must not restart the growth phase");
    } finally { Riiablo.colors = previous; }
  }

  @Test void hurricaneTangentDirectionSurvivesTheLateGenericDirectionPassOnEveryTick() {
    com.riiablo.Colors previous = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    try (Harness h = new Harness()) {
      int caster = h.world.create();
      h.world.getMapper(Position.class).create(caster).position.set(10, 20);
      h.world.getMapper(UnitStates.class).create(caster).init(caster).stateList
          .addState(StateId.HURRICANE, 250, 1, caster);
      h.tick(.04f);
      int index = h.names.indexOf("hurricaneswoosh");
      assertTrue(index >= 0);
      int wind = h.created.get(index);
      ReferenceElementalEffect effect = h.effect(wind);
      com.riiablo.codec.Animation animation = h.attachAnimation(wind, 8, 11);
      h.world.delete(caster);
      int nonDefaultDirections = 0;
      for (int i = 0; i < 16; i++) {
        h.tick(.04f);
        if (effect.direction != com.riiablo.engine.Direction.EAST) nonDefaultDirections++;
        assertEquals(effect.direction, animation.getDirection(),
            "tick " + i + ": the factory Angle must not overwrite the orbital tangent");
        assertEquals(ReferenceElementalPresentationSystem.frameAt(effect, 11), animation.getFrame(),
            "the 12.5 FPS wind clock must also survive the generic animation pass");
      }
      assertTrue(nonDefaultDirections > 8, "exercise directions that disagree with the birth Angle");
    } finally { Riiablo.colors = previous; }
  }

  @Test void ordinarySpritesStillResolveTheirAngleAfterTwoStableTicks() {
    com.riiablo.Colors previous = Riiablo.colors;
    Riiablo.colors = new com.riiablo.Colors();
    try (Harness h = new Harness()) {
      int unit = h.world.create();
      h.world.getMapper(com.riiablo.engine.server.component.Angle.class).create(unit).set(Vector2.X);
      com.riiablo.codec.Animation animation = h.attachAnimation(unit, 8, 11);
      animation.setDirection(com.riiablo.engine.Direction.SOUTH);
      h.tick(.04f);
      assertEquals(com.riiablo.engine.Direction.SOUTH, animation.getDirection());
      h.tick(.04f);
      assertEquals(com.riiablo.engine.Direction.EAST, animation.getDirection());
    } finally { Riiablo.colors = previous; }
  }

  private static final class Harness implements AutoCloseable {
    final ReferenceElementalPresentationSystem system = new ReferenceElementalPresentationSystem();
    final World world;
    final List<Integer> created = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    final List<Vector2> births = new ArrayList<>();

    Harness() {
      EntityFactory factory = mock(EntityFactory.class, CALLS_REAL_METHODS);
      world = new World(new WorldConfigurationBuilder().with(
          new net.mostlyoriginal.api.event.common.EventSystem(), system,
          new MissileImpactPresentationSystem(), new OverlayManager(), factory,
          new AnimationStepper(), new DirectionResolver()).build()
          .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      doAnswer(call -> {
        Missiles.Entry row = Riiablo.files.Missiles.get((Integer) call.getArgument(0));
        Vector2 at = call.getArgument(2);
        int id = world.create();
        world.getMapper(Missile.class).create(id).set(row, at, row.Range);
        world.getMapper(Position.class).create(id).position.set(at);
        world.getMapper(Velocity.class).create(id).velocity.set(100, 100);
        world.getMapper(com.riiablo.engine.server.component.Angle.class).create(id)
            .set((Vector2) call.getArgument(1));
        created.add(id); names.add(row.Missile); births.add(new Vector2(at));
        return id;
      }).when(factory).createMissile(anyInt(), any(Vector2.class), any(Vector2.class), anyInt());
    }
    int root(String name, int skill) {
      int id = world.create();
      Missile m = world.getMapper(Missile.class).create(id);
      m.missile = Riiablo.files.Missiles.get(name);
      assertNotNull(m.missile, name);
      m.skillId = skill;
      m.authoritative = true;
      world.getMapper(Position.class).create(id).position.set(10, 20);
      m.start.set(10, 20);
      world.getMapper(Velocity.class).create(id);
      return id;
    }
    Missile missile(int id) { return world.getMapper(Missile.class).get(id); }
    ReferenceElementalEffect effect(int id) { return world.getMapper(ReferenceElementalEffect.class).get(id); }
    com.riiablo.codec.Animation attachAnimation(int id, int directions, int frames) {
      DCC dcc = mock(DCC.class);
      when(dcc.getNumDirections()).thenReturn(directions);
      when(dcc.getNumFramesPerDir()).thenReturn(frames);
      when(dcc.getBox(anyInt())).thenReturn(new com.riiablo.codec.util.BBox());
      com.riiablo.codec.Animation animation = world.getMapper(
          com.riiablo.presentation.component.AnimationWrapper.class).create(id).animation;
      animation.edit().layer(dcc, com.riiablo.graphics.BlendMode.ID).build();
      return animation;
    }
    MissileImpactEvent impact(int id) { return MissileImpactEvent.obtain(id, missile(id).missile.Id,
        -1, -1, world.getMapper(Position.class).get(id).position); }
    void tick(float delta) { world.setDelta(delta); world.process(); }
    @Override public void close() { world.dispose(); }
  }
}
