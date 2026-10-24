package com.riiablo.engine.client;

/** Host-owned keyboard facts consumed by the fixed-step movement system. */
public interface ClientKeyboardInputPort {
  boolean movementBlocked();
}
