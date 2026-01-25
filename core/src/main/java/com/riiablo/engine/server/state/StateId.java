package com.riiablo.engine.server.state;

/**
 * 状态ID枚举 - 基于 D2MOO D2C_States 移植
 * 
 * <p>定义了游戏中所有状态效果的ID，对应 states.txt 中的条目。
 * 这些状态包括光环、诅咒、增益、减益等各种效果。
 * 
 * <p>参考：D2MOO/source/D2Common/include/D2States.h
 * 
 * @author riiablo team
 */
public final class StateId {
  private StateId() {} // 不可实例化

  //==========================================================================
  // 基础状态 (0-10)
  //==========================================================================

  /** 无状态 */
  public static final int NONE = 0;
  /** 冰冻（无法移动） */
  public static final int FREEZE = 1;
  /** 中毒 */
  public static final int POISON = 2;
  /** 火焰抗性增益 */
  public static final int RESISTFIRE = 3;
  /** 冰冷抗性增益 */
  public static final int RESISTCOLD = 4;
  /** 闪电抗性增益 */
  public static final int RESISTLIGHT = 5;
  /** 魔法抗性增益 */
  public static final int RESISTMAGIC = 6;
  /** 玩家尸体状态 */
  public static final int PLAYERBODY = 7;
  /** 全抗性增益 */
  public static final int RESISTALL = 8;
  /** 伤害加深诅咒 */
  public static final int AMPLIFYDAMAGE = 9;
  /** 冰冻护甲 */
  public static final int FROZENARMOR = 10;

  //==========================================================================
  // 冰系/火系状态 (11-20)
  //==========================================================================

  /** 减速（冰冷效果） */
  public static final int COLD = 11;
  /** 地狱火（持续伤害） */
  public static final int INFERNO = 12;
  /** 烈焰之径 */
  public static final int BLAZE = 13;
  /** 骨甲 */
  public static final int BONEARMOR = 14;
  /** 专注 */
  public static final int CONCENTRATE = 15;
  /** 附魔 */
  public static final int ENCHANT = 16;
  /** 心灵之眼 */
  public static final int INNERSIGHT = 17;
  /** 技能移动状态 */
  public static final int SKILL_MOVE = 18;
  /** 虚弱诅咒 */
  public static final int WEAKEN = 19;
  /** 寒冰护甲 */
  public static final int CHILLINGARMOR = 20;

  //==========================================================================
  // 控制/减益状态 (21-30)
  //==========================================================================

  /** 眩晕 */
  public static final int STUNNED = 21;
  /** 蜘蛛网 */
  public static final int SPIDERLAY = 22;
  /** 昏暗视野 */
  public static final int DIMVISION = 23;
  /** 减速 */
  public static final int SLOWED = 24;
  /** 恋物光环 */
  public static final int FETISHAURA = 25;
  /** 战斗怒吼 */
  public static final int SHOUT = 26;
  /** 嘲讽 */
  public static final int TAUNT = 27;
  /** 定罪光环 */
  public static final int CONVICTION = 28;
  /** 被定罪状态 */
  public static final int CONVICTED = 29;
  /** 能量护盾 */
  public static final int ENERGYSHIELD = 30;

  //==========================================================================
  // 战斗增益/光环 (31-50)
  //==========================================================================

  /** 毒爪 */
  public static final int VENOMCLAWS = 31;
  /** 战斗指令 */
  public static final int BATTLEORDERS = 32;
  /** 力量光环 */
  public static final int MIGHT = 33;
  /** 祈祷光环 */
  public static final int PRAYER = 34;
  /** 神圣之火光环 */
  public static final int HOLYFIRE = 35;
  /** 荆棘光环 */
  public static final int THORNS = 36;
  /** 反抗光环 */
  public static final int DEFIANCE = 37;
  /** 雷云 */
  public static final int THUNDERSTORM = 38;
  /** 闪电箭 */
  public static final int LIGHTNINGBOLT = 39;
  /** 神圣瞄准光环 */
  public static final int BLESSEDAIM = 40;
  /** 体力光环 */
  public static final int STAMINA = 41;
  /** 专注光环 */
  public static final int CONCENTRATION = 42;
  /** 神圣冰冻光环 */
  public static final int HOLYWIND = 43;
  /** 神圣冰冻冷却 */
  public static final int HOLYWINDCOLD = 44;
  /** 净化光环 */
  public static final int CLEANSING = 45;
  /** 神圣冲击光环 */
  public static final int HOLYSHOCK = 46;
  /** 庇护所光环 */
  public static final int SANCTUARY = 47;
  /** 冥想光环 */
  public static final int MEDITATION = 48;
  /** 狂热光环 */
  public static final int FANATICISM = 49;
  /** 救赎光环 */
  public static final int REDEMPTION = 50;

