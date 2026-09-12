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

  /**
   * Game-scoped A5Q6 Baal preset membership.  Entity ids are not stable when
   * a Room/ECS is rebuilt, so the quest system uses these native preset
   * identities for diagnostics and re-indexing.  The fields remain transient
   * and are never written to a character D2S save.
   */
  public int baalWaveIndex = -1;
  public int baalWaveSuperUniqueId = -1;
  public boolean baalWaveLeader;

  /** Native MonsterSpawn minion owner for ordinary monster party members. */
  public int minionOwnerId = -1;
  /** Deferred RoomEx pack identity and leader marker used during activation. */
  public int nativePackId = -1;
  public boolean nativePackLeader;

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

  /** Native Conversion alignment/ownership overlay.  The MonStats row remains
   * immutable; this runtime projection is cleared by the Conversion state
   * callback so AI and PvP targeting can observe the temporary allegiance. */
  public boolean converted;
  public int conversionOwnerId = -1;

  public Monster set(MonStats.Entry monstats, MonStats2.Entry monstats2) {
    this.monstats = monstats;
    this.monstats2 = monstats2;
    rank = 0;
    championType = -1;
    affixes = 0L;
    uniqueId = -1;
    baalWaveIndex = -1;
    baalWaveSuperUniqueId = -1;
    baalWaveLeader = false;
    minionOwnerId = -1;
    nativePackId = -1;
    nativePackLeader = false;
    attack2MinDamage = 0;
    attack2MaxDamage = 0;
    attack2ToHit = 0;
    spawnZone = null;
    spawnX = 0f;
    spawnY = 0f;
    converted = false;
    conversionOwnerId = -1;
    return this;
  }

  public Monster setRank(int rank, long affixes, int championType, int uniqueId) {
    this.rank = rank;
    this.affixes = affixes;
    this.championType = championType;
    this.uniqueId = uniqueId;
    return this;
  }

  public Monster setBaalWaveMember(int waveIndex, int superUniqueId, boolean leader) {
    baalWaveIndex = waveIndex;
    baalWaveSuperUniqueId = superUniqueId;
    baalWaveLeader = leader;
    return this;
  }

  /** Assigns the native pack leader for a regular monster minion. */
  public Monster setMinionOwner(int ownerId) {
    minionOwnerId = ownerId;
    return this;
  }

  public Monster setNativePack(int packId, boolean leader) {
    nativePackId = packId;
    nativePackLeader = leader;
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
