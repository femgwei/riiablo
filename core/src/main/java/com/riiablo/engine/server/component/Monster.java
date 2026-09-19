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
  public int baalWaveRoomId = -1;
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

  /** Runtime provenance for monsters restored from a corpse. */
  public boolean resurrected;
  public int resurrectedBy = -1;
  public boolean playerRevive;

  /** Native activation anchor. Monsters do not pursue targets outside their
   * spawn level/room scope; Zone is the current ECS equivalent of a level
   * room boundary. Null is retained for synthetic unit tests. */
  public Map.Zone spawnZone;
  public float spawnX;
  public float spawnY;
  public boolean hasSpawnAnchor;

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
    baalWaveRoomId = -1;
    baalWaveLeader = false;
    minionOwnerId = -1;
    nativePackId = -1;
    nativePackLeader = false;
    attack2MinDamage = 0;
    attack2MaxDamage = 0;
    attack2ToHit = 0;
    resurrected = false;
    resurrectedBy = -1;
    playerRevive = false;
    spawnZone = null;
    spawnX = 0f;
    spawnY = 0f;
    hasSpawnAnchor = false;
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

  public Monster setBaalWaveMember(int waveIndex, int superUniqueId, int roomId,
      boolean leader) {
    baalWaveIndex = waveIndex;
    baalWaveSuperUniqueId = superUniqueId;
    baalWaveRoomId = roomId;
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

  public Monster setResurrected(int sourceId, boolean playerRevive) {
    resurrected = true;
    resurrectedBy = sourceId;
    this.playerRevive = playerRevive;
    return this;
  }

  public static boolean isMeleeMode(int mode) {
    return mode == com.riiablo.engine.Engine.Monster.MODE_A1
        || mode == com.riiablo.engine.Engine.Monster.MODE_A2;
  }

  public static String modeName(int mode) {
    switch (mode) {
      case com.riiablo.engine.Engine.Monster.MODE_DT: return "DT";
      case com.riiablo.engine.Engine.Monster.MODE_NU: return "NU";
      case com.riiablo.engine.Engine.Monster.MODE_WL: return "WL";
      case com.riiablo.engine.Engine.Monster.MODE_GH: return "GH";
      case com.riiablo.engine.Engine.Monster.MODE_A1: return "A1";
      case com.riiablo.engine.Engine.Monster.MODE_A2: return "A2";
      case com.riiablo.engine.Engine.Monster.MODE_BL: return "BL";
      case com.riiablo.engine.Engine.Monster.MODE_SC: return "SC";
      case com.riiablo.engine.Engine.Monster.MODE_S1: return "S1";
      case com.riiablo.engine.Engine.Monster.MODE_S2: return "S2";
      case com.riiablo.engine.Engine.Monster.MODE_S3: return "S3";
      case com.riiablo.engine.Engine.Monster.MODE_S4: return "S4";
      case com.riiablo.engine.Engine.Monster.MODE_DD: return "DD";
      case com.riiablo.engine.Engine.Monster.MODE_XX: return "XX";
      case com.riiablo.engine.Engine.Monster.MODE_RN: return "RN";
      default: return "UNKNOWN(" + mode + ")";
    }
  }

  public Monster setSpawnAnchor(Map.Zone zone, float x, float y) {
    spawnZone = zone;
    spawnX = x;
    spawnY = y;
    hasSpawnAnchor = true;
    return this;
  }
}