  //==========================================================================
  // 战斗/诅咒状态 (51-70)
  //==========================================================================

  /** 战斗号令 */
  public static final int BATTLECOMMAND = 51;
  /** 阻止恢复 */
  public static final int PREVENTHEAL = 52;
  /** 皈依 */
  public static final int CONVERSION = 53;
  /** 不可打断状态 */
  public static final int UNINTERRUPTABLE = 54;
  /** 钢铁处女诅咒 */
  public static final int IRONMAIDEN = 55;
  /** 恐惧 */
  public static final int TERROR = 56;
  /** 吸引诅咒 */
  public static final int ATTRACT = 57;
  /** 生命窃取诅咒 */
  public static final int LIFETAP = 58;
  /** 混乱诅咒 */
  public static final int CONFUSE = 59;
  /** 衰老诅咒 */
  public static final int DECREPIFY = 60;
  /** 降低抗性诅咒 */
  public static final int LOWERRESIST = 61;
  /** 撕开伤口 */
  public static final int OPENWOUNDS = 62;
  /** 诱饵 */
  public static final int DOPPLEZON = 63;
  /** 致命攻击 */
  public static final int CRITICALSTRIKE = 64;
  /** 闪避 */
  public static final int DODGE = 65;
  /** 回避 */
  public static final int AVOID = 66;
  /** 穿透 */
  public static final int PENETRATE = 67;
  /** 躲闪 */
  public static final int EVADE = 68;
  /** 刺穿 */
  public static final int PIERCE = 69;
  /** 温暖（法力恢复） */
  public static final int WARMTH = 70;

  //==========================================================================
  // 精通/被动状态 (71-100)
  //==========================================================================

  /** 火焰精通 */
  public static final int FIREMASTERY = 71;
  /** 闪电精通 */
  public static final int LIGHTNINGMASTERY = 72;
  /** 冰冷精通 */
  public static final int COLDMASTERY = 73;
  /** 剑术精通 */
  public static final int SWORDMASTERY = 74;
  /** 斧头精通 */
  public static final int AXEMASTERY = 75;
  /** 钉锤精通 */
  public static final int MACEMASTERY = 76;
  /** 长柄精通 */
  public static final int POLEARMMASTERY = 77;
  /** 投掷精通 */
  public static final int THROWINGMASTERY = 78;
  /** 长矛精通 */
  public static final int SPEARMASTERY = 79;
  /** 增加体力 */
  public static final int INCREASEDSTAMINA = 80;
  /** 铁皮肤 */
  public static final int IRONSKIN = 81;
  /** 增加速度 */
  public static final int INCREASEDSPEED = 82;
  /** 自然抗性 */
  public static final int NATURALRESISTANCE = 83;
  /** 手指法师诅咒 */
  public static final int FINGERMAGECURSE = 84;
  /** 无法力恢复 */
  public static final int NOMANAREGEN = 85;
  /** 刚被击中 */
  public static final int JUSTHIT = 86;
  /** 减速飞弹 */
  public static final int SLOWMISSILES = 87;
  /** 颤抖护甲 */
  public static final int SHIVERARMOR = 88;
  /** 战斗哭嚎 */
  public static final int BATTLECRY = 89;
  /** 蓝色闪光 */
  public static final int BLUE = 90;
  /** 红色闪光 */
  public static final int RED = 91;
  /** 死亡延迟 */
  public static final int DEATH_DELAY = 92;
  /** 女武神 */
  public static final int VALKYRIE = 93;
  /** 狂乱 */
  public static final int FRENZY = 94;
  /** 狂暴 */
  public static final int BERSERK = 95;
  /** 复活状态 */
  public static final int REVIVE = 96;
  /** 物品全套装 */
  public static final int ITEMFULLSET = 97;
  /** 源单位 */
  public static final int SOURCEUNIT = 98;
  /** 已救赎 */
  public static final int REDEEMED = 99;
  /** 生命药剂 */
  public static final int HEALTHPOT = 100;

  //==========================================================================
  // 高级状态 (101-150)
  //==========================================================================

