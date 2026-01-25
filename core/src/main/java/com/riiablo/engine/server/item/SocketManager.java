package com.riiablo.engine.server.item;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 镶嵌管理器 - 基于 D2MOO Items.cpp 移植
 * 
 * <p>管理物品的镶嵌系统：
 * <ul>
 *   <li>宝石/符文镶嵌到孔洞装备</li>
 *   <li>宝石属性应用（武器/盔甲/盾牌不同属性）</li>
 *   <li>符文之语判定和激活</li>
 *   <li>镶嵌物移除</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Common/src/Items/Items.cpp
 * 
 * @author riiablo team
 */
public class SocketManager {
  private static final Logger log = LogManager.getLogger(SocketManager.class);

  //==========================================================================
  // 常量 - 宝石应用类型
  //==========================================================================

  /** 武器类装备 */
  public static final int GEM_APPLY_WEAPON = 0;

  /** 盔甲类装备（头盔、护甲） */
  public static final int GEM_APPLY_ARMOR = 1;

  /** 盾牌类装备 */
  public static final int GEM_APPLY_SHIELD = 2;

  //==========================================================================
  // 常量 - 符文之语
  //==========================================================================

  /** 符文之语数据 */
  private static final ObjectMap<String, RunewordEntry> runewords = new ObjectMap<>();

  static {
    // 注册常见符文之语
    registerRuneword("Stealth", "TalEth", new String[]{"r07", "r05"}, new String[]{"body", "armo"});
    registerRuneword("Leaf", "TirRal", new String[]{"r03", "r08"}, new String[]{"staf"});
    registerRuneword("Zephyr", "OrtEth", new String[]{"r09", "r05"}, new String[]{"miss"});
    registerRuneword("Lore", "OrtSol", new String[]{"r09", "r12"}, new String[]{"helm"});
    registerRuneword("Insight", "RalTirTalSol", new String[]{"r08", "r03", "r07", "r12"}, new String[]{"pole", "staf"});
    registerRuneword("Spirit", "TalThulOrtAmn", new String[]{"r07", "r10", "r09", "r11"}, new String[]{"swor", "shld"});
    registerRuneword("Smoke", "NefLum", new String[]{"r04", "r17"}, new String[]{"body", "armo"});
    registerRuneword("Rhyme", "ShaelEth", new String[]{"r13", "r05"}, new String[]{"shld"});
    registerRuneword("Ancient's Pledge", "RalOrtTal", new String[]{"r08", "r09", "r07"}, new String[]{"shld"});
    registerRuneword("Honor", "AmnElIthTirSol", new String[]{"r11", "r01", "r06", "r03", "r12"}, new String[]{"mele"});
    registerRuneword("King's Grace", "AmnRalThul", new String[]{"r11", "r08", "r10"}, new String[]{"swor", "scep"});
    registerRuneword("Malice", "IthElEth", new String[]{"r06", "r01", "r05"}, new String[]{"mele"});
    registerRuneword("Strength", "AmnTir", new String[]{"r11", "r03"}, new String[]{"mele"});
    registerRuneword("Holy Thunder", "EthRalOrtTal", new String[]{"r05", "r08", "r09", "r07"}, new String[]{"scep"});
    registerRuneword("White", "DolIo", new String[]{"r14", "r16"}, new String[]{"wand"});
    registerRuneword("Nadir", "NefTir", new String[]{"r04", "r03"}, new String[]{"helm"});
    registerRuneword("Radiance", "NefSolIth", new String[]{"r04", "r12", "r06"}, new String[]{"helm"});
    // 高级符文之语
    registerRuneword("Enigma", "JahIthBer", new String[]{"r31", "r06", "r30"}, new String[]{"body", "armo"});
    registerRuneword("Infinity", "BerMalBerIst", new String[]{"r30", "r23", "r30", "r24"}, new String[]{"pole"});
    registerRuneword("Heart of the Oak", "KoVexPulThul", new String[]{"r18", "r26", "r21", "r10"}, new String[]{"staf", "mace"});
    registerRuneword("Call to Arms", "AmnRalMalIstOhm", new String[]{"r11", "r08", "r23", "r24", "r27"}, new String[]{"weap"});
    registerRuneword("Grief", "EthTirLoMalRal", new String[]{"r05", "r03", "r28", "r23", "r08"}, new String[]{"swor", "axe"});
    registerRuneword("Fortitude", "ElSolDolLo", new String[]{"r01", "r12", "r14", "r28"}, new String[]{"body", "weap"});
  }

  private static void registerRuneword(String name, String letters, String[] runes, String[] types) {
    RunewordEntry entry = new RunewordEntry();
    entry.name = name;
    entry.letters = letters;
    entry.runes = runes;
    entry.allowedTypes = types;
    runewords.put(letters, entry);
  }

  //==========================================================================
  // 内部类
  //==========================================================================

  /**
   * 符文之语条目
   */
  public static class RunewordEntry {
    public String name;
    public String letters;
    public String[] runes;
    public String[] allowedTypes;

