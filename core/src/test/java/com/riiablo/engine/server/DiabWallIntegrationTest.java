package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
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
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.*;
import com.riiablo.map.Map;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

class DiabWallIntegrationTest extends RiiabloTest {
  @Test void realS3CreatesOnlyLevelCountMakersThenTableOwnedFractionalFire() {
    for (String name : new String[] {"diablo", "diabloclone"}) try (Fixture f = new Fixture(name)) {
      f.begin(); assertEquals(0, f.makers.size());
      while (f.makers.isEmpty()) f.tick();
      assertEquals(name.equals("diablo") ? 3 : 2, f.makers.size());
      assertTrue(f.probe.hits.isEmpty(), "zero-damage makers must not inherit a melee packet");
      int first = f.makers.get(0);
      Missile maker = f.missile(first);
      assertEquals(77, maker.nativeLifetimeFrames); assertEquals(0, maker.skillId);
      while (f.fires.isEmpty()) f.tick();
      int fireId = f.fires.get(0); Missile fire = f.missile(fireId);
      assertTrue(f.makers.contains(fire.diabWallParentId)); assertEquals(f.source, fire.ownerId);
      assertEquals(0, fire.skillId); assertTrue(fire.persistent); assertTrue(fire.fixedElementalRate);
      assertEquals(name.equals("diablo") ? 176 : 136, fire.elementalMinRateFixed);
      assertEquals(name.equals("diablo") ? 280 : 240, fire.elementalMaxRateFixed);
      f.position(f.target).set(f.position(fireId));
      float before = f.hp(); f.tick();
      assertTrue(f.hp() < before); assertTrue(f.probe.hits.stream().allMatch(h -> h.physicalDamage == 0 && h.fireDamage > 0));
      while (f.cast() != null) f.tick();
      assertEquals(1, f.probe.finished);
      assertEquals(name.equals("diablo") ? 3 : 2, f.makers.size());
      for (int i = 0; i < 120; i++) f.tick();
      for (int id : f.makers) assertFalse(f.world.getEntityManager().isActive(id));
      for (int id : f.fires) assertFalse(f.world.getEntityManager().isActive(id));
    }
  }

  @Test void stationaryMakerDoesNotLayDuplicateFireAndSlowMotionWaitsForTileCrossing() {
    try (Fixture f = new Fixture("diablo")) {
      f.begin(); while (f.makers.isEmpty()) f.tick();
      for (int id : f.makers) {f.world.getMapper(Velocity.class).get(id).velocity.setZero(); f.missile(id).diabWallMoved = false;}
      for (int i = 0; i < 5; i++) f.tick();
      assertTrue(f.fires.isEmpty());
      int id = f.makers.get(0); Missile maker = f.missile(id); maker.chargedBoltPath = false;
      f.position(id).set(100,100);
      f.world.getMapper(Velocity.class).get(id).velocity.set(1, 0);
      for (int i = 0; i < 24; i++) f.tick();
      assertTrue(f.fires.isEmpty());
      for (int i = 0; i < 4; i++) f.tick();
      assertEquals(1, f.fires.size());
    }
  }

  @Test void coordinateTargetSurvivesRemovedUnitButZeroAxisAndFarTargetsReject() {
    try (Fixture f = new Fixture("diablo")) {
      f.begin(); f.world.delete(f.target);
      while (f.cast() != null) f.tick();
      assertEquals(3, f.makers.size());
      assertTrue(f.probe.hits.isEmpty());
    }
    for (Vector2 target : new Vector2[] {new Vector2(0, 100), new Vector2(118, 0), new Vector2(200, 100)}) {
      try (Fixture f = new Fixture("diablo")) {
        f.begin(); f.cast().targetId = Engine.INVALID_ENTITY; f.cast().targetVec.set(target);
        while (f.cast() != null) f.tick();
        assertTrue(f.makers.isEmpty()); assertTrue(f.fires.isEmpty());
      }
    }
  }

  @Test void childDamageDoesNotDependOnWeaponDamageAndUsesFireResistance() {
    try (Fixture f = new Fixture("diablo")) {
      f.attrs(f.source).base().put(Stat.mindamage, 9999); f.attrs(f.source).base().put(Stat.maxdamage, 9999); f.attrs(f.source).reset();
      f.begin(); while (f.fires.isEmpty()) f.tick();
      int id = f.fires.get(0);
      f.position(f.target).set(f.position(id));
      f.attrs(f.target).base().put(Stat.fireresist, 75); f.attrs(f.target).reset();
      float before = f.hp(); f.tick();
      assertTrue(f.hp() < before); assertTrue(before - f.hp() < 4);
      assertTrue(f.probe.hits.stream().allMatch(h -> h.fireDamage < 2 && h.physicalDamage == 0));
    }
  }

  @Test void immunePlayerOwnedMonsterTakesNoFireAndFailedMakerIsNotRetried() {
    try (Fixture f = new Fixture("diablo")) {
      f.failFirstMaker = true;
      f.world.getMapper(Player.class).remove(f.target);
      MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
      f.world.getMapper(Monster.class).create(f.target).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      f.world.getMapper(SummonedPet.class).create(f.target).ownerId = f.target;
      f.attrs(f.target).base().put(Stat.fireresist, 100); f.attrs(f.target).reset();
      f.begin(); while (f.fires.isEmpty()) f.tick();
      assertEquals(3, f.makerAttempts); assertEquals(2, f.makers.size());
      f.position(f.target).set(f.position(f.fires.get(0)));
      float before = f.hp(); for (int i = 0; i < 5; i++) f.tick();
      assertEquals(before, f.hp()); assertTrue(f.probe.hits.isEmpty());
    }
  }

