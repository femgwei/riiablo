package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ai.utils.Collision;
import com.badlogic.gdx.ai.utils.Ray;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.Pathfinder;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.item.Item;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Fn112 decisions with real ECS and MPQ data; movement recording needs no Mockito. */
class DruidBearAiIntegrationTest extends RiiabloTest {
  @Test void meleeRollSelectsOrdinaryA1OrTheNativeBearSmiteSequence() {
    for (int chance : new int[] {0, 100}) {
      try (Fixture f = new Fixture()) {
        int enemy = f.enemy(11, 10);
        f.flush();
        f.ai.params[2] = chance;
        f.ai.params[0] = 9;
        f.ai.update(Animation.FRAME_DURATION);
        Casting cast = f.world.getMapper(Casting.class).get(f.bear);
        assertNotNull(cast);
        assertEquals(enemy, cast.targetId);
        Sequence sequence = f.world.getMapper(Sequence.class).get(f.bear);
        if (chance == 0) {
          assertEquals(SkillCodes.attack, cast.skillId);
          assertEquals(Engine.Monster.MODE_A1, sequence.mode1);
          assertEquals(9 * Animation.FRAME_DURATION, f.ai.nextThink, .0001f);
        } else {
          assertEquals(Riiablo.files.skills.get("BearSmite").Id, cast.skillId);
          assertNotNull(sequence.nativeMonster);
          assertEquals("seq_bearsmite", sequence.nativeMonsterName);
        }
        f.ai.update(2f);
        assertSame(cast, f.world.getMapper(Casting.class).get(f.bear),
            "active attack/sequence must not be overwritten by a new decision");
      }
    }
  }

  @Test void approachChanceInstallsNativeVelocityBonusWithoutRunMode() {
    for (int chance : new int[] {0, 100}) {
      try (Fixture f = new Fixture()) {
        int enemy = f.enemy(20, 10);
        f.flush();
        f.ai.params[1] = chance;
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals("APPROACH", f.ai.getState());
        assertEquals(enemy, f.paths.target);
        assertEquals(chance == 100 ? .8f : 0f,
            f.world.getMapper(Velocity.class).get(f.bear).modeSpeedBonusMultiplier, .0001f);
        assertFalse(f.world.getMapper(Running.class).has(f.bear));
      }
    }
  }

  @Test void ownerMovementAndDistanceChooseRegroupBeforeAttacking() {
    for (int mode : new int[] {Engine.Player.MODE_NU, Engine.Player.MODE_WL,
        Engine.Player.MODE_TW, Engine.Player.MODE_RN}) {
      try (Fixture f = new Fixture()) {
        int enemy = f.enemy(11, 10);
        f.world.getMapper(Position.class).get(f.owner).position.set(31, 10);
        f.world.getMapper(CofReference.class).get(f.owner).mode = (byte) mode;
        f.flush();
        f.ai.params[2] = 0;
        f.ai.update(Animation.FRAME_DURATION);
        if (mode == Engine.Player.MODE_NU) {
          assertEquals(enemy, f.world.getMapper(Casting.class).get(f.bear).targetId);
        } else {
          assertEquals("REGROUP", f.ai.getState());
          assertNull(f.world.getMapper(Casting.class).get(f.bear));
          assertEquals(f.owner, f.paths.target);
          assertEquals(mode == Engine.Player.MODE_RN ? 1f : 0f,
              f.world.getMapper(Velocity.class).get(f.bear).modeSpeedBonusMultiplier);
        }
      }
    }
    try (Fixture f = new Fixture()) {
      f.enemy(11, 10);
      f.world.getMapper(Position.class).get(f.owner).position.set(42, 10);
      f.flush();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("REGROUP", f.ai.getState());
      assertEquals(f.owner, f.paths.target);
    }
  }

  @Test void noTargetUsesSixteenCellIdleBandAndTenFrameMissingOwnerDelay() {
    try (Fixture f = new Fixture()) {
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals("IDLE", f.ai.getState());
      assertEquals(15 * Animation.FRAME_DURATION, f.ai.nextThink, .0001f);
      f.world.getMapper(Position.class).get(f.owner).position.set(29, 10);
      f.ai.update(1f);
      assertEquals("FOLLOW", f.ai.getState());
      f.world.getMapper(SummonedPet.class).remove(f.bear);
      f.flush();
      f.ai.update(1f);
      assertEquals("IDLE_NO_OWNER", f.ai.getState());
      assertEquals(10 * Animation.FRAME_DURATION, f.ai.nextThink, .0001f);
    }
  }

