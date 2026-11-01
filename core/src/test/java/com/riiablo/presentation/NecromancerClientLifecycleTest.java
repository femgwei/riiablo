package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NecromancerClientLifecycleTest extends RiiabloTest {
  @Test void spearEmitsFiniteStationaryTrailWithoutDccGate() {
    try (Fixture f = new Fixture()) {
      int head = f.missile(Riiablo.files.Missiles.get("bonespear"), false);
      f.world.getMapper(Velocity.class).get(head).velocity.set(10, 0);
      for (int tick = 0; tick < 4; tick++) f.step();
      assertEquals(4, f.created.size());
      for (int id : f.created) {
        Missile child = f.world.getMapper(Missile.class).get(id);
        assertEquals("bonespeartrail", child.missile.Missile);
        assertTrue(child.presentationOnly);
        assertFalse(child.authoritative);
        assertEquals(8, child.nativeLifetimeFrames);
        assertTrue(f.world.getMapper(Velocity.class).get(id).velocity.isZero());
      }
    }
  }

  @Test void curseCreatesInvisibleControllerAndLateAreaChildren() {
    try (Fixture f = new Fixture()) {
      Skills.Entry skill = Riiablo.files.skills.get("Amplify Damage");
      int caster = f.world.create();
      f.world.getMapper(Position.class).create(caster);
      f.casts.cltdofunc(SkillDoEvent.obtain(caster, skill.Id, -1,
          new Vector2(20, 30), skill.srvdofunc, skill.cltdofunc));
      // The invisible controller deliberately bypasses the DCC-loading factory.
      assertEquals(0, f.created.size());
      Missile center = f.world.getMapper(Missile.class).get(caster + 1);
      assertEquals("cursecenter", center.missile.Missile);
      assertEquals(SkillCastHandler.curseVisualRadius(skill, 1), center.clientPathX);
      assertEquals(Riiablo.files.Missiles.get(skill.cltmissilea).Id, center.clientPathY);
      for (int tick = 0; tick < 14; tick++) f.step();
      assertEquals(0, f.created.size(), "remaining frames 20..7 do not emit");
      f.step();
      assertTrue(f.created.size() > 0, "remaining frame 6 emits even without DCC");
      for (int i = 0; i < f.created.size(); i++) {
        Missile child = f.world.getMapper(Missile.class).get(f.created.get(i));
        assertEquals(skill.cltmissilea, child.missile.Missile);
        assertTrue(child.presentationOnly);
      }
    }
  }

  private static final class Fixture implements AutoCloseable {
    final EntityFactory factory = mock(EntityFactory.class);
    final SkillCastHandler casts = new SkillCastHandler();
    final List<Integer> created = new ArrayList<>();
    final World world;
    Fixture() {
      world = new World(new WorldConfigurationBuilder().with(
          casts, new MissileImpactPresentationSystem(), factory, new OverlayManager()).build()
          .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0))
          .register("clientContext", ClientGameplayContext.fromGlobals()));
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(-1))).thenAnswer(call -> {
            int id = missile(call.getArgument(0), true);
            world.getMapper(Position.class).get(id).position.set(call.getArgument(2));
            created.add(id);
            return id;
          });
    }
    int missile(Missiles.Entry row, boolean visual) {
      int id = world.create();
      Missile missile = world.getMapper(Missile.class).create(id);
      missile.missile = row;
      missile.presentationOnly = visual;
      missile.nativeLifetimeFrames = row.Range;
      world.getMapper(Position.class).create(id);
      world.getMapper(Velocity.class).create(id);
      return id;
    }
    void step() { world.setDelta(1f / 25f); world.process(); }
    @Override public void close() { world.dispose(); }
  }
}
