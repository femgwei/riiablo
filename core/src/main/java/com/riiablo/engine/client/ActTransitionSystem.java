package com.riiablo.engine.client;

import com.riiablo.Riiablo;
import com.riiablo.engine.server.event.NativeActTransitionEvent;
import com.riiablo.screen.GameScreen;
import com.d2moo.common.drlg.D2LevelIds;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

/** Applies server-authoritative act transitions to the local loading screen. */
public class ActTransitionSystem extends PassiveSystem {
  @Subscribe
  public void onActTransition(NativeActTransitionEvent transition) {
    if (transition == null || transition.destinationLevelId <= 0 || Riiablo.game == null) return;
    int act = actForLevel(transition.destinationLevelId);
    if (act < 0 || !(Riiablo.game instanceof GameScreen)) return;
    transition.accept();
    ((GameScreen) Riiablo.game).setAct(act);
  }

  static int actForLevel(int levelId) {
    if (levelId >= D2LevelIds.LEVEL_ROGUEENCAMPMENT
        && levelId < D2LevelIds.LEVEL_LUTGHOLEIN) return 0;
    if (levelId >= D2LevelIds.LEVEL_LUTGHOLEIN
        && levelId < D2LevelIds.LEVEL_KURASTDOCKTOWN) return 1;
    if (levelId >= D2LevelIds.LEVEL_KURASTDOCKTOWN && levelId < 105) return 2;
    if (levelId >= 105 && levelId < 111) return 3;
    if (levelId >= 111) return 4;
    return -1;
  }
}
