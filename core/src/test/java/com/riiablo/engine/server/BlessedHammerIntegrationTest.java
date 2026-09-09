package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.client.OverlayManager;
import com.riiablo.engine.client.SkillCastHandler;
import com.riiablo.engine.server.combat.CombatSystem;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native SrvDo073 path, snapshot and SrvDmg05 regression coverage. */
class BlessedHammerIntegrationTest extends RiiabloTest {
  private static final float EPSILON = 0.0002f;

  @Test
  void nativeSpiralHasSeventySevenPointsAndIgnoresClickedDirection() {
    Vector2 origin = new Vector2(10f, 20f);
    Vector2 point = MissileCollisionSystem.blessedHammerPathPoint(origin, 1, new Vector2());
    assertEquals(9600f / 65536f, point.dst(origin), EPSILON);
    assertTrue(point.x > origin.x);
    assertTrue(point.y > origin.y);

    MissileCollisionSystem.blessedHammerPathPoint(origin, 32, point);
    assertEquals(origin.x + 32f * 9600f / 65536f, point.x, EPSILON);
    assertEquals(origin.y, point.y, EPSILON);

    MissileCollisionSystem.blessedHammerPathPoint(
        origin, MissileCollisionSystem.BLESSED_HAMMER_PATH_POINTS, point);
    assertEquals(77f * 9600f / 65536f, point.dst(origin), 0.01f);
    // The pure path contract has no target/direction input: casts aimed north
    // or west therefore produce the identical native global-coordinate path.
    assertEquals(77, MissileCollisionSystem.BLESSED_HAMMER_PATH_POINTS);
  }

