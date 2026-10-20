package com.riiablo.engine.server.combat;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.BaseSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Casting;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.VitalsSerializer;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.event.SkillStartEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.item.BodyLoc;
import com.riiablo.save.CharData;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.ComponentP;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual start/keyframe/settlement boundary with deterministic draws. */
class WeaponSkillLifecycleTest extends RiiabloTest {
  @Test void frenzyAlternatesConcreteHandsWithoutCombiningDamageEndpoints() {
    try (Fixture f = new Fixture(SkillId.FRENZY)) {
      f.attacker.base().put(Stat.mindamage, 0); f.attacker.base().put(Stat.maxdamage, 0);
      f.attacker.base().put(Stat.strength, 10); f.attacker.reset();
      f.equip(BodyLoc.RARM, 1); f.equip(BodyLoc.LARM, 100);
      int other = f.unit(false, 2, attrs(1000f));
      f.keyframe(); f.keyframe();
      assertEquals(998f, f.hp(f.target));
      assertEquals(800f, f.hp(other));
      assertEquals(2, f.casting.frenzyStrikeIndex);
    }
  }

  @Test void jabAppliesThreeFractionalPacketsAndNoFourthPacket() {
    try (Fixture f = new Fixture(SkillId.JAB)) {
      f.start();
      for (int i = 0; i < 4; i++) f.keyframe();
      assertEquals(1000f - 3 * 218 / 256f, f.hp(f.target));
      assertEquals(3, f.recorder.applied.size());
    }
  }

  @Test void fendKeepsFractionalPhysicalDamageWhileAdvancingTargets() {
    try (Fixture f = new Fixture(SkillId.FEND)) {
      int other = f.unit(false, 2, attrs(1000f));
      f.start(); f.keyframe(); f.keyframe(); f.keyframe();
      assertEquals(1000f - 435 / 256f, f.hp(f.target));
      assertEquals(1000f - 435 / 256f, f.hp(other));
      assertEquals(2, f.recorder.applied.size());
    }
  }

  @Test void zealRetargetsAndDoesNotApplySynergyEDToEquipmentFire() {
    try (Fixture f = new Fixture(SkillId.ZEAL)) {
      f.attacker.base().put(Stat.firemindam, 1); f.attacker.base().put(Stat.firemaxdam, 1);
      f.attacker.reset();
      f.world.getMapper(Player.class).get(f.player).data.setSkillLevel(SkillId.SACRIFICE, 2);
      int other = f.unit(false, 2, attrs(1000f));
      f.start(); f.keyframe(); f.keyframe(); f.keyframe();
      assertEquals(2, f.recorder.applied.size());
      for (DamageAppliedEvent hit : f.recorder.applied) {
        assertEquals(317 / 256f, hit.physicalDamage); // 256 + floor(256*24/100)
        assertEquals(1f, hit.fireDamage);
      }
      assertEquals(1000f - 573 / 256f, f.hp(f.target));
      assertEquals(1000f - 573 / 256f, f.hp(other));
    }
  }

  @Test void berserkConsumesStartSnapshotOnceEvenIfStatsChangeBeforeKeyframe() {
    try (Fixture f = new Fixture(SkillId.BERSERK)) {
      f.start();
      CombatSystem.CombatResult record = f.casting.weaponSkillCombat;
      assertNotNull(record); assertTrue(record.hit);
      f.attacker.base().put(Stat.mindamage, 999); f.attacker.base().put(Stat.maxdamage, 999);
      f.attacker.reset();
      f.keyframe();
      assertEquals(1000f - record.totalDamageHp(), f.hp(f.target));
      assertTrue(f.casting.weaponSkillProcessed); assertNull(f.casting.weaponSkillCombat);
      f.keyframe();
      assertEquals(1, f.recorder.applied.size());
    }
  }

  @Test void sacrificeKeepsPhysicalAndFireSeparateAndPaysSelfBeforeLeech() {
    try (Fixture f = new Fixture(SkillId.SACRIFICE)) {
      f.attacker.base().put(Stat.firemindam, 1); f.attacker.base().put(Stat.firemaxdam, 1);
      f.attacker.base().put(Stat.lifedrainmindam, 100); f.attacker.reset();
      f.defender.base().put(Stat.damageresist, 50); f.defender.reset();
      f.start();
      assertEquals(358, f.casting.weaponSkillCombat.physicalDamage);
      f.keyframe();
      assertEquals(2, f.recorder.applied.size());
      DamageAppliedEvent self = f.recorder.applied.get(0), target = f.recorder.applied.get(1);
      assertEquals(f.player, self.victim); assertEquals(28 / 256f, self.damage);
      assertEquals(0f, self.lifeLeeched);
      assertEquals(f.target, target.victim); assertEquals(256 / 256f, target.fireDamage);
      assertEquals(358 / 256f, target.physicalDamage);
      assertEquals(358 / 256f, target.lifeLeeched);
      assertEquals(100f - 28 / 256f + 358 / 256f, f.hp(f.player));
      f.keyframe(); assertEquals(2, f.recorder.applied.size());
    }
  }

