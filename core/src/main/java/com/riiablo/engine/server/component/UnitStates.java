package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.World;
import com.artemis.annotations.PooledWeaver;

import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.NativeTargetNodes;

/**
 * 单位状态组件 - 存储单位上的所有状态效果
 * 
 * <p>该组件将状态系统集成到 ECS 架构中，每个需要状态效果的
 * 实体都应该拥有此组件。
 * 
 * <p>使用示例：
 * <pre>
 * // 添加状态
 * UnitStates states = mUnitStates.get(entityId);
 * states.stateList.addState(StateId.POISON, 250, 5, attackerId);
 * 
 * // 检查状态
 * if (states.stateList.hasState(StateId.FREEZE)) {
 *   // 单位被冰冻
 * }
 * </pre>
 * 
 * @author riiablo team
 */
@PooledWeaver
public class UnitStates extends Component {
  /** Client replicas receive state snapshots but never tick authoritative DOT. */
  public boolean snapshotOnly;

  /** StateUpdater consumed this frame's health-potion rate, including a final poison tick. */
  public boolean healthPotionAdvanced;

  /** 状态列表 */
  public StateList stateList;

  /** Last state-derived resource percentages folded into aggregate stats. */
  public int appliedMaxLifePercent;
  public int appliedMaxManaPercent;
  public int appliedMaxStaminaPercent;
  /** Unmodified permanent/equipment aggregate captured before the state percentage phase. */
  public int baseMaxLifeEncoded;
  public int baseMaxManaEncoded;
  public int resolvedMaxLifeEncoded;
  public int resolvedMaxManaEncoded;
  public long observedAggregateRevision;

  /** Authoritative allocation path; init(int) remains the detached/client bridge. */
  public UnitStates init(World world, int entityId) {
    init(entityId);
    stateList.bindNativeTargetNode(NativeTargetNodes.get(world, entityId));
    return this;
  }

  /**
   * 初始化状态组件
   * 
   * @param entityId 实体ID
   * @return this
   */
  public UnitStates init(int entityId) {
    snapshotOnly = false;
    healthPotionAdvanced = false;
    appliedMaxLifePercent = 0;
    appliedMaxManaPercent = 0;
    appliedMaxStaminaPercent = 0;
    baseMaxLifeEncoded = Integer.MIN_VALUE;
    baseMaxManaEncoded = Integer.MIN_VALUE;
    resolvedMaxLifeEncoded = Integer.MIN_VALUE;
    resolvedMaxManaEncoded = Integer.MIN_VALUE;
    observedAggregateRevision = Long.MIN_VALUE;
    if (stateList == null) {
      stateList = new StateList(entityId);
    } else {
      stateList.setEntityId(entityId);
    }
    return this;
  }

  protected void reset() {
    snapshotOnly = false;
    healthPotionAdvanced = false;
    if (stateList != null) {
      stateList.discardAll();
    }
    appliedMaxLifePercent = 0;
    appliedMaxManaPercent = 0;
    appliedMaxStaminaPercent = 0;
    baseMaxLifeEncoded = Integer.MIN_VALUE;
    baseMaxManaEncoded = Integer.MIN_VALUE;
    resolvedMaxLifeEncoded = Integer.MIN_VALUE;
    resolvedMaxManaEncoded = Integer.MIN_VALUE;
    observedAggregateRevision = Long.MIN_VALUE;
  }
}
