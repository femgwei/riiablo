package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.presentation.component.ReferenceElementalEmitter;
import com.riiablo.presentation.component.ReferenceElementalEffect;
import java.util.*;
import org.junit.jupiter.api.Test;

class HurricanePresentationLifecycleTest extends RiiabloTest {
  private int caster(ReferenceElementalPresentationTest.Harness h) {
    int id=h.world.create();h.world.getMapper(Position.class).create(id).position.set(10,20);
    h.world.getMapper(UnitStates.class).create(id).init(id).stateList.addState(StateId.HURRICANE,250,1,id);
    return id;
  }

  @Test void sixtyHzRenderingEmitsNinePerNativeFrameWithOneBirthAgePerCohort() {
    try(ReferenceElementalPresentationTest.Harness h=new ReferenceElementalPresentationTest.Harness()) {
      caster(h);h.tick(1f/60f);assertEquals(0,h.created.size());h.tick(1f/60f);assertEquals(0,h.created.size());
      for(int tick=2;tick<60;tick++) {
        int before=h.created.size();h.tick(1f/60f);int after=h.created.size();
        assertTrue(after-before==0 || after-before==9);
        if(after>before) {
          float age=h.effect(h.created.get(before)).age;
          for(int n=before;n<after;n++)assertEquals(age,h.effect(h.created.get(n)).age);
        }
      }
      assertEquals(225,h.created.size());
    }
  }

  @Test void movementChangesOnlyNewBirthCentresAndStateRemovalAllowsTheExistingParticlesToExpire() {
    try(ReferenceElementalPresentationTest.Harness h=new ReferenceElementalPresentationTest.Harness()) {
      int caster=caster(h);h.tick(.04f);
      int old=h.created.get(0);ReferenceElementalEffect particle=h.effect(old);
      int centerX=particle.hurricaneFlight.centerX,centerY=particle.hurricaneFlight.centerY;
      h.world.getMapper(Position.class).get(caster).position.set(30,40);h.tick(.04f);
      assertEquals(centerX,particle.hurricaneFlight.centerX);assertEquals(centerY,particle.hurricaneFlight.centerY);
      for(int n=9;n<18;n++) {
        assertEquals(30,h.effect(h.created.get(n)).hurricaneFlight.centerX);
        assertEquals(40,h.effect(h.created.get(n)).hurricaneFlight.centerY);
      }
      h.world.getMapper(UnitStates.class).get(caster).stateList.removeState(StateId.HURRICANE);h.tick(.04f);
      assertEquals(18,h.created.size());assertTrue(h.world.getEntityManager().isActive(old));
      for(int n=0;n<23;n++)h.tick(.04f);
      for(int id:h.created)assertFalse(h.world.getEntityManager().isActive(id));
    }
  }

  @Test void aNewActivationKeepsThePresentationRandomStreamInsteadOfRepeatingItsFirstBurst() {
    try(ReferenceElementalPresentationTest.Harness h=new ReferenceElementalPresentationTest.Harness()) {
      int caster=caster(h);h.tick(.04f);
      ReferenceElementalEmitter emitter=h.world.getMapper(ReferenceElementalEmitter.class).get(caster);
      com.d2moo.common.drlg.D2Seed seed=emitter.hurricaneSeed;long low=Integer.toUnsignedLong(seed.getNLowSeed());
      h.world.getMapper(UnitStates.class).get(caster).stateList.removeState(StateId.HURRICANE);h.tick(.04f);
      h.world.getMapper(UnitStates.class).get(caster).stateList.addState(StateId.HURRICANE,250,1,caster);h.tick(.04f);
      assertSame(seed,emitter.hurricaneSeed);assertNotEquals(low,Integer.toUnsignedLong(seed.getNLowSeed()));
      assertEquals(18,h.created.size());
    }
  }

  @Test void longRenderStepCatchesUpInWholeNativeCohortsWithoutChangingExistingMissileAuthority() {
    try(ReferenceElementalPresentationTest.Harness h=new ReferenceElementalPresentationTest.Harness()) {
      int caster=caster(h);h.tick(.16f);assertEquals(36,h.created.size());
      Set<Integer> ages=new HashSet<>();
      for(int id:h.created) {
        ReferenceElementalEffect effect=h.effect(id);ages.add(effect.hurricaneFlight.age);
        assertTrue(h.missile(id).presentationOnly);assertFalse(h.missile(id).authoritative);
        assertFalse(h.missile(id).damageSnapshot);
      }
      assertEquals(new HashSet<>(Arrays.asList(0,1,2,3)),ages);
      assertTrue(h.world.getMapper(UnitStates.class).get(caster).stateList.hasState(StateId.HURRICANE));
    }
  }

  @Test void knownSceneBoundaryRemovesOnlyTheCrossingParticleAndKeepsTheEmitterRunning() {
    com.riiablo.map.Map map = org.mockito.Mockito.mock(com.riiablo.map.Map.class);
    com.riiablo.map.Map.Zone zone = org.mockito.Mockito.mock(com.riiablo.map.Map.Zone.class);
    org.mockito.Mockito.when(map.getZone(org.mockito.Mockito.any(Vector2.class))).thenAnswer(call -> {
      Vector2 p = call.getArgument(0);
      return p.x < 10.8f ? zone : null;
    });
    try (ReferenceElementalPresentationTest.Harness h = new ReferenceElementalPresentationTest.Harness(map)) {
      int caster = caster(h); h.tick(.04f);
      int crossing = -1;
      for (int id : h.created) if (h.effect(id).hurricaneFlight != null) { crossing = id; break; }
      assertTrue(crossing >= 0);
      h.world.getMapper(Position.class).get(caster).position.set(10, 20);
      h.world.setDelta(1f);
      h.tick(1f);
      assertFalse(h.world.getEntityManager().isActive(crossing));
      assertTrue(h.world.getMapper(UnitStates.class).get(caster).stateList.hasState(StateId.HURRICANE));
      assertTrue(h.created.size() > 9, "the state emitter must continue after one particle leaves the scene");
    }
  }
}
