package com.riiablo.engine.client;

/** Narrow client-host port for accepting a server-authoritative act transition. */
@FunctionalInterface
public interface ClientActTransitionPort {
  boolean transitionAct(int act);
}
