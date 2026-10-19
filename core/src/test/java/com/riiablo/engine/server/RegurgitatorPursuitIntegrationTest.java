package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.ai.Regurgitator;
import com.riiablo.engine.server.component.*;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Fn071 with real 1.10f rows, A*, collision, movement and ordinary A1 selection. */
class RegurgitatorPursuitIntegrationTest extends RiiabloTest {
  @ParameterizedTest
  @ValueSource(strings = {"regurgitator1", "regurgitator2", "regurgitator3", "regurgitator4"})
  void unloadedMonsterActuallyMovesOnFirstTickThenReachesMeleeAndAttacks(String rowId) {
    World previous = Riiablo.engine;
    try {
      for (int difficulty = 0; difficulty < 3; difficulty++) {
        for (int distance : new int[] {10, 16}) {
          try (PathfinderTerrainPursuitTest.Fixture f =
              new PathfinderTerrainPursuitTest.Fixture((x, y) -> false, true,
                  new EventSystem(), new CofManager(),
                  mock(com.riiablo.engine.EntityFactory.class))) {
            Riiablo.engine = f.world;
            MonStats.Entry row = Riiablo.files.monstats.get(rowId);
            MonStats2.Entry dimensions = Riiablo.files.monstats2.get(row.MonStatsEx);
            assertEquals("Regurgitator", row.AI);
            assertTrue(row.MissA1 == null || row.MissA1.isEmpty());
            assertEquals("regurgitatorcorpse", row.MissA2);
            assertTrue(distance < Riiablo.files.Missiles.get(row.MissA2).Range - 2,
                "reproduce a spawn already within the incorrectly applied ranged stop");
            f.world.getMapper(Monster.class).get(f.actor).set(row, dimensions);
            f.world.getMapper(Size.class).get(f.actor).size = dimensions.SizeX;
            f.world.getMapper(Velocity.class).get(f.actor).setMonster(row.Velocity);
            when(f.map.getDifficulty()).thenReturn(difficulty);
            f.world.getMapper(MapWrapper.class).create(f.actor).set(f.map, null);
            f.paths.actioneer = new Actioneer() {
              @Override public boolean isInMeleeRange(int a, int b, int bonus) { return false; }
              @Override public int getMeleeRange(int a) { return dimensions.MeleeRng; }
            };
            assertEquals(3, dimensions.SizeX);
            f.world.getMapper(Player.class).create(f.target);
            f.position(f.target).set(10 + distance, 10);
            f.world.process();
            f.collision.refreshNow();
            PursuitAI ai = new PursuitAI(f.actor);
            f.world.getInjector().inject(ai);
            ai.initialize();
            ai.forceApproachAndAttack();
            assertFalse(ai.inMelee(f.target));
            Vector2 start = new Vector2(f.position(f.actor));

            ai.update(Animation.FRAME_DURATION);
            assertEquals("APPROACH", ai.getState());
            assertTrue(f.world.getMapper(Pathfind.class).has(f.actor));
            f.advance(1);
            assertTrue(f.position(f.actor).x > start.x,
                rowId + " must move, not just log MONSTER_MOVE and cancel before its first step");

            for (int tick = 0; tick < 240
                && !f.world.getMapper(Casting.class).has(f.actor); tick++) {
              ai.update(Animation.FRAME_DURATION);
              f.advance(1);
            }
            Casting attack = f.world.getMapper(Casting.class).get(f.actor);
            assertNotNull(attack, rowId + " must reach the player and start A1");
            assertTrue(ai.inMelee(f.target));
            assertEquals(SkillCodes.attack, attack.skillId);
            assertEquals(f.target, attack.targetId);
            assertEquals(Engine.Monster.MODE_A1,
                f.world.getMapper(Sequence.class).get(f.actor).mode1);
            assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
          }
        }
      }
    } finally {
      Riiablo.engine = previous;
    }
  }

  @Test void ordinaryRangedMonsterStillStopsAtItsMissileRange() {
    try (PathfinderTerrainPursuitTest.Fixture f =
        new PathfinderTerrainPursuitTest.Fixture((x, y) -> false)) {
      MonStats.Entry row = new MonStats.Entry();
      row.Id = "ordinary-ranged-control";
      row.AI = "SkeletonBow";
      row.MissA1 = "arrow";
      f.world.getMapper(Monster.class).get(f.actor).monstats = row;
      f.position(f.target).set(20, 10);
      f.collision.refreshNow();
      assertTrue(f.start());
      Vector2 start = new Vector2(f.position(f.actor));
      f.advance(1);
      assertEquals(start, f.position(f.actor));
      assertFalse(f.world.getMapper(Pathfind.class).has(f.actor));
    }
  }

  private static final class PursuitAI extends Regurgitator {
    PursuitAI(int entityId) { super(entityId); }

    void forceApproachAndAttack() {
      // Preserve the real per-difficulty decision delay; remove only chance
      // stalls so this exercises movement deterministically, not RNG luck.
      params[0] = params[2] = 100;
    }

    boolean inMelee(int target) { return isInNativeMeleeRange(target); }
  }
}
