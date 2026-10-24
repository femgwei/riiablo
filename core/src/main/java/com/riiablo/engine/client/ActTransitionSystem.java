package com.riiablo.engine.client;

import com.riiablo.engine.server.event.NativeActTransitionEvent;
import com.d2moo.common.drlg.D2LevelIds;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Applies server-authoritative act transitions to the local loading screen. */
public class ActTransitionSystem extends PassiveSystem {
  @com.artemis.annotations.Wire(name = "clientActTransition", failOnNull = false)
  protected ClientActTransitionPort clientActTransition;

  @Subscribe
  public void onActTransition(NativeActTransitionEvent transition) {
    if (transition == null || transition.destinationLevelId <= 0 || clientActTransition == null) return;
    int act = actForLevel(transition.destinationLevelId);
    if (act < 0 || !clientActTransition.transitionAct(act)) return;
    transition.accept();
  }

  static int actForLevel(int levelId) {
    if (levelId >= D2LevelIds.LEVEL_ROGUEENCAMPMENT
        && levelId < D2LevelIds.LEVEL_LUTGHOLEIN) return 0;
    if (levelId >= D2LevelIds.LEVEL_LUTGHOLEIN
        && levelId < D2LevelIds.LEVEL_KURASTDOCKTOWN) return 1;
    if (levelId >= D2LevelIds.LEVEL_KURASTDOCKTOWN && levelId < D2LevelIds.LEVEL_OUTERSTEPPES) return 2;
    if (levelId >= D2LevelIds.LEVEL_THEPANDEMONIUMFORTRESS && levelId <= D2LevelIds.LEVEL_CHAOSSANCTUM) return 3;
    if (levelId >= 109) return 4;
    return -1;
  }
}

