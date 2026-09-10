package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.MeleeAttackEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.skill.SorceressSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Server-authoritative SrvDo018/SrvDo025 and armor-event integration. */
class SorceressDefenseIntegrationTest extends RiiabloTest {
  @Test
  void enchantTargetsAlliesAndFallsBackFromHostileTargets() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int caster = player(world, "caster", 0, 0);
      int ally = player(world, "ally", 1, 0);
      int monster = monster(world, 2, 0, 100);
      CharData data = world.getMapper(Player.class).get(caster).data;
      data.setSkillLevel(SkillId.ENCHANT, 1);
      data.setSkillLevel(SkillId.WARMTH, 1);
      data.setSkillLevel(SkillId.FIRE_MASTERY, 1);
      world.process();

      UnitState mastery = states(world, caster).getState(StateId.FIREMASTERY);
      assertNotNull(mastery);
      assertEquals(30,
          mastery.getStatContributionValue(Stat.passive_fire_mastery));

      dispatch(world, caster, ally, SkillId.ENCHANT);
      UnitState alliedEnchant = states(world, ally).getState(StateId.ENCHANT);
      assertNotNull(alliedEnchant);
      assertEquals(caster, alliedEnchant.sourceEntityId);
      assertEquals(10, alliedEnchant.getStatContributionValue(Stat.firemindam));
      assertEquals(13, alliedEnchant.getStatContributionValue(Stat.firemaxdam));
      assertEquals(20,
          alliedEnchant.getStatContributionValue(Stat.item_tohit_percent));