    /**
     * 检查装备类型是否适用
     */
    public boolean isTypeAllowed(String itemType) {
      for (String type : allowedTypes) {
        if (type.equalsIgnoreCase(itemType) || itemType.startsWith(type)) {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * 镶嵌结果
   */
  public static class SocketResult {
    public boolean success;
    public String errorMessage;
    public boolean runewordActivated;
    public String runewordName;
    
    /** 应用的属性修改 */
    public Array<StatMod> appliedStats = new Array<>();
  }

  /**
   * 属性修改
   */
  public static class StatMod {
    public short statId;
    public int value;
    public int param;
  }

  /**
   * 物品镶嵌数据
   */
  public static class SocketedItemData {
    /** 物品代码 */
    public String itemCode;

    /** 是否是宝石 */
    public boolean isGem;

    /** 是否是符文 */
    public boolean isRune;

    /** 符文编号（r01-r33） */
    public int runeNumber;

    /** 宝石类型和等级 */
    public int gemType;
    public int gemLevel;
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 镶嵌回调
   */
  public interface SocketCallback {
    /**
     * 镶嵌成功
     */
    void onSocketSuccess(int itemEntityId, int socketFillerEntityId, SocketResult result);

    /**
     * 符文之语激活
     */
    void onRunewordActivated(int itemEntityId, String runewordName);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 回调 */
  private SocketCallback callback;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public SocketManager() {}

  //==========================================================================
  // 核心方法 - 镶嵌
  //==========================================================================

  /**
   * 镶嵌物品到孔洞
   * 
   * <p>参考 D2MOO ITEMS_InsertItemIntoSocket
   * 
   * @param targetItem 目标装备信息
   * @param targetSockets 目标装备当前已镶嵌的物品
   * @param maxSockets 目标装备最大孔洞数
   * @param socketFiller 要镶嵌的宝石/符文
   * @param itemType 目标装备类型（用于确定宝石属性）
   * @return 镶嵌结果
   */
  public SocketResult insertSocket(String targetItem, Array<String> targetSockets,
      int maxSockets, SocketedItemData socketFiller, String itemType) {

    SocketResult result = new SocketResult();

    // 检查是否还有空槽
    if (targetSockets.size >= maxSockets) {
      result.success = false;
      result.errorMessage = "No empty sockets available";
      log.debug("Socket failed: no empty sockets in item {}", targetItem);
      return result;
    }

    // 检查镶嵌物是否有效
    if (!socketFiller.isGem && !socketFiller.isRune) {
      result.success = false;
      result.errorMessage = "Item cannot be socketed";
      log.debug("Socket failed: {} is not a gem or rune", socketFiller.itemCode);
      return result;
    }

    // 添加到孔洞
    targetSockets.add(socketFiller.itemCode);

    // 应用属性
    if (socketFiller.isGem) {
      applyGemStats(socketFiller, itemType, result);
    } else if (socketFiller.isRune) {
      applyRuneStats(socketFiller, result);
    }

    result.success = true;

    // 检查符文之语
    if (socketFiller.isRune && targetSockets.size == maxSockets) {
      checkRuneword(targetSockets, itemType, result);
    }

    log.debug("Socket successful: {} into {}, runes={}", 
        socketFiller.itemCode, targetItem, targetSockets);

    return result;
  }

  /**
   * 应用宝石属性
   * 
   * <p>宝石在不同装备类型上有不同属性：
   * <ul>
   *   <li>武器：增加伤害/攻击属性</li>
   *   <li>盔甲：增加生命/抗性等</li>
   *   <li>盾牌：增加抗性/格挡等</li>
   * </ul>
   */
  private void applyGemStats(SocketedItemData gem, String itemType, SocketResult result) {
    int applyType = getGemApplyType(itemType);

    // 根据宝石类型和等级应用属性
    // 这里简化实现，实际应从 Gems.txt 读取
    log.debug("Applied gem stats: type={}, applyType={}", gem.gemType, applyType);
  }

  /**
   * 应用符文属性
   */
  private void applyRuneStats(SocketedItemData rune, SocketResult result) {
    // 符文单独也有属性加成
    // 实际应从数据表读取
    log.debug("Applied rune stats: {}", rune.itemCode);
  }

  /**
   * 检查并激活符文之语
   */
  private void checkRuneword(Array<String> sockets, String itemType, SocketResult result) {
    // 构建符文字母组合
    StringBuilder letters = new StringBuilder();
    for (String runeCode : sockets) {
      String letter = getRuneLetter(runeCode);
      if (letter != null) {
        letters.append(letter);
      } else {
        // 包含非符文，无法形成符文之语
        return;
      }
    }

    String runeLetters = letters.toString();
    RunewordEntry runeword = runewords.get(runeLetters);

    if (runeword != null && runeword.isTypeAllowed(itemType)) {
      result.runewordActivated = true;
      result.runewordName = runeword.name;
      log.debug("Runeword activated: {} on {}", runeword.name, itemType);

      if (callback != null) {
        callback.onRunewordActivated(-1, runeword.name);
      }
    }
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 确定宝石应用类型
   */
  public int getGemApplyType(String itemType) {
    if (itemType == null) {
      return GEM_APPLY_ARMOR;
    }

    String type = itemType.toLowerCase();

    // 盾牌类
    if (type.contains("shld") || type.contains("shie") || type.contains("pala")) {
      return GEM_APPLY_SHIELD;
    }

    // 武器类
    if (type.contains("weap") || type.contains("swor") || type.contains("axe") ||
        type.contains("mace") || type.contains("pole") || type.contains("spea") ||
        type.contains("bow") || type.contains("xbow") || type.contains("staf") ||
        type.contains("wand") || type.contains("scep") || type.contains("claw") ||
        type.contains("orb") || type.contains("knif") || type.contains("thro") ||
        type.contains("jave")) {
      return GEM_APPLY_WEAPON;
    }

    // 默认为盔甲类
    return GEM_APPLY_ARMOR;
  }

  /**
   * 获取符文字母
   */
  private String getRuneLetter(String runeCode) {
    // 符文代码为 r01-r33
    if (runeCode == null || !runeCode.startsWith("r")) {
      return null;
    }

    // 符文名称映射
    String[] runeNames = {
        null, "El", "Eld", "Tir", "Nef", "Eth", "Ith", "Tal", "Ral", "Ort", "Thul",
        "Amn", "Sol", "Shael", "Dol", "Hel", "Io", "Lum", "Ko", "Fal", "Lem",
        "Pul", "Um", "Mal", "Ist", "Gul", "Vex", "Ohm", "Lo", "Sur", "Ber",
        "Jah", "Cham", "Zod"
    };

    try {
      int runeNum = Integer.parseInt(runeCode.substring(1));
      if (runeNum >= 1 && runeNum < runeNames.length) {
        return runeNames[runeNum];
      }
    } catch (NumberFormatException e) {
      // 忽略
    }

    return null;
  }

  /**
   * 解析物品代码为镶嵌数据
   */
  public SocketedItemData parseSocketFiller(String itemCode) {
    SocketedItemData data = new SocketedItemData();
    data.itemCode = itemCode;

    if (itemCode == null) {
      return data;
    }

    // 检查是否是符文 (r01-r33)
    if (itemCode.startsWith("r") && itemCode.length() == 3) {
      try {
        int runeNum = Integer.parseInt(itemCode.substring(1));
        if (runeNum >= 1 && runeNum <= 33) {
          data.isRune = true;
          data.runeNumber = runeNum;
          return data;
        }
      } catch (NumberFormatException e) {
        // 不是符文
      }
    }

    // 检查是否是宝石
    if (isGemCode(itemCode)) {
      data.isGem = true;
      parseGemCode(itemCode, data);
    }

    return data;
  }

  /**
   * 检查是否是宝石代码
   */
  public boolean isGemCode(String code) {
    if (code == null || code.length() != 3) {
      return false;
    }

    char prefix = code.charAt(0);
    char type = code.charAt(1);

    // 宝石格式：[g][等级][颜色]
    // 等级：c=碎裂, f=裂开, s=普通, l=无瑕, p=完美
    // 颜色：r=红宝石, g=绿宝石, b=蓝宝石, w=钻石, y=黄宝石, v=紫水晶

    if (prefix != 'g') {
      // 检查骷髅：skc, skf, sku, skl, skz
      if (code.startsWith("sk")) {
        char level = code.charAt(2);
        return level == 'c' || level == 'f' || level == 'u' || level == 'l' || level == 'z';
      }
      return false;
    }

    return (type == 'c' || type == 'f' || type == 's' || type == 'l' || type == 'p');
  }

  /**
   * 解析宝石代码
   */
  private void parseGemCode(String code, SocketedItemData data) {
    if (code.startsWith("sk")) {
      // 骷髅
      data.gemType = 6;
      char level = code.charAt(2);
      data.gemLevel = parseGemLevel(level);
    } else {
      // 普通宝石
      char level = code.charAt(1);
      char color = code.charAt(2);

      data.gemLevel = parseGemLevel(level);
      data.gemType = parseGemColor(color);
    }
  }

  private int parseGemLevel(char c) {
    switch (c) {
      case 'c': return 0; // 碎裂
      case 'f': return 1; // 裂开
      case 's':
      case 'u': return 2; // 普通
      case 'l': return 3; // 无瑕
      case 'p':
      case 'z': return 4; // 完美
      default: return 0;
    }
  }

  private int parseGemColor(char c) {
    switch (c) {
      case 'r': return 0; // 红宝石
      case 'g': return 1; // 绿宝石
      case 'b': return 2; // 蓝宝石
      case 'w': return 3; // 钻石
      case 'y': return 4; // 黄宝石
      case 'v': return 5; // 紫水晶
      default: return 0;
    }
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCallback(SocketCallback callback) {
    this.callback = callback;
  }

  /**
   * 获取所有符文之语
   */
  public ObjectMap<String, RunewordEntry> getRunewords() {
    return runewords;
  }

  /**
   * 根据名称查找符文之语
   */
  public RunewordEntry findRunewordByName(String name) {
    for (RunewordEntry entry : runewords.values()) {
      if (entry.name.equalsIgnoreCase(name)) {
        return entry;
      }
    }
    return null;
  }
}
