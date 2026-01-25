package com.riiablo.engine.server.npc;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * NPC 对话管理器 - 基于 D2MOO SUnitProxy.cpp 和 SUnitNpc.cpp 移植
 * 
 * <p>管理 NPC 交互和对话系统：
 * <ul>
 *   <li>NPC 交互开始/结束</li>
 *   <li>对话菜单管理</li>
 *   <li>交易/赌博/雇佣/修理等功能</li>
 *   <li>任务相关对话</li>
 * </ul>
 * 
 * <p>参考：
 * <ul>
 *   <li>D2MOO/source/D2Game/src/UNIT/SUnitProxy.cpp</li>
 *   <li>D2MOO/source/D2Game/src/UNIT/SUnitNpc.cpp</li>
 * </ul>
 * 
 * @author riiablo team
 */
public class NpcDialogManager {
  private static final Logger log = LogManager.getLogger(NpcDialogManager.class);

  //==========================================================================
  // 常量 - NPC 菜单选项类型
  //==========================================================================

  /** 交易（买卖物品） */
  public static final int MENU_TRADE = 0;

  /** 对话 */
  public static final int MENU_TALK = 1;

  /** 赌博 */
  public static final int MENU_GAMBLE = 2;

  /** 雇佣佣兵 */
  public static final int MENU_HIRE = 3;

  /** 复活佣兵 */
  public static final int MENU_RESURRECT = 4;

  /** 修理物品 */
  public static final int MENU_REPAIR = 5;

  /** 鉴定物品 */
  public static final int MENU_IDENTIFY = 6;

  /** 离开 */
  public static final int MENU_CANCEL = 7;

  /** 传送门（旅行） */
  public static final int MENU_TRAVEL = 8;

  /** 技能重置 */
  public static final int MENU_RESET_SKILLS = 9;

  /** 属性重置 */
  public static final int MENU_RESET_STATS = 10;

  /** 任务 */
  public static final int MENU_QUEST = 11;

  /** 洗点（重生） */
  public static final int MENU_RESPEC = 12;

  //==========================================================================
  // 常量 - NPC 类型
  //==========================================================================

  /** 普通 NPC（仅对话） */
  public static final int NPC_TYPE_NORMAL = 0;

  /** 商人 */
  public static final int NPC_TYPE_VENDOR = 1;

  /** 雇佣兵 NPC */
  public static final int NPC_TYPE_HIRELING = 2;

  /** 铁匠（修理） */
  public static final int NPC_TYPE_BLACKSMITH = 3;

  /** 赌博商人 */
  public static final int NPC_TYPE_GAMBLER = 4;

  /** 传送门 NPC */
  public static final int NPC_TYPE_TRAVEL = 5;

  //==========================================================================
  // 内部类
  //==========================================================================

  /**
   * NPC 定义
   */
  public static class NpcDefinition {
    /** NPC 类型 ID（Monster ID） */
    public int npcId;

    /** NPC 名称 */
    public String name;

    /** NPC 类型 */
    public int npcType;

    /** 所属章节（1-5） */
    public int act;

    /** 可用菜单选项 */
    public Array<Integer> menuOptions = new Array<>();

    /** 是否为商人 */
    public boolean isVendor;

    /** 是否可赌博 */
    public boolean canGamble;

    /** 是否可修理 */
    public boolean canRepair;

    /** 是否可雇佣 */
    public boolean canHire;

    /** 是否提供传送 */
    public boolean canTravel;

    /** 对话字符串 ID */
    public int[] dialogStringIds;
  }

  /**
   * 对话会话
   */
  public static class DialogSession {
    /** 玩家 ID */
    public int playerId;

    /** NPC 实体 ID */
    public int npcEntityId;

    /** NPC 类型 ID */
    public int npcTypeId;

    /** NPC 定义 */
    public NpcDefinition npcDefinition;

    /** 当前菜单状态 */
    public int currentMenu;

    /** 会话开始时间 */
    public long startTime;

    /** 是否在交易中 */
    public boolean inTrade;

    /** 是否在赌博中 */
    public boolean inGamble;

    /** 任务对话 ID（如果有） */
    public int questDialogId;
  }

  /**
   * NPC 库存刷新信息
   */
  public static class NpcInventoryInfo {
    /** NPC 类型 ID */
    public int npcId;

    /** 上次刷新时间 */
    public long lastRefreshTime;

    /** 刷新间隔（毫秒） */
    public long refreshInterval;
  }

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * NPC 对话事件回调
   */
  public interface NpcDialogCallback {
    /**
     * 对话开始
     */
    void onDialogStart(int playerId, int npcEntityId, NpcDefinition def);

    /**
     * 对话结束
     */
    void onDialogEnd(int playerId, int npcEntityId);