  @Test void sacrificeCapsSelfCostAtTargetLifeAtConsumptionNotAtStart() {
    try (Fixture f = new Fixture(SkillId.SACRIFICE)) {
      f.start(); f.defender.get(Stat.hitpoints).set(1f);
      f.keyframe();
      assertEquals(20 / 256f, f.recorder.applied.get(0).damage);
      assertEquals(0f, f.hp(f.target));
      assertEquals(List.of(f.target), f.recorder.deaths);
      f.keyframe(); assertEquals(1, f.recorder.deaths.size());
    }
  }

  @Test void lethalSacrificeCannotBeRescuedByLaterLeechAndPublishesOneDeath() {
    try (Fixture f = new Fixture(SkillId.SACRIFICE)) {
      f.attacker.base().put(Stat.hitpoints, 1f);
      f.attacker.base().put(Stat.mindamage, 100); f.attacker.base().put(Stat.maxdamage, 101);
      f.attacker.base().put(Stat.lifedrainmindam, 100); f.attacker.reset();
      f.start(); f.keyframe(); f.keyframe();
      assertEquals(0f, f.hp(f.player));
      assertEquals(List.of(f.player), f.recorder.deaths);
      assertEquals(0f, f.recorder.applied.get(1).lifeLeeched);
    }
  }

  @Test void missedStartRecordIsConsumedWithoutSelfDamageOrReroll() {
    try (Fixture f = new Fixture(SkillId.SACRIFICE)) {
      MathUtils.random = new RandomXS128() { @Override public int nextInt(int bound) { return bound - 1; } };
      f.start(); assertFalse(f.casting.weaponSkillCombat.hit);
      MathUtils.random = new RandomXS128() { @Override public int nextInt(int bound) { return 0; } };
      f.keyframe(); f.keyframe();
      assertTrue(f.casting.weaponSkillProcessed); assertTrue(f.recorder.applied.isEmpty());
      assertEquals(100f, f.hp(f.player)); assertEquals(1000f, f.hp(f.target));
    }
  }

  @Test void blockedSacrificeConsumesRecordWithoutSelfDamage() {
    try (Fixture f = new Fixture(SkillId.SACRIFICE)) {
      f.defender.base().put(Stat.toblock, 75); f.defender.reset();
      f.defender.setCombatBlockContext(true, 0);
      f.start(); assertTrue(f.casting.weaponSkillCombat.blocked);
      f.keyframe(); f.keyframe();
      assertTrue(f.casting.weaponSkillProcessed); assertTrue(f.recorder.applied.isEmpty());
      assertEquals(100f, f.hp(f.player)); assertEquals(1000f, f.hp(f.target));
    }
  }

  @Test void sacrificeFractionalLifeSurvivesServerClientVitalsRoundTrip() {
    try (Fixture f = new Fixture(SkillId.SACRIFICE)) {
      f.start(); f.keyframe();
      assertEquals(100f - 57 / 256f, f.hp(f.player));
      assertEquals(1000f - 716 / 256f, f.hp(f.target));
      assertEquals(f.hp(f.player), vitals(f, f.player));
      assertEquals(f.hp(f.target), vitals(f, f.target));
    }
  }

  @Test void pooledResetAlsoReleasesPreparedRecordAndOneShotMarkers() {
    class ReusableCasting extends Casting { void release() { reset(); } }
    ReusableCasting c = new ReusableCasting();
    c.weaponSkillCombat = new CombatSystem.CombatResult();
    c.weaponSkillPrepared = c.weaponSkillProcessed = true;
    c.weaponSkillSelfPercent = 8; c.weaponSkillTargetId = 10;
    c.release();
    assertNull(c.weaponSkillCombat); assertFalse(c.weaponSkillPrepared);
    assertFalse(c.weaponSkillProcessed); assertEquals(0, c.weaponSkillSelfPercent);
    assertEquals(Engine.INVALID_ENTITY, c.weaponSkillTargetId);
  }

