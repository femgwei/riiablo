package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Leap;
import com.riiablo.engine.server.component.Position;
import org.junit.jupiter.api.Test;

/** Headless coverage for the native Sand Leaper skill and airborne movement. */
class LeapIntegrationTest extends RiiabloTest {
  @Test
  void sandLeaperUsesNativeLeapFunctions() {
    MonStats.Entry row = Riiablo.files.monstats.get("sandleaper1");
    assertNotNull(row);
    Skills.Entry skill = Riiablo.files.skills.get(row.Skill1);
    assertNotNull(skill);
    assertEquals("Leap", skill.skill);
    assertEquals(40, skill.srvstfunc);
    assertEquals(77, skill.srvdofunc);
    assertEquals(43, skill.cltdofunc);
    System.out.println("[LEAP_AUDIT] monster=" + row.Id + " skill=" + skill.skill
        + " srvstfunc=" + skill.srvstfunc + " srvdofunc=" + skill.srvdofunc
        + " cltdofunc=" + skill.cltdofunc + " status=PASS");
  }

  @Test
  void airborneLeapInterpolatesAndLandsAtAuthoritativeDestination() {
    LeapSystem leaps = new LeapSystem();
    World world = new World(new WorldConfigurationBuilder().with(leaps).build());
    try {
      int entity = world.create();
      Position position = world.getMapper(Position.class).create(entity);
      position.position.set(2, 3);
      world.getMapper(Leap.class).create(entity).set(
          new Vector2(2, 3), new Vector2(8, 3), 0.3f, Engine.INVALID_ENTITY);
      world.delta = 0.1f;
      world.process();
      assertTrue(position.position.x > 2f && position.position.x < 8f);
      world.process();
      world.process();
      assertEquals(8f, position.position.x, 0.001f);
      assertEquals(3f, position.position.y, 0.001f);
      assertTrue(!world.getMapper(Leap.class).has(entity));
      System.out.println("[LEAP_CHAIN] entity=" + entity + " destination=(8.0, 3.0)"
          + " elapsed=0.3 componentRemoved=true status=PASS");
    } finally {
      world.dispose();
    }
  }
}
