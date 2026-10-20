package com.riiablo.engine.server.quest;

/** Read-only, current-difficulty view; no host, inventory runtime or ECS dependency. */
public interface QuestPlayer {
  short record(int act, int index);
  QuestInventory items();
  boolean expansion();
}
