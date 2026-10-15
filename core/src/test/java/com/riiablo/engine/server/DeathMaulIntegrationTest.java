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
import com.riiablo.codec.D2;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class DeathMaulIntegrationTest extends RiiabloTest {
  @Test void realSequenceCreatesOneStationarySkillMissileAtLiveTarget() {
    try (Fixture f = new Fixture("deathmauler3")) {
      for (int i = 0; i < 12; i++) f.tick();
      assertTrue(f.ids.isEmpty());
      f.position(f.target).set(21, 12);
      f.tick();
      assertEquals(1, f.ids.size(), "Do136 must be reached through the MonSeq action");
      Missile m = f.missile();
      assertEquals(502, m.missile.Id);
      assertEquals(f.source, m.ownerId);
      assertEquals(308, m.skillId);
      assertEquals(3, m.damageLevel);
      assertEquals(new Vector2(21, 12), f.position(f.ids.get(0)));
      assertTrue(f.world.getMapper(Velocity.class).get(f.ids.get(0)).velocity.isZero());
      assertEquals(60, m.nativeLifetimeFrames); // native distance 12, Range * distance / 12
      assertEquals(96, f.world.getMapper(AnimData.class).get(f.ids.get(0)).override);
      assertTrue(m.damageSnapshot);
      assertEquals(18, m.damage.get(Stat.mindamage).asInt());
      assertEquals(28, m.damage.get(Stat.maxdamage).asInt());
      while (f.ticks < 40) f.tick();
      assertEquals(1, f.ids.size());
    }
  }

  @Test void movingOutBeforeActivationAvoidsDamageAndCreationFailureDoesNotRetry() {
    try (Fixture f = new Fixture("deathmauler3")) {
      for (int i = 0; i < 13; i++) f.tick();
      assertEquals(1, f.ids.size());
      f.position(f.target).set(30, 30);
      while (f.ticks < 100) f.tick();
      assertEquals(1000, f.hp()); assertTrue(f.probe.hits.isEmpty());
    }
    try (Fixture f = new Fixture("deathmauler3")) {
      f.fail = true;
      while (f.ticks < 80) f.tick();
      assertEquals(1, f.attempts);
      assertTrue(f.ids.isEmpty()); assertEquals(1000, f.hp());
    }
  }

  @Test void coordinateGuardAndPhysicalImmunityKeepHpUntouched() {
    for (Vector2 target : new Vector2[] {new Vector2(), new Vector2(111, 10)}) {
      try (Fixture f = new Fixture("deathmauler3")) {
        f.position(f.target).set(target);
        while (f.ticks < 40) f.tick();
        assertEquals(0, f.attempts);
      }
    }
    try (Fixture f = new Fixture("deathmauler3")) {
      Attributes attrs = f.world.getMapper(AttributesWrapper.class).get(f.target).attrs;
      // Player physical resistance has a cap; a hostile owned monster exercises full immunity.
      f.world.getMapper(Player.class).remove(f.target);
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
      f.world.getMapper(Monster.class).create(f.target).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      f.world.getMapper(SummonedPet.class).create(f.target).ownerId = f.target;
      attrs.base().put(Stat.damageresist, 100); attrs.reset();
      while (f.ticks < 100) f.tick();
      assertEquals(1, f.ids.size()); assertEquals(1000, f.hp());
    }
  }

  @Test void activationDelayThenPhysicalDamageAndNextDelayRepeatReduceRealHp() {
    for (int row = 1; row <= 6; row++) {
      try (Fixture f = new Fixture("deathmauler" + row)) {
        while (f.ids.isEmpty() && f.ticks < 20) f.tick();
        assertEquals(1, f.ids.size());
        Missile m = f.missile();
        assertEquals(50, m.nativeLifetimeFrames);
        while (m.nativeFrame < 19) { f.tick(); assertEquals(1000, f.hp()); }
        f.tick();
        assertEquals(1, f.probe.hits.size(), "current countdown reaches Activate=30");
        DamageEvent hit = f.probe.hits.get(0);
        assertEquals(DamageEvent.MISSILE, hit.kind);
        assertEquals(f.ids.get(0).intValue(), hit.missileId);
        assertEquals(f.source, hit.attacker);
        assertTrue(hit.physicalDamage >= 10);
        assertEquals(hit.physicalDamage, hit.damage);
        assertEquals(1000 - hit.damage, f.hp(), .001f);
        while (m.nativeFrame < 29) f.tick();
        assertEquals(1, f.probe.hits.size());
        f.tick(); assertEquals(2, f.probe.hits.size());
        while (f.world.getEntityManager().isActive(f.ids.get(0)) && f.ticks < 100) f.tick();
        assertFalse(f.world.getEntityManager().isActive(f.ids.get(0)));
        assertEquals(3, f.probe.hits.size());
        float sum = 0; for (DamageEvent event : f.probe.hits) sum += event.damage;
        assertEquals(1000 - sum, f.hp(), .001f);
      }
    }
  }

  static final class Probe extends PassiveSystem {
    final List<DamageEvent> hits = new ArrayList<>();
    @Subscribe public void damage(DamageEvent event) { hits.add(event); }
  }
  static final class Fixture implements AutoCloseable {
    final Probe probe = new Probe();
    final List<Integer> ids = new ArrayList<>();
    final World world;
    final int source, target;
    int ticks, attempts;
    boolean fail;
    Fixture(String rowId) {
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), probe,
          new StateUpdater(), new Actioneer(), new Pathfinder(), new CofManager(),
          new AnimStepper(), new ServerSkillSystem(), new MissileCollisionSystem(), factory)
          .build().register("map", new Map(1, 0)).register("factory", factory));
      world.setDelta(SimulationClock.STEP_SECONDS);
      source = unit(10, 10);
      Attributes sourceAttrs = world.getMapper(AttributesWrapper.class).get(source).attrs;
      sourceAttrs.base().put(Stat.mindamage, 9999); sourceAttrs.base().put(Stat.maxdamage, 9999);
      sourceAttrs.base().put(Stat.firemindam, 9999); sourceAttrs.base().put(Stat.firemaxdam, 9999);
      sourceAttrs.reset();
      com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get(rowId);
      world.getMapper(Monster.class).create(source).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      target = unit(20, 10);
      world.getMapper(Player.class).create(target);
      when(factory.createMissile(any(Missiles.Entry.class), any(), any(), anyInt(), anyInt()))
          .thenAnswer(call -> {
            attempts++;
            if (fail) return -1;
            int id = world.create();
            Missiles.Entry missile = call.getArgument(0);
            Vector2 origin = call.getArgument(2);
            world.getMapper(Missile.class).create(id).set(missile, origin, missile.Range)
                .setOwner(call.getArgument(3));
            world.getMapper(Position.class).create(id).position.set(origin);
            world.getMapper(Velocity.class).create(id);
            world.getMapper(AnimData.class).create(id);
            ids.add(id); return id;
          });
      world.process();
      Sequence seq = world.getMapper(Sequence.class).create(source)
          .sequence(Engine.Monster.MODE_A2, Engine.Monster.MODE_NU);
      seq.nativeDeathMaul = seq.started = true;
      world.getMapper(Casting.class).create(source).set(308, target, new Vector2(20, 10));
      D2.Entry entry = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2"))
          .getEntry((row.Code + "A2HTH").toUpperCase());
      AnimData anim = world.getMapper(AnimData.class).create(source);
      anim.speed = entry.speed; anim.numFrames = entry.framesPerDir << 8;
      anim.keyframes = entry.data;
    }
    int unit(int x, int y) {
      int id = world.create(); world.getMapper(Position.class).create(id).position.set(x, y);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, 1000); attrs.base().put(Stat.maxhp, 1000);
      attrs.base().put(Stat.level, 30); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(UnitStates.class).create(id).init(world, id);
      return id;
    }
    Vector2 position(int id) { return world.getMapper(Position.class).get(id).position; }
    Missile missile() { return world.getMapper(Missile.class).get(ids.get(0)); }
    float hp() { return world.getMapper(AttributesWrapper.class).get(target).attrs.get(Stat.hitpoints).asFixed(); }
    void tick() { assertTrue(ticks++ < 150); world.process(); }
    public void close() { world.dispose(); }
  }
}
