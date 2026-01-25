package com.riiablo.engine.server.cube;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 赫拉迪克方块管理器 - 基于 D2MOO HoradricCube.cpp 移植
 * 
 * <p>管理赫拉迪克方块的合成系统：
 * <ul>
 *   <li>配方注册和查询</li>
 *   <li>配方匹配</li>
 *   <li>合成执行</li>
 * </ul>
 * 
 * <p>参考：D2MOO/source/D2Common/src/DataTbls/HoradricCube.cpp
 * 
 * @author riiablo team
 */
public class HoradricCube {
  private static final Logger log = LogManager.getLogger(HoradricCube.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 方块格子宽度 */
  public static final int CUBE_WIDTH = 3;

  /** 方块格子高度 */
  public static final int CUBE_HEIGHT = 4;

  /** 最大物品数量 */
  public static final int MAX_ITEMS = CUBE_WIDTH * CUBE_HEIGHT;

  //==========================================================================
  // 字段
  //==========================================================================

  /** 所有配方 */
  private final Array<CubeRecipe> recipes = new Array<>();

  /** 按类型索引的配方 */
  private final ObjectMap<Integer, Array<CubeRecipe>> recipesByType = new ObjectMap<>();

  /** 合成结果回调 */
  private CubeTransmuteCallback transmuteCallback;

  //==========================================================================
  // 回调接口
  //==========================================================================

  /**
   * 合成结果回调
   */
  public interface CubeTransmuteCallback {
    /**
     * 合成成功
     * 
     * @param recipe 使用的配方
     * @param playerId 玩家 ID
     */
    void onTransmuteSuccess(CubeRecipe recipe, int playerId);

    /**
     * 合成失败
     * 
     * @param reason 失败原因
     * @param playerId 玩家 ID
     */
    void onTransmuteFailed(String reason, int playerId);
  }

  //==========================================================================
  // 构造函数
  //==========================================================================

  public HoradricCube() {
    // 注册默认配方
    registerDefaultRecipes();
  }

  //==========================================================================
  // 配方注册
  //==========================================================================

  /**
   * 注册配方
   */
  public void registerRecipe(CubeRecipe recipe) {
    recipes.add(recipe);

    // 按类型索引
    Array<CubeRecipe> typeRecipes = recipesByType.get(recipe.recipeType);
    if (typeRecipes == null) {
      typeRecipes = new Array<>();
      recipesByType.put(recipe.recipeType, typeRecipes);
    }
    typeRecipes.add(recipe);

    log.debug("Registered recipe: {}", recipe);
  }

  /**
   * 注册默认配方
   */
  private void registerDefaultRecipes() {
    // 宝石升级配方
    registerGemUpgradeRecipes();

    // 符文升级配方
    registerRuneUpgradeRecipes();

    // 药水合成配方
    registerPotionRecipes();

    // 其他常用配方
    registerMiscRecipes();

    log.debug("Registered {} default recipes", recipes.size);
  }

  /**
   * 注册宝石升级配方
   */
  private void registerGemUpgradeRecipes() {
    // 碎裂宝石 -> 裂开宝石
    String[] gemTypes = {"gcr", "gcg", "gcb", "gcw", "gcy", "gcv", "skc"};
    String[] flawedGems = {"gfr", "gfg", "gfb", "gfw", "gfy", "gfv", "skf"};

    for (int i = 0; i < gemTypes.length; i++) {
      CubeRecipe recipe = new CubeRecipe(100 + i, CubeRecipeType.GEM_UPGRADE);
      recipe.inputs = new CubeRecipe.CubeInput[] {
          new CubeRecipe.CubeInput(gemTypes[i], 3)
      };
      recipe.inputCount = 3;
      recipe.output.itemCode = flawedGems[i];
      recipe.output.quantity = 1;
      registerRecipe(recipe);
    }

    // 裂开宝石 -> 普通宝石
    String[] normalGems = {"gsr", "gsg", "gsb", "gsw", "gsy", "gsv", "sku"};
    for (int i = 0; i < flawedGems.length; i++) {
      CubeRecipe recipe = new CubeRecipe(110 + i, CubeRecipeType.GEM_UPGRADE);
      recipe.inputs = new CubeRecipe.CubeInput[] {
          new CubeRecipe.CubeInput(flawedGems[i], 3)
      };
      recipe.inputCount = 3;
      recipe.output.itemCode = normalGems[i];
      recipe.output.quantity = 1;
      registerRecipe(recipe);
    }

    // 普通宝石 -> 无瑕宝石
    String[] flawlessGems = {"glr", "glg", "glb", "glw", "gly", "glv", "skl"};
    for (int i = 0; i < normalGems.length; i++) {
      CubeRecipe recipe = new CubeRecipe(120 + i, CubeRecipeType.GEM_UPGRADE);
      recipe.inputs = new CubeRecipe.CubeInput[] {
          new CubeRecipe.CubeInput(normalGems[i], 3)
      };
      recipe.inputCount = 3;
      recipe.output.itemCode = flawlessGems[i];
      recipe.output.quantity = 1;
      registerRecipe(recipe);
    }

    // 无瑕宝石 -> 完美宝石
    String[] perfectGems = {"gpr", "gpg", "gpb", "gpw", "gpy", "gpv", "skz"};
    for (int i = 0; i < flawlessGems.length; i++) {
      CubeRecipe recipe = new CubeRecipe(130 + i, CubeRecipeType.GEM_UPGRADE);
      recipe.inputs = new CubeRecipe.CubeInput[] {
          new CubeRecipe.CubeInput(flawlessGems[i], 3)
      };
      recipe.inputCount = 3;
      recipe.output.itemCode = perfectGems[i];
      recipe.output.quantity = 1;
      registerRecipe(recipe);
    }
  }

  /**
   * 注册符文升级配方
   */
  private void registerRuneUpgradeRecipes() {
    // El(1) ~ Thul(10): 3个升1级
    for (int i = 1; i <= 9; i++) {
      CubeRecipe recipe = new CubeRecipe(200 + i, CubeRecipeType.RUNE_UPGRADE);
      recipe.inputs = new CubeRecipe.CubeInput[] {
          new CubeRecipe.CubeInput("r" + String.format("%02d", i), 3)
      };
      recipe.inputCount = 3;
      recipe.output.itemCode = "r" + String.format("%02d", i + 1);
      recipe.output.quantity = 1;
      registerRecipe(recipe);
    }

    // Amn(11) ~ Fal(19): 3个+碎裂宝石升1级
    // 简化：仍使用3个升1级
    for (int i = 11; i <= 19; i++) {
      CubeRecipe recipe = new CubeRecipe(200 + i, CubeRecipeType.RUNE_UPGRADE);
      recipe.inputs = new CubeRecipe.CubeInput[] {
          new CubeRecipe.CubeInput("r" + String.format("%02d", i), 3)
      };
      recipe.inputCount = 3;
      recipe.output.itemCode = "r" + String.format("%02d", i + 1);
      recipe.output.quantity = 1;
      registerRecipe(recipe);
    }

    // Lem(20) ~ Zod(33): 2个+宝石升1级
    // 简化：仍使用2个升1级
    for (int i = 20; i <= 32; i++) {
      CubeRecipe recipe = new CubeRecipe(200 + i, CubeRecipeType.RUNE_UPGRADE);
      recipe.inputs = new CubeRecipe.CubeInput[] {
          new CubeRecipe.CubeInput("r" + String.format("%02d", i), 2)
      };
      recipe.inputCount = 2;
      recipe.output.itemCode = "r" + String.format("%02d", i + 1);
      recipe.output.quantity = 1;
      registerRecipe(recipe);
    }
  }

  /**
   * 注册药水合成配方
   */
  private void registerPotionRecipes() {
    // 3个小药水 -> 1个大药水
    CubeRecipe recipe1 = new CubeRecipe(300, CubeRecipeType.POTION_COMBINE);
    recipe1.inputs = new CubeRecipe.CubeInput[] {
        new CubeRecipe.CubeInput("hp1", 3)
    };
    recipe1.inputCount = 3;
    recipe1.output.itemCode = "hp2";
    registerRecipe(recipe1);

    CubeRecipe recipe2 = new CubeRecipe(301, CubeRecipeType.POTION_COMBINE);
    recipe2.inputs = new CubeRecipe.CubeInput[] {
        new CubeRecipe.CubeInput("mp1", 3)
    };
    recipe2.inputCount = 3;
    recipe2.output.itemCode = "mp2";
    registerRecipe(recipe2);

    // 3个体力药水 -> 1个满体力药水
    CubeRecipe recipe3 = new CubeRecipe(302, CubeRecipeType.POTION_COMBINE);
    recipe3.inputs = new CubeRecipe.CubeInput[] {
        new CubeRecipe.CubeInput("vps", 3)
    };
    recipe3.inputCount = 3;
    recipe3.output.itemCode = "rvl";
    registerRecipe(recipe3);
  }

  /**
   * 注册其他常用配方
   */
  private void registerMiscRecipes() {
    // 洗点配方：Wirt's Leg + Tome of Town Portal -> 牛关传送门
    CubeRecipe cowRecipe = new CubeRecipe(400, CubeRecipeType.QUEST);
    cowRecipe.inputs = new CubeRecipe.CubeInput[] {
        new CubeRecipe.CubeInput("leg", 1),
        new CubeRecipe.CubeInput("tbk", 1)
    };
    cowRecipe.inputCount = 2;
    cowRecipe.output.itemCode = "cow_portal";
    cowRecipe.output.special = 1; // 特殊：开启牛关
    registerRecipe(cowRecipe);

    // 洗点 Token：四种精华合成
    CubeRecipe tokenRecipe = new CubeRecipe(401, CubeRecipeType.TOKEN);
    tokenRecipe.inputs = new CubeRecipe.CubeInput[] {
        new CubeRecipe.CubeInput("tes", 1), // Twisted Essence of Suffering
        new CubeRecipe.CubeInput("ceh", 1), // Charged Essense of Hatred
        new CubeRecipe.CubeInput("bet", 1), // Burning Essence of Terror
        new CubeRecipe.CubeInput("fed", 1)  // Festering Essence of Destruction
    };
    tokenRecipe.inputCount = 4;
    tokenRecipe.output.itemCode = "toa"; // Token of Absolution
    registerRecipe(tokenRecipe);
  }

  //==========================================================================
  // 配方匹配
  //==========================================================================

  /**
   * 查找匹配的配方
   * 
   * @param itemCodes 方块中的物品代码列表
   * @param playerLevel 玩家等级
   * @param difficulty 当前难度
   * @return 匹配的配方，或 null
   */
  public CubeRecipe findMatchingRecipe(Array<String> itemCodes, int playerLevel, int difficulty) {
    for (CubeRecipe recipe : recipes) {
      if (!recipe.enabled) {
        continue;
      }

      // 检查难度限制
      if (recipe.difficultyReq >= 0 && difficulty < recipe.difficultyReq) {
        continue;
      }

      // 检查等级限制
      if (playerLevel < recipe.levelReq) {
        continue;
      }

      // 检查输入匹配
      if (matchesInputs(recipe, itemCodes)) {
        return recipe;
      }
    }

    return null;
  }

  /**
   * 检查物品是否匹配配方输入
   */
  private boolean matchesInputs(CubeRecipe recipe, Array<String> itemCodes) {
    // 创建物品计数
    ObjectMap<String, Integer> itemCounts = new ObjectMap<>();
    for (String code : itemCodes) {
      itemCounts.put(code, itemCounts.get(code, 0) + 1);
    }

    // 检查每个输入是否满足
    for (CubeRecipe.CubeInput input : recipe.inputs) {
      int available = itemCounts.get(input.itemCode, 0);
      if (available < input.quantity) {
        return false;
      }
    }

    // 检查是否有多余物品（某些配方需要精确匹配）
    int totalRequired = 0;
    for (CubeRecipe.CubeInput input : recipe.inputs) {
      totalRequired += input.quantity;
    }

    return itemCodes.size == totalRequired;
  }

  //==========================================================================
  // 合成执行
  //==========================================================================

  /**
   * 执行合成
   * 
   * @param itemCodes 方块中的物品代码列表
   * @param playerId 玩家 ID
   * @param playerLevel 玩家等级
   * @param difficulty 当前难度
   * @return 合成结果（输出物品代码和数量）
   */
  public TransmuteResult transmute(Array<String> itemCodes, int playerId, 
      int playerLevel, int difficulty) {

    // 查找匹配配方
    CubeRecipe recipe = findMatchingRecipe(itemCodes, playerLevel, difficulty);

    if (recipe == null) {
      log.debug("No matching recipe found for items: {}", itemCodes);
      if (transmuteCallback != null) {
        transmuteCallback.onTransmuteFailed("No matching recipe", playerId);
      }
      return null;
    }

    // 创建结果
    TransmuteResult result = new TransmuteResult();
    result.recipe = recipe;
    result.outputItemCode = recipe.output.itemCode;
    result.outputQuantity = recipe.output.quantity;
    result.success = true;

    log.debug("Transmute successful: {}", recipe);

    if (transmuteCallback != null) {
      transmuteCallback.onTransmuteSuccess(recipe, playerId);
    }

    return result;
  }

  //==========================================================================
  // 结果类
  //==========================================================================

  /**
   * 合成结果
   */
  public static class TransmuteResult {
    public boolean success;
    public CubeRecipe recipe;
    public String outputItemCode;
    public int outputQuantity;
    public String errorMessage;
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 获取所有配方
   */
  public Array<CubeRecipe> getAllRecipes() {
    return recipes;
  }

  /**
   * 获取指定类型的配方
   */
  public Array<CubeRecipe> getRecipesByType(int type) {
    return recipesByType.get(type, new Array<>());
  }

  /**
   * 获取配方数量
   */
  public int getRecipeCount() {
    return recipes.size;
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  public void setTransmuteCallback(CubeTransmuteCallback callback) {
    this.transmuteCallback = callback;
  }

  /**
   * 清除所有配方
   */
  public void clearRecipes() {
    recipes.clear();
    recipesByType.clear();
  }
}
