package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;

@Transient
@PooledWeaver
public class Monster extends Component {
  public MonStats.Entry  monstats;
  public MonStats2.Entry monstats2;

  /** Level-scaled native A2 profile captured when this monster is spawned. */
  public int attack2MinDamage;
  public int attack2MaxDamage;
  public int attack2ToHit;

  public Monster set(MonStats.Entry monstats, MonStats2.Entry monstats2) {
    this.monstats = monstats;
    this.monstats2 = monstats2;
    attack2MinDamage = 0;
    attack2MaxDamage = 0;
    attack2ToHit = 0;
    return this;
  }

  public Monster setAttack2Profile(int minDamage, int maxDamage, int toHit) {
    attack2MinDamage = Math.max(0, minDamage);
    attack2MaxDamage = Math.max(attack2MinDamage, maxDamage);
    attack2ToHit = Math.max(0, toHit);
    return this;
  }
}
