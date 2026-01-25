package com.riiablo.engine.server.item;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 赫拉迪克方块管理器 - 基于 D2MOO PlrTrade.cpp 移植
 * 
 * <p>管理赫拉迪克方块的物品合成：
 * <ul>
 *   <li>配方匹配</li>
 *   <li>物品转化</li>
 *   <li>符文合成</li>
 *   <li>装备升级</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Game/src/PLAYER/PlrTrade.cpp
 * 
 * @author riiablo team
 */
public class HoradricCubeManager {
  private static final Logger log = LogManager.getLogger(HoradricCubeManager.class);

  //==========================================================================
  // 常量 - 配方输入标志
  //==========================================================================

  /** 使用物品代码匹配 */
  public static final int FLAG_IN_ITEMCODE = 0x01;

  /** 使用物品类型匹配 */
  public static final int FLAG_IN_ITEMTYPE = 0x02;

  /** 任意物品 */
  public static final int FLAG_IN_USEANY = 0x04;

  /** 升级物品 */
  public static final int FLAG_IN_UPGRADED = 0x08;

  /** 凹槽物品 */
  public static final int FLAG_IN_SOCKETED = 0x10;

  /** 以太物品 */
  public static final int FLAG_IN_ETHEREAL = 0x20;

  /** 未鉴定 */
  public static final int FLAG_IN_UNIDENTIFIED = 0x40;

  //==========================================================================
  // 常量 - 配方输出标志
  //==========================================================================

  /** 使用固定物品代码 */
  public static final int FLAG_OUT_ITEMCODE = 0x01;

  /** 使用物品类型（随机选择） */
  public static final int FLAG_OUT_ITEMTYPE = 0x02;

  /** 保持原物品 */
  public static final int FLAG_OUT_USEITEM = 0x04;

  /** 生成凹槽 */
  public static final int FLAG_OUT_SOCKETED = 0x08;

  /** 修复物品 */
  public static final int FLAG_OUT_REPAIR = 0x10;

  /** 以太化 */
  public static final int FLAG_OUT_ETHEREAL = 0x20;

  /** 重置耐久 */
  public static final int FLAG_OUT_REPLENISH = 0x40;

  //==========================================================================
  // 常量 - 物品品质
  //==========================================================================

  public static final int QUALITY_NONE = 0;
  public static final int QUALITY_LOW = 1;
  public static final int QUALITY_NORMAL = 2;
  public static final int QUALITY_SUPERIOR = 3;
  public static final int QUALITY_MAGIC = 4;
  public static final int QUALITY_SET = 5;
  public static final int QUALITY_RARE = 6;
  public static final int QUALITY_UNIQUE = 7;
  public static final int QUALITY_CRAFT = 8;

  //==========================================================================
  // 内部类
  //==========================================================================

  /**
   * 配方输入物品
   */
  public static class RecipeInput {
    /** 物品代码或类型 ID */
    public int itemId;

    /** 数量要求 */
    public int quantity;

    /** 匹配标志 */
    public int flags;

    /** 品质要求（0 = 无要求） */
    public int quality;

    /** 匹配到的物品列表 */
    public Array<Integer> matchedItems = new Array<>();
  }

  /**
   * 配方输出物品
   */
  public static class RecipeOutput {
    /** 物品代码或类型 ID */
    public int itemId;

    /** 数量 */
    public int quantity;

    /** 输出标志 */
    public int flags;

    /** 输出品质 */
    public int quality;

    /** 凹槽数量（如果适用） */
    public int sockets;

    /** 附加属性 */
    public IntMap<Integer> modifiers = new IntMap<>();
  }

  /**
   * 配方定义
   */
  public static class Recipe {
    /** 配方 ID */
    public int recipeId;

    /** 配方名称 */
    public String name;

    /** 输入物品列表 */
    public Array<RecipeInput> inputs = new Array<>();

    /** 输出物品列表 */
    public Array<RecipeOutput> outputs = new Array<>();

    /** 是否启用 */
    public boolean enabled;

    /** 操作类型（0=合成，1=升级，2=修复等） */
    public int operation;

