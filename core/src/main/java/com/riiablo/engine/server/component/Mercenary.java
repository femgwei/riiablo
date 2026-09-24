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
  /** Native Hireling.txt skills and their current levels. */
  public final int[] skills = new int[6];
  public final int[] skillLevels = new int[6];

  /** Mutable state owned by D2Game's hireling AI tick (not save data). */
  public int aiRngState;
  public int aiChanceParam;
  public int targetId = -1;

  public Mercenary set(int ownerId, int mercType, int level, int seed, int nameId) {
    this.ownerId = ownerId;
    this.mercType = mercType;
    this.level = level;
    this.seed = seed;
    this.nameId = nameId;
    this.aiRngState = seed == 0 ? 0x6D2B79F5 : seed;
    this.aiChanceParam = 0;
    this.targetId = -1;
    java.util.Arrays.fill(skills, -1);
    java.util.Arrays.fill(skillLevels, 0);
    return this;
  }

  public void setSkill(int slot, int skillId, int level) {
    if (slot < 0 || slot >= skills.length) return;
    skills[slot] = skillId;
    skillLevels[slot] = Math.max(0, level);
  }
}
