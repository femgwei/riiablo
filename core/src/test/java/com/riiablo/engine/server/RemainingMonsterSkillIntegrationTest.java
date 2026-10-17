package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

class RemainingMonsterSkillIntegrationTest extends RiiabloTest {
  @Test void siegeStompUsesOneSharedDirectPacketAndNeverCreatesAnAttackMissile() {
    RecordingFactory factory = new RecordingFactory();
    Probe probe = new Probe();
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new ServerSkillSystem(true), probe, factory).build()
        .register("factory", factory).register("map", new Map(0, 0)));
    try {
      Skills.Entry skill = null;
      for (Skills.Entry row : Riiablo.files.skills) if (row.srvdofunc == 134) { skill = row; break; }
      assertNotNull(skill);
      int source = world.create();
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(source).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      MonStats.Entry row = Riiablo.files.monstats.get("siegebeast1");
      world.getMapper(Monster.class).create(source).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attrs();
      int first = player(world, 11), second = player(world, 12), distant = player(world, 1000);
      world.process();
      String originalMissile = skill.srvmissilea;
      try {
        if (originalMissile == null || originalMissile.isEmpty()) {
          world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source, skill.Id,
              Engine.INVALID_ENTITY, new Vector2(), skill.srvdofunc, skill.cltdofunc));
          assertEquals(0, probe.hits, "native SrvDo134 rejects the unconfigured 1.10f missile field");
        }
        // Exercise the enabled branch without changing the installed data.
        skill.srvmissilea = "firebolt";
        world.getSystem(EventSystem.class).dispatch(SkillDoEvent.obtain(source, skill.Id,
            Engine.INVALID_ENTITY, new Vector2(), skill.srvdofunc, skill.cltdofunc));
      } finally { skill.srvmissilea = originalMissile; }
      assertEquals(2, probe.hits);
      assertTrue(probe.first > 0);
      assertEquals(probe.first, probe.second, "all victims receive the same initial skill roll");
      assertEquals(hp(world, first), hp(world, second));
      assertTrue(hp(world, first) < 1000);
      assertEquals(1000f, hp(world, distant));
      assertEquals(0, factory.monsters);
      assertEquals(0, factory.missiles);
    } finally { world.dispose(); }
  }

  @Test void korlicLeapTakesOffAtTheTargetAndCannotHitAgainOnLaterActionPoints() {
    RecordingFactory factory = new RecordingFactory();
    Actioneer actions = new Actioneer();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        actions, new Pathfinder(), new LeapSystem(), factory).build()
        .register("factory", factory).register("map", mock(Map.class)));
    try {
      int source = world.create();
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(source).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      MonStats.Entry row = Riiablo.files.monstats.get("ancientbarb3");
      world.getMapper(Monster.class).create(source).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(MovementModes.class).create(source).set(
          Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attrs();
      int target = player(world, 18);
      Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
      assertEquals(78, skill.srvdofunc);
      actions.cast(source, skill.Id, target, new Vector2(18, 10));
      EventSystem events = world.getSystem(EventSystem.class);
      events.dispatch(com.riiablo.engine.server.event.AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_ATK));
      Leap leap = world.getMapper(Leap.class).get(source);
      assertNotNull(leap);
      assertEquals(new Vector2(18, 10), leap.destination, "Leap Attack lands at its target instead of vaulting across it");
      world.setDelta(1f);
      world.process();
      assertFalse(world.getMapper(Leap.class).has(source));
      events.dispatch(com.riiablo.engine.server.event.AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_ATK));
      Casting casting = world.getMapper(Casting.class).get(source);
      assertTrue(casting.monsterLeapHit);
      float afterFirstHit = hp(world, target);
      events.dispatch(com.riiablo.engine.server.event.AnimDataKeyframeEvent.obtain(source, Engine.KEYFRAME_ATK));
      assertEquals(afterFirstHit, hp(world, target));
      assertFalse(world.getMapper(Leap.class).has(source));
    } finally { world.dispose(); }
  }

  @Test void deadCatapultRemainsQueryableAfterItsActiveEntityIsRemoved() {
    Map map = new Map(0, 0);
    Map.Zone zone = new Map.Zone();
    Map.RoomEx room = zone.addRoomEx(0, 0, 40, 40);
    World world = new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager()).build());
    try {
      MonStats.Entry row = Riiablo.files.monstats.get("catapult1");
      int id = world.create();
      world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(id).position.set(10, 10);
      world.getMapper(MapWrapper.class).create(id).set(map, zone);
      world.getMapper(CofReference.class).create(id).mode = Engine.Monster.MODE_NU;
      world.getSystem(CofManager.class).setMode(id, Engine.Monster.MODE_DT);
      assertTrue(room.hasDeadCatapult(row.hcIdx));
      world.delete(id);
      world.process();
      assertTrue(room.hasDeadCatapult(row.hcIdx));
      assertFalse(room.hasDeadCatapult(row.hcIdx + 1));
    } finally { world.dispose(); }
  }

  @Test void nihlathakArcticChannelIsInitializedBeforeItsMonSeqIsInstalled() {
    RecordingFactory factory = new RecordingFactory();
    Actioneer actions = new Actioneer();
    World world = new World(new WorldConfigurationBuilder().with(new EventSystem(),
        actions, new Pathfinder(), factory).build().register("factory", factory).register("map", new Map(0, 0)));
    try {
      int source = world.create();
      MonStats.Entry row = null;
      for (MonStats.Entry candidate : Riiablo.files.monstats)
        if ("Nihlathak".equals(candidate.AI)) { row = candidate; break; }
      assertNotNull(row);
      world.getMapper(Monster.class).create(source).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(source).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(Position.class).create(source).position.set(10, 10);
      world.getMapper(Angle.class).create(source);
      world.getMapper(MovementModes.class).create(source).set(
          Engine.Monster.MODE_NU, Engine.Monster.MODE_WL, Engine.Monster.MODE_RN);
      world.getMapper(AttributesWrapper.class).create(source).attrs = attrs();
      int target = player(world, 18);
      Skills.Entry skill = Riiablo.files.skills.get(row.Skill4);
      assertEquals(95, skill.srvdofunc);
      actions.castWithMode(source, skill.Id, Engine.Monster.MODE_A1, target, new Vector2(18, 10));
      assertNotNull(world.getMapper(Casting.class).get(source).monInferno);
    } finally { world.dispose(); }
  }

  private static int player(World world, int x) {
    int id = world.create();
    world.getMapper(Player.class).create(id).data = CharData.createRemote("stomp", (byte) Riiablo.SORCERESS);
    world.getMapper(Position.class).create(id).position.set(x, 10);
    world.getMapper(AttributesWrapper.class).create(id).attrs = attrs();
    return id;
  }
  private static Attributes attrs() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 1000f);
    attrs.base().put(Stat.maxhp, 1000f);
    attrs.base().put(Stat.level, 1);
    attrs.reset();
    return attrs;
  }
  private static float hp(World world, int id) {
    return world.getMapper(AttributesWrapper.class).get(id).attrs.get(Stat.hitpoints).asFixed();
  }
  public static class Probe extends com.artemis.BaseSystem {
    int hits;
    float first, second;
    @Subscribe public void damage(DamageEvent event) {
      assertEquals(DamageEvent.MISSILE, event.kind);
      if (++hits == 1) first = event.damage; else second = event.damage;
    }
    @Override protected void processSystem() {}
  }
  static class RecordingFactory extends EntityFactory {
    int monsters, missiles;
    float spawnX, spawnY;
    @Override public int createPlayer(com.riiablo.save.CharData data, com.badlogic.gdx.math.Vector2 p) { return -1; }
    @Override public int createDynamicObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int a, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { monsters++; spawnX=x; spawnY=y; return 42; }
    @Override public int createWarp(int id, float x, float y) { return -1; }
    @Override public int createItem(com.riiablo.item.Item item, float x, float y) { return -1; }
    @Override public int createMissile(int id, com.badlogic.gdx.math.Vector2 a, com.badlogic.gdx.math.Vector2 p) { missiles++; return -1; }
  }
}