  @Test void threeBlockedRaysFallBackToOwnerTargetButOneClearEdgeKeepsOwnTarget() {
    for (boolean clearEdge : new boolean[] {false, true}) {
      try (Fixture f = new Fixture()) {
        int nearest = f.enemy(20, 10);
        int ownerTarget = f.enemy(20, 20);
        f.world.getMapper(Target.class).create(f.owner).target = ownerTarget;
        ApproachMap map = new ApproachMap(clearEdge);
        f.world.getMapper(MapWrapper.class).create(f.bear).set(map, new Map.Zone());
        f.flush();
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(clearEdge ? nearest : ownerTarget, f.paths.target);
        assertEquals(clearEdge ? 2 : 4, map.rays);
      }
    }
  }

  @Test void ownTargetWinsOverOwnerTargetAndTargetsBeyondRangeAreNotChased() {
    try (Fixture f = new Fixture()) {
      int nearest = f.enemy(20, 10);
      int distant = f.enemy(24, 10);
      f.world.getMapper(Target.class).create(f.owner).target = distant;
      f.flush();
      f.ai.update(Animation.FRAME_DURATION);
      assertEquals(nearest, f.paths.target);
      f.world.getMapper(Position.class).get(nearest).position.set(50, 10);
      f.world.getMapper(Position.class).get(distant).position.set(51, 10);
      f.ai.update(1f);
      assertEquals("IDLE", f.ai.getState());
      assertEquals(Engine.INVALID_ENTITY, f.ai.targetId);
    }
  }

  private static final class Fixture implements AutoCloseable {
    final World previous = Riiablo.engine;
    final World world;
    final RecordingPaths paths = new RecordingPaths();
    final DruidBear ai;
    final int owner, bear;

    Fixture() {
      NoopFactory factory = new NoopFactory();
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new Pathfinder(), new CofManager(), factory)
          .build().register("factory", factory).register("map", new Map(0, 0)));
      Riiablo.engine = world;
      owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner).position.set(10, 10);
      world.getMapper(CofReference.class).create(owner).mode = Engine.Player.MODE_NU;
      bear = enemy(10, 10, "druidbear");
      world.getMapper(SummonedPet.class).create(bear)
          .set(owner, "grizzly", SkillId.SUMMON_GRIZZLY, 1, false, 0);
      MonStats.Entry row = Riiablo.files.monstats.get("druidbear");
      world.getMapper(Velocity.class).create(bear).setMonsterFromNativeTable(row.Velocity);
      flush();
      ai = new DruidBear(bear);
      world.getInjector().inject(ai);
      ai.pathfinder = paths;
      ai.initialize();
    }

    int enemy(float x, float y) { return enemy(x, y, "fallen1"); }
    int enemy(float x, float y, String name) {
      int id = world.create();
      MonStats.Entry row = Riiablo.files.monstats.get(name);
      world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
      world.getMapper(Position.class).create(id).position.set(x, y);
      world.getMapper(Angle.class).create(id);
      world.getMapper(Size.class).create(id).size = 1;
      return id;
    }
    void flush() { world.process(); }
    @Override public void close() { world.dispose(); Riiablo.engine = previous; }
  }

  @com.artemis.annotations.All({Pathfind.class, Position.class, Velocity.class})
  private static final class RecordingPaths extends Pathfinder {
    int target = Engine.INVALID_ENTITY;
    @Override public boolean findPath(int src, Vector2 destination) { return true; }
    @Override public boolean findPath(int src, Vector2 destination, boolean raycast, int targetId) {
      target = targetId;
      return true;
    }
  }

  private static final class ApproachMap extends Map {
    final boolean clearEdge;
    int rays;
    ApproachMap(boolean clearEdge) { super(0, 0); this.clearEdge = clearEdge; }
    @Override public boolean castRay(Zone zone, Ray<Vector2> ray, int flags, int size,
        Collision<Vector2> collision, boolean doors) {
      assertEquals(0xC01, flags);
      assertFalse(doors);
      rays++;
      return ray.end.y < 15 && !(clearEdge && rays == 2);
    }
  }

  private static final class NoopFactory extends EntityFactory {
    @Override public int createPlayer(CharData data, Vector2 position) { return -1; }
    @Override public int createDynamicObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObject(int act, int id, float x, float y) { return -1; }
    @Override public int createStaticObjectByClassId(int id, float x, float y) { return -1; }
    @Override public int createMonster(int id, float x, float y) { return -1; }
    @Override public int createWarp(int index, float x, float y) { return -1; }
    @Override public int createItem(Item item, float x, float y) { return -1; }
    @Override public int createMissile(int id, Vector2 angle, Vector2 position) { return -1; }
  }
}
