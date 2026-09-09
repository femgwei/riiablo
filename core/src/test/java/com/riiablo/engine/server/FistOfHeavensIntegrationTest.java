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
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.PaladinSkills;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Authoritative SrvDo080 and shared SrvHit07 regression coverage. */
class FistOfHeavensIntegrationTest extends RiiabloTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void nativeDamageHealingRangeAndCountUseDataFormulas() {
    Skills.Entry fist = Riiablo.files.skills.get(SkillId.FIST_OF_THE_HEAVENS);
    Skills.Entry holyBolt = Riiablo.files.skills.get(SkillId.HOLY_BOLT);
    Missiles.Entry delay = Riiablo.files.Missiles.get(fist.srvmissilea);
    Missiles.Entry bolt = Riiablo.files.Missiles.get(delay.HitSubMissile[0]);

    assertEquals(150, PaladinSkills.getFistOfHeavensLightningDamage(
        fist, 1, name -> 0)[0]);
    assertEquals(200, PaladinSkills.getFistOfHeavensLightningDamage(
        fist, 1, name -> 0)[1]);
    int[] lightningSynergy = PaladinSkills.getFistOfHeavensLightningDamage(
        fist, 1, name -> "Holy Shock".equals(name) ? 1 : 0);
    assertEquals(160, lightningSynergy[0]);
    assertEquals(214, lightningSynergy[1]);

    int[] splitDamage = PaladinSkills.getFistOfHeavensBoltMagicDamage(
        bolt, fist, 1, name -> "Holy Bolt".equals(name) ? 1 : 0);
    assertEquals(46, splitDamage[0]);
    assertEquals(57, splitDamage[1]);
    assertEquals(20, PaladinSkills.getFistOfHeavensRange(delay, fist, 1));
    assertEquals(6, PaladinSkills.getFistOfHeavensBoltCount(delay, fist, 1));
    assertEquals(7, PaladinSkills.getFistOfHeavensBoltCount(delay, fist, 2));