    /** 等级要求 */
    public int levelRequirement;

    /** 是否资料片专属 */
    public boolean expansionOnly;
  }

  /**
   * 方块物品
   */
  public static class CubeItem {
    /** 物品实体 ID */
    public int entityId;

    /** 物品代码 */
    public int itemCode;

    /** 物品类型 */
    public int itemType;

    /** 品质 */
    public int quality;

    /** 是否凹槽 */
    public boolean socketed;

    /** 是否以太 */
    public boolean ethereal;

    /** 是否鉴定 */
    public boolean identified;

    /** 数量（可堆叠物品） */
    public int quantity;
  }

  /**
   * 合成结果
   */
  public static class TransmuteResult {
    /** 是否成功 */
    public boolean success;

    /** 错误代码（失败时） */
    public int errorCode;

    /** 匹配的配方 */
    public Recipe recipe;

    /** 输出物品列表 */
    public Array<Integer> outputItems = new Array<>();

    /** 消耗的物品列表 */
    public Array<Integer> consumedItems = new Array<>();
  }

  //==========================================================================
  // 错误代码
  //==========================================================================

  public static final int ERROR_NONE = 0;
  public static final int ERROR_NO_RECIPE = 1;
  public static final int ERROR_MISSING_ITEMS = 2;
  public static final int ERROR_LEVEL_TOO_LOW = 3;
  public static final int ERROR_NOT_EXPANSION = 4;
  public static final int ERROR_INVALID_ITEM = 5;

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 方块事件回调
   */
  public interface CubeCallback {
    /**
     * 获取方块中的物品
     */
    Array<CubeItem> getCubeContents(int playerId);

    /**
     * 创建物品
     */
    int createItem(int playerId, int itemCode, int quality, boolean ethereal, int sockets);

    /**
     * 删除物品
     */
    void removeItem(int playerId, int itemId);

    /**
     * 修改物品属性
     */
    void modifyItem(int itemId, IntMap<Integer> modifiers);

    /**
     * 将物品放入方块
     */
    void addItemToCube(int playerId, int itemId);

    /**
     * 合成成功通知
     */
    void onTransmuteSuccess(int playerId, Recipe recipe, TransmuteResult result);

    /**
     * 合成失败通知
     */
    void onTransmuteFailed(int playerId, int errorCode);

    /**
     * 获取玩家等级
     */
    int getPlayerLevel(int playerId);

    /**
     * 是否是资料片
     */
    boolean isExpansion();
  }

  //==========================================================================
  // 字段
  //==========================================================================

  /** 配方列表 */
  private final Array<Recipe> recipes = new Array<>();

  /** 配方 ID 索引 */
  private final IntMap<Recipe> recipeById = new IntMap<>();

  /** 回调 */
  private CubeCallback callback;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public HoradricCubeManager() {
    registerDefaultRecipes();
  }

  //==========================================================================
  // 核心方法 - 合成
  //==========================================================================

