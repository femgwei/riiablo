package com.riiablo.engine.server.quest;

/** Read-only inventory port; quest rules never mutate host item storage. */
@FunctionalInterface
public interface QuestInventory {
  boolean containsItemCode(String code);
}
