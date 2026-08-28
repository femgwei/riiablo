package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.map.Map;

@Transient
@PooledWeaver
public class Monster extends Component {
  public MonStats.Entry  monstats;
  public MonStats2.Entry monstats2;

  /** Native monster quality captured at spawn time (normal/champion/unique…). */
  public int rank;
  /** Champion affix type, when {@link #rank} is champion. */
  public int championType;
  /** Unique monster affix bit mask and owner link for minions. */
  public long affixes;
  public int uniqueId;

  /** Level-scaled native A2 profile captured when this monster is spawned. */
  public int attack2MinDamage;
  public int attack2MaxDamage;
  public int attack2ToHit;

  /** Native activation anchor. Monsters do not pursue targets outside their
   * spawn level/room scope; Zone is the current ECS equivalent of a level
   * room boundary. Null is retained for synthetic unit tests. */
  public Map.Zone spawnZone;
  public float spawnX;
  public float spawnY;

  public Monster set(MonStats.Entry monstats, MonStats2.Entry monstats2) {
    this.monstats = monstats;
    this.monstats2 = monstats2;
    rank = 0;
    championType = -1;
    affixes = 0L;
    uniqueId = -1;
    attack2MinDamage = 0;
    attack2MaxDamage = 0;
    attack2ToHit = 0;
    spawnZone = null;
    spawnX = 0f;
    spawnY = 0f;
    return this;
  }

  public Monster setRank(int rank, long affixes, int championType, int uniqueId) {
    this.rank = rank;
    this.affixes = affixes;
    this.championType = championType;
    this.uniqueId = uniqueId;
    return this;
  }

  public Monster setAttack2Profile(int minDamage, int maxDamage, int toHit) {
    attack2MinDamage = Math.max(0, minDamage);
    attack2MaxDamage = Math.max(attack2MinDamage, maxDamage);
    attack2ToHit = Math.max(0, toHit);
    return this;
  }

  public Monster setSpawnAnchor(Map.Zone zone, float x, float y) {
    spawnZone = zone;
    spawnX = x;
    spawnY = y;
    return this;
  }
}