      dispatch(world, caster, monster, SkillId.ENCHANT);
      assertNull(states(world, monster).getState(StateId.ENCHANT));
      assertNotNull(states(world, caster).getState(StateId.ENCHANT),
          "native SrvDo025 falls back to the caster for a non-aligned target");
    } finally {
      world.dispose();
    }
  }

  @Test
  void armorFamilyIsExclusiveAndDispatchesItsThreeNativeEvents() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int sorceress = player(world, "armor", 0, 0);
      int attacker = monster(world, 1, 0, 200);
      CharData data = world.getMapper(Player.class).get(sorceress).data;
      data.setSkillLevel(SkillId.FROZEN_ARMOR, 1);
      data.setSkillLevel(SkillId.SHIVER_ARMOR, 1);
      data.setSkillLevel(SkillId.CHILLING_ARMOR, 1);

      dispatch(world, sorceress, Engine.INVALID_ENTITY, SkillId.FROZEN_ARMOR);
      UnitState frozen = states(world, sorceress).getState(StateId.FROZENARMOR);
      assertNotNull(frozen);
      assertEquals(30,
          frozen.getStatContributionValue(Stat.skill_armor_percent));
      world.getSystem(EventSystem.class).dispatch(
          DamageEvent.obtainMelee(attacker, sorceress, 4, 4));
      assertTrue(states(world, attacker).hasState(StateId.FREEZE)
              || states(world, attacker).hasState(StateId.COLD),
          "Frozen Armor must freeze or chill a native-ineligible monster");

      states(world, attacker).removeState(StateId.FREEZE);
      states(world, attacker).removeState(StateId.COLD);
      dispatch(world, sorceress, Engine.INVALID_ENTITY, SkillId.SHIVER_ARMOR);
      assertFalse(states(world, sorceress).hasState(StateId.FROZENARMOR));
      assertNotNull(states(world, sorceress).getState(StateId.SHIVERARMOR));
      float hpBefore = hp(world, attacker);
      world.getSystem(EventSystem.class).dispatch(
          MeleeAttackEvent.obtain(attacker, sorceress, false, false));
      assertTrue(hp(world, attacker) < hpBefore,
          "Shiver Armor must resolve its cold retaliation packet");
      assertTrue(states(world, attacker).hasState(StateId.FREEZE)
              || states(world, attacker).hasState(StateId.COLD));

      dispatch(world, sorceress, Engine.INVALID_ENTITY, SkillId.CHILLING_ARMOR);
      assertFalse(states(world, sorceress).hasState(StateId.SHIVERARMOR));
      assertNotNull(states(world, sorceress).getState(StateId.CHILLINGARMOR));
      DamageEvent ignored = DamageEvent.obtainMissile(attacker, sorceress, 4, 0, null);
      world.getSystem(EventSystem.class).dispatch(ignored);
      assertEquals(0, factory.created.size());
      DamageEvent returnFire = DamageEvent.obtainMissile(attacker, sorceress, 4, 0, null)
          .withReturnFire(true);
      world.getSystem(EventSystem.class).dispatch(returnFire);
      assertEquals(1, factory.created.size());
      Missile bolt = factory.created.get(0);
      assertEquals("chillingarmorbolt", bolt.missile.Missile);
      assertEquals(sorceress, bolt.ownerId);
      assertEquals(attacker, bolt.targetId);
      assertTrue(bolt.damageSnapshot);
    } finally {
      world.dispose();
    }
  }

  @Test
  void fireMasteryStateIsIncludedInMissileAndStateSnapshots() {
    Skills.Entry masterySkill = Riiablo.files.skills.get(SkillId.FIRE_MASTERY);
    StateList ownerStates = new StateList(7);
    SorceressSkills.applyFireMasteryState(ownerStates, masterySkill, 1, 7);
    Skills.Entry fireBolt = Riiablo.files.skills.get(SkillId.FIRE_BOLT);
    Missiles.Entry row = Riiablo.files.Missiles.get(fireBolt.srvmissile);
    assertNotNull(row);
    Attributes owner = attributes(100);
    Missile plain = new Missile().set(row, Vector2.Zero, row.Range).setOwner(7);
    Missile mastered = new Missile().set(row, Vector2.Zero, row.Range).setOwner(7);
    assertTrue(MissileDamageResolver.initializeSkill(
        plain, fireBolt, owner, 1, name -> 0));
    assertTrue(MissileDamageResolver.initializeSkill(
        mastered, fireBolt, owner, 1, name -> 0, ownerStates));
    assertTrue(mastered.damage.get(Stat.firemaxdam).asInt()
        > plain.damage.get(Stat.firemaxdam).asInt());

    StateList armorStates = new StateList(8);
    UnitState armor = SorceressSkills.applyDefensiveArmorState(
        armorStates, Riiablo.files.skills.get(SkillId.FROZEN_ARMOR), 2, 8, name -> 1);
    UnitStates source = new UnitStates().init(8);
    source.stateList = armorStates;
    StateSerializer serializer = new StateSerializer();
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int stateOffset = serializer.putData(builder, source);
    int types = EntitySync.createComponentTypeVector(builder, new byte[] {ComponentP.StateP});
    int components = EntitySync.createComponentVector(builder, new int[] {stateOffset});
    int root = EntitySync.createEntitySync(builder, 8, 0, 0, types, components,
        0L, 0L, 0L, 0L, 0L, -1);
    builder.finish(root);
    UnitStates remote = new UnitStates().init(8);
    serializer.getData(EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, remote);
    UnitState remoteArmor = remote.stateList.getState(StateId.FROZENARMOR);
    assertNotNull(remoteArmor);
    assertEquals(armor.getStatContributionValue(Stat.skill_armor_percent),
        remoteArmor.getStatContributionValue(Stat.skill_armor_percent));
  }

  private static World world(RecordingFactory factory) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static void dispatch(World world, int source, int target, int skillId) {
    Skills.Entry skill = Riiablo.files.skills.get(skillId);
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        source, skillId, target, new Vector2(1, 0), skill.srvdofunc, 0));
  }

  private static int player(World world, String name, float x, float y) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data =
        CharData.createRemote(name, (byte) Riiablo.SORCERESS);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(100);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float x, float y, float hp) {
    int id = world.create();
    world.getMapper(Monster.class).create(id).monstats =
        Riiablo.files.monstats.get("fallen1");
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static StateList states(World world, int entityId) {
    return world.getMapper(UnitStates.class).get(entityId).stateList;
  }

  private static float hp(World world, int entityId) {
    return world.getMapper(AttributesWrapper.class).get(entityId).attrs
        .get(Stat.hitpoints).asFixed();
  }

  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 30);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.tohit, 1000);
    attrs.base().put(Stat.mindamage, 1);
    attrs.base().put(Stat.maxdamage, 2);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> created = new ArrayList<>();

    @Override public int createMissile(
        int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      Missile missile = world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      world.getMapper(Velocity.class).create(entity).velocity
          .set(direction).setLength(row.Vel);
      created.add(missile);
      return entity;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }

    @Override public int createPlayer(CharData data, Vector2 position) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createDynamicObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createStaticObject(int act, int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createStaticObjectByClassId(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createMonster(int id, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createWarp(int index, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
    @Override public int createItem(Item item, float x, float y) {
      return Engine.INVALID_ENTITY;
    }
  }
}
