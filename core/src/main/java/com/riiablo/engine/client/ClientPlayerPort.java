package com.riiablo.engine.client;

/** Read-only player identity port used by client simulation systems. */
@FunctionalInterface
public interface ClientPlayerPort {
  int playerId();
}
