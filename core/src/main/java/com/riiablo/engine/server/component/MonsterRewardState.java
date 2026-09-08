package com.riiablo.engine.server.component;

import com.badlogic.gdx.utils.IntArray;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/**
 * Per-monster authoritative death reward state.
 *
 * <p>The component belongs to the entity lifecycle, so Artemis removes it
 * when an entity is deleted and later reuses the numeric id. Native unit
 * eligibility flags are kept separately in {@link NativeUnitFlags}; this
 * component only makes duplicate death events idempotent.</p>
 */
@Transient
@PooledWeaver
public class MonsterRewardState extends Component {
  public static final int CLAIM_EXPERIENCE = 1;
  public static final int CLAIM_TREASURE_CLASS = 1 << 1;

  private int claims;
  private int snapshotOwnerId = -1;
  private int snapshotDifficulty;
  private int snapshotMonsterLevel;
  private int snapshotExperience;
  private int[] snapshotEligiblePlayers = new int[0];

  public MonsterRewardState reset() {
    claims = 0;
    snapshotOwnerId = -1;
    snapshotDifficulty = 0;
    snapshotMonsterLevel = 0;
    snapshotExperience = 0;
    snapshotEligiblePlayers = new int[0];
    return this;
  }

  /** Captures immutable reward context on the first valid death event. */
  public MonsterRewardState captureSnapshot(int ownerId, int difficulty,
      int monsterLevel, int experience, IntArray eligiblePlayers) {
    if (snapshotOwnerId >= 0) return this;
    snapshotOwnerId = ownerId;
    snapshotDifficulty = difficulty;
    snapshotMonsterLevel = monsterLevel;
    snapshotExperience = experience;
    snapshotEligiblePlayers = eligiblePlayers == null ? new int[0] : eligiblePlayers.toArray();
    return this;
  }

  public boolean hasSnapshot() { return snapshotOwnerId >= 0; }
  public int snapshotOwnerId() { return snapshotOwnerId; }
  public int snapshotDifficulty() { return snapshotDifficulty; }
  public int snapshotMonsterLevel() { return snapshotMonsterLevel; }
  public int snapshotExperience() { return snapshotExperience; }
  public int[] snapshotEligiblePlayers() { return snapshotEligiblePlayers.clone(); }

  public boolean claimExperience() {
    if ((claims & CLAIM_EXPERIENCE) != 0) return false;
    claims |= CLAIM_EXPERIENCE;
    return true;
  }

  public boolean claimTreasureClass() {
    if ((claims & CLAIM_TREASURE_CLASS) != 0) return false;
    claims |= CLAIM_TREASURE_CLASS;
    return true;
  }

  public int flags() {
    return claims;
  }
}
