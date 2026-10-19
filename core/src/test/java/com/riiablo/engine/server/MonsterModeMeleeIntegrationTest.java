package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.MonsterModeElementProfileTest.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.*;
import com.riiablo.engine.server.state.*;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.net.packet.d2gs.VitalsP;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;
import org.junit.jupiter.api.Test;

/** Production Attack keyframe -> mitigation -> HP/events/states/wire, no hand-applied damage. */
class MonsterModeMeleeIntegrationTest extends RiiabloTest {
  static final class Probe extends PassiveSystem {
    DamageEvent request;
    DamageAppliedEvent applied;
    @Subscribe public void request(DamageEvent e) { request = e; }
    @Subscribe public void applied(DamageAppliedEvent e) { applied = e; }
  }
  static final class Fixture implements AutoCloseable {
    final java.util.Random previousRandom = MathUtils.random;
    final MonStats.Entry row = row();
    final Attributes source = attrs(), target = attrs();
    final Probe probe = new Probe();
    final Actioneer actions = new Actioneer();
    final CofManager cofs = new CofManager();
    final World world;
    final int owner, victim;
    Fixture(String type) {
      row.El1Type = type; row.El1MinD = row.El1MaxD = new int[] {100,100,100};
      Map map = new Map(0, 0);
      FireHitIntegrationTest.NoopFactory factory = new FireHitIntegrationTest.NoopFactory();
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, actions, new Pathfinder(), cofs, factory, new StateUpdater())
          .build().register("serverContext", new ServerGameplayContext(files(row), 1234))
          .register("map", map).register("factory", factory));
      owner = world.create(); victim = world.create();
      world.getMapper(Monster.class).create(owner).set(row, null);
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(owner).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(AttributesWrapper.class).create(owner).attrs = source;
      world.getMapper(Position.class).create(owner).position.set(10,10);
      world.getMapper(Angle.class).create(owner);
      world.getMapper(CofReference.class).create(owner).mode = Engine.Monster.MODE_NU;
      world.getMapper(MovementModes.class).create(owner)
          .set(Engine.Monster.MODE_NU,Engine.Monster.MODE_WL,Engine.Monster.MODE_RN);
      world.getMapper(Player.class).create(victim);
      world.getMapper(UnitRandomSeed.class).create(victim).low = 1;
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(victim).type =
          com.riiablo.engine.server.component.Class.Type.PLR;
      world.getMapper(AttributesWrapper.class).create(victim).attrs = target;
      world.getMapper(Position.class).create(victim).position.set(11,10);
      world.getMapper(UnitStates.class).create(victim).init(victim);
      target.base().put(Stat.normal_damage_reduction, 1000); target.reset();
      MathUtils.random = new java.util.Random(0) {
        @Override public int nextInt(int bound) { return bound == 100 ? 0 : bound - 1; }
      };
      world.setDelta(Animation.FRAME_DURATION); world.process();
    }
    void attack() {
      attack(1068);
    }
    void attack(int seed) {
      actions.cast(owner, com.riiablo.skill.SkillCodes.attack, victim, new Vector2(11,10));
      cofs.setMode(owner, Engine.Monster.MODE_A1, true);
      world.getMapper(Monster.class).get(owner).rngState = seed;
      world.getSystem(EventSystem.class).dispatch(AnimDataKeyframeEvent.obtain(owner, Engine.KEYFRAME_ATK));
    }
    UnitState state(int id) { return world.getMapper(UnitStates.class).get(victim).stateList.getState(id); }
    int hp() { return target.get(Stat.hitpoints).encodedValues(); }
    void assertWire() {
      FlatBufferBuilder buffer = new FlatBufferBuilder(64);
      buffer.finish(new VitalsSerializer().putData(buffer,world.getMapper(AttributesWrapper.class).get(victim)));
      AttributesWrapper client = new AttributesWrapper(); client.attrs = Attributes.obtainLarge();
      VitalsSerializer.apply(client, VitalsP.getRootAsVitalsP(buffer.dataBuffer()));
      assertEquals(hp(), client.attrs.get(Stat.hitpoints).encodedValues());
    }
    @Override public void close() { world.dispose(); MathUtils.random = previousRandom; }
  }
  static Attributes attrs() {
    Attributes a = Attributes.obtainStandard();
    a.base().put(Stat.hitpoints,10000f); a.base().put(Stat.maxhp,10000f);
    a.base().put(Stat.level,40); a.base().put(Stat.tohit,100000);
    a.base().put(Stat.mindamage,2); a.base().put(Stat.maxdamage,2); a.reset(); return a;
  }

  @Test void manaModeDrainReachesSettlementDespiteZeroHpDamage() {
    try (Fixture f = new Fixture("mana")) {
      f.source.aggregate().put(Stat.hitpoints, 9000f);
      f.target.aggregate().put(Stat.mana, 20f);
      f.attack();
      assertNotNull(f.probe.request);
      assertTrue(f.probe.request.fixedMonsterDrain);
      assertTrue(f.probe.request.manaDrainFixed > 0);
      assertEquals(0f, f.target.get(Stat.mana).asFixed());
      assertEquals(9020f, f.source.get(Stat.hitpoints).asFixed());
      assertEquals(10000 * 256, f.hp());
    }
  }

  @Test void selectedFireCombinesWithPermanentStatsExactlyOnceInLifeEventsAndVitals() {
    try (Fixture f = new Fixture("fire")) {
      f.source.base().put(Stat.firemindam,2); f.source.base().put(Stat.firemaxdam,2); f.source.reset();
      f.target.base().put(Stat.fireresist,50); f.target.base().put(Stat.magic_damage_reduction,1); f.target.reset();
      f.attack();
      int selected = f.world.getMapper(Monster.class).get(f.owner).modeElements.minimum[1];
      int expected = (selected + 1) * 128;
      assertEquals(10000 * 256 - expected,f.hp());
      assertNotNull(f.probe.request); assertNotNull(f.probe.applied);
      assertEquals(expected / 256f, f.probe.request.fireDamage);
      assertEquals(expected / 256f, f.probe.applied.lifeLost);
      assertEquals(2,f.source.getValue(Stat.firemindam,0)); f.assertWire();
    }
  }

  @Test void poisonOnlyHitSurvivesZeroImmediateDamageAndTicksAsRawRate() {
    try (Fixture f = new Fixture("pois")) {
      f.attack();
      int rate = f.world.getMapper(Monster.class).get(f.owner).modeElements.minimum[4];
      assertTrue(rate > 0); assertEquals(10000 * 256,f.hp());
      UnitState poison = f.state(StateId.POISON); assertNotNull(poison);
      assertEquals(rate / 256f, poison.exactDamagePerFrame); assertEquals(34,poison.duration);
      f.world.process(); assertEquals(10000 * 256 - rate, f.hp()); f.assertWire();
    }
  }

  @Test void monsterCriticalDoublesFireBeforeMdrAndResistanceInActualSettlement() {
    try (Fixture f = new Fixture("fire")) {
      f.row.Crit = 100;
      f.source.base().put(Stat.firemindam,2); f.source.base().put(Stat.firemaxdam,2); f.source.reset();
      f.target.base().put(Stat.fireresist,50); f.target.base().put(Stat.magic_damage_reduction,1); f.target.reset();
      f.attack();
      int selected = f.world.getMapper(Monster.class).get(f.owner).modeElements.minimum[1];
      int expected = (2 * (selected + 2) - 1) * 128;
      assertEquals(10000 * 256 - expected,f.hp());
      assertEquals(expected / 256f,f.probe.applied.lifeLost); f.assertWire();
    }
  }

  @Test void fullMdrDoesNotEraseColdStateAndZeroDamagePathDoesNotLoseIt() {
    try (Fixture f = new Fixture("cold")) {
      f.target.base().put(Stat.magic_damage_reduction,1000);
      f.target.base().put(Stat.coldresist,50); f.target.reset();
      f.attack(); assertEquals(10000 * 256,f.hp());
      UnitState cold = f.state(StateId.COLD); assertNotNull(cold); assertEquals(8,cold.duration);
    }
  }
}
