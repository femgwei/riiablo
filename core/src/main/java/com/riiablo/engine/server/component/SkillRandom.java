package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;

/** Authoritative skill-roll state; owned by one entity in one world. */
public class SkillRandom extends PooledComponent {
  public int state;

  @Override protected void reset() { state = 0; }
}
