package com.riiablo.engine.server.component;

import com.artemis.Component;
import com.artemis.annotations.PooledWeaver;

import com.riiablo.engine.server.state.StateList;

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

  /** 状态列表 */
  public StateList stateList;

  /**
   * 初始化状态组件
   * 
   * @param entityId 实体ID
   * @return this
   */
  public UnitStates init(int entityId) {
    snapshotOnly = false;
    if (stateList == null) {
      stateList = new StateList(entityId);
    } else {
      stateList.setEntityId(entityId);
    }
    return this;
  }

  protected void reset() {
    if (stateList != null) {
      stateList.clearAll();
    }
  }
}
