package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.EntityId;
import com.artemis.annotations.PooledWeaver;

import com.badlogic.gdx.math.Vector2;

import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.CombatSystem;

@PooledWeaver
public class Casting extends PooledComponent {
  public int skillId = -1;
  @EntityId
  public int targetId;
  public final Vector2 targetVec = new Vector2();
  /** Server-only state for native Dragon Talon SrvSt24/SrvDo042 chaining. */
  public int dragonTalonRemainingKicks;
  public int dragonTalonSuccessfulKicks;
  public boolean dragonTalonInitialized;
  public boolean dragonTalonProgressiveReleased;
  public boolean dragonTalonKickProcessed;
  /** Server-only state for native Dragon Claw's A2/S4 two-hit sequence. */
  public int dragonClawRemainingStrikes;
  public int dragonClawStrikeIndex;
  public boolean dragonClawInitialized;
  public boolean dragonClawProgressiveReleased;
  public boolean dragonClawStrikeProcessed;
  /** Native Dragon Tail keeps the SrvSt27 combat record until SrvDo050. */
  public CombatSystem.CombatResult dragonTailCombat;
  @EntityId public int dragonTailTargetId;
  public boolean dragonTailPrepared;
  /** Native Dragon Flight SQ sequence: first event warps, second event kicks. */
  public boolean dragonFlightInitialized;
  public boolean dragonFlightWarped;
  public boolean dragonFlightKickProcessed;
  /** Per-animation phase for native Frenzy's two sequence hit events. */
  public boolean frenzyInitialized;
  public int frenzyStrikeIndex;
  @EntityId public int frenzyOriginalTargetId;
  /** Server-only SrvSt37/SrvDo013 Fury target chain. */
  public boolean furyInitialized;
  public boolean furyStrikeProcessed;
  public int furyRemainingStrikes;
  public int furyStrikeIndex;
  @EntityId public int furyCurrentTargetId;
  /** Native Feral Rage/Maul SrvSt56 combat record consumed by SrvDo120. */
  public CombatSystem.CombatResult feralMaulCombat;
  @EntityId public int feralMaulTargetId;
  public int feralMaulStunFrames;
  public boolean feralMaulPrepared;
  /** Native Druid SrvSt57/SrvDo121 Rabies combat record. */
  public CombatSystem.CombatResult rabiesCombat;
  @EntityId public int rabiesTargetId;
  public boolean rabiesPrepared;
  /** Native Druid SrvSt58 Fire Claws combat record consumed by SrvDo002. */
  public CombatSystem.CombatResult fireClawsCombat;
  @EntityId public int fireClawsTargetId;
  public boolean fireClawsPrepared;

  public Casting set(int skillId, int targetId, Vector2 targetVec) {
    this.skillId = skillId;
    this.targetId = targetId;
    this.targetVec.set(targetVec);
    dragonTalonRemainingKicks = 0;
    dragonTalonSuccessfulKicks = 0;
    dragonTalonInitialized = false;
    dragonTalonProgressiveReleased = false;
    dragonTalonKickProcessed = false;
    dragonClawRemainingStrikes = 0;
    dragonClawStrikeIndex = 0;
    dragonClawInitialized = false;
    dragonClawProgressiveReleased = false;
    dragonClawStrikeProcessed = false;
    dragonTailCombat = null;
    dragonTailTargetId = Engine.INVALID_ENTITY;
    dragonTailPrepared = false;
    dragonFlightInitialized = false;
    dragonFlightWarped = false;
    dragonFlightKickProcessed = false;
    frenzyInitialized = false;
    frenzyStrikeIndex = 0;
    frenzyOriginalTargetId = Engine.INVALID_ENTITY;
    furyInitialized = false;
    furyStrikeProcessed = false;
    furyRemainingStrikes = 0;
    furyStrikeIndex = 0;
    furyCurrentTargetId = Engine.INVALID_ENTITY;
    feralMaulCombat = null;
    feralMaulTargetId = Engine.INVALID_ENTITY;
    feralMaulStunFrames = 0;
    feralMaulPrepared = false;
    rabiesCombat = null;
    rabiesTargetId = Engine.INVALID_ENTITY;
    rabiesPrepared = false;
    fireClawsCombat = null;
    fireClawsTargetId = Engine.INVALID_ENTITY;
    fireClawsPrepared = false;
    return this;
  }

  @Override
  protected void reset() {
    skillId = -1;
    targetId = Engine.INVALID_ENTITY;
    targetVec.setZero();
    dragonTalonRemainingKicks = 0;
    dragonTalonSuccessfulKicks = 0;
    dragonTalonInitialized = false;
    dragonTalonProgressiveReleased = false;
    dragonTalonKickProcessed = false;
    dragonClawRemainingStrikes = 0;
    dragonClawStrikeIndex = 0;
    dragonClawInitialized = false;
    dragonClawProgressiveReleased = false;
    dragonClawStrikeProcessed = false;
    dragonTailCombat = null;
    dragonTailTargetId = Engine.INVALID_ENTITY;
    dragonTailPrepared = false;
    dragonFlightInitialized = false;
    dragonFlightWarped = false;
    dragonFlightKickProcessed = false;
    frenzyInitialized = false;
    frenzyStrikeIndex = 0;
    frenzyOriginalTargetId = Engine.INVALID_ENTITY;
    furyInitialized = false;
    furyStrikeProcessed = false;
    furyRemainingStrikes = 0;
    furyStrikeIndex = 0;
    furyCurrentTargetId = Engine.INVALID_ENTITY;
    feralMaulCombat = null;
    feralMaulTargetId = Engine.INVALID_ENTITY;
    feralMaulStunFrames = 0;
    feralMaulPrepared = false;
    rabiesCombat = null;
    rabiesTargetId = Engine.INVALID_ENTITY;
    rabiesPrepared = false;
    fireClawsCombat = null;
    fireClawsTargetId = Engine.INVALID_ENTITY;
    fireClawsPrepared = false;
  }
}