  /** 神圣护盾 */
  public static final int HOLYSHIELD = 101;
  /** 刚传送 */
  public static final int JUST_PORTALED = 102;
  /** 怪物狂乱 */
  public static final int MONFRENZY = 103;
  /** 尸体不绘制 */
  public static final int CORPSE_NODRAW = 104;
  /** 阵营 */
  public static final int ALIGNMENT = 105;
  /** 法力药剂 */
  public static final int MANAPOT = 106;
  /** 粉碎 */
  public static final int SHATTER = 107;
  /** 同步传送 */
  public static final int SYNC_WARPED = 108;
  /** 皈依保存 */
  public static final int CONVERSION_SAVE = 109;
  /** 怀孕 */
  public static final int PREGNANT = 110;
  /** 状态111 */
  public static final int STATE_111 = 111;
  /** 狂犬病 */
  public static final int RABIES = 112;
  /** 防御诅咒 */
  public static final int DEFENSE_CURSE = 113;
  /** 血魔法 */
  public static final int BLOOD_MANA = 114;
  /** 燃烧 */
  public static final int BURNING = 115;
  /** 龙翔 */
  public static final int DRAGONFLIGHT = 116;
  /** 重击 */
  public static final int MAUL = 117;
  /** 尸体不可选择 */
  public static final int CORPSE_NOSELECT = 118;
  /** 影子战士 */
  public static final int SHADOWWARRIOR = 119;
  /** 狂怒 */
  public static final int FERALRAGE = 120;
  /** 技能延迟 */
  public static final int SKILLDELAY = 121;
  /** 累进伤害 */
  public static final int PROGRESSIVE_DAMAGE = 122;
  /** 累进窃取 */
  public static final int PROGRESSIVE_STEAL = 123;
  /** 累进其他 */
  public static final int PROGRESSIVE_OTHER = 124;
  /** 累进火焰 */
  public static final int PROGRESSIVE_FIRE = 125;
  /** 累进冰冷 */
  public static final int PROGRESSIVE_COLD = 126;
  /** 累进闪电 */
  public static final int PROGRESSIVE_LIGHTNING = 127;

  //==========================================================================
  // 神殿状态 (128-140)
  //==========================================================================

  /** 护甲神殿 */
  public static final int SHRINE_ARMOR = 128;
  /** 战斗神殿 */
  public static final int SHRINE_COMBAT = 129;
  /** 闪电抗性神殿 */
  public static final int SHRINE_RESIST_LIGHTNING = 130;
  /** 火焰抗性神殿 */
  public static final int SHRINE_RESIST_FIRE = 131;
  /** 冰冷抗性神殿 */
  public static final int SHRINE_RESIST_COLD = 132;
  /** 毒素抗性神殿 */
  public static final int SHRINE_RESIST_POISON = 133;
  /** 技能神殿 */
  public static final int SHRINE_SKILL = 134;
  /** 法力恢复神殿 */
  public static final int SHRINE_MANA_REGEN = 135;
  /** 体力神殿 */
  public static final int SHRINE_STAMINA = 136;
  /** 经验神殿 */
  public static final int SHRINE_EXPERIENCE = 137;

  //==========================================================================
  // 德鲁伊变形/召唤状态 (138-170)
  //==========================================================================

  /** 芬里斯狂怒 */
  public static final int FENRIS_RAGE = 138;
  /** 狼形态 */
  public static final int WOLF = 139;
  /** 熊形态 */
  public static final int BEAR = 140;
  /** 嗜血 */
  public static final int BLOODLUST = 141;
  /** 变形 */
  public static final int CHANGECLASS = 142;
  /** 依附 */
  public static final int ATTACHED = 143;
  /** 飓风 */
  public static final int HURRICANE = 144;
  /** 末日 */
  public static final int ARMAGEDDON = 145;
  /** 隐身 */
  public static final int INVIS = 146;
  /** 荆棘藤 */
  public static final int BARBS = 147;
  /** 狼獾之心 */
  public static final int WOLVERINE = 148;
  /** 橡树智者 */
  public static final int OAKSAGE = 149;
  /** 蔓藤兽 */
  public static final int VINE_BEAST = 150;

  //==========================================================================
  // 刺客状态 (151-180)
  //==========================================================================

  /** 旋风护甲 */
  public static final int CYCLONEARMOR = 151;
  /** 爪精通 */
  public static final int CLAWMASTERY = 152;
  /** 影之斗篷 */
  public static final int CLOAK_OF_SHADOWS = 153;
  /** 回收 */
  public static final int RECYCLED = 154;
  /** 武器格挡 */
  public static final int WEAPONBLOCK = 155;
  /** 隐蔽 */
  public static final int CLOAKED = 156;
  /** 迅捷 */
  public static final int QUICKNESS = 157;
  /** 刀刃护盾 */
  public static final int BLADESHIELD = 158;
  /** 褪色 */
  public static final int FADE = 159;
  /** 召唤抗性 */
  public static final int SUMMONRESIST = 160;
  /** 橡树智者控制 */
  public static final int OAKSAGECONTROL = 161;
  /** 狼獾控制 */
  public static final int WOLVERINECONTROL = 162;
  /** 荆棘控制 */
  public static final int BARBSCONTROL = 163;
  /** 调试控制 */
  public static final int DEBUGCONTROL = 164;