    int[] healing = PaladinSkills.getHolyBoltHealing(
        holyBolt, 1, name -> "Prayer".equals(name) ? 1 : 0);
    assertEquals(1, healing[0]);
    assertEquals(6, healing[1]);
    int[] holyDamage = PaladinSkills.getHolyBoltMagicDamage(
        holyBolt, 1,
        name -> "Blessed Hammer".equals(name) || "Fist of the Heavens".equals(name) ? 1 : 0);
    assertEquals(16, holyDamage[0]);
    assertEquals(32, holyDamage[1]);
  }

  @Test
  void srvDo080RequiresARealLivingHostileTarget() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory, new ServerSkillSystem());
    try {
      int paladin = createPlayer(world, 0, 0);
      Skills.Entry fist = Riiablo.files.skills.get(SkillId.FIST_OF_THE_HEAVENS);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, fist.Id, Engine.INVALID_ENTITY, new Vector2(10, 10),
          fist.srvdofunc, fist.cltdofunc));
      assertEquals(0, factory.createdIds.size());

      int friendlyPet = createMonster(world, 5, 5, false, false, false);
      world.getMapper(SummonedPet.class).create(friendlyPet)
          .set(paladin, "test", fist.Id, 1, false, 0);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, fist.Id, friendlyPet, new Vector2(5, 5),
          fist.srvdofunc, fist.cltdofunc));
      assertEquals(0, factory.createdIds.size());
    } finally {
      world.dispose();
    }
  }

  @Test
  void delayHitsSavedTargetAfterTenFramesAndSplitsOnlyToEligibleUndead() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory, new ServerSkillSystem(), new MissileCollisionSystem());
    try {
      int paladin = createPlayer(world, 0, 0);
      Player player = world.getMapper(Player.class).get(paladin);
      player.data.setSkillLevel(SkillId.FIST_OF_THE_HEAVENS, 1);
      player.data.setSkillLevel(SkillId.HOLY_SHOCK, 1);
      player.data.setSkillLevel(SkillId.HOLY_BOLT, 1);
      int primary = createMonster(world, 20, 20, false, false, false);
      for (int i = 0; i < 8; i++) {
        double angle = Math.PI * 2.0 * i / 8.0;
        createMonster(world,
            20f + (float) Math.cos(angle) * 8f,
            20f + (float) Math.sin(angle) * 8f,
            true, false, false);
      }
      createMonster(world, 25, 20, false, true, false); // Demon, not undead.
      createMonster(world, 15, 20, true, false, true);  // Bosses fail AuraFilter 0x4000.

      Skills.Entry fist = Riiablo.files.skills.get(SkillId.FIST_OF_THE_HEAVENS);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, fist.Id, primary, new Vector2(99, 99),
          fist.srvdofunc, fist.cltdofunc));
      assertEquals(1, factory.count("fistoftheheavensdelay"));
      int delayId = factory.createdIds.get(0);
      Missile delay = world.getMapper(Missile.class).get(delayId);
      assertTrue(delay.fistOfHeavensDelay);
      assertEquals(primary, delay.targetId);
      assertEquals(10, delay.nativeLifetimeFrames);
      assertEquals(0f, delay.range, EPSILON);
      assertEquals(20f, factory.origins.get(0).x, EPSILON);
      assertEquals(20f, factory.origins.get(0).y, EPSILON);
      assertEquals(160, delay.damage.get(Stat.lightmindam).asInt());
      assertEquals(214, delay.damage.get(Stat.lightmaxdam).asInt());

      world.setDelta(1f / 25f);
      for (int frame = 0; frame < 9; frame++) world.process();
      assertEquals(1000f, life(world, primary), EPSILON);
      assertEquals(0, factory.count("fistoftheheavensbolt"));

      world.process();
      assertTrue(life(world, primary) >= 786f && life(world, primary) <= 840f);
      assertEquals(6, factory.count("fistoftheheavensbolt"));
      for (int i = 0; i < factory.createdIds.size(); i++) {
        if (!"fistoftheheavensbolt".equals(factory.names.get(i))) continue;
        Missile bolt = world.getMapper(Missile.class).get(factory.createdIds.get(i));
        assertEquals(fist.Id, bolt.skillId);
        assertEquals(46, bolt.damage.get(Stat.magicmindam).asInt());
        assertEquals(57, bolt.damage.get(Stat.magicmaxdam).asInt());
      }
    } finally {
      world.dispose();
    }
  }

  @Test
  void deletedTargetCancelsTheDelayedImpactSafely() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory, new ServerSkillSystem(), new MissileCollisionSystem());
    try {
      int paladin = createPlayer(world, 0, 0);
      int primary = createMonster(world, 10, 10, false, false, false);
      Skills.Entry fist = Riiablo.files.skills.get(SkillId.FIST_OF_THE_HEAVENS);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, fist.Id, primary, new Vector2(10, 10),
          fist.srvdofunc, fist.cltdofunc));
      world.delete(primary);
      world.setDelta(1f / 25f);
      for (int frame = 0; frame < 12; frame++) world.process();
      assertEquals(0, factory.count("fistoftheheavensbolt"));
    } finally {
      world.dispose();
    }
  }

  @Test
  void srvHit07HealsOwnedPetsAndClampsAtMaximumLife() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory, new MissileCollisionSystem());
    try {
      int paladin = createPlayer(world, 0, 0);
      int pet = createMonster(world, 1, 0, false, false, false);
      world.getMapper(SummonedPet.class).create(pet)
          .set(paladin, "test", SkillId.HOLY_BOLT, 1, false, 0);
      Attributes petAttrs = world.getMapper(AttributesWrapper.class).get(pet).attrs;
      petAttrs.get(Stat.hitpoints).set(99f);
      petAttrs.get(Stat.maxhp).set(100f);

      Skills.Entry skill = Riiablo.files.skills.get(SkillId.HOLY_BOLT);
      Missiles.Entry row = Riiablo.files.Missiles.get(skill.srvmissile);
      int missileId = factory.createMissile(row, Vector2.X, Vector2.Zero, paladin);
      Missile missile = world.getMapper(Missile.class).get(missileId);
      assertTrue(MissileDamageResolver.initializePaladinHolyBolt(
          missile, skill, world.getMapper(AttributesWrapper.class).get(paladin).attrs,
          1, name -> 0));
      world.setDelta(1f / 25f);
      world.process();
      assertEquals(100f, life(world, pet), EPSILON);
      assertFalse(world.getEntityManager().isActive(missileId));
    } finally {
      world.dispose();
    }
  }

  @Test
  void srvHit07DamageModeMatchesNativeMonsterTypeRules() {
    Monster ordinary = monster(false, false, false);
    Monster undead = monster(true, false, false);
    Monster demon = monster(false, true, false);
    assertTrue(MissileCollisionSystem.holyBoltCanDamage(0, true, null));
    assertFalse(MissileCollisionSystem.holyBoltCanDamage(1, true, null));
    assertTrue(MissileCollisionSystem.holyBoltCanDamage(0, false, ordinary));
    assertFalse(MissileCollisionSystem.holyBoltCanDamage(1, false, ordinary));
    assertTrue(MissileCollisionSystem.holyBoltCanDamage(1, false, undead));
    assertFalse(MissileCollisionSystem.holyBoltCanDamage(2, false, undead));
    assertTrue(MissileCollisionSystem.holyBoltCanDamage(2, false, demon));
  }

  @Test
  void localAuthoritativeWorldCreatesOnlyOneDelayMissile() {
    RecordingFactory factory = new RecordingFactory();
    Audio oldAudio = Riiablo.audio;
    Riiablo.audio = new SilentAudio();
    World world = world(factory,
        new ServerSkillSystem(true), new SkillCastHandler(), new OverlayManager());
    try {
      int paladin = createPlayer(world, 0, 0);
      int target = createMonster(world, 10, 0, false, false, false);
      Skills.Entry fist = Riiablo.files.skills.get(SkillId.FIST_OF_THE_HEAVENS);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, fist.Id, target, new Vector2(10, 0),
          fist.srvdofunc, fist.cltdofunc));
      assertEquals(1, factory.count("fistoftheheavensdelay"));
    } finally {
      world.dispose();
      Riiablo.audio = oldAudio;
    }
  }

  @Test
  void localHolyBoltAlsoUsesOneAuthoritativeSynergySnapshot() {
    RecordingFactory factory = new RecordingFactory();
    Audio oldAudio = Riiablo.audio;
    Riiablo.audio = new SilentAudio();
    World world = world(factory,
        new ServerSkillSystem(true), new SkillCastHandler(), new OverlayManager());
    try {
      int paladin = createPlayer(world, 0, 0);
      Player player = world.getMapper(Player.class).get(paladin);
      player.data.setSkillLevel(SkillId.HOLY_BOLT, 1);
      player.data.setSkillLevel(SkillId.BLESSED_HAMMER, 1);
      player.data.setSkillLevel(SkillId.FIST_OF_THE_HEAVENS, 1);
      int target = createMonster(world, 10, 0, true, false, false);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.HOLY_BOLT);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          paladin, skill.Id, target, new Vector2(10, 0),
          skill.srvdofunc, skill.cltdofunc));
      assertEquals(1, factory.count("holybolt"));
      Missile missile = world.getMapper(Missile.class).get(factory.createdIds.get(0));
      assertEquals(16, missile.damage.get(Stat.magicmindam).asInt());
      assertEquals(32, missile.damage.get(Stat.magicmaxdam).asInt());
    } finally {
      world.dispose();
      Riiablo.audio = oldAudio;
    }
  }

  private static World world(RecordingFactory factory, com.artemis.BaseSystem... systems) {
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(new EventSystem());
    for (com.artemis.BaseSystem system : systems) builder.with(system);
    return new World(builder.with(factory).build()
        .register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int createPlayer(World world, float x, float y) {
    int id = world.create();
    CharData data = CharData.createRemote("paladin", (byte) Riiablo.PALADIN);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(40, 1000);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int createMonster(
      World world, float x, float y, boolean undead, boolean demon, boolean boss) {
    int id = world.create();
    world.getMapper(Monster.class).create(id).set(
        monster(undead, demon, boss).monstats, new MonStats2.Entry());
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(1, 1000);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Monster monster(boolean undead, boolean demon, boolean boss) {
    MonStats.Entry row = new MonStats.Entry();
    row.Id = "foh-test-target";
    row.lUndead = undead;
    row.demon = demon;
    row.boss = boss;
    return new Monster().set(row, new MonStats2.Entry());
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
    attrs.reset();
    return attrs;
  }

  private static final class SilentAudio extends Audio {
    SilentAudio() { super(null); }
    @Override public Instance play(String id, boolean global) { return null; }
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Integer> createdIds = new ArrayList<>();
    final ArrayList<String> names = new ArrayList<>();
    final ArrayList<Vector2> origins = new ArrayList<>();

    int count(String name) {
      int count = 0;
      for (String value : names) if (name.equals(value)) count++;
      return count;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(direction).setLength(row.Vel);
      createdIds.add(entity);
      names.add(row.Missile);
      origins.add(new Vector2(position));
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
