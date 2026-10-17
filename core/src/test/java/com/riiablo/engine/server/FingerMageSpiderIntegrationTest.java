package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

class FingerMageSpiderIntegrationTest extends RiiabloTest {
  @Test void keyframeCreatesExactlyOneProjectileAimedAtLiveTarget() {
    try (Fixture f = new Fixture()) {
      f.position(f.target).set(25, 13);
      f.world.getMapper(Casting.class).create(f.source).set(f.skill.Id, f.target, new Vector2(20, 10));
      f.events.dispatch(AnimDataKeyframeEvent.obtain(f.source, Engine.KEYFRAME_MIS));
      assertEquals(1, f.ids.size());
      Missile m = f.missile();
      assertEquals(f.source, m.ownerId);
      assertEquals(f.skill.Id, m.skillId);
      assertEquals(f.target, m.targetId);
      assertEquals(f.skill.srvmissilea, m.missile.Missile);
      assertEquals(new Vector2(10, 10), f.position(f.ids.get(0)));
      assertEquals(new Vector2(15, 3).nor(), f.launchDirection);
      assertEquals(Missile.nativeRangeFrames(m.missile, m.damageLevel), m.nativeLifetimeFrames);
    }
  }

  @Test void nativeCountdownSteersByAxisSignsAndOwnerTargetIsFallback() {
    try (Fixture f = new Fixture()) {
      f.cast(f.target);
      Missile m = f.missile();
      int id = f.ids.get(0);
      f.position(f.target).set(14, 11);
      Velocity v = f.world.getMapper(Velocity.class).get(id);
      v.velocity.set(10, 0);
      int interval = m.missile.Param[0] > 0 ? m.missile.Param[0] : 5;
      m.nativeFrame = m.nativeLifetimeFrames - interval - 1;
      f.world.process();
      assertEquals(new Vector2(10, 0), v.velocity);
      f.world.process();
      assertEquals(v.velocity.x, v.velocity.y, .0001f);
      assertEquals(10, v.velocity.len(), .0001f);
      m.targetId = -1;
      f.world.getMapper(Casting.class).create(f.source).set(f.skill.Id, f.target, f.position(f.target));
      f.position(f.target).set(14, 9);
      m.nativeFrame = m.nativeLifetimeFrames - interval;
      f.world.process();
      assertTrue(v.velocity.x > 0 && v.velocity.y < 0);
    }
  }

  @Test void contactAppliesAndRefreshesAuraStateWithoutWeaponDamage() {
    try (Fixture f = new Fixture()) {
      f.cast(f.target);
      Missile m = f.missile();
      int level = m.damageLevel;
      f.position(f.ids.get(0)).set(f.position(f.target));
      f.world.process();
      int stateId = Riiablo.files.States.get(f.skill.auratargetstate).id;
      UnitState state = f.world.getMapper(UnitStates.class).get(f.target).stateList.getState(stateId);
      assertNotNull(state);
      int duration = Math.max(5, SkillFormula.evaluate(f.skill.auralencalc, f.skill, level));
      assertEquals(duration, state.duration);
      for (int i = 0; i < f.skill.aurastat.length; i++) {
        String name = f.skill.aurastat[i];
        if (name == null || name.isEmpty()) continue;
        int stat = Stat.index(name);
        if (stat >= 0) assertEquals(SkillFormula.evaluate(f.skill.aurastatcalc[i], f.skill, level),
            state.getStatContributionValue(stat));
      }
      assertEquals(1000, f.hp());
      state.duration = 1;
      f.cast(f.target);
      f.position(f.ids.get(1)).set(f.position(f.target));
      f.world.process();
      assertEquals(duration, state.duration);
      assertEquals(1000, f.hp());
    }
  }

  static final class Fixture implements AutoCloseable {
    final EventSystem events = new EventSystem();
    final List<Integer> ids = new ArrayList<>();
    final World world;
    final Skills.Entry skill;
    final int source, target;
    Vector2 launchDirection;
    Fixture() {
      Skills.Entry found = null;
      for (Skills.Entry row : Riiablo.files.skills) if (row.srvdofunc == 101) { found = row; break; }
      assertNotNull(found); skill = found;
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder().with(events, new Actioneer(),
          new Pathfinder(), new CofManager(), new ServerSkillSystem(), new MissileCollisionSystem(), factory)
          .build().register("factory", factory).register("map", new Map(1, 0)));
      world.setDelta(0);
      source = unit(10, 10);
      MonStats.Entry monster = Riiablo.files.monstats.get("fingermage1");
      assertNotNull(monster);
      world.getMapper(Monster.class).create(source).set(monster, Riiablo.files.monstats2.get(monster.MonStatsEx));
      target = unit(20, 10); world.getMapper(Player.class).create(target);
      when(factory.createMissile(any(Missiles.Entry.class), any(), any(), anyInt(), anyInt())).thenAnswer(call -> {
        int id = world.create(); Missiles.Entry row = call.getArgument(0);
        Vector2 origin = call.getArgument(2); launchDirection = ((Vector2) call.getArgument(1)).cpy();
        world.getMapper(Missile.class).create(id).set(row, origin, row.Range).setOwner(call.getArgument(3));
        world.getMapper(Position.class).create(id).position.set(origin);
        world.getMapper(Velocity.class).create(id).velocity.set(launchDirection).scl(10);
        world.getMapper(AnimData.class).create(id);
        ids.add(id); return id;
      });
      world.process();
    }
    int unit(int x, int y) {
      int id = world.create(); world.getMapper(Position.class).create(id).position.set(x, y);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 1000); attrs.base().put(Stat.maxhp, 1000);
      attrs.base().put(Stat.mindamage, 9999); attrs.base().put(Stat.maxdamage, 9999); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(UnitStates.class).create(id).init(world, id);
      return id;
    }
    void cast(int targetId) {
      events.dispatch(SkillDoEvent.obtain(source, skill.Id, targetId, position(target), 101, 0));
    }
    Vector2 position(int id) { return world.getMapper(Position.class).get(id).position; }
    Missile missile() { return world.getMapper(Missile.class).get(ids.get(0)); }
    float hp() { return world.getMapper(AttributesWrapper.class).get(target).attrs.get(Stat.hitpoints).asFixed(); }
    public void close() { world.dispose(); }
  }
}
