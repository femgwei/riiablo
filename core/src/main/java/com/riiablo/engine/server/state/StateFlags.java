package com.riiablo.engine.server.state;

/**
 * 状态标志位集合 - 基于 D2MOD 的位操作实现
 * 
 * <p>该类使用位数组来高效存储和查询单位上激活的状态。
 * 每个状态ID对应一个位，可以快速检查状态是否存在。
 * 
 * <p>参考：D2MOD/source/D2Common/src/D2States.cpp 中的位操作
 * 
 * @author riiablo team
 */
public class StateFlags {

  /** 每个 int 存储 32 个状态位 */
  private static final int BITS_PER_INT = 32;

  /** 状态标志位数组（每个 int 存储32个状态） */
  private final int[] flags;

  /** GFX 状态标志（用于客户端渲染） */
  private final int[] gfxFlags;

  /** 数组大小 */
  private final int arraySize;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建状态标志集合
   * 
   * @param maxStateCount 最大状态数量
   */
  public StateFlags(int maxStateCount) {
    this.arraySize = (maxStateCount + BITS_PER_INT - 1) / BITS_PER_INT;
    this.flags = new int[arraySize];
    this.gfxFlags = new int[arraySize];
  }

  /**
   * 使用默认状态数量创建
   */
  public StateFlags() {
    this(StateId.MAX_STATE_COUNT);
  }

  //==========================================================================
  // 状态标志操作
  //==========================================================================

  /**
   * 设置状态标志
   * 
   * @param stateId 状态ID
   * @param set true 设置，false 清除
   */
  public void toggle(int stateId, boolean set) {
    if (!StateId.isValid(stateId)) return;
    
    int arrayIndex = stateId / BITS_PER_INT;
    int bitMask = 1 << (stateId % BITS_PER_INT);
    
    if (set) {
      flags[arrayIndex] |= bitMask;
    } else {
      flags[arrayIndex] &= ~bitMask;
    }
  }

  /**
   * 设置状态标志
   * 
   * @param stateId 状态ID
   */
  public void set(int stateId) {
    toggle(stateId, true);
  }

  /**
   * 清除状态标志
   * 
   * @param stateId 状态ID
   */
  public void clear(int stateId) {
    toggle(stateId, false);
  }

  /**
   * 检查状态是否存在
   * 
   * @param stateId 状态ID
   * @return true 如果状态存在
   */
  public boolean check(int stateId) {
    if (!StateId.isValid(stateId)) return false;
    
    int arrayIndex = stateId / BITS_PER_INT;
    int bitMask = 1 << (stateId % BITS_PER_INT);
    
    return (flags[arrayIndex] & bitMask) != 0;
  }

  /**
   * 清除所有状态标志
   */
  public void clearAll() {
    for (int i = 0; i < arraySize; i++) {
      flags[i] = 0;
    }
  }

  /**
   * 检查是否有任何状态存在
   * 
   * @return true 如果有至少一个状态
   */
  public boolean hasAny() {
    for (int i = 0; i < arraySize; i++) {
      if (flags[i] != 0) {
        return true;
      }
    }
    return false;
  }

  //==========================================================================
  // GFX 状态标志操作（用于客户端渲染）
  //==========================================================================

  /**
   * 设置 GFX 状态标志
   * 
   * @param stateId 状态ID
   * @param set true 设置，false 清除
   */
  public void toggleGfx(int stateId, boolean set) {
    if (!StateId.isValid(stateId)) return;
    
    int arrayIndex = stateId / BITS_PER_INT;
    int bitMask = 1 << (stateId % BITS_PER_INT);
    
    if (set) {
      gfxFlags[arrayIndex] |= bitMask;
    } else {
      gfxFlags[arrayIndex] &= ~bitMask;
    }
  }

