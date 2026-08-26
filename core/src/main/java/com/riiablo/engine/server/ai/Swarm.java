package com.riiablo.engine.server.ai;

/**
 * Native Swarm AI alias.
 *
 * <p>D2MOO maps AI functions 012 (Goatman) and 019 (Swarm) to the same
 * {@code AITHINK_Fn012_019_Goatman_Swarm} routine. Keeping the alias as a
 * concrete class lets the data-driven AI loader preserve that exact mapping
 * without falling back to GenericMonster.</p>
 */
public class Swarm extends Goatman {
  public Swarm(int entityId) {
    super(entityId);
  }
}
