package com.riiablo.engine.server.trade;

/**
 * 交易槽位数据 - 基于 D2MOD 移植
 * 
 * <p>存储单个交易槽位中的物品信息。
 * 
 * <p>参考：D2MOD/source/D2Game/src/PLAYER/PlrTrade.cpp
 * 
 * @author riiablo team
 */
public class TradeSlot {

  //==========================================================================
  // 常量
  //==========================================================================

  /** 交易区域宽度（格数） */
  public static final int TRADE_WIDTH = 4;
  
  /** 交易区域高度（格数） */
  public static final int TRADE_HEIGHT = 4;
  
  /** 总槽位数 */
  public static final int TOTAL_SLOTS = TRADE_WIDTH * TRADE_HEIGHT;

  //==========================================================================
  // 槽位数据
  //==========================================================================

  /** 物品实体 ID（-1 表示空） */
  public int itemEntityId;
  
  /** 物品类型 ID */
  public int itemClassId;
  
  /** 物品代码（四字符码） */
  public String itemCode;
  
  /** 物品名称 */
  public String itemName;
  
  /** 物品等级 */
  public int itemLevel;
  
  /** 物品品质 */
  public int itemQuality;
  
  /** 物品数量（堆叠物品） */
  public int quantity;
  
  /** 是否已鉴定 */
  public boolean identified;
  
  /** 是否为灵魂绑定物品 */
  public boolean soulbound;
  
  /** 槽位 X 坐标 */
  public int x;
  
  /** 槽位 Y 坐标 */
  public int y;
  
  /** 物品宽度 */
  public int width;
  
  /** 物品高度 */
  public int height;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建空槽位
   */
  public TradeSlot() {
    reset();
  }

  /**
   * 创建交易槽位
   * 
   * @param itemEntityId 物品实体 ID
   * @param x X 坐标
   * @param y Y 坐标
   */
  public TradeSlot(int itemEntityId, int x, int y) {
    this.itemEntityId = itemEntityId;
    this.x = x;
    this.y = y;
    this.width = 1;
    this.height = 1;
    this.quantity = 1;
    this.identified = true;
    this.soulbound = false;
  }

  //==========================================================================
  // 方法
  //==========================================================================

  /**
   * 重置槽位
   */
  public void reset() {
    itemEntityId = -1;
    itemClassId = 0;
    itemCode = "";
    itemName = "";
    itemLevel = 0;
    itemQuality = 0;
    quantity = 0;
    identified = false;
    soulbound = false;
    x = 0;
    y = 0;
    width = 1;
    height = 1;
  }

  /**
   * 设置物品信息
   * 
   * @param entityId 物品实体 ID
   * @param classId 物品类型 ID
   * @param code 物品代码
   * @param name 物品名称
   * @param level 物品等级
   * @param quality 物品品质
   */
  public void setItem(int entityId, int classId, String code, String name, 
                      int level, int quality) {
    this.itemEntityId = entityId;
    this.itemClassId = classId;
    this.itemCode = code;
    this.itemName = name;
    this.itemLevel = level;
    this.itemQuality = quality;
  }

  /**
   * 设置位置和大小
   * 
   * @param x X 坐标
   * @param y Y 坐标
   * @param width 宽度
   * @param height 高度
   */
  public void setPosition(int x, int y, int width, int height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  /**
   * 检查槽位是否为空
   * 
   * @return true 如果为空
   */
  public boolean isEmpty() {
    return itemEntityId < 0;
  }

  /**
   * 检查槽位是否有效
   * 
   * @return true 如果有效
   */
  public boolean isValid() {
    return itemEntityId >= 0;
  }

  /**
   * 检查物品是否可交易
   * 
   * @return true 如果可交易
   */
  public boolean isTradable() {
    return !soulbound && isValid();
  }

  /**
   * 检查指定坐标是否在物品占用范围内
   * 
   * @param checkX X 坐标
   * @param checkY Y 坐标
   * @return true 如果在范围内
   */
  public boolean occupies(int checkX, int checkY) {
    return checkX >= x && checkX < x + width &&
           checkY >= y && checkY < y + height;
  }

  /**
   * 获取槽位索引
   * 
   * @return 槽位索引
   */
  public int getSlotIndex() {
    return y * TRADE_WIDTH + x;
  }

  /**
   * 从索引获取 X 坐标
   * 
   * @param index 槽位索引
   * @return X 坐标
   */
  public static int getXFromIndex(int index) {
    return index % TRADE_WIDTH;
  }

  /**
   * 从索引获取 Y 坐标
   * 
   * @param index 槽位索引
   * @return Y 坐标
   */
  public static int getYFromIndex(int index) {
    return index / TRADE_WIDTH;
  }

  @Override
  public String toString() {
    if (isEmpty()) {
      return "TradeSlot{empty}";
    }
    return "TradeSlot{" +
        "itemEntityId=" + itemEntityId +
        ", name='" + itemName + '\'' +
        ", pos=(" + x + "," + y + ")" +
        ", size=(" + width + "x" + height + ")" +
        '}';
  }
}
