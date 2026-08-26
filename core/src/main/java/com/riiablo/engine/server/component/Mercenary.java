package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/** Marks a monster-shaped entity as a player-owned friendly hireling. */
@Transient
@PooledWeaver
public class Mercenary extends Component {
  public int ownerId = -1;
  public int mercType;
  public int level;
  public int seed;
  public int nameId;

  public Mercenary set(int ownerId, int mercType, int level, int seed, int nameId) {
    this.ownerId = ownerId;
    this.mercType = mercType;
    this.level = level;
    this.seed = seed;
    this.nameId = nameId;
    return this;
  }
}