  /**
   * 尝试合成
   * 
   * @param playerId 玩家 ID
   * @return 合成结果
   */
  public TransmuteResult transmute(int playerId) {
    TransmuteResult result = new TransmuteResult();

    if (callback == null) {
      result.errorCode = ERROR_INVALID_ITEM;
      return result;
    }

    // 获取方块内容
    Array<CubeItem> contents = callback.getCubeContents(playerId);
    if (contents == null || contents.size == 0) {
      result.errorCode = ERROR_MISSING_ITEMS;
      callback.onTransmuteFailed(playerId, result.errorCode);
      return result;
    }

    // 尝试匹配配方
    Recipe matchedRecipe = findMatchingRecipe(playerId, contents);
    if (matchedRecipe == null) {
      result.errorCode = ERROR_NO_RECIPE;
      callback.onTransmuteFailed(playerId, result.errorCode);
      return result;
    }

    // 检查等级要求
    if (matchedRecipe.levelRequirement > 0) {
      int playerLevel = callback.getPlayerLevel(playerId);
      if (playerLevel < matchedRecipe.levelRequirement) {
        result.errorCode = ERROR_LEVEL_TOO_LOW;
        callback.onTransmuteFailed(playerId, result.errorCode);
        return result;
      }
    }

    // 检查资料片要求
    if (matchedRecipe.expansionOnly && !callback.isExpansion()) {
      result.errorCode = ERROR_NOT_EXPANSION;
      callback.onTransmuteFailed(playerId, result.errorCode);
      return result;
    }

    // 执行合成
    result.success = true;
    result.recipe = matchedRecipe;

    // 收集消耗的物品
    for (RecipeInput input : matchedRecipe.inputs) {
      for (int itemId : input.matchedItems) {
        result.consumedItems.add(itemId);
      }
    }

    // 删除消耗的物品
    for (int itemId : result.consumedItems) {
      callback.removeItem(playerId, itemId);
    }

    // 生成输出物品
    for (RecipeOutput output : matchedRecipe.outputs) {
      for (int i = 0; i < output.quantity; i++) {
        int itemId = callback.createItem(playerId, output.itemId, output.quality, 
            (output.flags & FLAG_OUT_ETHEREAL) != 0,
            output.sockets);

        if (output.modifiers.size > 0) {
          callback.modifyItem(itemId, output.modifiers);
        }

        callback.addItemToCube(playerId, itemId);
        result.outputItems.add(itemId);
      }
    }

    log.debug("Transmute success: {} -> {} items", 
        matchedRecipe.name, result.outputItems.size);

    callback.onTransmuteSuccess(playerId, matchedRecipe, result);

    return result;
  }

  /**
   * 查找匹配的配方
   */
  private Recipe findMatchingRecipe(int playerId, Array<CubeItem> contents) {
    for (Recipe recipe : recipes) {
      if (!recipe.enabled) {
        continue;
      }

      if (matchRecipe(recipe, contents)) {
        return recipe;
      }
    }
    return null;
  }

  /**
   * 检查配方是否匹配
   */
  private boolean matchRecipe(Recipe recipe, Array<CubeItem> contents) {
    // 清除之前的匹配
    for (RecipeInput input : recipe.inputs) {
      input.matchedItems.clear();
    }

    // 复制物品列表用于匹配
    Array<CubeItem> remaining = new Array<>(contents);

    // 尝试匹配每个输入
    for (RecipeInput input : recipe.inputs) {
      int matched = 0;
      int required = input.quantity > 0 ? input.quantity : 1;

      for (int i = remaining.size - 1; i >= 0 && matched < required; i--) {
        CubeItem item = remaining.get(i);
        if (matchItem(input, item)) {
          input.matchedItems.add(item.entityId);
          remaining.removeIndex(i);
          matched++;
        }
      }

      if (matched < required) {
        return false;
      }
    }

    return true;
  }

  /**
   * 检查物品是否匹配输入要求
   */
  private boolean matchItem(RecipeInput input, CubeItem item) {
    // 检查物品代码/类型
    if ((input.flags & FLAG_IN_ITEMCODE) != 0) {
      if (item.itemCode != input.itemId) {
        return false;
      }
    } else if ((input.flags & FLAG_IN_ITEMTYPE) != 0) {
      if (item.itemType != input.itemId) {
        return false;
      }
    }

    // 检查品质
    if (input.quality > 0 && item.quality != input.quality) {
      return false;
    }

    // 检查凹槽
    if ((input.flags & FLAG_IN_SOCKETED) != 0 && !item.socketed) {
      return false;
    }

    // 检查以太
    if ((input.flags & FLAG_IN_ETHEREAL) != 0 && !item.ethereal) {
      return false;
    }

    // 检查未鉴定
    if ((input.flags & FLAG_IN_UNIDENTIFIED) != 0 && item.identified) {
      return false;
    }

    return true;
  }

  //==========================================================================
  // 配方注册
  //==========================================================================

