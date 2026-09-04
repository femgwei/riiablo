package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Angle;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.MovementModes;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.codec.excel.Missiles;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;

/** Headless ECS coverage for the next special-skill porting slice. */
class SpecialSkillEcsScenarioTest extends RiiabloTest {
  @Test
  void chainLightningCreatesOneAuthoritativeSegmentPerHostileJump() {
    int skillId = com.riiablo.engine.server.skill.SkillId.CHAIN_LIGHTNING;
    assertTrue(Riiablo.files.skills.get(skillId) != null, "Chain Lightning row must exist");
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int caster = world.create();
      world.getMapper(Player.class).create(caster);
      world.getMapper(Class.class).create(caster).type = Class.Type.PLR;
      world.getMapper(Position.class).create(caster).position.set(0, 0);
      int first = monster(world, 5, 0);
      monster(world, 8, 1);
      monster(world, 11, 0);

      world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(
          caster, skillId, first, new Vector2(5, 0), 0, 0));

      assertEquals(3, factory.created, "chain lightning must emit three segments for three targets");
      System.out.println("[CHAIN_LIGHTNING_ECS] caster=" + caster + " initialTarget=" + first
          + " segments=" + factory.created + " status=PASS");
    } finally {
      world.dispose();
    }
  }

  @Test
  void frenzyStateRaisesAuthoritativeMovementMultiplierAndExpires() {
    RecordingFactory factory = new RecordingFactory();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new StateUpdater(), factory)
        .build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int entity = world.create();
      com.riiablo.engine.server.state.UnitState frenzy = world.getMapper(UnitStates.class)
          .create(entity).init(entity).stateList.addState(StateId.FRENZY, 2, 2, entity);
      frenzy.velocityModifier = 2;
      Velocity velocity = world.getMapper(Velocity.class).create(entity);
      velocity.velocity.set(1, 0);
      world.process();
      assertEquals(1.02f, velocity.stateSpeedMultiplier, 0.001f);
      world.process();
      assertEquals(1f, velocity.stateSpeedMultiplier, 0.001f);
      System.out.println("[FRENZY_ECS] entity=" + entity + " level=2 multiplier=1.02"
          + " expired=true status=PASS");
    } finally {
      world.dispose();
    }
  }

  @Test
  void teleportRejectsBlockedLandingAndClearsOldMovementOnSuccess() {
    RecordingFactory factory = new RecordingFactory();
    TestMap map = new TestMap();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", map));
    try {
      int entity = world.create();
      world.getMapper(Position.class).create(entity).position.set(1, 1);
      world.getMapper(Velocity.class).create(entity).velocity.set(3, 0);
      world.getMapper(Pathfind.class).create(entity);
      world.getMapper(UnitStates.class).create(entity).init(entity);

      map.blocked = true;
      assertTrue(!actioneer.resolveTeleport(entity, new Vector2(10, 10)));
      assertEquals(new Vector2(1, 1), world.getMapper(Position.class).get(entity).position);

      map.blocked = false;
      assertTrue(actioneer.resolveTeleport(entity, new Vector2(10, 10)));
      assertEquals(new Vector2(10, 10), world.getMapper(Position.class).get(entity).position);
      assertTrue(!world.getMapper(Pathfind.class).has(entity));
      assertTrue(world.getMapper(Velocity.class).get(entity).velocity.isZero());
      assertTrue(world.getMapper(UnitStates.class).get(entity).stateList
          .hasState(StateId.SYNC_WARPED));
      System.out.println("[TELEPORT_ECS] entity=" + entity
          + " blockedRejected=true landing=(10,10) pathCleared=true status=PASS");
    } finally {
      world.dispose();
    }
  }

  @Test
  void playerAndMonsterFrenzyRowsUseAuthoritativeHitStatePath() {
    assertEquals(9, Riiablo.files.skills.get("Frenzy").srvdofunc);
    assertEquals(109, Riiablo.files.skills.get("MonFrenzy").srvdofunc);
    RecordingFactory factory = new RecordingFactory();
    Actioneer actioneer = new Actioneer();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), actioneer, new Pathfinder(), factory)
        .build().register("factory", factory).register("map", new TestMap()));
    try {
      CharData data = CharData.createRemote("frenzy", (byte) 4);
      data.setSkillLevel(com.riiablo.engine.server.skill.SkillId.FRENZY, 3);
      data.getItems().equipItem(com.riiablo.item.BodyLoc.RARM,
          data.getItems().add(testWeapon("hax", 3)));
      data.getItems().equipItem(com.riiablo.item.BodyLoc.LARM,
          data.getItems().add(testWeapon("hax", 4)));
      int player = world.create();
      world.getMapper(Player.class).create(player).data = data;
      world.getMapper(Class.class).create(player).type = Class.Type.PLR;
      world.getMapper(Position.class).create(player).position.set(0, 0);
      world.getMapper(Angle.class).create(player);
      world.getMapper(MovementModes.class).create(player).set(
          (byte) Class.Type.PLR.getMode("NU"), (byte) Class.Type.PLR.getMode("WL"),
          (byte) Class.Type.PLR.getMode("RN"));
      world.getMapper(AttributesWrapper.class).create(player).attrs = combatAttributes(100, 10);
      world.getMapper(UnitStates.class).create(player).init(player);

      int target = monster(world, 1, 0);
      world.getMapper(AttributesWrapper.class).create(target).attrs = combatAttributes(100, 1);
      world.getMapper(UnitStates.class).create(target).init(target);

      actioneer.cast(player, com.riiablo.engine.server.skill.SkillId.FRENZY,
          target, new Vector2(1, 0));
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(player, Engine.KEYFRAME_ATK));

      assertTrue(world.getMapper(UnitStates.class).get(player)
          .stateList.getState(StateId.FRENZY) == null,
          "native Frenzy applies the successful first strike at the next event");
      world.getSystem(EventSystem.class).dispatch(
          AnimDataKeyframeEvent.obtain(player, Engine.KEYFRAME_ATK));

      com.riiablo.engine.server.state.UnitState frenzy = world.getMapper(UnitStates.class)
          .get(player).stateList.getState(StateId.FRENZY);
      assertTrue(frenzy != null);
      assertEquals(3, frenzy.level);
      assertEquals(1, frenzy.runtimeValue);
      assertEquals(48, frenzy.velocityModifier);
      System.out.println("[FRENZY_HIT_ECS] entity=" + player + " skillLevel=" + frenzy.level
          + " stacks=" + frenzy.runtimeValue + " srvDo=9 status=PASS");
    } finally {
      world.dispose();
    }
  }

  private static int monster(World world, float x, float y) {
    int id = world.create();
    world.getMapper(Monster.class).create(id);
    world.getMapper(Class.class).create(id).type = Class.Type.MON;
    world.getMapper(Position.class).create(id).position.set(x, y);
    return id;
  }

  private static Attributes combatAttributes(float hp, int level) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, hp);
    attrs.base().put(Stat.level, level);
    attrs.base().put(Stat.armorclass, 0);
    attrs.base().put(Stat.mindamage, 2);
    attrs.base().put(Stat.maxdamage, 3);
    attrs.base().put(Stat.tohit, 10000);
    attrs.reset();
    return attrs;
  }

  private static Item testWeapon(String code, int damage) {
    Item item = new Item();
    item.reset();
    item.setBase(Riiablo.files.weapons.get(code));
    item.attrs.base().get(Stat.mindamage).set(damage);
    item.attrs.base().get(Stat.maxdamage).set(damage);
    item.attrs.reset();
    return item;
  }

  private static final class RecordingFactory extends EntityFactory {
    int created;

    @Override public int createPlayer(CharData data, Vector2 position) { return Engine.INVALID_ENTITY; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createMonster(int id, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createWarp(int index, float x, float y) { return Engine.INVALID_ENTITY; }
    @Override public int createItem(Item item, float x, float y) { return Engine.INVALID_ENTITY; }

    @Override public int createMissile(int missileId, Vector2 direction, Vector2 position) {
      return createMissileInternal(missileId, direction, position);
    }

    @Override public int createMissile(int missileId, Vector2 direction, Vector2 position, int ownerId) {
      return createMissileInternal(missileId, direction, position);
    }

    private int createMissileInternal(int missileId, Vector2 direction, Vector2 position) {
      Missiles.Entry row = Riiablo.files.Missiles.get(missileId);
      if (row == null) return Engine.INVALID_ENTITY;
      int id = world.create();
      world.getMapper(Missile.class).create(id).set(row, position, row.Range).setOwner(-1);
      world.getMapper(Position.class).create(id).position.set(position);
      created++;
      return id;
    }
  }

  private static final class TestMap extends Map {
    boolean blocked;
    TestMap() { super(0, 0); }
    @Override public int flags(Vector2 position) {
      return blocked ? com.riiablo.map.DT1.Tile.FLAG_BLOCK_WALK : 0;
    }
  }
}
