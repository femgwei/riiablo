package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.RiiabloTest;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.BarbarianSkills;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native Skills.txt contract tests for Barbarian corpse tools. */
class BarbarianCorpseSkillTest extends RiiabloTest {
  @Test
  void nativeFunctionsAndChanceFormulasAreLoaded() {
    Skills.Entry potion = Riiablo.files.skills.get("Find Potion");
    Skills.Entry item = Riiablo.files.skills.get("Find Item");
    Skills.Entry ward = Riiablo.files.skills.get("Grim Ward");
    assertEquals(69, potion.srvdofunc);
    assertEquals(72, item.srvdofunc);
    assertEquals(75, ward.srvdofunc);
    assertTrue(BarbarianSkills.getFindPotionChance(potion, 1) > 0);
    assertTrue(BarbarianSkills.getFindItemChance(item, 1) > 0);
  }

  @Test
  void findItemUsesNativeFourQualityBuckets() {
    Skills.Entry item = Riiablo.files.skills.get("Find Item");
    assertEquals(1, BarbarianSkills.resolveFindItemBucket(item, 0));
    assertEquals(4, BarbarianSkills.resolveFindItemBucket(item, 99));
  }

  @Test
  void findPotionReservesBeforeChanceRollAndRejectsRepeatKeyframe() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    int oldSeed = Riiablo.gameSeed;
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Find Potion");
      int owner = player(world, skill);
      int corpseId = corpse(world, false, false);
      Riiablo.gameSeed = seedForFailedRoll(owner, corpseId,
          BarbarianSkills.getFindPotionChance(skill, 1));

      dispatch(world, owner, skill, corpseId);
      Corpse corpse = world.getMapper(Corpse.class).get(corpseId);
      UnitStates states = world.getMapper(UnitStates.class).get(corpseId);
      assertFalse(corpse.usable, "native Find Potion reserves before its chance roll");
      assertTrue(states.stateList.hasState(StateId.CORPSE_NOSELECT));

      dispatch(world, owner, skill, corpseId);
      assertEquals(0, factory.created.size(),
          "a repeated keyframe must not run a second corpse transaction");
      assertTrue(states.stateList.hasState(StateId.CORPSE_NOSELECT));
    } finally {
      Riiablo.gameSeed = oldSeed;
      world.dispose();
    }
  }

  @Test
  void corpseToolsRejectCorpseSelShatteredAndFadingTargets() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Find Item");
      int owner = player(world, skill);
      int noSelectCorpse = corpse(world, true, false);
      world.getMapper(Monster.class).get(noSelectCorpse).monstats2.corpseSel = false;
      dispatch(world, owner, skill, noSelectCorpse);
      assertUsableAndUnmarked(world, noSelectCorpse);

      int shatteredCorpse = corpse(world, false, true);
      dispatch(world, owner, skill, shatteredCorpse);
      assertUsableAndUnmarked(world, shatteredCorpse);

      int fadingCorpse = corpse(world, false, false);
      world.getMapper(Corpse.class).get(fadingCorpse).fading = true;
      dispatch(world, owner, skill, fadingCorpse);
      assertUsableAndUnmarked(world, fadingCorpse);
    } finally {
      world.dispose();
    }
  }

  @Test
  void grimWardCreatesOneSizeSpecificMissileAndConsumesCorpseOnce() {
    RecordingFactory factory = new RecordingFactory();
    World world = world(factory);
    try {
      Skills.Entry skill = Riiablo.files.skills.get("Grim Ward");
      int owner = player(world, skill);
      int corpseId = corpse(world, false, false);
      Monster monster = world.getMapper(Monster.class).get(corpseId);
      monster.monstats2.small = true;

      dispatch(world, owner, skill, corpseId);
      Corpse corpse = world.getMapper(Corpse.class).get(corpseId);
      UnitStates states = world.getMapper(UnitStates.class).get(corpseId);
      assertFalse(corpse.usable);
      assertTrue(states.stateList.hasState(StateId.CORPSE_NOSELECT));
      assertTrue(states.stateList.hasState(StateId.CORPSE_NODRAW));
      assertEquals(1, factory.created.size());
      assertEquals(skill.srvmissileb, factory.created.get(0));

      dispatch(world, owner, skill, corpseId);
      assertEquals(1, factory.created.size(),
          "native Grim Ward must not create a second missile for a reserved corpse");
    } finally {
      world.dispose();
    }
  }

  private static World world(RecordingFactory factory) {
    ServerSkillSystem skills = new ServerSkillSystem(false);
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), skills, factory)
        .build()
        .register("factory", factory)
        .register("map", new com.riiablo.map.Map(0, 0)));
  }

  private static int player(World world, Skills.Entry skill) {
    int id = world.create();
    CharData data = CharData.createRemote("barbarian-" + id, (byte) Riiablo.BARBARIAN);
    data.setSkillLevel(skill.Id, 1);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(Position.class).create(id).position.set(0, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(10, 100);
    world.getMapper(UnitStates.class).create(id).init(id);
    return id;
  }

  private static int corpse(World world, boolean corpseSel, boolean shattered) {
    int id = world.create();
    MonStats.Entry stats = Riiablo.files.monstats.get("fallen1");
    MonStats2.Entry stats2 = new MonStats2.Entry();
    stats2.corpseSel = corpseSel || !shattered;
    Monster monster = world.getMapper(Monster.class).create(id).set(stats, stats2);
    monster.shatteredAtDeath = shattered;
    world.getMapper(Position.class).create(id).position.set(3, 0);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attributes(5, 0);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Corpse.class).create(id).reset(Corpse.DEFAULT_DURATION, true);
    return id;
  }

  private static Attributes attributes(int level, float hitpoints) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.hitpoints, hitpoints);
    attrs.base().put(Stat.maxhp, Math.max(1, hitpoints));
    attrs.base().put(Stat.mana, 100);
    attrs.base().put(Stat.maxmana, 100);
    attrs.reset();
    return attrs;
  }

  private static void dispatch(World world, int owner, Skills.Entry skill, int corpse) {
    world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
        owner, skill.Id, corpse, new Vector2(3, 0), skill.srvdofunc, skill.cltdofunc));
  }

  private static int seedForFailedRoll(int owner, int corpse, int chance) {
    for (int seed = 1; seed < 100_000; seed++) {
      NativeRng rng = new NativeRng(seed ^ owner * 0x45D9F3B ^ corpse * 31);
      if (rng.nextInt(100) >= chance) return seed;
    }
    throw new AssertionError("unable to find deterministic failed corpse roll");
  }

  private static void assertUsableAndUnmarked(World world, int corpseId) {
    assertTrue(world.getMapper(Corpse.class).get(corpseId).usable);
    UnitStates states = world.getMapper(UnitStates.class).get(corpseId);
    assertNotNull(states.stateList);
    assertFalse(states.stateList.hasState(StateId.CORPSE_NOSELECT));
  }

  private static final class RecordingFactory extends EntityFactory {
    final List<String> created = new ArrayList<>();

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entity = world.create();
      world.getMapper(Missile.class).create(entity)
          .set(row, position, row.Range).setOwner(ownerId);
      world.getMapper(Position.class).create(entity).position.set(position);
      created.add(row.Missile);
      return entity;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }
  }
}