  @Test void changedTargetCannotStealPreparedRecordAndReusingCastClearsIt() {
    try (Fixture f = new Fixture(SkillId.BERSERK)) {
      f.start();
      int other = f.unit(false, 1, attrs(1000f));
      f.casting.targetId = other; f.keyframe();
      assertEquals(1000f, f.hp(other)); assertEquals(1000f, f.hp(f.target));
      f.casting.set(SkillId.SACRIFICE, other, new Vector2(1, 0));
      assertNull(f.casting.weaponSkillCombat); assertFalse(f.casting.weaponSkillPrepared);
      assertFalse(f.casting.weaponSkillProcessed); assertEquals(0, f.casting.weaponSkillSelfPercent);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final java.util.Random previousRandom = MathUtils.random;
    final Actioneer actioneer = new Actioneer();
    final Recorder recorder = new Recorder();
    final World world;
    final Skills.Entry skill;
    final Attributes attacker = attrs(100f), defender = attrs(1000f);
    final int player, target;
    final Casting casting;
    Fixture(int skillId) {
      MathUtils.random = new RandomXS128() { @Override public int nextInt(int bound) { return 0; } };
      DummyFactory factory = new DummyFactory();
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), actioneer, new Pathfinder(), factory, recorder)
          .build().register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
      skill = Riiablo.files.skills.get(skillId);
      player = unit(true, 0, attacker); target = unit(false, 1, defender);
      world.getMapper(Player.class).get(player).data = CharData.createRemote("weapon", (byte) Riiablo.PALADIN);
      world.getMapper(Player.class).get(player).data.setSkillLevel(skillId, 1);
      casting = world.getMapper(Casting.class).create(player).set(skillId, target, new Vector2(1, 0));
    }
    int unit(boolean playerUnit, int x, Attributes attrs) {
      int id = world.create();
      if (playerUnit) world.getMapper(Player.class).create(id);
      else {
        MonStats.Entry row = new MonStats.Entry(); row.Drain = new int[] {100, 100, 100};
        world.getMapper(Monster.class).create(id).monstats = row;
      }
      world.getMapper(Class.class).create(id).type = playerUnit ? Class.Type.PLR : Class.Type.MON;
      world.getMapper(Position.class).create(id).position.set(x, 0);
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(UnitStates.class).create(id).init(id);
      return id;
    }
    void start() { actioneer.onSkillStart(SkillStartEvent.obtain(player, skill.Id, target,
        new Vector2(1, 0), skill.srvstfunc, skill.cltstfunc)); }
    void keyframe() { actioneer.onAnimDataKeyframe(AnimDataKeyframeEvent.obtain(player, Engine.KEYFRAME_ATK)); }
    float hp(int id) { return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed(); }
    void equip(BodyLoc slot, int damage) {
      Item item = new Item(); item.reset(); item.setBase(Riiablo.files.weapons.get("hax"));
      item.attrs.base().put(Stat.mindamage, damage); item.attrs.base().put(Stat.maxdamage, damage);
      item.attrs.reset();
      CharData data = world.getMapper(Player.class).get(player).data;
      data.getItems().equipItem(slot, data.getItems().add(item));
    }
    @Override public void close() { world.dispose(); MathUtils.random = previousRandom; }
  }
  private static Attributes attrs(float hp) {
    Attributes a = Attributes.obtainStandard();
    a.base().put(Stat.hitpoints, hp); a.base().put(Stat.maxhp, 1000f); a.base().put(Stat.level, 1);
    a.base().put(Stat.mindamage, 1); a.base().put(Stat.maxdamage, 2); a.base().put(Stat.tohit, 10000);
    a.reset(); return a;
  }
  private static float vitals(Fixture f, int id) {
    FlatBufferBuilder b = new FlatBufferBuilder();
    VitalsSerializer serializer = new VitalsSerializer();
    int component = serializer.putData(b, f.world.getMapper(AttributesWrapper.class).get(id));
    int types = EntitySync.createComponentTypeVector(b, new byte[] {ComponentP.VitalsP});
    int values = EntitySync.createComponentVector(b, new int[] {component});
    EntitySync.startEntitySync(b); EntitySync.addComponentType(b, types); EntitySync.addComponent(b, values);
    b.finish(EntitySync.endEntitySync(b));
    AttributesWrapper client = new AttributesWrapper(); client.attrs = attrs(0f);
    serializer.getData(EntitySync.getRootAsEntitySync(b.dataBuffer()), 0, client);
    return client.attrs.get(Stat.hitpoints).asFixed();
  }
  public static final class Recorder extends BaseSystem {
    final List<DamageAppliedEvent> applied = new ArrayList<>();
    final List<Integer> deaths = new ArrayList<>();
    @Subscribe public void damage(DamageAppliedEvent e) { applied.add(e); }
    @Subscribe public void death(DeathEvent e) { deaths.add(e.victim); }
    @Override protected void processSystem() {}
  }
  private static final class DummyFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a, int p, float x, float y) { return -1; }
    @Override public int createStaticObject(int a, int o, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int o, float x, float y) { return -1; }
    @Override public int createMonster(int m, float x, float y) { return -1; }
    @Override public int createWarp(int i, float x, float y) { return -1; }
    @Override public int createItem(Item i, float x, float y) { return -1; }
    @Override public int createMissile(int m, Vector2 a, Vector2 p) { return -1; }
  }
}