    /**
     * 显示菜单选项
     */
    void onShowMenu(int playerId, int npcEntityId, Array<Integer> menuOptions);

    /**
     * 菜单选项被选择
     */
    void onMenuSelected(int playerId, int npcEntityId, int menuOption);

    /**
     * 打开交易界面
     */
    void onOpenTrade(int playerId, int npcEntityId);

    /**
     * 关闭交易界面
     */
    void onCloseTrade(int playerId, int npcEntityId);

    /**
     * 打开赌博界面
     */
    void onOpenGamble(int playerId, int npcEntityId);

    /**
     * 打开修理界面
     */
    void onOpenRepair(int playerId, int npcEntityId);

    /**
     * 显示任务对话
     */
    void onShowQuestDialog(int playerId, int npcEntityId, int questId, int dialogId);

    /**
     * 检查玩家是否有死亡的雇佣兵
     */
    boolean hasDeadMercenary(int playerId);

    /**
     * 获取玩家当前任务信息
     */
    int[] getPlayerActiveQuests(int playerId, int act);

    /**
     * 获取玩家到 NPC 的距离
     */
    float getDistance(int playerId, int npcEntityId);
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** NPC 定义表（NPC ID -> 定义） */
  private final IntMap<NpcDefinition> npcDefinitions = new IntMap<>();

  /** 活跃对话会话（玩家 ID -> 会话） */
  private final IntMap<DialogSession> activeSessions = new IntMap<>();

  /** NPC 库存刷新信息（NPC ID -> 信息） */
  private final IntMap<NpcInventoryInfo> inventoryInfo = new IntMap<>();

  /** 回调 */
  private NpcDialogCallback callback;

  /** 最大交互距离（像素） */
  private static final float MAX_INTERACTION_DISTANCE = 150.0f;

  /** 库存刷新间隔（毫秒） */
  private static final long DEFAULT_REFRESH_INTERVAL = 600000; // 10 分钟

  //==========================================================================
  // 构造函数
  //==========================================================================

  public NpcDialogManager() {
    registerDefaultNpcs();
  }

  //==========================================================================
  // 核心方法 - 对话管理
  //==========================================================================

  /**
   * 开始与 NPC 对话
   * 
   * @param playerId 玩家 ID
   * @param npcEntityId NPC 实体 ID
   * @param npcTypeId NPC 类型 ID
   * @return true 如果成功开始对话
   */
  public boolean startDialog(int playerId, int npcEntityId, int npcTypeId) {
    // 检查是否已有对话
    if (activeSessions.containsKey(playerId)) {
      log.debug("Player {} already in dialog", playerId);
      return false;
    }

    // 检查距离
    if (callback != null) {
      float distance = callback.getDistance(playerId, npcEntityId);
      if (distance > MAX_INTERACTION_DISTANCE) {
        log.debug("Player {} too far from NPC {} (distance: {})", playerId, npcEntityId, distance);
        return false;
      }
    }

    // 获取 NPC 定义
    NpcDefinition def = npcDefinitions.get(npcTypeId);
    if (def == null) {
      // 未知 NPC，使用默认定义
      def = createDefaultNpcDefinition(npcTypeId);
    }

    // 创建会话
    DialogSession session = new DialogSession();
    session.playerId = playerId;
    session.npcEntityId = npcEntityId;
    session.npcTypeId = npcTypeId;
    session.npcDefinition = def;
    session.currentMenu = -1;
    session.startTime = System.currentTimeMillis();

    activeSessions.put(playerId, session);

    log.debug("Player {} started dialog with NPC {} (type: {})", playerId, npcEntityId, npcTypeId);

    if (callback != null) {
      callback.onDialogStart(playerId, npcEntityId, def);
    }

    // 显示菜单
    showMenu(session);

    return true;
  }

  /**
   * 结束对话
   * 
   * @param playerId 玩家 ID
   */
  public void endDialog(int playerId) {
    DialogSession session = activeSessions.remove(playerId);
    if (session == null) {
      return;
    }

    // 关闭交易界面
    if (session.inTrade && callback != null) {
      callback.onCloseTrade(playerId, session.npcEntityId);
    }

    log.debug("Player {} ended dialog with NPC {}", playerId, session.npcEntityId);

    if (callback != null) {
      callback.onDialogEnd(playerId, session.npcEntityId);
    }
  }