  @Test void makerRespectsMapBarrierAndDoesNotEmitAfterItsRemoval() {
    try (Fixture f = new Fixture("diablo")) {
      f.begin(); while (f.makers.isEmpty()) f.tick();
      Map map = new Map(1,0);
      try {
        Map.Zone zone = map.createSyntheticArena(2,4,4);
        for (int id : f.makers) {
          f.position(id).set(128,128);
          f.missile(id).chargedBoltPath = false; f.missile(id).diabWallMoved = false;
          f.world.getMapper(Velocity.class).get(id).velocity.set(50,0);
          f.world.getMapper(MapWrapper.class).create(id).set(map,zone);
        }
        zone.or(129,128,com.riiablo.map.DT1.Tile.FLAG_BLOCK_JUMP);
        f.tick(); f.tick();
        for (int id : f.makers) assertFalse(f.world.getEntityManager().isActive(id));
        assertTrue(f.fires.isEmpty()); assertTrue(f.probe.hits.isEmpty());
      } finally {map.dispose();}
    }
  }

  @Test void reusedComponentCannotRetainWallCastOrParentMarkers() {
    World world = new World();
    try {
      int old = world.create(); Missile m = world.getMapper(Missile.class).create(old);
      m.diabWallMaker = true; m.diabWallFire = true; m.diabWallMoved = true;
      m.diabWallCastId = 9; m.diabWallParentId = 8;
      world.process(); world.delete(old); world.process();
      int id = world.create(); Missile reused = world.getMapper(Missile.class).create(id);
      assertFalse(reused.diabWallMaker); assertFalse(reused.diabWallFire); assertFalse(reused.diabWallMoved);
      assertEquals(0,reused.diabWallCastId); assertEquals(-1,reused.diabWallParentId);
    } finally {world.dispose();}
  }

  static class Probe extends PassiveSystem {
    final List<DamageEvent> hits = new ArrayList<>(); int finished;
    @Subscribe public void hit(DamageEvent e) { hits.add(e); }
    @Subscribe public void end(AnimDataFinishedEvent e) { finished++; }
  }
  static class Fixture implements AutoCloseable {
    final World world; final Probe probe = new Probe();
    final List<Integer> makers = new ArrayList<>(), fires = new ArrayList<>();
    final Skills.Entry skill = Riiablo.files.skills.get(197);
    final int source, target; int ticks, makerAttempts; boolean failFirstMaker;
    Fixture(String name) {
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), probe, new StateUpdater(),
          new Actioneer(), new Pathfinder(), new CofManager(), new AnimStepper(), new ServerSkillSystem(),
          new MissileCollisionSystem(), factory).build().register("map", new Map(1, 0)).register("factory", factory));
      world.setDelta(SimulationClock.STEP_SECONDS);
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class), any(Vector2.class), anyInt(), anyInt()))
          .thenAnswer(invocation -> {
            Missiles.Entry row = invocation.getArgument(0); Vector2 direction = invocation.getArgument(1), pos = invocation.getArgument(2);
            int owner = invocation.getArgument(3), level = invocation.getArgument(4);
            if (row.Id == 179 && ++makerAttempts == 1 && failFirstMaker) return Engine.INVALID_ENTITY;
            int id = world.create();
            Missile m = world.getMapper(Missile.class).create(id).set(row, pos, row.Range).setOwner(owner);
            m.damageLevel = level; m.rngState = NativeRng.forUnit(Riiablo.gameSeed, id).state();
            world.getMapper(Position.class).create(id).position.set(pos);
            world.getMapper(Velocity.class).create(id).velocity.set(direction).setLength(row.Vel);
            if (row.Id == 179) makers.add(id); else if (row.Id == 180) fires.add(id);
            return id;
          });
      source = unit(100,100); MonStats.Entry row = Riiablo.files.monstats.get(name);
      world.getMapper(Monster.class).create(source).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      target = unit(118,100); world.getMapper(Player.class).create(target);
      world.process();
    }
    int unit(float x, float y) {
      int id = world.create(); world.getMapper(Position.class).create(id).position.set(x,y);
      Attributes a = Attributes.obtainStandard(); a.base().put(Stat.hitpoints,1000); a.base().put(Stat.maxhp,1000); a.base().put(Stat.level,30); a.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = a; world.getMapper(UnitStates.class).create(id).init(world,id); return id;
    }
    void begin() {
      world.getMapper(Sequence.class).create(source).sequence(Engine.Monster.MODE_S3,Engine.Monster.MODE_NU).started = true;
      world.getMapper(Casting.class).create(source).set(197,target,new Vector2(118,100));
      MonStats.Entry row = world.getMapper(Monster.class).get(source).monstats;
      D2.Entry entry = D2.loadFromFile(Riiablo.mpqs.resolve("data/global/eanimdata.d2")).getEntry((row.Code+"S3HTH").toUpperCase());
      AnimData anim = world.getMapper(AnimData.class).create(source); anim.speed=entry.speed; anim.numFrames=entry.framesPerDir<<8; anim.keyframes=entry.data; anim.lastKeyframeIndex=-1;
      world.getSystem(EventSystem.class).dispatch(SkillStartEvent.obtain(source,197,target,new Vector2(118,100),skill.srvstfunc,skill.cltstfunc));
    }
    Casting cast() {return world.getMapper(Casting.class).get(source);}
    Missile missile(int id) {return world.getMapper(Missile.class).get(id);}
    Vector2 position(int id) {return world.getMapper(Position.class).get(id).position;}
    Attributes attrs(int id) {return world.getMapper(AttributesWrapper.class).get(id).attrs;}
    float hp() {return attrs(target).get(Stat.hitpoints).asFixed();}
    void tick() {assertTrue(ticks++<500);world.process();}
    public void close() {world.dispose();}
  }
}
