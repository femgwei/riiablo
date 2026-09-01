package com.riiablo.engine.server.component;

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

  public MonsterRewardState reset() {
    claims = 0;
    return this;
  }

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
