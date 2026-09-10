package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import java.util.ArrayList;
import java.util.List;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Headless contract for the native Thunder Storm aura and periodic strike. */
class SorceressThunderStormIntegrationTest extends RiiabloTest {
  @Test
  void hydraUsesNativeThreeOwnedSummons() {
    Skills.Entry hydra = Riiablo.files.skills.get(SkillId.HYDRA);
    assertNotNull(hydra);
    assertEquals(14, hydra.srvstfunc);
    assertEquals(144, hydra.srvdofunc);
    assertTrue(hydra.summon != null && !hydra.summon.isEmpty());
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 1);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, hydra.Id, Engine.INVALID_ENTITY, new Vector2(10f, 10f),
          hydra.srvdofunc, hydra.cltdofunc));
      assertEquals(3, factory.summons.size(),
          "SrvDo144 creates the three native Hydra units");
      assertEquals("hydra", factory.summons.get(0).petType.toLowerCase());
      assertTrue(factory.summons.get(0).durationFrames > 0);
      assertEquals(factory.summons.get(0).durationFrames,
          factory.summons.get(1).durationFrames);
      assertTrue(factory.summons.get(0).x != factory.summons.get(1).x
          || factory.summons.get(0).y != factory.summons.get(1).y,
          "Hydras use distinct native offsets");
    } finally {
      world.dispose();
    }
  }

  @Test
  void nativeRowsUseThunderStormCallbacks() {
    Skills.Entry skill = Riiablo.files.skills.get(SkillId.THUNDER_STORM);
    assertNotNull(skill);
    assertEquals(13, skill.srvstfunc);
    assertEquals(29, skill.srvdofunc);
    assertEquals("thunderstorm", skill.aurastate.toLowerCase());
    assertTrue(skill.srvmissilea != null && !skill.srvmissilea.isEmpty());
    Missiles.Entry missile = Riiablo.files.Missiles.get(skill.srvmissilea);
    assertNotNull(missile);
    assertEquals(3, missile.pSrvDoFunc,
        "Thunder Storm uses the shared PoisonCloud/Blizzard/ThunderStorm SrvDo03 path");
  }

  @Test
  void castInstallsAuraAndEmitsOneAuthoritativeStrikePerPeriod() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), new StateUpdater(),
            new MissileCollisionSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = player(world, 1);
      int target = monster(world, 1.5f, 0f);
      Skills.Entry skill = Riiablo.files.skills.get(SkillId.THUNDER_STORM);
      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skill.Id, target, new Vector2(1.5f, 0f), skill.srvdofunc, skill.cltdofunc));

      UnitStates sourceStates = world.getMapper(UnitStates.class).get(caster);
      assertNotNull(sourceStates);
      assertNotNull(sourceStates.stateList);
      assertTrue(sourceStates.stateList.hasState(StateId.THUNDERSTORM));
      int delay = sourceStates.stateList.getState(StateId.THUNDERSTORM).periodicDelayFrames;
      assertTrue(delay > 0);

      world.setDelta(1f / 25f);
      for (int i = 0; i < delay; i++) world.process();
      assertEquals(1, factory.count("thunderstorm1"),
          "one native strike is emitted at the first periodic deadline");
      assertTrue(world.getMapper(AttributesWrapper.class).get(target).attrs
          .get(Stat.hitpoints).asFixed() < 200f,
          "the strike resolves through the ordinary elemental damage path");
    } finally {
      world.dispose();
    }
  }

  private static int player(World world, int level) {
    int id = world.create();
    CharData data = CharData.createRemote("thunder-storm", (byte) Riiablo.SORCERESS);
    data.setSkillLevel(SkillId.THUNDER_STORM, level);
    world.getMapper(Player.class).create(id).data = data;
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Position.class).create(id).position.set(0f, 0f);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs(100f);
    return id;
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    MonStats.Entry row = Riiablo.files.monstats.get("fallen1");
    MonStats2.Entry row2 = row != null ? Riiablo.files.monstats2.get(row.MonStatsEx) : null;
    world.getMapper(Monster.class).create(id).set(row, row2);
    world.getMapper(UnitStates.class).create(id).init(id);
    world.getMapper(Position.class).create(id).position.set(x, y);
    world.getMapper(Velocity.class).create(id).velocity.setZero();
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs(200f);
    return id;
  }

  private static Attributes attrs(float hp) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.level, 1);
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.mana, 1000);
    attrs.base().put(Stat.maxmana, 1000);
    attrs.base().put(Stat.lightresist, 0);
    attrs.reset();
    return attrs;
  }

  private static final class RecordingFactory extends EntityFactory {
    final ArrayList<Missile> missiles = new ArrayList<>();
    final ArrayList<String> createdNames = new ArrayList<>();
    final List<Summon> summons = new ArrayList<>();

    static final class Summon {
      final String petType;
      final int durationFrames;
      final float x;
      final float y;
      Summon(String petType, int durationFrames, float x, float y) {
        this.petType = petType;
        this.durationFrames = durationFrames;
        this.x = x;
        this.y = y;
      }
    }

    int count(String name) {
      int count = 0;
      for (String created : createdNames) if (name.equalsIgnoreCase(created)) count++;
      return count;
    }

    @Override public int createMissile(int id, Vector2 direction, Vector2 position, int ownerId) {
      Missiles.Entry row = Riiablo.files.Missiles.get(id);
      if (row == null) return Engine.INVALID_ENTITY;
      int entityId = world.create();
      Missile missile = world.getMapper(Missile.class).create(entityId)
          .set(row, position, row.Range).setOwner(ownerId);
      createdNames.add(row.Missile);
      world.getMapper(Position.class).create(entityId).position.set(position);
      world.getMapper(Velocity.class).create(entityId).velocity
          .set(direction).setLength(row.Vel);
      missiles.add(missile);
      return entityId;
    }
    @Override public int createMissile(int id, Vector2 direction, Vector2 position) {
      return createMissile(id, direction, position, Engine.INVALID_ENTITY);
    }
    @Override public int createSummonedPet(int ownerId, MonStats.Entry summon,
        String petType, int skillId, int skillLevel, int petMax, boolean passive,
        int durationFrames, float x, float y) {
      summons.add(new Summon(petType, durationFrames, x, y));
      return 1000 + summons.size();
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
