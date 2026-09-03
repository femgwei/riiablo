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
  /** Native assassin-trap shot budget; zero means unlimited/non-sentry summon. */
  public int maxShots;
  public int shotsFired;
  public int attackCooldownFrames;
  public boolean bladeSentinel;
  public boolean hasTrapTarget;
  public float trapTargetX;
  public float trapTargetY;
  /** Blade Creeper AI command origin (the caster position at placement). */
  public float bladeOriginX;
  public float bladeOriginY;
  /** True while Blade Creeper is travelling from its origin to the cast target. */
  public boolean bladeMovingToTarget;
  /** The single SrvDo20 missile attached to this Blade Creeper controller. */
  public int bladeMissileId;
  /** Native SrvDo095 repeatedly emits inferno missiles during one attack. */
  public boolean infernoChanneling;
  public int infernoRemainingFrames;
  public int infernoPulseFrames;
  public int infernoPulseCooldownFrames;
  public int infernoTargetId;

  public SummonedPet set(int ownerId, String petType, int skillId, int skillLevel,
      boolean passive, int durationFrames) {
    this.ownerId = ownerId;
    this.petType = petType;
    this.skillId = skillId;
    this.skillLevel = Math.max(1, skillLevel);
    this.passive = passive;
    this.durationFrames = Math.max(0, durationFrames);
    elapsedFrames = 0f;
    maxShots = 0;
    shotsFired = 0;
    attackCooldownFrames = 0;
    bladeSentinel = false;
    hasTrapTarget = false;
    trapTargetX = 0f;
    trapTargetY = 0f;
    bladeOriginX = 0f;
    bladeOriginY = 0f;
    bladeMovingToTarget = true;
    bladeMissileId = -1;
    infernoChanneling = false;
    infernoRemainingFrames = 0;
    infernoPulseFrames = 0;
    infernoPulseCooldownFrames = 0;
    infernoTargetId = -1;
    return this;
  }
}
