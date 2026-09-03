package com.riiablo.engine.server.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntSet;
import com.riiablo.attributes.Attributes;
import com.riiablo.codec.DCC;
import com.riiablo.codec.excel.Missiles;

@Transient
@PooledWeaver
public class Missile extends PooledComponent {
  public Missiles.Entry missile;
  public float range = 0;
  public AssetDescriptor<DCC> missileDescriptor;
  public final Vector2 start = new Vector2();
  
  /** 导弹拥有者实体 ID（用于伤害计算和敌人判断） */
  public int ownerId = -1;

  /** Current native RoomEx id, updated as the missile crosses room borders. */
  public int roomId = -1;

  /** False for client-side replicas; only the server may resolve collisions. */
  public boolean authoritative = true;
  
  /** 已移动距离（用于范围检查，与 d2mod 一致） */
  public float distanceTraveled = 0f;

  /** Optional monster attack profile captured when the missile is spawned. */
  public int attackMinDamage;
  public int attackMaxDamage;
  public int attackRating;

  /**
   * Native-style damage stat list captured at spawn time. D2MOO writes these
   * stats onto the missile unit before it begins moving, so later owner state
   * changes cannot alter an in-flight projectile.
   */
  public final Attributes damage = Attributes.obtainStandard();
  public boolean damageSnapshot;
  public int damageLevel;
  /** Skills.txt row used to build this projectile's authoritative snapshot. */
  public int skillId = -1;
  /** Ice Arrow and freeze-element explosions convert cold length to freeze. */
  public boolean freezesTarget;
  /** Weapon-source skills still perform the native attack-rating check. */
  public boolean usesAttackRating;
  /** Stationary native area missile (Immolation Fire) remains for its range. */
  public boolean persistent;
  public int remainingFrames;
  public int tickInterval = 1;
  public int tickFrames;

  /** Optional native homing target (Guided Arrow/Bone Spirit). */
  public int targetId = -1;
  public boolean homing;

  /**
   * D2MOO Missile SrvDo20 attaches Blade Creeper's visual/damage missile to
   * its moving Blade Sentinel monster controller. Damage ownership remains
   * with the player, so this is deliberately separate from {@link #ownerId}.
   */
  public int attachedEntityId = -1;
  public boolean attached;
  /** Native frame clock used by NextHit/NextDelay for attached missiles. */
  public int nativeFrame;
  /** Frame lifetime for zero-velocity one-shot missiles such as Blades of Ice cubes. */
  public int nativeLifetimeFrames;
  public final com.badlogic.gdx.utils.IntIntMap nextHitFrame =
      new com.badlogic.gdx.utils.IntIntMap();

  /** Native Wake of Fire maker (SrvDo31) control missile. */
  public boolean wakeMaker;
  public boolean wakeSpawned;
  public float wakeTargetX;
  public float wakeTargetY;
  public float wakeDirectionX;
  public float wakeDirectionY;
  /** Root player owner resolved through a summoned trap ownership chain. */
  public int damageOwnerId = -1;

  /** D2Common PATHTYPE_CHARGEDBOLT deterministic two-tile zig-zag path. */
  public boolean chargedBoltPath;
  public int chargedBoltMainDirection;
  public int chargedBoltSeedLow;
  public int chargedBoltSeedHigh;
  public float chargedBoltNextTurnDistance;

  /** Skill damage multiplier captured when the missile is spawned. */
  public float damageMultiplier = 1f;

  /** Native Pierce state. A missile may survive a hit and continue travelling. */
  public boolean pierceEnabled;
  public int pierceChance;
  public final IntSet hitTargets = new IntSet();

  /** Targets already resolved by another missile from the same cast. */
  public IntSet sharedHitTargets;

  @Override
  protected void reset() {
    missile = null;
    range = 0;
    missileDescriptor = null;
    start.setZero();
    ownerId = -1;
    roomId = -1;
    authoritative = true;
    distanceTraveled = 0f;
    attackMinDamage = 0;
    attackMaxDamage = 0;
    attackRating = 0;
    damage.clear();
    damageSnapshot = false;
    damageLevel = 0;
    skillId = -1;
    freezesTarget = false;
    usesAttackRating = false;
    persistent = false;
    remainingFrames = 0;
    tickInterval = 1;
    tickFrames = 0;
    targetId = -1;
    homing = false;
    attachedEntityId = -1;
    attached = false;
    nativeFrame = 0;
    nativeLifetimeFrames = 0;
    nextHitFrame.clear();
    wakeMaker = false;
    wakeSpawned = false;
    wakeTargetX = 0f;
    wakeTargetY = 0f;
    wakeDirectionX = 0f;
    wakeDirectionY = 0f;
    damageOwnerId = -1;
    chargedBoltPath = false;
    chargedBoltMainDirection = 0;
    chargedBoltSeedLow = 0;
    chargedBoltSeedHigh = 0;
    chargedBoltNextTurnDistance = 0f;
    damageMultiplier = 1f;
    pierceEnabled = false;
    pierceChance = 0;
    hitTargets.clear();
    sharedHitTargets = null;
  }

  public Missile set(Missiles.Entry missile, Vector2 start, float range) {
    this.missile = missile;
    this.start.set(start);
    this.range = range;
    this.missileDescriptor = new AssetDescriptor<>(Class.Type.MIS.PATH + '\\' + missile.CelFile + ".dcc", DCC.class);
    return this;
  }
  
  public Missile setOwner(int ownerId) {
    this.ownerId = ownerId;
    return this;
  }

  public Missile shareHitTargets(IntSet targets) {
    sharedHitTargets = targets;
    return this;
  }
}
