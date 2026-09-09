package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.serializer.StateSerializer;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillCooldownManager;
import com.riiablo.engine.server.skill.SkillFormula;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native SrvDo018 and physical-hit contract for Necromancer Bone Armor. */
class NecromancerBoneArmorIntegrationTest extends RiiabloTest {
  @Test
  void castUsesNativeAuraFormulaAndRecastRestoresTheShield() {
    World world = world();
    try {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BONE_ARMOR);
      assertNotNull(skill);
      assertEquals(18, skill.srvdofunc);
      int necromancer = player(world, 100);
      CharData data = world.getMapper(Player.class).get(necromancer).data;
      data.setSkillLevel(skill.Id, 4);
      Skills.Entry wall = Riiablo.files.skills.get("Bone Wall");
      Skills.Entry prison = Riiablo.files.skills.get("Bone Prison");
      assertNotNull(wall);
      assertNotNull(prison);
      data.setSkillLevel(wall.Id, 2);
      data.setSkillLevel(prison.Id, 3);

      dispatchCast(world, necromancer, skill);

      UnitState armor = armor(world, necromancer);
      int expected = auraValue(skill, Stat.bonearmor, 4, data);
      assertTrue(expected > 0, "1.10f AuraStatCalc must provide Bone Armor capacity");
      assertEquals(expected, armor.runtimeValue);
      assertEquals(expected, armor.getStatContributionValue(Stat.bonearmor));
      assertEquals(expected, armor.getStatContributionValue(Stat.bonearmormax));

      DamageEvent hit = DamageEvent.obtainMelee(1, necromancer, 8, 8);
      world.getSystem(EventSystem.class).dispatch(hit);
      assertEquals(expected - 8, armor(world, necromancer).runtimeValue);
      dispatchCast(world, necromancer, skill);
      assertEquals(expected, armor(world, necromancer).runtimeValue,
          "native SrvDo018 replaces the old stat-list and refills the armor");
    } finally {
      world.dispose();
    }
  }

  @Test
  void armorAbsorbsOnlyPhysicalDamageAndIsRemovedWhenEmpty() {
    World world = world();
    try {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BONE_ARMOR);
      int necromancer = player(world, 100);
      int attacker = monster(world, 100);
      world.getMapper(Player.class).get(necromancer).data.setSkillLevel(skill.Id, 1);
      dispatchCast(world, necromancer, skill);
      int capacity = armor(world, necromancer).runtimeValue;

      DamageEvent mixed = DamageEvent.obtainMissile(
          attacker, necromancer, 15, 10, null);
      world.getSystem(EventSystem.class).dispatch(mixed);
      assertEquals(5f, mixed.damage, 0.0001f,
          "the elemental portion must pass through Bone Armor");
      assertEquals(0f, mixed.physicalDamage, 0.0001f);
      assertEquals(capacity - 10, armor(world, necromancer).runtimeValue);

      DamageEvent depleted = DamageEvent.obtainMelee(
          attacker, necromancer, capacity, capacity);
      world.getSystem(EventSystem.class).dispatch(depleted);
      assertFalse(states(world, necromancer).stateList.hasState(StateId.BONEARMOR));
      assertEquals(10f, depleted.damage, 0.0001f,
          "only the shield remainder is consumed by the final hit");
      assertEquals(10f, depleted.physicalDamage, 0.0001f);
    } finally {
      world.dispose();
    }
  }

  @Test
  void snapshotClientDoesNotConsumeArmorAndRuntimeCapacityIsSerialized() {
    World world = world();
    try {
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.BONE_ARMOR);
      int necromancer = player(world, 100);
      int attacker = monster(world, 100);
      world.getMapper(Player.class).get(necromancer).data.setSkillLevel(skill.Id, 1);
      dispatchCast(world, necromancer, skill);
      UnitStates source = states(world, necromancer);
      int capacity = armor(world, necromancer).runtimeValue;

      source.snapshotOnly = true;
      DamageEvent replay = DamageEvent.obtainMelee(attacker, necromancer, 7, 7);
      world.getSystem(EventSystem.class).dispatch(replay);
      assertEquals(7f, replay.damage, 0.0001f);
      assertEquals(capacity, armor(world, necromancer).runtimeValue);

      StateSerializer serializer = new StateSerializer();
      FlatBufferBuilder builder = new FlatBufferBuilder(256);
      int stateOffset = serializer.putData(builder, source);
      int typeOffset = EntitySync.createComponentTypeVector(
          builder, new byte[] {ComponentP.StateP});
      int componentOffset = EntitySync.createComponentVector(
          builder, new int[] {stateOffset});
      int root = EntitySync.createEntitySync(builder, necromancer, 0, 0,
          typeOffset, componentOffset, 0L, 0L, 0L, 0L, 0L, -1);
      builder.finish(root);
      UnitStates remote = new UnitStates().init(necromancer);
      serializer.getData(EntitySync.getRootAsEntitySync(builder.dataBuffer()), 0, remote);
      assertEquals(capacity,
          remote.stateList.getState(StateId.BONEARMOR).runtimeValue);
    } finally {
      world.dispose();
    }
  }

  @Test
  void boneArmorHasNoInventedCooldown() {
    assertEquals(SkillCooldownManager.NO_COOLDOWN,
        new SkillCooldownManager().getSkillCooldown(SkillId.BONE_ARMOR));
  }

  private static World world() {
    NoopFactory factory = new NoopFactory();
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static void dispatchCast(World world, int owner, Skills.Entry skill) {
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        owner, skill.Id, Engine.INVALID_ENTITY, new Vector2(1, 0), skill.srvdofunc, 0));
  }

  private static int auraValue(Skills.Entry skill, int statId, int level, CharData data) {
    for (int i = 0; i < skill.aurastat.length && i < skill.aurastatcalc.length; i++) {
      if (Stat.index(skill.aurastat[i]) != statId) continue;
      return SkillFormula.evaluate(skill.aurastatcalc[i], skill, level,
          name -> baseLevel(data, name), name -> Riiablo.files.skills.get(name));
    }
    return 0;
  }

  private static int baseLevel(CharData data, String name) {
    Skills.Entry skill = Riiablo.files.skills.get(name);
    return skill != null ? data.getSkill(skill.Id) : 0;
  }

  private static UnitState armor(World world, int entityId) {
    UnitState armor = states(world, entityId).stateList.getState(StateId.BONEARMOR);
    assertNotNull(armor);
    return armor;
  }

  private static UnitStates states(World world, int entityId) {
    UnitStates states = world.getMapper(UnitStates.class).get(entityId);
    assertNotNull(states);
    return states;
  }

  private static int player(World world, float hp) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data =
        CharData.createRemote("bone-armor", (byte) Riiablo.NECROMANCER);
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int monster(World world, float hp) {
    int id = world.create();
    world.getMapper(Monster.class).create(id).monstats = Riiablo.files.monstats.get("fallen1");
    world.getMapper(Position.class).create(id).position.set(1, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(hp);
    return id;
  }

  private static Attributes attributes(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, 10);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.reset();
    return attrs;
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return Engine.INVALID_ENTITY; }
  }
}
