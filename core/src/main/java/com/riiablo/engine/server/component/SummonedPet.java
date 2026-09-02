package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/** Marks a monster-shaped entity as a player-owned native summon. */
@Transient
@PooledWeaver
public class SummonedPet extends Component {
  public int ownerId = -1;
  public String petType;
  public int skillId = -1;
  public int skillLevel;
  /** Decoy/Dopplezon is targetable but never performs an attack. */
  public boolean passive;
  /** Native duration in 25 Hz game frames; zero means permanent. */
  public int durationFrames;
  public float elapsedFrames;

  public SummonedPet set(int ownerId, String petType, int skillId, int skillLevel,
      boolean passive, int durationFrames) {
    this.ownerId = ownerId;
    this.petType = petType;
    this.skillId = skillId;
    this.skillLevel = Math.max(1, skillLevel);
    this.passive = passive;
    this.durationFrames = Math.max(0, durationFrames);
    elapsedFrames = 0f;
    return this;
  }
}
