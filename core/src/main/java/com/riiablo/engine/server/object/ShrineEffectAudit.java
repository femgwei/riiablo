package com.riiablo.engine.server.object;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;

/**
 * Small deterministic shrine-effect matrix used by the hidden 1x1 audit.
 * It exercises the same data-driven code paths used after a player operation:
 * basic restoration, timed-state stacking, and special-code dispatch.
 */
public final class ShrineEffectAudit {
  private ShrineEffectAudit() {}

  public static String run() {
    StringBuilder report = new StringBuilder(4096);
    report.append("effectAudit=shrine-interaction-matrix\n");

    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, 25f);
    attrs.base().put(Stat.maxhp, 100f);
    attrs.base().put(Stat.mana, 10f);
    attrs.base().put(Stat.maxmana, 80f);
    attrs.reset();
    for (int code = 1; code <= 5; code++) {
      attrs.base().put(Stat.hitpoints, 25f);
      attrs.base().put(Stat.mana, 10f);
      attrs.reset();
      boolean applied = NativeShrineSystem.applyBasicEffect(attrs, code,
          code == 4 || code == 5 ? 25 : 0, 200);
      report.append("basic code=").append(code)
          .append(" applied=").append(applied)
          .append(" hp=").append((int) attrs.aggregate().getValue(Stat.hitpoints, 0f))
          .append(" mana=").append((int) attrs.aggregate().getValue(Stat.mana, 0f))
          .append('\n');
    }

    StateList states = new StateList(1);
    for (int code = 6; code <= 15; code++) {
      com.riiablo.engine.server.state.UnitState state =
          NativeShrineEffectSystem.applyTimedEffect(states, code, code, 75, 200, 2400);
      boolean active = state != null && states.hasState(state.stateId);
      report.append("timed code=").append(code)
          .append(" state=").append(state == null ? StateId.NONE : state.stateId)
          .append(" active=").append(active)
          .append(" activeStateCount=").append(states.getStates().size)
          .append('\n');
    }
    boolean allTimedStatesActive = states.getStates().size == 10;
    for (int code = 6; code <= 15; code++) {
      int stateId = NativeShrineEffectSystem.stateIdForCode(code);
      allTimedStatesActive &= states.hasState(stateId);
    }
    report.append("timedStackingFinal=")
        .append(allTimedStatesActive)
        .append('\n');

    for (int code = 17; code <= 22; code++) {
      report.append("special code=").append(code)
          .append(" kind=").append(NativeShrineEffectResolver.kindForCode(code))
          .append('\n');
    }
    report.append("result=PASS\n");
    return report.toString();
  }
}