  /**
   * 注册默认配方
   */
  private void registerDefaultRecipes() {
    int id = 0;

    // 3 个碎裂宝石 -> 1 个有缺陷宝石
    registerGemUpgrade(id++, "gcr", "gfr"); // 红宝石
    registerGemUpgrade(id++, "gcg", "gfg"); // 绿宝石
    registerGemUpgrade(id++, "gcb", "gfb"); // 蓝宝石
    registerGemUpgrade(id++, "gcw", "gfw"); // 白宝石
    registerGemUpgrade(id++, "gcy", "gfy"); // 黄宝石
    registerGemUpgrade(id++, "gcv", "gfv"); // 紫宝石
    registerGemUpgrade(id++, "skc", "skf"); // 骷髅

    // 3 个有缺陷宝石 -> 1 个普通宝石
    registerGemUpgrade(id++, "gfr", "gsr"); // 红宝石
    registerGemUpgrade(id++, "gfg", "gsg"); // 绿宝石
    registerGemUpgrade(id++, "gfb", "gsb"); // 蓝宝石
    registerGemUpgrade(id++, "gfw", "gsw"); // 白宝石
    registerGemUpgrade(id++, "gfy", "gsy"); // 黄宝石
    registerGemUpgrade(id++, "gfv", "gsv"); // 紫宝石
    registerGemUpgrade(id++, "skf", "sku"); // 骷髅

    // 3 个普通宝石 -> 1 个无暇宝石
    registerGemUpgrade(id++, "gsr", "glr"); // 红宝石
    registerGemUpgrade(id++, "gsg", "glg"); // 绿宝石
    registerGemUpgrade(id++, "gsb", "glb"); // 蓝宝石
    registerGemUpgrade(id++, "gsw", "glw"); // 白宝石
    registerGemUpgrade(id++, "gsy", "gly"); // 黄宝石
    registerGemUpgrade(id++, "gsv", "glv"); // 紫宝石
    registerGemUpgrade(id++, "sku", "skl"); // 骷髅

    // 3 个无暇宝石 -> 1 个完美宝石
    registerGemUpgrade(id++, "glr", "gpr"); // 红宝石
    registerGemUpgrade(id++, "glg", "gpg"); // 绿宝石
    registerGemUpgrade(id++, "glb", "gpb"); // 蓝宝石
    registerGemUpgrade(id++, "glw", "gpw"); // 白宝石
    registerGemUpgrade(id++, "gly", "gpy"); // 黄宝石
    registerGemUpgrade(id++, "glv", "gpv"); // 紫宝石
    registerGemUpgrade(id++, "skl", "skz"); // 骷髅

    // 符文升级（3 个低级 -> 1 个高级）
    registerRuneUpgrade(id++, "r01", "r02"); // El -> Eld
    registerRuneUpgrade(id++, "r02", "r03"); // Eld -> Tir
    registerRuneUpgrade(id++, "r03", "r04"); // Tir -> Nef
    registerRuneUpgrade(id++, "r04", "r05"); // Nef -> Eth
    registerRuneUpgrade(id++, "r05", "r06"); // Eth -> Ith
    registerRuneUpgrade(id++, "r06", "r07"); // Ith -> Tal
    registerRuneUpgrade(id++, "r07", "r08"); // Tal -> Ral
    registerRuneUpgrade(id++, "r08", "r09"); // Ral -> Ort
    registerRuneUpgrade(id++, "r09", "r10"); // Ort -> Thul
    registerRuneUpgrade(id++, "r10", "r11"); // Thul -> Amn
    registerRuneUpgrade(id++, "r11", "r12"); // Amn -> Sol
    registerRuneUpgrade(id++, "r12", "r13"); // Sol -> Shael
    registerRuneUpgrade(id++, "r13", "r14"); // Shael -> Dol

    // 药水合成
    registerPotionRecipe(id++, "hp3", 3, "hp4"); // 3 大红药 -> 1 超大红药
    registerPotionRecipe(id++, "hp4", 3, "hp5"); // 3 超大红药 -> 1 满红
    registerPotionRecipe(id++, "mp3", 3, "mp4"); // 3 大蓝药 -> 1 超大蓝药
    registerPotionRecipe(id++, "mp4", 3, "mp5"); // 3 超大蓝药 -> 1 满蓝

    // 回城卷轴合成
    registerScrollRecipe(id++, "tsc", 3, "tbk"); // 3 卷轴 -> 1 书

    log.debug("Registered {} default recipes", recipes.size);
  }

