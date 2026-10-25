package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.server.NativeTargetOrder;

/** Marks a monster-shaped entity as a player-owned native summon. */
@Transient
@PooledWeaver
public class SummonedPet extends Component {
  /** Detached/custom-world compatibility only; production uses its bound World allocator. */
  private static long nextSpawnOrder;

  public int ownerId = -1;
  public String petType;
  public int skillId = -1;
  public int skillLevel;
  /** Decoy/Dopplezon is targetable but never performs an attack. */
  public boolean passive;
  /** Native duration in 25 Hz game frames; zero means permanent. */
  public int durationFrames;
  public float elapsedFrames;
  /** Monotonic native pet-list insertion order used for quota eviction. */
  public long spawnOrder;
  /** Detached/custom-world fallback only; production reads the shared node. */
  private long nativeTargetOrder;
  private NativeTargetNode nativeTargetNode;
  /** True after pet-list removal has started the DT -> DD sequence. */
  public boolean unsummonPending;
  public String unsummonReason;
  /** Client-only marker: this component came from a D2GS replica. */
  public boolean networkReplica;
  /** Native assassin-trap shot budget; zero means unlimited/non-sentry summon. */
  public int maxShots;
  public int shotsFired;
  public int attackCooldownFrames;
  public boolean bladeSentinel;
  public boolean hasTrapTarget;
  public float trapTargetX;
  public float trapTargetY;
  /** Blade Creeper AI command origin (the caster position at placement). */
  public float bladeOriginX;
  public float bladeOriginY;
  /** True while Blade Creeper is travelling from its origin to the cast target. */
  public boolean bladeMovingToTarget;
  /** The single SrvDo20 missile attached to this Blade Creeper controller. */
  public int bladeMissileId;
  /** Native SrvDo095 repeatedly emits inferno missiles during one attack. */
  public boolean infernoChanneling;
  /** St53 begins at sequence entry, nine frames before the first Do95. */
  public int infernoFrame;
  public int infernoSequencePoint;
  public int infernoEndFrame = -1;
  public int infernoRemainingFrames;
  public int infernoPulseFrames;
  public int infernoPulseCooldownFrames;
  public int infernoTargetId;
  /** Last corpse consumed by Death Sentry AI Fn104. */
  public int deathLastCorpseId;
  /** Native death animation grace period before the pet is removed from the owner list. */
  public boolean deathPending;
  public float deadFrames;
  /** Item retained by Iron Golem after its ground entity is consumed. */
  public com.riiablo.item.Item sourceItem;
  /** Native Bone Wall/Bone Prison unit; stationary, attackable and walk-blocking. */
  public boolean boneWall;
  /** First segment which owns the remaining wall/prison segments. */
  public int controllerId;

  /** Bind at allocation, before set/gameplay events; never import old metadata. */
  public SummonedPet bindNativeTargetNode(NativeTargetNode node) {
    nativeTargetNode = node;
    nativeTargetOrder = 0;
    return this;
  }

  public long nativeTargetOrder() {
    return nativeTargetNode == null ? nativeTargetOrder
        : nativeTargetNode.order(boneWall ? NativeTargetNode.WALL : NativeTargetNode.SUMMON);
  }

  /** Raw checkpoint ownership, without invalidating a released player group. */
  public NativeTargetNode checkpointTargetNode() { return nativeTargetNode; }
  public boolean hasDetachedTargetOrder() { return nativeTargetNode == null && nativeTargetOrder != 0; }

  public void detachNativeTargetNode() {
    nativeTargetOrder = 0;
    if (nativeTargetNode != null) nativeTargetNode.detach();
  }

  public SummonedPet set(int ownerId, String petType, int skillId, int skillLevel,
      boolean passive, int durationFrames) {
    long worldOrder = nativeTargetNode == null ? -1 : nativeTargetNode.nextPetSpawnOrder();
    if (worldOrder < 0 && nextSpawnOrder == Long.MAX_VALUE) {
      throw new IllegalStateException("detached pet spawn order exhausted");
    }
    long allocatedOrder = worldOrder < 0 ? ++nextSpawnOrder : worldOrder;
    this.ownerId = ownerId;
    this.petType = petType;
    this.skillId = skillId;
    this.skillLevel = Math.max(1, skillLevel);
    this.passive = passive;
    this.durationFrames = Math.max(0, durationFrames);
    elapsedFrames = 0f;
    spawnOrder = allocatedOrder;
    if (nativeTargetNode == null) {
      // Standalone/custom-world compatibility; production binds before set.
      nativeTargetOrder = NativeTargetOrder.inserted();
    } else {
      nativeTargetNode.insert("bonewall".equals(petType)
          ? NativeTargetNode.WALL : NativeTargetNode.SUMMON, ownerId);
    }
    unsummonPending = false;
    unsummonReason = null;
    networkReplica = false;
    maxShots = 0;
    shotsFired = 0;
    attackCooldownFrames = 0;
    bladeSentinel = false;
    hasTrapTarget = false;
    trapTargetX = 0f;
    trapTargetY = 0f;
    bladeOriginX = 0f;
    bladeOriginY = 0f;
    bladeMovingToTarget = true;
    bladeMissileId = -1;
    infernoChanneling = false;
    infernoFrame = 0;
    infernoSequencePoint = 0;
    infernoEndFrame = -1;
    infernoRemainingFrames = 0;
    infernoPulseFrames = 0;
    infernoPulseCooldownFrames = 0;
    infernoTargetId = -1;
    deathLastCorpseId = -1;
    deathPending = false;
    deadFrames = 0f;
    sourceItem = null;
    boneWall = false;
    controllerId = -1;
    return this;
  }
}
