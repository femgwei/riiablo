package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;

/** Server-authoritative equivalent of the used Frenzy skill's persistent Param1. */
@PooledWeaver
public class FrenzyRuntime extends PooledComponent {
  public int skillId = -1;
  public boolean previousStrikeHit;

  public FrenzyRuntime set(int skillId, boolean previousStrikeHit) {
    this.skillId = skillId;
    this.previousStrikeHit = previousStrikeHit;
    return this;
  }

  @Override
  protected void reset() {
    skillId = -1;
    previousStrikeHit = false;
  }
}
