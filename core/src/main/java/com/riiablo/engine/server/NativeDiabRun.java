package com.riiablo.engine.server;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.server.skill.SkillFormula;

/** Used-skill flags/target and seq_diablorun cursor, not a tracking Charge. */
public final class NativeDiabRun {
  public final int targetId;
  public final Vector2 destination;
  public int cursor;
  public int remaining = 22 << 8;
  public boolean moving;
  public boolean pathStarted;
  public boolean tail;
  public boolean hitProcessed;
  public boolean damageProcessed;
  public int loops;

  public NativeDiabRun(int targetId, Vector2 target) {
    this.targetId = targetId;
    destination = new Vector2((float) Math.floor(target.x), (float) Math.floor(target.y));
  }

  /** Explicit state constructor used only by the versioned checkpoint codec. */
  public NativeDiabRun(int targetId, Vector2 destination, boolean checkpoint) {
    this.targetId = targetId;
    this.destination = new Vector2(destination);
  }

  public static boolean supports(Skills.Entry skill) {
    return skill != null && skill.Id == 198 && skill.srvstfunc == 54 && skill.srvdofunc == 103;
  }

  public void seek(int point, int frames) {
    cursor = (point << 8) | (cursor & 255);
    remaining = frames << 8;
  }

  public static int action(int point) { return point >= 5 && point <= 13 ? 2 : point == 18 ? 1 : 0; }

  /** PATH velocity 8.8 -> 16.16 displacement at 25 Hz; percentage is applied by Velocity. */
  public static float baseSpeed(Skills.Entry skill, int level) {
    return Math.max(1, SkillFormula.evaluate(skill.calc1, skill, level)) * 25f / 16f;
  }
}