  @Test
  void rangeIsAOneHundredTwentyFrameLifetimeRatherThanLinearDistance() {
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new MissileCollisionSystem()).build());
    try {
      Missiles.Entry row = Riiablo.files.Missiles.get("blessedhammer");
      int id = world.create();
      Missile missile = world.getMapper(Missile.class).create(id)
          .set(row, Vector2.Zero, 0f).setOwner(Engine.INVALID_ENTITY);
      missile.blessedHammerPath = true;
      missile.blessedHammerOrigin.setZero();
      missile.blessedHammerPointIndex = 1;
      missile.nativeLifetimeFrames = row.Range;
      world.getMapper(Position.class).create(id).position.setZero();
      world.getMapper(Velocity.class).create(id).velocity.set(Vector2.X).setLength(row.Vel);
      world.setDelta(1f / 25f);

      for (int frame = 1; frame < row.Range; frame++) world.process();
      assertTrue(world.getEntityManager().isActive(id));
      assertTrue(missile.distanceTraveled > 80f);
      assertTrue(missile.distanceTraveled < 86f);
      world.process();
      assertFalse(world.getEntityManager().isActive(id));
    } finally {
      world.dispose();
    }
  }

  @Test
  void skillDamageUsesHardPointSynergiesAndCastTimeConcentrationSnapshot() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLESSED_HAMMER);
    assertEquals(12, PaladinSkills.getBlessedHammerMagicDamage(skill, 1, name -> 0)[0]);
    assertEquals(16, PaladinSkills.getBlessedHammerMagicDamage(skill, 1, name -> 0)[1]);
    int[] synergy = PaladinSkills.getBlessedHammerMagicDamage(skill, 1,
        name -> "Vigor".equals(name) || "Blessed Aim".equals(name) ? 1 : 0);
    assertEquals(15, synergy[0]);
    assertEquals(20, synergy[1]);
    assertEquals(30, PaladinSkills.getBlessedHammerConcentrationPercent(skill, 60));

    Missile missile = new Missile();
    missile.missile = Riiablo.files.Missiles.get("blessedhammer");
    assertTrue(MissileDamageResolver.initializePaladinBlessedHammer(
        missile, skill, attributes(1, 1000), 1,
        name -> "Vigor".equals(name) || "Blessed Aim".equals(name) ? 1 : 0, 30));
    assertEquals(19, missile.damage.get(Stat.magicmindam).asInt());
    assertEquals(26, missile.damage.get(Stat.magicmaxdam).asInt());

    // Removing/changing Concentration after creation cannot mutate the
    // already-created missile unit's native stat list.
    assertEquals(19, missile.damage.get(Stat.magicmindam).asInt());
    assertEquals(26, missile.damage.get(Stat.magicmaxdam).asInt());
  }

  @Test
  void srvDmg05StacksUndeadAndDemonBonuses() {
    Missile missile = new Missile();
    missile.missile = Riiablo.files.Missiles.get("blessedhammer");
    Monster target = new Monster().set(new MonStats.Entry(), new MonStats2.Entry());
    assertEquals(0, MissileCollisionSystem.blessedHammerTargetBonusPercent(missile, target));
    target.monstats.lUndead = true;
    assertEquals(50, MissileCollisionSystem.blessedHammerTargetBonusPercent(missile, target));
    target.monstats.demon = true;
    assertEquals(100, MissileCollisionSystem.blessedHammerTargetBonusPercent(missile, target));
    target.monstats.lUndead = false;
    target.monstats.hUndead = true;
    assertEquals(100, MissileCollisionSystem.blessedHammerTargetBonusPercent(missile, target));
  }

  @Test
  void targetBonusPrecedesMagicResistanceAbsorbAndPvpScalar() {
    Attributes attacker = attributes(1, 1000);
    attacker.base().put(Stat.magicmindam, 100);
    attacker.base().put(Stat.magicmaxdam, 100);
    attacker.reset();
    Attributes defender = attributes(1, 1000);
    defender.base().put(Stat.magicresist, 50);
    defender.base().put(Stat.item_absorbmagic_percent, 20);
    defender.reset();

    CombatSystem.CombatResult result = CombatSystem.INSTANCE.calculateAttackAtDifficulty(
        attacker, defender, true, true, true,
        0, 0, 0, true, null, null, 0, 0,
        null, null, false, null, 0, 50);
    // 100 + 50% target bonus = 150; 50% resist = 75; 20% absorb = 15;
    // PvP scalar then reduces the remaining 60 to 10 (integer arithmetic).
    assertEquals(10, result.elementalDamage[CombatSystem.DAMAGE_MAGIC]);
    assertEquals(10, result.totalDamage);
    assertEquals(15, result.absorbedLife);
  }

  @Test
  void localAuthoritativeWorldCreatesOnlyOneHammerMissile() {
    RecordingFactory factory = new RecordingFactory();
    Audio oldAudio = Riiablo.audio;
    Riiablo.audio = new SilentAudio();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new SkillCastHandler(),
            new OverlayManager(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int paladin = world.create();
      CharData data = CharData.createRemote("paladin", (byte) Riiablo.PALADIN);
      data.setSkillLevel(SkillId.BLESSED_HAMMER, 1);
      data.setSkillLevel(SkillId.VIGOR, 1);
      data.setSkillLevel(SkillId.BLESSED_AIM, 1);
      world.getMapper(Player.class).create(paladin).data = data;
      world.getMapper(Position.class).create(paladin).position.set(5, 7);
      world.getMapper(AttributesWrapper.class).create(paladin).attrs = attributes(1, 1000);
      UnitState concentration = world.getMapper(UnitStates.class).create(paladin).init(paladin)
          .stateList.addState(StateId.CONCENTRATION, 100, 1, paladin);
      concentration.setNativeModifier(Stat.damagepercent, 60);

      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLESSED_HAMMER);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, skill.Id, Engine.INVALID_ENTITY, new Vector2(15, 7),
          skill.srvdofunc, skill.cltdofunc));
      assertEquals(1, factory.created.size());
      Missile missile = factory.created.get(0);
      assertTrue(missile.blessedHammerPath);
      assertEquals(120, missile.nativeLifetimeFrames);
      assertEquals(0f, missile.range, EPSILON);
      assertEquals(1f, factory.lastDirection.x, EPSILON);
      assertEquals(0f, factory.lastDirection.y, EPSILON);
      assertEquals(19, missile.damage.get(Stat.magicmindam).asInt());
      assertEquals(26, missile.damage.get(Stat.magicmaxdam).asInt());
      world.getMapper(UnitStates.class).get(paladin).stateList.removeState(StateId.CONCENTRATION);
      assertEquals(19, missile.damage.get(Stat.magicmindam).asInt());
      assertEquals(26, missile.damage.get(Stat.magicmaxdam).asInt());
    } finally {
      world.dispose();
      Riiablo.audio = oldAudio;
    }
  }

  @Test
  void persistentHammerHitsDifferentTargetsButEachTargetOnlyOnce() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), new MissileCollisionSystem(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
    try {
      int paladin = world.create();
      CharData data = CharData.createRemote("paladin", (byte) Riiablo.PALADIN);
      data.setSkillLevel(SkillId.BLESSED_HAMMER, 1);
      world.getMapper(Player.class).create(paladin).data = data;
      world.getMapper(Position.class).create(paladin).position.setZero();
      world.getMapper(AttributesWrapper.class).create(paladin).attrs = attributes(1, 1000);
      world.getMapper(UnitStates.class).create(paladin).init(paladin);
      int first = createMonster(world, 0.5f, 0.1f);
      int second = createMonster(world, 1.5f, 0.1f);

      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BLESSED_HAMMER);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, skill.Id, Engine.INVALID_ENTITY, new Vector2(-20, 0),
          skill.srvdofunc, skill.cltdofunc));
      world.setDelta(1f / 25f);
      world.process();
      float firstAfterHit = life(world, first);
      float secondAfterHit = life(world, second);
      assertTrue(firstAfterHit >= 984f && firstAfterHit <= 988f);
      assertTrue(secondAfterHit >= 984f && secondAfterHit <= 988f);

      for (int i = 0; i < 10; i++) world.process();
      assertEquals(firstAfterHit, life(world, first), EPSILON);
      assertEquals(secondAfterHit, life(world, second), EPSILON);
      assertTrue(factory.created.get(0).hitTargets.contains(first));
      assertTrue(factory.created.get(0).hitTargets.contains(second));
    } finally {
      world.dispose();
    }
  }

  private static int createMonster(World world, float x, float y) {
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "hammer-test-target";
    int id = world.create();
    world.getMapper(Monster.class).create(id).set(row, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1, 1000);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static float life(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId)
        .attrs.get(Stat.hitpoints).asFixed();
  }

  private static Attributes attributes(int level, float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.base().put(Stat.tohit, 100000);
    attrs.base().put(Stat.mindamage, 0);
    attrs.base().put(Stat.maxdamage, 0);
    attrs.reset();
    return attrs;
  }

  private static final class SilentAudio extends Audio {
    SilentAudio() { super(null); }
    @Override public Instance play(String id, boolean global) { return null; }
  }

  private static final class RecordingFactory extends EntityFactory {
    final java.util.ArrayList<Missile> created = new java.util.ArrayList<>();
    final Vector2 lastDirection = new Vector2();

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(direction).setLength(row.Vel);
      lastDirection.set(direction);
      created.add(missile);
      return entity;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }
  }
}
