package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativeUnitFlags;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.ai.NecroPet;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native SrvDo056/057/058 summon integration. */
class NecromancerGolemReviveIntegrationTest extends RiiabloTest {
  @Test
  void playerReviveUsesSpecialNecroPetAiWithoutChangingOrdinaryRaiseAi() {
    com.riiablo.codec.excel.MonStats.Entry row = firstReviveableNonNecroPetMonster();
    assertTrue(ServerEntityFactory.restoredMonsterAi(17, row, true) instanceof NecroPet);
    assertFalse(ServerEntityFactory.restoredMonsterAi(17, row, false) instanceof NecroPet,
        "Shaman/self resurrection must retain the monster's ordinary AI");
  }

  @Test
  void golemAndIronGolemCreateOwnedPetsAndConsumeOnlyMetalItem() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int owner = owner(world, SkillId.CLAY_GOLEM, SkillId.IRON_GOLEM);
      Skills.Entry clay = Riiablo.files.skills.get(SkillId.CLAY_GOLEM);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, clay.Id, Engine.INVALID_ENTITY, new Vector2(12, 10), 56, 0));
      assertEquals(owner, world.getMapper(SummonedPet.class).get(factory.lastEntity).ownerId);

      com.riiablo.item.Item sword = new ItemGenerator().generate("ssd");
      sword.flags |= com.riiablo.item.Item.ITEMFLAG_IDENTIFIED;
      sword.location = Location.GROUND;
      int itemId = world.create();
      world.getMapper(com.riiablo.engine.server.component.Item.class).create(itemId).set(sword);
      world.getMapper(Position.class).create(itemId).position.set(13, 10);
      Skills.Entry iron = Riiablo.files.skills.get(SkillId.IRON_GOLEM);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, iron.Id, itemId, new Vector2(13, 10), 57, 0));
      SummonedPet pet = world.getMapper(SummonedPet.class).get(factory.lastEntity);
      assertNotNull(pet.sourceItem);
      assertTrue(pet.sourceItem == sword, "Iron Golem must retain its source item payload");
      world.process();
      assertFalse(world.getEntityManager().isActive(itemId));
    } finally {
      world.dispose();
    }
  }

  @Test
  void reviveConvertsCorpseInPlaceAndMarksNoRewardOwnedState() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      int owner = owner(world, SkillId.REVIVE);
      Skills.Entry revive = Riiablo.files.skills.get(SkillId.REVIVE);
      com.riiablo.codec.excel.MonStats.Entry row = firstReviveableMonster();
      int corpse = world.create();
      Monster monster = world.getMapper(Monster.class).create(corpse);
      monster.monstats = row;
      monster.monstats2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      world.getMapper(Position.class).create(corpse).position.set(12, 10);
      world.getMapper(AttributesWrapper.class).create(corpse).attrs = attributes(3, 0, 40);
      world.getMapper(UnitStates.class).create(corpse).init(corpse);
      world.getMapper(NativeUnitFlags.class).create(corpse).reset();
      world.getMapper(Corpse.class).create(corpse).reset(Corpse.DEFAULT_DURATION, true);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          owner, revive.Id, corpse, new Vector2(12, 10), 58, 0));
      assertTrue(factory.playerReviveRequested,
          "SrvDo058 must use the dedicated player-Revive restoration path");
      assertFalse(world.getMapper(Corpse.class).has(corpse));
      assertEquals(owner, world.getMapper(SummonedPet.class).get(corpse).ownerId);
      assertTrue(world.getMapper(NativeUnitFlags.class).get(corpse)
          .has(NativeUnitFlags.PLAYER_SUMMON | NativeUnitFlags.IS_REVIVE));
      assertTrue(world.getMapper(UnitStates.class).get(corpse).stateList
          .hasState(com.riiablo.engine.server.state.StateId.REVIVE));
      assertTrue(world.getMapper(Monster.class).get(corpse).monstats == row,
          "Revive must preserve the corpse's original MonStats and skill slots");
    } finally {
      world.dispose();
    }
  }

  private static World world(RecordingFactory factory) {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), factory)
        .build().register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int owner(World world, int... skills) {
    int owner = world.create();
    CharData data = CharData.createRemote("necromancer", (byte) Riiablo.NECROMANCER);
    for (int skill : skills) data.setSkillLevel(skill, 5);
    world.getMapper(Player.class).create(owner).data = data;
    world.getMapper(Position.class).create(owner).position.set(10, 10);
    world.getMapper(AttributesWrapper.class).create(owner).attrs = attributes(20, 100, 100);
    return owner;
  }

  private static com.riiablo.codec.excel.MonStats.Entry firstReviveableMonster() {
    for (com.riiablo.codec.excel.MonStats.Entry row : Riiablo.files.monstats) {
      com.riiablo.codec.excel.MonStats2.Entry row2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      if (row2 != null && row2.revive) return row;
    }
    throw new AssertionError("1.10f contains no reviveable monster row");
  }

  private static com.riiablo.codec.excel.MonStats.Entry firstReviveableNonNecroPetMonster() {
    for (com.riiablo.codec.excel.MonStats.Entry row : Riiablo.files.monstats) {
      com.riiablo.codec.excel.MonStats2.Entry row2 = Riiablo.files.monstats2.get(row.MonStatsEx);
      if (row2 != null && row2.revive && !"NecroPet".equals(row.AI)) return row;
    }
    throw new AssertionError("1.10f contains no non-NecroPet reviveable monster row");
  }

  private static Attributes attributes(int level, float hp, float maxHp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    int lastEntity = Engine.INVALID_ENTITY;
    boolean playerReviveRequested;

    @Override public int createSummonedPet(int ownerId,
        com.riiablo.codec.excel.MonStats.Entry summon, String petType, int skillId,
        int skillLevel, int petMax, boolean passive, int durationFrames, float x, float y) {
      lastEntity = world.create();
      Monster monster = world.getMapper(Monster.class).create(lastEntity);
      monster.monstats = summon;
      monster.monstats2 = Riiablo.files.monstats2.get(summon.MonStatsEx);
      world.getMapper(Position.class).create(lastEntity).position.set(x, y);
      world.getMapper(AttributesWrapper.class).create(lastEntity).attrs = attributes(1, 10, 10);
      world.getMapper(UnitStates.class).create(lastEntity).init(lastEntity);
      world.getMapper(NativeUnitFlags.class).create(lastEntity).reset()
          .set(NativeUnitFlags.PLAYER_SUMMON);
      world.getMapper(SummonedPet.class).create(lastEntity)
          .set(ownerId, petType, skillId, skillLevel, passive, durationFrames);
      return lastEntity;
    }

    @Override public boolean resurrectMonster(int monsterId, int sourceId) {
      Corpse corpse = world.getMapper(Corpse.class).get(monsterId);
      if (corpse == null || !corpse.usable) return false;
      corpse.usable = false;
      world.getMapper(Corpse.class).remove(monsterId);
      Attributes attrs = world.getMapper(AttributesWrapper.class).get(monsterId).attrs;
      attrs.base().put(Stat.hitpoints, attrs.get(Stat.maxhp).asFixed());
      attrs.reset();
      return true;
    }

    @Override public boolean reviveMonster(int monsterId, int ownerId) {
      playerReviveRequested = true;
      return resurrectMonster(monsterId, ownerId);
    }

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