  /**
   * 处理菜单选择
   * 
   * @param playerId 玩家 ID
   * @param menuOption 选择的菜单项
   */
  public void handleMenuSelection(int playerId, int menuOption) {
    DialogSession session = activeSessions.get(playerId);
    if (session == null) {
      log.debug("No active dialog for player {}", playerId);
      return;
    }

    log.debug("Player {} selected menu option {}", playerId, menuOption);

    if (callback != null) {
      callback.onMenuSelected(playerId, session.npcEntityId, menuOption);
    }

    switch (menuOption) {
      case MENU_TRADE:
        openTrade(session);
        break;

      case MENU_GAMBLE:
        openGamble(session);
        break;

      case MENU_REPAIR:
        openRepair(session);
        break;

      case MENU_HIRE:
        // 雇佣逻辑由 MercenaryManager 处理
        session.currentMenu = MENU_HIRE;
        break;

      case MENU_RESURRECT:
        // 复活逻辑由 MercenaryManager 处理
        session.currentMenu = MENU_RESURRECT;
        break;

      case MENU_TALK:
        showQuestDialog(session);
        break;

      case MENU_QUEST:
        showQuestDialog(session);
        break;

      case MENU_TRAVEL:
        session.currentMenu = MENU_TRAVEL;
        break;

      case MENU_CANCEL:
        endDialog(playerId);
        break;

      default:
        log.debug("Unknown menu option: {}", menuOption);
        break;
    }
  }

  //==========================================================================
  // 菜单显示
  //==========================================================================

  /**
   * 显示 NPC 菜单
   */
  private void showMenu(DialogSession session) {
    NpcDefinition def = session.npcDefinition;
    Array<Integer> options = new Array<>();

    // 根据 NPC 类型添加菜单选项
    if (def.isVendor) {
      options.add(MENU_TRADE);
    }

    if (def.canGamble) {
      options.add(MENU_GAMBLE);
    }

    if (def.canRepair) {
      options.add(MENU_REPAIR);
    }

    if (def.canHire) {
      options.add(MENU_HIRE);

      // 检查是否有死亡的雇佣兵
      if (callback != null && callback.hasDeadMercenary(session.playerId)) {
        options.add(MENU_RESURRECT);
      }
    }

    if (def.canTravel) {
      options.add(MENU_TRAVEL);
    }

    // 检查任务对话
    if (callback != null) {
      int[] activeQuests = callback.getPlayerActiveQuests(session.playerId, def.act);
      if (activeQuests != null && activeQuests.length > 0) {
        options.add(MENU_QUEST);
      }
    }

    // 对话选项
    options.add(MENU_TALK);

    // 离开选项
    options.add(MENU_CANCEL);

    if (callback != null) {
      callback.onShowMenu(session.playerId, session.npcEntityId, options);
    }
  }

  /**
   * 打开交易界面
   */
  private void openTrade(DialogSession session) {
    session.inTrade = true;
    session.currentMenu = MENU_TRADE;

    log.debug("Opening trade for player {} with NPC {}", session.playerId, session.npcEntityId);

    if (callback != null) {
      callback.onOpenTrade(session.playerId, session.npcEntityId);
    }
  }

  /**
   * 打开赌博界面
   */
  private void openGamble(DialogSession session) {
    session.inGamble = true;
    session.currentMenu = MENU_GAMBLE;

    log.debug("Opening gamble for player {} with NPC {}", session.playerId, session.npcEntityId);

    if (callback != null) {
      callback.onOpenGamble(session.playerId, session.npcEntityId);
    }
  }

  /**
   * 打开修理界面
   */
  private void openRepair(DialogSession session) {
    session.currentMenu = MENU_REPAIR;

    log.debug("Opening repair for player {} with NPC {}", session.playerId, session.npcEntityId);

    if (callback != null) {
      callback.onOpenRepair(session.playerId, session.npcEntityId);
    }
  }

  /**
   * 显示任务对话
   */
  private void showQuestDialog(DialogSession session) {
    session.currentMenu = MENU_QUEST;

    if (callback != null) {
      int[] activeQuests = callback.getPlayerActiveQuests(session.playerId, session.npcDefinition.act);
      if (activeQuests != null && activeQuests.length > 0) {
        // 显示第一个激活任务的对话
        callback.onShowQuestDialog(session.playerId, session.npcEntityId, activeQuests[0], 0);
      }
    }
  }

  //==========================================================================
  // NPC 注册
  //==========================================================================

