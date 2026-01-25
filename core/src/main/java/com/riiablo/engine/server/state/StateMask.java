package com.riiablo.engine.server.state;

/**
 * 状态掩码 - 基于 D2MOO D2C_StateMasks 移植
 * 
 * <p>定义了用于分类和过滤状态的掩码。这些掩码对应 states.txt 中的
 * 各个标志列，用于快速判断状态的特性。
 * 
 * <p>参考：D2MOO/source/D2Common/include/D2States.h
 * 
 * @author riiablo team
 */
public final class StateMask {
  private StateMask() {} // 不可实例化

  //==========================================================================
  // 状态掩码定义
  //==========================================================================

  /** 不发送到客户端的状态 */
  public static final int NOSEND = 0;
  
  /** 光环类状态 */
  public static final int AURA = 1;
  
  /** 隐藏的状态（不显示图标） */
  public static final int HIDE = 2;
  
  /** 变形类状态 */
  public static final int TRANSFORM = 3;
  
  /** PGSV 状态（游戏保存） */
  public static final int PGSV = 4;
  
  /** 主动状态 */
  public static final int ACTIVE = 5;
  
  /** 被命中时移除的状态 */
  public static final int REMHIT = 6;
  
  /** 伤害显示蓝色 */
  public static final int DAMBLUE = 7;
  
  /** 伤害显示红色 */
  public static final int DAMRED = 8;
  
  /** 攻击显示蓝色 */
  public static final int ATTBLUE = 9;
  
  /** 攻击显示红色 */
  public static final int ATTRED = 10;
  
  /** 诅咒类状态 */
  public static final int CURSE = 11;
  
  /** 可治愈的状态 */
  public static final int CURABLE = 12;
  
  /** 玩家死亡时保留的状态 */
  public static final int PLRSTAYDEATH = 13;
  
  /** 怪物死亡时保留的状态 */
  public static final int MONSTAYDEATH = 14;
  
  /** Boss死亡时保留的状态 */
  public static final int BOSSSTAYDEATH = 15;
  
  /** 变装状态 */
  public static final int DISGUISE = 16;
  
  /** 限制状态（阻止某些技能） */
  public static final int RESTRICT = 17;
  
  /** 蓝色状态效果 */
  public static final int BLUE = 18;
  
  /** 护甲蓝色高亮 */
  public static final int ARMBLUE = 19;
  
  /** 火焰抗性蓝色高亮 */
  public static final int RFBLUE = 20;
  
  /** 冰冷抗性蓝色高亮 */
  public static final int RCBLUE = 21;
  
  /** 闪电抗性蓝色高亮 */
  public static final int RLBLUE = 22;
  
  /** 毒素抗性蓝色高亮 */
  public static final int RPBLUE = 23;
  
  /** 体力条蓝色 */
  public static final int STAMBARBLUE = 24;
  
  /** 护甲红色高亮 */
  public static final int ARMRED = 25;
  
  /** 火焰抗性红色高亮 */
  public static final int RFRED = 26;
  
  /** 冰冷抗性红色高亮 */
  public static final int RCRED = 27;
  
  /** 闪电抗性红色高亮 */
  public static final int RLRED = 28;
  
  /** 毒素抗性红色高亮 */
  public static final int RPRED = 29;
  
  /** 经验加成状态 */
  public static final int EXP = 30;
  
  /** 粉碎效果状态 */
  public static final int SHATTER = 31;
  
  /** 生命相关状态 */
  public static final int LIFE = 32;
  
  /** 亡灵状态 */
  public static final int UDEAD = 33;
  
  /** 绿色状态效果 */
  public static final int GREEN = 34;
  
  /** 无覆盖层的状态 */
  public static final int NOOVERLAYS = 35;
  
  /** 不清除的状态 */
  public static final int NOCLEAR = 36;
  
  /** Boss无敌状态 */
  public static final int BOSSINV = 37;
  
  /** 仅近战的状态 */
  public static final int MELEEONLY = 38;
  
  /** 不对死亡单位的状态 */
  public static final int NOTONDEAD = 39;

  /** 掩码总数 */
  public static final int MAX_MASK_COUNT = 40;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查掩码ID是否有效
   * 
   * @param maskId 掩码ID
   * @return true 如果有效
   */
  public static boolean isValid(int maskId) {
    return maskId >= 0 && maskId < MAX_MASK_COUNT;
  }

  /**
   * 获取掩码名称（调试用）
   * 
   * @param maskId 掩码ID
   * @return 掩码名称
   */
  public static String getName(int maskId) {
    switch (maskId) {
      case NOSEND: return "nosend";
      case AURA: return "aura";
      case HIDE: return "hide";
      case TRANSFORM: return "transform";
      case PGSV: return "pgsv";
      case ACTIVE: return "active";
      case REMHIT: return "remhit";
      case CURSE: return "curse";
      case CURABLE: return "curable";
      case BLUE: return "blue";
      case GREEN: return "green";
      default: return "mask_" + maskId;
    }
  }
}