  /**
   * 检查 GFX 状态是否存在
   * 
   * @param stateId 状态ID
   * @return true 如果 GFX 状态存在
   */
  public boolean checkGfx(int stateId) {
    if (!StateId.isValid(stateId)) return false;
    
    int arrayIndex = stateId / BITS_PER_INT;
    int bitMask = 1 << (stateId % BITS_PER_INT);
    
    return (gfxFlags[arrayIndex] & bitMask) != 0;
  }

  /**
   * 清除所有 GFX 状态标志
   */
  public void clearAllGfx() {
    for (int i = 0; i < arraySize; i++) {
      gfxFlags[i] = 0;
    }
  }

  /**
   * 检查是否有任何 GFX 状态存在
   * 
   * @return true 如果有至少一个 GFX 状态
   */
  public boolean hasAnyGfx() {
    for (int i = 0; i < arraySize; i++) {
      if (gfxFlags[i] != 0) {
        return true;
      }
    }
    return false;
  }

  //==========================================================================
  // 批量操作
  //==========================================================================

  /**
   * 检查是否存在指定掩码类型的状态
   * 
   * <p>注意：完整实现需要读取 states.txt 中的掩码数据
   * 
   * @param maskType 掩码类型
   * @return true 如果存在匹配的状态
   */
  public boolean checkMask(int maskType) {
    // 简化实现：检查特定状态组
    switch (maskType) {
      case StateMask.CURSE:
        return check(StateId.AMPLIFYDAMAGE) ||
               check(StateId.WEAKEN) ||
               check(StateId.DIMVISION) ||
               check(StateId.IRONMAIDEN) ||
               check(StateId.TERROR) ||
               check(StateId.ATTRACT) ||
               check(StateId.LIFETAP) ||
               check(StateId.CONFUSE) ||
               check(StateId.DECREPIFY) ||
               check(StateId.LOWERRESIST);
               
      case StateMask.AURA:
        return check(StateId.MIGHT) ||
               check(StateId.PRAYER) ||
               check(StateId.HOLYFIRE) ||
               check(StateId.THORNS) ||
               check(StateId.DEFIANCE) ||
               check(StateId.BLESSEDAIM) ||
               check(StateId.CONCENTRATION) ||
               check(StateId.HOLYSHOCK) ||
               check(StateId.SANCTUARY) ||
               check(StateId.MEDITATION) ||
               check(StateId.FANATICISM) ||
               check(StateId.CONVICTION);
               
      case StateMask.TRANSFORM:
        return check(StateId.WOLF) ||
               check(StateId.BEAR) ||
               check(StateId.CHANGECLASS);
               
      default:
        return false;
    }
  }

  /**
   * 从另一个状态标志集合复制
   * 
   * @param other 源状态标志
   */
  public void copyFrom(StateFlags other) {
    int copySize = Math.min(this.arraySize, other.arraySize);
    System.arraycopy(other.flags, 0, this.flags, 0, copySize);
    System.arraycopy(other.gfxFlags, 0, this.gfxFlags, 0, copySize);
  }

  /**
   * 获取内部标志数组（调试用）
   * 
   * @return 标志数组副本
   */
  public int[] getFlags() {
    int[] copy = new int[arraySize];
    System.arraycopy(flags, 0, copy, 0, arraySize);
    return copy;
  }

  /**
   * 获取所有激活的状态ID列表
   * 
   * @return 激活的状态ID数组
   */
  public int[] getActiveStates() {
    // 首先计算数量
    int count = 0;
    for (int i = 0; i < StateId.MAX_STATE_COUNT; i++) {
      if (check(i)) count++;
    }
    
    // 然后填充数组
    int[] result = new int[count];
    int index = 0;
    for (int i = 0; i < StateId.MAX_STATE_COUNT && index < count; i++) {
      if (check(i)) {
        result[index++] = i;
      }
    }
    
    return result;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("StateFlags{");
    boolean first = true;
    for (int i = 0; i < StateId.MAX_STATE_COUNT; i++) {
      if (check(i)) {
        if (!first) sb.append(", ");
        sb.append(StateId.getName(i));
        first = false;
      }
    }
    sb.append('}');
    return sb.toString();
  }
}
