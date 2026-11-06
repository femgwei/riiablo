package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.state.StateId;
import java.util.ArrayList;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Native Grim Ward start/loop contracts used by MissileCollisionSystem. */
class GrimWardLifecycleServerTest extends RiiabloTest {
  @Test
  void allWardSizesReplaceStartWithLoopAndUseSkillDuration() {
    Skills.Entry skill = Riiablo.files.skills.get("Grim Ward");
    assertEquals(75, skill.srvdofunc);
    for (String size : new String[] {"small", "medium", "large"}) {
      Missiles.Entry start = Riiablo.files.Missiles.get("grimward" + size + "start");
      Missiles.Entry loop = Riiablo.files.Missiles.get("grimward" + size);
      assertTrue(MissileCollisionSystem.isGrimWardStage(start));
      assertTrue(MissileCollisionSystem.isGrimWardStage(loop));
      assertEquals(loop.Missile, start.HitSubMissile[0]);
      assertTrue(MissileCollisionSystem.grimWardLifetimeFrames(start, skill, 1) >= 5);
    }
  }

  @Test
  void startCreatesOneAuthoritativeLoopWithNativeDurationAndExpiresIt() {
    for (String size : new String[] {"small", "medium", "large"}) {
      try (Fixture f = new Fixture()) {
        Missiles.Entry start = Riiablo.files.Missiles.get("grimward" + size + "start");
        int startId = f.missile(start, start.Range);
        for (int i = 1; i < start.Range; i++) f.world.process();
        assertTrue(f.children.isEmpty());
        f.world.process();
        assertEquals(1, f.children.size());
        int childId = f.children.get(0);
        Missile loop = f.world.getMapper(Missile.class).get(childId);
        assertEquals("grimward" + size, loop.missile.Missile);
        assertEquals(1000, loop.nativeLifetimeFrames);
        assertEquals(f.owner, loop.ownerId);
        assertEquals(f.skill.Id, loop.skillId);
        assertEquals(3, loop.damageLevel);
        assertTrue(loop.authoritative);
        f.world.process();
        assertTrue(!f.world.getEntityManager().isActive(startId));
        loop.nativeFrame = 999;
        f.world.process();
        f.world.process();
        assertTrue(!f.world.getEntityManager().isActive(childId));
        assertEquals(1, f.children.size(), "loop expiry must not create a second server ward");
      }
    }
  }

  @Test
  void nativeRemainingFramePulseAppliesTerrorOnlyToEligibleNearbyMonster() {
    try (Fixture f = new Fixture()) {
      int wardId = f.missile(Riiablo.files.Missiles.get("grimwardmedium"), 10);
      int eligible = f.monster(1, true);
      int outside = f.monster(20, true);
      int immune = f.monster(1, false);
      // Native countdown10 is not divisible by6: first pulse is frame5.
      for (int i = 0; i < 4; i++) f.world.process();
      assertNull(f.world.getMapper(UnitStates.class).get(eligible));
      f.world.process();
      UnitStates states = f.world.getMapper(UnitStates.class).get(eligible);
      assertNotNull(states);
      assertNotNull(states.stateList.getState(StateId.TERROR));
      assertEquals(wardId, states.stateList.getState(StateId.TERROR).sourceEntityId);
      assertEquals(60, states.stateList.getState(StateId.TERROR).duration);
      assertNull(f.world.getMapper(UnitStates.class).get(outside));
      assertNull(f.world.getMapper(UnitStates.class).get(immune));
    }
  }

  private static final class Fixture implements AutoCloseable {
    final EntityFactory factory = mock(EntityFactory.class);
    final World world;
    final Skills.Entry skill = Riiablo.files.skills.get("Grim Ward");
    final ArrayList<Integer> children = new ArrayList<>();
    final int owner;

    Fixture() {
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), new MissileCollisionSystem(), factory).build()
          .register("factory", factory).register("map", new com.riiablo.map.Map(0, 0)));
      world.setDelta(1f / 25f);
      owner = world.create();
      world.getMapper(Player.class).create(owner);
      world.getMapper(Position.class).create(owner);
      when(factory.createMissile(any(Missiles.Entry.class), any(Vector2.class),
          any(Vector2.class), eq(owner), anyInt())).thenAnswer(call -> {
            Missiles.Entry row = call.getArgument(0);
            int id = missile(row, row.Range);
            children.add(id);
            return id;
          });
    }

    int missile(Missiles.Entry row, int frames) {
      int id = world.create();
      Missile missile = world.getMapper(Missile.class).create(id)
          .set(row, Vector2.Zero, 0).setOwner(owner);
      missile.skillId = skill.Id;
      missile.damageLevel = 3;
      missile.nativeLifetimeFrames = frames;
      world.getMapper(Position.class).create(id);
      world.getMapper(Velocity.class).create(id);
      return id;
    }

    int monster(float x, boolean switchAi) {
      int id = world.create();
      MonStats.Entry stats = new MonStats.Entry();
      stats.switchai = switchAi;
      MonStats2.Entry stats2 = new MonStats2.Entry();
      stats2.mMode = new boolean[16];
      stats2.mMode[Engine.Monster.MODE_WL] = true;
      world.getMapper(Monster.class).create(id).set(stats, stats2);
      world.getMapper(Position.class).create(id).position.set(x, 0);
      Attributes attrs = Attributes.obtainStandard();
      attrs.base().clear();
      attrs.base().put(Stat.hitpoints, 100f);
      attrs.base().put(Stat.maxhp, 100f);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      return id;
    }

    @Override public void close() { world.dispose(); }
  }
}
