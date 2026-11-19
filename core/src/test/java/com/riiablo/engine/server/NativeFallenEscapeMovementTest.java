package com.riiablo.engine.server;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.engine.SimulationClock;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Pathfind;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Size;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.map.pfa.GraphPath;
import com.riiablo.map.pfa.Point2;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Replays an observed native movement action, not the entire AI/path query. */
class NativeFallenEscapeMovementTest {
  @Test void productionIntegratorMatchesOriginalDiagonalEscapeUntilArrival() throws Exception {
    replay("fallen-escape-diagonal.csv", 50, 45, true);
  }

  @Test void productionIntegratorMatchesOriginalObliqueReengagementBeforeAttack() throws Exception {
    replay("fallen-reengage-oblique.csv", 0, 79, false);
  }

  private void replay(String fixture, int speedBonus, int samples, boolean reachesGoal) throws Exception {
    List<int[]> rows;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        getClass().getResourceAsStream("/native/" + fixture), StandardCharsets.UTF_8))) {
      rows = reader.lines().skip(1).map(line -> java.util.Arrays.stream(line.split(","))
          .mapToInt(Integer::parseInt).toArray()).collect(Collectors.toList());
    }
    assertEquals(samples, rows.size());
    int[] first = rows.get(0);
    int goalX = first.length > 8 ? first[8] : first[5];
    int goalY = first.length > 8 ? first[9] : first[6];
    World world = new World(new WorldConfigurationBuilder()
        .with(new DynamicUnitCollisionSystem(), new VelocityAdder()).build());
    try {
      int id = world.create();
      world.getMapper(com.riiablo.engine.server.component.Class.class).create(id).type =
          com.riiablo.engine.server.component.Class.Type.MON;
      world.getMapper(Monster.class).create(id);
      world.getMapper(Size.class).create(id).size = 1;
      Position position = world.getMapper(Position.class).create(id).setNativePrecision(first[2], first[3]);
      Velocity velocity = world.getMapper(Velocity.class).create(id).setMonsterFromNativeTable(5);
      velocity.setModeSpeedBonusPercent(speedBonus);
      assertEquals(first[4], velocity.nativeMonsterVelocity());
      GraphPath graph = new GraphPath();
      graph.add(new Point2(first[2] >>> 16, first[3] >>> 16));
      graph.add(new Point2(goalX, goalY));
      world.getMapper(Pathfind.class).create(id).set(graph);
      world.setDelta(SimulationClock.STEP_SECONDS);
      for (int index = 1; index < rows.size() - (reachesGoal ? 0 : 1); index++) {
        int[] expected = rows.get(index);
        assertEquals(first[0] + index, expected[0]);
        assertEquals(first[1] + index, expected[1]);
        // Supply movement intent; the production system computes native
        // direction, velocity multiplication, collision and arrival clamping.
        velocity.velocity.set(new Vector2(goalX, goalY).sub(position.position).nor()
            .scl(velocity.speed(false)));
        world.process();
        assertTrue(velocity.nativeMovementOwned);
        assertEquals(expected[2], position.nativePrecisionX(), "x at original step " + expected[0]);
        assertEquals(expected[3], position.nativePrecisionY(), "y at original step " + expected[0]);
      }
      if (reachesGoal) {
        assertEquals(goalX, position.position.x, .00001f);
        assertEquals(goalY, position.position.y, .00001f);
      } else {
        // Retain the A2 entry as evidence; it is not another WL update.
        assertEquals(5, rows.get(rows.size() - 1)[7]);
      }
    } finally { world.dispose(); }
  }
}


