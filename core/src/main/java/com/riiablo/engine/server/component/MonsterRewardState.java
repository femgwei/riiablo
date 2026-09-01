package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;

/**
 * Per-monster authoritative death reward state.
 *
 * <p>The component belongs to the entity lifecycle, so Artemis removes it
 * when an entity is deleted and later reuses the numeric id. It intentionally
 * survives an in-place Shaman resurrection: native {@code SKILLS_ResurrectUnit}
 * marks that monster {@code UNITFLAG_NOXP | UNITFLAG_NOTC}.</p>
 */
@Transient
@PooledWeaver
public class MonsterRewardState extends Component {
  public static final int CLAIM_EXPERIENCE = 1;
  public static final int CLAIM_TREASURE_CLASS = 1 << 1;
  public static final int NO_EXPERIENCE = 1 << 8;
  public static final int NO_TREASURE_CLASS = 1 << 9;

  private int claims;
  private boolean noExperience;
  private boolean noTreasureClass;

  public MonsterRewardState reset() {
    claims = 0;
    noExperience = false;
    noTreasureClass = false;
    return this;
  }

  public boolean claimExperience() {
    if (noExperience || (claims & CLAIM_EXPERIENCE) != 0) return false;
    claims |= CLAIM_EXPERIENCE;
    return true;
  }

  public boolean claimTreasureClass() {
    if (noTreasureClass || (claims & CLAIM_TREASURE_CLASS) != 0) return false;
    claims |= CLAIM_TREASURE_CLASS;
    return true;
  }

  /** Mirrors the flags installed by D2Game {@code SKILLS_ResurrectUnit}. */
  public MonsterRewardState markNativeResurrection() {
    noExperience = true;
    noTreasureClass = true;
    return this;
  }

  public boolean noExperience() {
    return noExperience;
  }

  public boolean noTreasureClass() {
    return noTreasureClass;
  }

  public int flags() {
    return claims
        | (noExperience ? NO_EXPERIENCE : 0)
        | (noTreasureClass ? NO_TREASURE_CLASS : 0);
  }
}
