package com.riiablo.engine.server.skill;

import com.riiablo.codec.excel.Skills;

/**
 * Exact, data-backed description of one native Skills.txt behavior row.
 *
 * <p>The family is deliberately descriptive rather than executable.  A row
 * only enters a family when its skill id <em>and</em> native callback numbers
 * match the registered declaration.  This keeps the dispatch boundary closed
 * when a custom table introduces an unknown or mismatched callback.
 */
public final class NativeSkillBehavior {
  public final int skillId;
  public final String family;
  public final int serverStartFunction;
  public final int serverDoFunction;
  public final String[] serverMissiles;
  public final String evidence;

  NativeSkillBehavior(int skillId, String family, int serverStartFunction,
      int serverDoFunction, String evidence, String[] serverMissiles) {
    this.skillId = skillId;
    this.family = family;
    this.serverStartFunction = serverStartFunction;
    this.serverDoFunction = serverDoFunction;
    this.evidence = evidence;
    this.serverMissiles = serverMissiles == null ? new String[0] : serverMissiles.clone();
  }

  /** Returns true only when the loaded row has the exact native callbacks. */
  public boolean matches(Skills.Entry skill) {
    return skill != null && skill.Id == skillId
        && skill.srvstfunc == serverStartFunction
        && skill.srvdofunc == serverDoFunction;
  }
}
