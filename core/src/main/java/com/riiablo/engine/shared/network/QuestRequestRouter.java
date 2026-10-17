package com.riiablo.engine.shared.network;

/** Minimal request boundary shared by authoritative interaction systems. */
public interface QuestRequestRouter {
  long requestQuest(byte operation, int localTargetEntityId, int messageIndex);
}