  //==========================================================================
  // 套装/符文之语状态 (165-185)
  //==========================================================================

  /** 物品套装1 */
  public static final int ITEMSET1 = 165;
  /** 物品套装2 */
  public static final int ITEMSET2 = 166;
  /** 物品套装3 */
  public static final int ITEMSET3 = 167;
  /** 物品套装4 */
  public static final int ITEMSET4 = 168;
  /** 物品套装5 */
  public static final int ITEMSET5 = 169;
  /** 物品套装6 */
  public static final int ITEMSET6 = 170;
  /** 符文之语 */
  public static final int RUNEWORD = 171;
  /** 安息 */
  public static final int RESTINPEACE = 172;
  /** 尸体爆炸 */
  public static final int CORPSEEXP = 173;
  /** 旋风斩 */
  public static final int WHIRLWIND = 174;
  /** 全套装通用 */
  public static final int FULLSETGENERIC = 175;
  /** 怪物套装 */
  public static final int MONSTERSET = 176;
  /** 谵妄 */
  public static final int DELERIUM = 177;
  /** 解毒剂 */
  public static final int ANTIDOTE = 178;
  /** 解冻 */
  public static final int THAWING = 179;
  /** 体力药剂 */
  public static final int STAMINAPOT = 180;

  //==========================================================================
  // 被动抗性/Uber状态 (181-200)
  //==========================================================================

  /** 被动火焰抗性 */
  public static final int PASSIVE_RESISTFIRE = 181;
  /** 被动冰冷抗性 */
  public static final int PASSIVE_RESISTCOLD = 182;
  /** 被动闪电抗性 */
  public static final int PASSIVE_RESISTLTNG = 183;
  /** Uber爪牙 */
  public static final int UBERMINION = 184;

  /** 状态总数（用于数组大小） */
  public static final int MAX_STATE_COUNT = 200;

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 检查状态ID是否有效
   * 
   * @param stateId 状态ID
   * @return true 如果有效
   */
  public static boolean isValid(int stateId) {
    return stateId >= NONE && stateId < MAX_STATE_COUNT;
  }

  /**
   * 检查是否是诅咒状态
   * 
   * @param stateId 状态ID
   * @return true 如果是诅咒
   */
  public static boolean isCurse(int stateId) {
    switch (stateId) {
      case AMPLIFYDAMAGE:
      case WEAKEN:
      case DIMVISION:
      case CONVICTION:
      case IRONMAIDEN:
      case TERROR:
      case ATTRACT:
      case LIFETAP:
      case CONFUSE:
      case DECREPIFY:
      case LOWERRESIST:
        return true;
      default:
        return false;
    }
  }

  /**
   * 检查是否是光环状态
   * 
   * @param stateId 状态ID
   * @return true 如果是光环
   */
  public static boolean isAura(int stateId) {
    switch (stateId) {
      case MIGHT:
      case PRAYER:
      case HOLYFIRE:
      case THORNS:
      case DEFIANCE:
      case BLESSEDAIM:
      case STAMINA:
      case CONCENTRATION:
      case HOLYWIND:
      case CLEANSING:
      case HOLYSHOCK:
      case SANCTUARY:
      case MEDITATION:
      case FANATICISM:
      case REDEMPTION:
      case CONVICTION:
        return true;
      default:
        return false;
    }
  }

  /**
   * 检查是否是变形状态
   * 
   * @param stateId 状态ID
   * @return true 如果是变形
   */
  public static boolean isTransform(int stateId) {
    return stateId == WOLF || stateId == BEAR || stateId == CHANGECLASS;
  }

  /**
   * 获取状态名称（调试用）
   * 
   * @param stateId 状态ID
   * @return 状态名称
   */
  public static String getName(int stateId) {
    switch (stateId) {
      case NONE: return "none";
      case FREEZE: return "freeze";
      case POISON: return "poison";
      case AMPLIFYDAMAGE: return "amplify_damage";
      case FROZENARMOR: return "frozen_armor";
      case COLD: return "cold";
      case STUNNED: return "stunned";
      case SLOWED: return "slowed";
      case TERROR: return "terror";
      case WOLF: return "wolf";
      case BEAR: return "bear";
      // ... 可以根据需要添加更多
      default: return "state_" + stateId;
    }
  }
}
