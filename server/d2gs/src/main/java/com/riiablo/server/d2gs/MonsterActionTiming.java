package com.riiablo.server.d2gs;

import java.util.ArrayList;
import java.util.List;

/** Read-only arena observer. Execution is SkillDo, never damage/hit success. */
final class MonsterActionTiming {
  static final class Action {
    public int decisionTick, executionTick = -1, endTick = -1, skill, target, mode;
    public int meleeAttemptTick = -1, meleeAttempts, meleeMisses;
    public int projectileTick = -1, projectiles;
    public String kind, outcome = "PENDING";
  }

  final List<Action> actions = new ArrayList<>();
  int unmatchedExecutions;
  private long actionId;
  private Action current;

  void observe(int tick, long identity, boolean present,
      int skill, int target, int mode, int player) {
    // Neutral/death/movement/recovery sequences can coexist briefly with stale Casting.
    if (!present) {
      end(tick, "REMOVED_WITHOUT_EXECUTION");
      actionId = 0;
      return;
    }
    if (identity == actionId) return;
    end(tick, "REPLACED_WITHOUT_EXECUTION");
    actionId = identity;
    if (skill == 0 && (mode == 0 || mode == 1 || mode == 2 || mode == 3
        || mode == 6 || mode == 12 || mode == 15)) return;
    current = new Action();
    current.decisionTick = tick;
    current.skill = skill; current.target = target; current.mode = mode;
    current.kind = skill == 0 ? (mode == 4 || mode == 5 ? "BASIC_ATTACK" : "OTHER_MODE_ATTACK_CAST")
        : target == player ? "PLAYER_TARGET_SKILL" : "OTHER_SKILL";
    actions.add(current);
  }

  void execute(int tick, int skill) {
    Action action = current;
    // Some server functions remove/reset Casting before dispatching SkillDo.
    if (action == null && !actions.isEmpty()) {
      Action last = actions.get(actions.size() - 1);
      if (last.endTick == tick && last.skill == skill) action = last;
    }
    if (action == null || action.skill != skill) { unmatchedExecutions++; return; }
    // Multi-hit/channel skills keep their first action frame; pulses are not new decisions.
    if (action.executionTick < 0) {
      action.executionTick = tick;
      action.outcome = "EXECUTED";
    }
  }

  void melee(int tick, boolean hit) {
    if (current == null) return;
    if (current.meleeAttemptTick < 0) current.meleeAttemptTick = tick;
    current.meleeAttempts++;
    if (!hit) current.meleeMisses++;
  }

  void projectile(int tick, int skill) {
    for (int i = actions.size() - 1; i >= 0; i--) {
      Action action = actions.get(i);
      if (action.executionTick < 0) continue;
      if (action.executionTick >= 0 && tick - action.executionTick > 1) break;
      if (action.skill != skill && !(skill < 0 && action.skill == 0)) continue;
      if (action.projectileTick < 0) action.projectileTick = tick;
      action.projectiles++;
      return;
    }
  }

  void close(int tick) { end(tick, "CENSORED_AT_TEST_END"); }

  private void end(int tick, String outcome) {
    if (current == null) return;
    current.endTick = tick;
    if (current.executionTick < 0) current.outcome = outcome;
    current = null;
  }
}
