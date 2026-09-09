package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.CorpseConsumption;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Authoritative SrvSt17/SrvDo055/SrvDo063 integration coverage. */
class NecromancerExplosionIntegrationTest extends RiiabloTest {
  @Test
  void corpseExplosionConsumesOnceAndAppliesInnerPhysicalOuterFireDamage() {
    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem(true);
    World world = world(factory, skills, false);
    try {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.CORPSE_EXPLOSION);
      int owner = player(world, SkillId.CORPSE_EXPLOSION);
      int corpse = corpse(world, 0, 0, 100, 5);
      int inner = monster(world, 3, 0, 1000);
      int outer = monster(world, 4.5f, 0, 1000);
      int friendly = player(world);
      world.getMapper(Position.class).get(friendly).position.set(3, 0);

      SkillDoEvent event = SkillDoEvent.obtain(
          owner, skill.Id, corpse, new Vector2(0, 0), skill.srvdofunc, skill.cltdofunc);
      world.getSystem(EventSystem.class).dispatch(event);

      Corpse marker = world.getMapper(Corpse.class).get(corpse);
      assertFalse(marker.usable);
      assertTrue(world.getMapper(UnitStates.class).get(corpse)
          .stateList.hasState(StateId.CORPSE_NOSELECT));
      assertTrue(world.getMapper(UnitStates.class).get(corpse)
          .stateList.hasState(StateId.CORPSE_NODRAW));
      float innerDamage = 1000 - hp(world, inner);
      float outerDamage = 1000 - hp(world, outer);
      assertTrue(innerDamage >= 70 && innerDamage <= 119,
          "inner radius must receive the complete 70%-119% packet");
      assertTrue(outerDamage >= 35 && outerDamage <= 59,
          "the outer half-square ring receives only the 50% fire portion");
      assertEquals(100f, hp(world, friendly));

      world.getSystem(EventSystem.class).dispatch(event);
      assertEquals(innerDamage, 1000 - hp(world, inner));
      assertEquals(outerDamage, 1000 - hp(world, outer));
    } finally {
      world.dispose();
    }
  }

  @Test
  void sharedReservationPreventsDeathSentryOrSecondPlayerDoubleConsumption() {
    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem(true);
    World world = world(factory, skills, false);
    try {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.CORPSE_EXPLOSION);
      int owner = player(world, SkillId.CORPSE_EXPLOSION);
      int corpseId = corpse(world, 0, 0, 100, 5);
      int target = monster(world, 1, 0, 1000);
      Corpse corpse = world.getMapper(Corpse.class).get(corpseId);
      Monster monster = world.getMapper(Monster.class).get(corpseId);
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(corpseId).attrs;
      assertTrue(CorpseConsumption.tryReserve(corpse, monster, attrs,
          world.getMapper(UnitStates.class).get(corpseId).stateList,
          true, 1, 99, skill.Id));
      assertFalse(CorpseConsumption.tryReserve(corpse, monster, attrs,
          world.getMapper(UnitStates.class).get(corpseId).stateList,
          true, 1, owner, skill.Id));

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, corpseId, Vector2.Zero, skill.srvdofunc, skill.cltdofunc));
      assertEquals(1000f, hp(world, target));
    } finally {
      world.dispose();
    }
  }

  @Test
  void poisonExplosionCreatesEightAuthoritativeDriftingCloudsAndAppliesFixedPoison() {
    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem(true);
    World world = world(factory, skills, true);
    try {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.POISON_EXPLOSION);
      int owner = player(world, SkillId.POISON_EXPLOSION);
      Attributes ownerAttrs = world.getMapper(AttributesWrapper.class).get(owner).attrs;
      ownerAttrs.base().put(Stat.passive_pois_mastery, 25);
      ownerAttrs.base().put(Stat.item_pierce_pois, 10);
      ownerAttrs.reset();
      int corpse = corpse(world, 0, 0, 100, 5);
      int target = monster(world, 0.5f, 0, 1000);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, skill.Id, corpse, Vector2.Zero, skill.srvdofunc, skill.cltdofunc));
      assertFalse(world.getMapper(Corpse.class).get(corpse).usable);
      assertTrue(world.getMapper(UnitStates.class).get(corpse)
          .stateList.hasState(StateId.CORPSE_NOSELECT));
      assertFalse(world.getMapper(UnitStates.class).get(corpse)
          .stateList.hasState(StateId.CORPSE_NODRAW));

      IntBag missiles = world.getAspectSubscriptionManager()
          .get(Aspect.all(Missile.class)).getEntities();
      assertEquals(8, missiles.size());
      for (int i = 0; i < missiles.size(); i++) {
        Missile cloud = world.getMapper(Missile.class).get(missiles.get(i));
        assertEquals("poisonexplosioncloud", cloud.missile.Missile);
        assertTrue(cloud.authoritative);
        assertTrue(cloud.fixedPoisonRate);
        assertTrue(cloud.persistent);
        assertEquals(200, cloud.poisonMinRateFixed);
        assertEquals(520, cloud.poisonMaxRateFixed);
        assertEquals(60, cloud.poisonDurationFrames);
        assertEquals(10, cloud.poisonPiercePercent);
        assertEquals(2f, world.getMapper(Velocity.class).get(missiles.get(i)).velocity.len(), 0.001f);
      }

      world.setDelta(1f / 25f);
      world.process();
      assertNotNull(world.getMapper(UnitStates.class).get(target)
          .stateList.getState(StateId.POISON));
      assertTrue(hp(world, target) < 1000f,
          "StateUpdater must consume the authoritative per-frame poison state");

      int snapshotTarget = monster(world, 0.5f, 0.2f, 1000);
      for (int i = 0; i < missiles.size(); i++) {
        world.getMapper(Missile.class).get(missiles.get(i)).authoritative = false;
      }
      world.process();
      assertNull(world.getMapper(UnitStates.class).get(snapshotTarget)
          .stateList.getState(StateId.POISON));
      assertEquals(1000f, hp(world, snapshotTarget));
    } finally {
      world.dispose();
    }
  }

  @Test
  void townOrNoSelectCorpseIsRejectedBeforeManaSpend() {
    RecordingFactory factory = new RecordingFactory();
    ServerSkillSystem skills = new ServerSkillSystem(false);
    World world = world(factory, skills, false);
    try {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.CORPSE_EXPLOSION);
      int owner = player(world, SkillId.CORPSE_EXPLOSION);
      int corpse = corpse(world, 0, 0, 100, 5);
      world.getMapper(MapWrapper.class).create(corpse).zone = new com.riiablo.map.Map.Zone() {
        @Override public boolean isTown() { return true; }
      };
      float mana = mana(world, owner);
      SkillCastEvent cast = SkillCastEvent.obtain(owner, skill.Id, corpse, Vector2.Zero);
      skills.onSkillCast(cast);
      assertFalse(cast.accepted);
      assertEquals(mana, mana(world, owner));

      world.getMapper(MapWrapper.class).remove(corpse);
      world.getMapper(UnitStates.class).get(corpse).stateList.addState(
          StateId.CORPSE_NOSELECT, Integer.MAX_VALUE, 1, owner);
      SkillCastEvent reserved = SkillCastEvent.obtain(owner, skill.Id, corpse, Vector2.Zero);
      skills.onSkillCast(reserved);
      assertFalse(reserved.accepted);
      assertEquals(mana, mana(world, owner));
    } finally {
      world.dispose();
    }
  }

  private static World world(
      RecordingFactory factory, ServerSkillSystem skills, boolean missiles) {
    WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
        .with(new EventSystem(), skills);
    if (missiles) builder.with(new MissileCollisionSystem(), new StateUpdater());
    return new World(builder.with(factory).build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, int... learned) {
    int id = world.create();
    CharData data = CharData.createRemote("necromancer-" + id, (byte) Riiablo.NECROMANCER);
    for (int skill : learned) data.setSkillLevel(skill, 2);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(-2, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(10, 100, 100);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int corpse(
      World world, float x, float y, float maxHp, int level) {
    int id = monster(world, x, y, maxHp);
    Attributes attrs = world.getMapper(AttributesWrapper.class).get(id).attrs;
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, 0);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.reset();
    world.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
    return id;
  }

  private static int monster(World world, float x, float y, float hp) {
    int id = world.create();
    com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
    Monster monster = world.getMapper(Monster.class).create(id);
    monster.monstats = row;
    monster.monstats2 = Riiablo.files.monstats2.get(row.MonStatsEx);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(5, hp, 0);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static Attributes attributes(int level, float hp, int poisonResist) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.base().put(Stat.poisonresist, poisonResist);
    attrs.reset();
    return attrs;
  }

  private static float hp(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs
        .get(Stat.hitpoints).asFixed();
  }

  private static float mana(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.mana).asFixed();
  }

  private static final class RecordingFactory extends EntityFactory {
    @Override public int createMissile(
        int id, Vector2 angle, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      missile.rngState = NativeRng.forUnit(Riiablo.gameSeed, entity).state();
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity.set(angle).setLength(row.Vel);
      return entity;
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
  }
}
