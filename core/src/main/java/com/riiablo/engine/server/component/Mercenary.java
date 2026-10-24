package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;
import com.artemis.annotations.Transient;
import com.riiablo.engine.server.NativeTargetOrder;

/** Marks a monster-shaped entity as a player-owned friendly hireling. */
@Transient
@PooledWeaver
public class Mercenary extends Component {
  public int ownerId = -1;
  /** Detached/custom-world fallback only; production reads the shared node. */
  private long nativeTargetOrder;
  private NativeTargetNode nativeTargetNode;
  public int mercType;
  /** Hireling.txt skill variant; -1 retains custom/test-world type-based rows. */
  public int hirelingId = -1;
  public int hirelingId() { return hirelingId < 0 ? mercType : hirelingId; }
  public int level;
  public int seed;
  public int nameId;
  public long experience;
  public int flags;
  /** Monotonic runtime revision for committed hireling progression. */
  public long progressionRevision;
  /** Revision of the committed hireling equipment aggregate. */
  public long equipmentRevision;
  /** Native Hireling.txt skills and their current levels. */
  public final int[] skills = new int[6];
  public final int[] skillLevels = new int[6];

  /** Mutable state owned by D2Game's hireling AI tick (not save data). */
  public int aiRngState;
  public int aiChanceParam;
  public int targetId = -1;

  /** Bind at allocation, before set/gameplay events; never import old metadata. */
  public Mercenary bindNativeTargetNode(NativeTargetNode node) {
    nativeTargetNode = node;
    nativeTargetOrder = 0;
    return this;
  }

  public long nativeTargetOrder() {
    return nativeTargetNode == null ? nativeTargetOrder : nativeTargetNode.order(NativeTargetNode.MERCENARY);
  }

  public void detachNativeTargetNode() {
    nativeTargetOrder = 0;
    if (nativeTargetNode != null) nativeTargetNode.detach();
  }

  /** NPC resurrection is a node insertion, not a new hire/AI reset. */
  public void insertNativeTargetNode() {
    if (nativeTargetNode == null) nativeTargetOrder = NativeTargetOrder.inserted();
    else nativeTargetNode.insert(NativeTargetNode.MERCENARY, ownerId);
  }

  public Mercenary set(int ownerId, int mercType, int level, int seed, int nameId) {
    this.ownerId = ownerId;
    insertNativeTargetNode();
    this.mercType = mercType;
    this.hirelingId = -1;
    this.level = level;
    this.seed = seed;
    this.nameId = nameId;
    this.experience = 0;
    this.flags = 0;
    this.progressionRevision = 0L;
    this.equipmentRevision = 0L;
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