  private void registerGemUpgrade(int id, String inputCode, String outputCode) {
    Recipe recipe = new Recipe();
    recipe.recipeId = id;
    recipe.name = "Gem Upgrade: " + inputCode + " -> " + outputCode;
    recipe.enabled = true;
    recipe.operation = 0;

    RecipeInput input = new RecipeInput();
    input.itemId = stringToCode(inputCode);
    input.quantity = 3;
    input.flags = FLAG_IN_ITEMCODE;
    recipe.inputs.add(input);

    RecipeOutput output = new RecipeOutput();
    output.itemId = stringToCode(outputCode);
    output.quantity = 1;
    output.flags = FLAG_OUT_ITEMCODE;
    recipe.outputs.add(output);

    registerRecipe(recipe);
  }

  private void registerRuneUpgrade(int id, String inputCode, String outputCode) {
    Recipe recipe = new Recipe();
    recipe.recipeId = id;
    recipe.name = "Rune Upgrade: " + inputCode + " -> " + outputCode;
    recipe.enabled = true;
    recipe.operation = 0;

    RecipeInput input = new RecipeInput();
    input.itemId = stringToCode(inputCode);
    input.quantity = 3;
    input.flags = FLAG_IN_ITEMCODE;
    recipe.inputs.add(input);

    RecipeOutput output = new RecipeOutput();
    output.itemId = stringToCode(outputCode);
    output.quantity = 1;
    output.flags = FLAG_OUT_ITEMCODE;
    recipe.outputs.add(output);

    registerRecipe(recipe);
  }

  private void registerPotionRecipe(int id, String inputCode, int inputCount, String outputCode) {
    Recipe recipe = new Recipe();
    recipe.recipeId = id;
    recipe.name = "Potion Upgrade: " + inputCode + " -> " + outputCode;
    recipe.enabled = true;
    recipe.operation = 0;

    RecipeInput input = new RecipeInput();
    input.itemId = stringToCode(inputCode);
    input.quantity = inputCount;
    input.flags = FLAG_IN_ITEMCODE;
    recipe.inputs.add(input);

    RecipeOutput output = new RecipeOutput();
    output.itemId = stringToCode(outputCode);
    output.quantity = 1;
    output.flags = FLAG_OUT_ITEMCODE;
    recipe.outputs.add(output);

    registerRecipe(recipe);
  }

  private void registerScrollRecipe(int id, String inputCode, int inputCount, String outputCode) {
    Recipe recipe = new Recipe();
    recipe.recipeId = id;
    recipe.name = "Scroll Recipe: " + inputCode + " -> " + outputCode;
    recipe.enabled = true;
    recipe.operation = 0;

    RecipeInput input = new RecipeInput();
    input.itemId = stringToCode(inputCode);
    input.quantity = inputCount;
    input.flags = FLAG_IN_ITEMCODE;
    recipe.inputs.add(input);

    RecipeOutput output = new RecipeOutput();
    output.itemId = stringToCode(outputCode);
    output.quantity = 1;
    output.flags = FLAG_OUT_ITEMCODE;
    recipe.outputs.add(output);

    registerRecipe(recipe);
  }

  /**
   * 字符串转物品代码
   */
  private int stringToCode(String code) {
    if (code == null || code.length() < 3) {
      return 0;
    }
    // 简化处理：使用字符串哈希
    return code.hashCode();
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setCallback(CubeCallback callback) {
    this.callback = callback;
  }

  /**
   * 注册配方
   */
  public void registerRecipe(Recipe recipe) {
    recipes.add(recipe);
    recipeById.put(recipe.recipeId, recipe);
  }

  /**
   * 获取配方
   */
  public Recipe getRecipe(int recipeId) {
    return recipeById.get(recipeId);
  }

  /**
   * 获取所有配方
   */
  public Array<Recipe> getAllRecipes() {
    return recipes;
  }
}
