package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Skills;
import org.junit.jupiter.api.Test;

public class ServerSkillSystemTest {
  private static final float EPSILON = 0.0001f;

  @Test
  public void novaDirectionsCoverFullRingAtUnitLength() {
    Vector2 direction = new Vector2();
    for (int i = 0; i < 64; i++) {
      ServerSkillSystem.radialDirection(i, 64, direction);
      assertEquals(1f, direction.len(), EPSILON);
    }

    ServerSkillSystem.radialDirection(0, 64, direction);
    assertEquals(1f, direction.x, EPSILON);
    assertEquals(0f, direction.y, EPSILON);
    ServerSkillSystem.radialDirection(16, 64, direction);
    assertEquals(0f, direction.x, EPSILON);
    assertEquals(1f, direction.y, EPSILON);
    ServerSkillSystem.radialDirection(32, 64, direction);
    assertEquals(-1f, direction.x, EPSILON);
    assertEquals(0f, direction.y, EPSILON);
    ServerSkillSystem.radialDirection(48, 64, direction);
    assertEquals(0f, direction.x, EPSILON);
    assertEquals(-1f, direction.y, EPSILON);
  }

  @Test
  public void srvDo008UsesNativeCountAndCentreFormulas() {
    Skills.Entry multipleShot = new Skills.Entry();
    multipleShot.calc1 = "min(24,ln12)";
    multipleShot.calc2 = "par3";
    multipleShot.calc3 = "2";
    multipleShot.Param = new int[] {2, 1, 1};
    assertEquals(3, ServerSkillSystem.getSrvDo008Total(multipleShot, 1));
    assertEquals(12, ServerSkillSystem.getSrvDo008Total(multipleShot, 10));
    assertEquals(2, ServerSkillSystem.getSrvDo008Centre(multipleShot, 10, 12));

    Skills.Entry teeth = new Skills.Entry();
    teeth.calc1 = "min(ln12,24)";
    teeth.calc2 = "par3";
    teeth.calc3 = "";
    teeth.Param = new int[] {2, 1, 0};
    assertEquals(3, ServerSkillSystem.getSrvDo008Total(teeth, 1));
    // Native code falls back to the full count when calc2 evaluates to zero.
    assertEquals(3, ServerSkillSystem.getSrvDo008Centre(teeth, 1, 3));

    Skills.Entry shockWave = new Skills.Entry();
    shockWave.calc1 = "5";
    shockWave.Param = new int[] {40};
    assertEquals(5, ServerSkillSystem.getSrvDo008Total(shockWave, 20));
  }

  @Test
  public void srvDo008FanIsSymmetricAndNormalized() {
    Vector2 base = new Vector2(1, 0);
    Vector2 left = ServerSkillSystem.fanDirection(base, 0, 3, new Vector2());
    Vector2 centre = ServerSkillSystem.fanDirection(base, 1, 3, new Vector2());
    Vector2 right = ServerSkillSystem.fanDirection(base, 2, 3, new Vector2());
    assertEquals(1f, left.len(), EPSILON);
    assertEquals(1f, centre.len(), EPSILON);
    assertEquals(1f, right.len(), EPSILON);
    assertEquals(0f, centre.y, EPSILON);
    assertEquals(left.y, -right.y, EPSILON);
  }
}