  /**
   * 注册默认 NPC
   */
  private void registerDefaultNpcs() {
    // 第一幕 NPC
    registerNpc(148, "Akara", 1, NPC_TYPE_VENDOR, true, false, false, false, false);
    registerNpc(150, "Kashya", 1, NPC_TYPE_HIRELING, false, false, false, true, false);
    registerNpc(154, "Charsi", 1, NPC_TYPE_BLACKSMITH, true, false, true, false, false);
    registerNpc(147, "Gheed", 1, NPC_TYPE_GAMBLER, false, true, false, false, false);
    registerNpc(155, "Warriv", 1, NPC_TYPE_TRAVEL, false, false, false, false, true);

    // 第二幕 NPC
    registerNpc(177, "Fara", 2, NPC_TYPE_BLACKSMITH, true, false, true, false, false);
    registerNpc(175, "Drognan", 2, NPC_TYPE_VENDOR, true, false, false, false, false);
    registerNpc(199, "Elzix", 2, NPC_TYPE_GAMBLER, false, true, false, false, false);
    registerNpc(198, "Greiz", 2, NPC_TYPE_HIRELING, false, false, false, true, false);
    registerNpc(176, "Lysander", 2, NPC_TYPE_VENDOR, true, false, false, false, false);
    registerNpc(210, "Meshif", 2, NPC_TYPE_TRAVEL, false, false, false, false, true);

    // 第三幕 NPC
    registerNpc(252, "Ormus", 3, NPC_TYPE_VENDOR, true, false, false, false, false);
    registerNpc(253, "Hratli", 3, NPC_TYPE_BLACKSMITH, true, false, true, false, false);
    registerNpc(254, "Alkor", 3, NPC_TYPE_GAMBLER, false, true, false, false, false);
    registerNpc(199, "Asheara", 3, NPC_TYPE_HIRELING, false, false, false, true, false);

    // 第四幕 NPC
    registerNpc(405, "Halbu", 4, NPC_TYPE_BLACKSMITH, true, false, true, false, false);
    registerNpc(406, "Jamella", 4, NPC_TYPE_GAMBLER, true, true, false, false, false);
    registerNpc(257, "Tyrael", 4, NPC_TYPE_NORMAL, false, false, false, false, false);

    // 第五幕 NPC
    registerNpc(512, "Larzuk", 5, NPC_TYPE_BLACKSMITH, true, false, true, false, false);
    registerNpc(513, "Malah", 5, NPC_TYPE_VENDOR, true, false, false, false, false);
    registerNpc(514, "Anya", 5, NPC_TYPE_VENDOR, true, true, false, false, false);
    registerNpc(511, "Qual-Kehk", 5, NPC_TYPE_HIRELING, false, false, false, true, false);

    log.debug("Registered {} default NPCs", npcDefinitions.size);
  }

  private void registerNpc(int npcId, String name, int act, int npcType,
      boolean vendor, boolean gamble, boolean repair, boolean hire, boolean travel) {

    NpcDefinition def = new NpcDefinition();
    def.npcId = npcId;
    def.name = name;
    def.act = act;
    def.npcType = npcType;
    def.isVendor = vendor;
    def.canGamble = gamble;
    def.canRepair = repair;
    def.canHire = hire;
    def.canTravel = travel;

    npcDefinitions.put(npcId, def);
  }

  /**
   * 创建默认 NPC 定义
   */
  private NpcDefinition createDefaultNpcDefinition(int npcId) {
    NpcDefinition def = new NpcDefinition();
    def.npcId = npcId;
    def.name = "Unknown";
    def.act = 1;
    def.npcType = NPC_TYPE_NORMAL;
    return def;
  }

  //==========================================================================
  // 库存刷新
  //==========================================================================

  /**
   * 检查 NPC 库存是否需要刷新
   */
  public boolean needsInventoryRefresh(int npcId) {
    NpcInventoryInfo info = inventoryInfo.get(npcId);
    if (info == null) {
      return true;
    }

    long now = System.currentTimeMillis();
    return now - info.lastRefreshTime > info.refreshInterval;
  }

  /**
   * 标记 NPC 库存已刷新
   */
  public void markInventoryRefreshed(int npcId) {
    NpcInventoryInfo info = inventoryInfo.get(npcId);
    if (info == null) {
      info = new NpcInventoryInfo();
      info.npcId = npcId;
      info.refreshInterval = DEFAULT_REFRESH_INTERVAL;
      inventoryInfo.put(npcId, info);
    }
    info.lastRefreshTime = System.currentTimeMillis();
  }

  //==========================================================================
  // 访问器
  //==========================================================================

  public void setCallback(NpcDialogCallback callback) {
    this.callback = callback;
  }

  /**
   * 获取玩家当前对话会话
   */
  public DialogSession getSession(int playerId) {
    return activeSessions.get(playerId);
  }

  /**
   * 检查玩家是否在对话中
   */
  public boolean isInDialog(int playerId) {
    return activeSessions.containsKey(playerId);
  }

  /**
   * 获取 NPC 定义
   */
  public NpcDefinition getNpcDefinition(int npcId) {
    return npcDefinitions.get(npcId);
  }

  /**
   * 注册 NPC 定义
   */
  public void registerNpcDefinition(NpcDefinition def) {
    npcDefinitions.put(def.npcId, def);
  }

  /**
   * 玩家离开时清理
   */
  public void onPlayerLeave(int playerId) {
    endDialog(playerId);
  }
}
