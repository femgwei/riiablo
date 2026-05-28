package com.riiablo.engine.server.cube;

/**
 * 赫拉迪克方块配方 - 基于 D2MOD HoradricCube.h 移植
 * 
 * <p>表示单个配方的定义。
 * 
 * <p>参考：D2MOD/source/D2Common/src/DataTbls/HoradricCube.cpp
 * 
 * @author riiablo team
 */
public class CubeRecipe {

  //==========================================================================
  // 配方定义
  //==========================================================================

  /** 配方 ID */
  public int recipeId;

  /** 配方类型 */
  public int recipeType;

  /** 配方启用 */
  public boolean enabled;

  /** 配方难度限制（-1=无限制） */
  public int difficultyReq;

  /** 配方等级限制 */
  public int levelReq;

  //==========================================================================
  // 输入物品
  //==========================================================================

  /** 输入物品定义 */
  public CubeInput[] inputs;

  /** 需要的输入数量（自动计算） */
  public int inputCount;

  //==========================================================================
  // 输出物品
  //==========================================================================

  /** 输出物品定义 */
  public CubeOutput output;

  //==========================================================================
  // 构造函数
  //==========================================================================

  public CubeRecipe() {
    recipeId = -1;
    recipeType = CubeRecipeType.NONE;
    enabled = true;
    difficultyReq = -1;
    levelReq = 0;
    inputs = new CubeInput[0];
    inputCount = 0;
    output = new CubeOutput();
  }

  public CubeRecipe(int recipeId, int recipeType) {
    this();
    this.recipeId = recipeId;
    this.recipeType = recipeType;
  }

  //==========================================================================
  // 内部类：输入物品定义
  //==========================================================================

  /**
   * 输入物品定义
   */
  public static class CubeInput {
    /** 物品类型代码（如 "gem", "rune", "r01" 等） */
    public String itemCode;

    /** 需要数量 */
    public int quantity;

    /** 物品品质要求（-1=任意） */
    public int qualityReq;

    /** 是否需要凹槽 */
    public boolean socketedReq;

    /** 是否需要以太 */
    public boolean etherealReq;

    /** 参数（用于特殊匹配） */
    public int param;

    public CubeInput() {
      itemCode = "";
      quantity = 1;
      qualityReq = -1;
      socketedReq = false;
      etherealReq = false;
      param = 0;
    }

    public CubeInput(String itemCode, int quantity) {
      this();
      this.itemCode = itemCode;
      this.quantity = quantity;
    }
  }

  //==========================================================================
  // 内部类：输出物品定义
  //==========================================================================

  /**
   * 输出物品定义
   */
  public static class CubeOutput {
    /** 输出物品代码（"usesitem"=使用输入物品） */
    public String itemCode;

    /** 输出数量 */
    public int quantity;

    /** 输出物品品质 */
    public int quality;

    /** 是否保留输入物品的属性 */
    public boolean keepMods;

    /** 是否保留输入物品的凹槽 */
    public boolean keepSockets;

    /** 是否添加凹槽 */
    public boolean addSockets;

    /** 添加凹槽数量 */
    public int socketCount;

    /** 是否以太 */
    public boolean ethereal;

    /** 特殊处理 */
    public int special;

    public CubeOutput() {
      itemCode = "";
      quantity = 1;
      quality = -1;
      keepMods = false;
      keepSockets = false;
      addSockets = false;
      socketCount = 0;
      ethereal = false;
      special = 0;
    }
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 获取配方描述
   */
  public String getDescription() {
    StringBuilder sb = new StringBuilder();
    sb.append("Recipe #").append(recipeId);
    sb.append(" (").append(CubeRecipeType.getName(recipeType)).append(")");
    sb.append(": ");

    for (int i = 0; i < inputs.length; i++) {
      if (i > 0) sb.append(" + ");
      CubeInput input = inputs[i];
      if (input.quantity > 1) {
        sb.append(input.quantity).append("x ");
      }
      sb.append(input.itemCode);
    }

    sb.append(" = ");
    if (output.quantity > 1) {
      sb.append(output.quantity).append("x ");
    }
    sb.append(output.itemCode);

    return sb.toString();
  }

  @Override
  public String toString() {
    return getDescription();
  }
}
