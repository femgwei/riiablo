package com.riiablo.drlg;

/**
 * DRLG 全局上下文（简化版），对应 D2MOO 的 D2DrlgStrc 里的一些全局字段。
 *
 * 目前仅用于调试和结构搭建，不参与正式逻辑判断。
 */
public class DrlgContext {
  /** 随机种子（来自存档 mapSeed 或客户端种子） */
  public final int seed;
  /** 难度（0/1/2 = Normal/Nightmare/Hell） */
  public final int diff;
  /** Act 编号（0-4） */
  public final int act;

  public DrlgContext(int seed, int diff, int act) {
    this.seed = seed;
    this.diff = diff;
    this.act = act;
  }

  @Override
  public String toString() {
    return "DrlgContext{" +
        "seed=" + seed +
        ", diff=" + diff +
        ", act=" + act +
        '}';
  }
}

