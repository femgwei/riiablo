package com.riiablo.engine.client.automap;

/**
 * 小地图单元格数据
 * 表示小地图上的单个显示元素
 * 
 * 参考: D2MOO D2AutomapCellStrc
 */
public class AutomapCell {
  
  /** 是否已保存（用于持久化） */
  public boolean saved;
  
  /** 单元格编号/图标帧索引 */
  public final int cellNo;
  
  /** X像素坐标（相对于小地图） */
  public final int xPixel;
  
  /** Y像素坐标（相对于小地图） */
  public final int yPixel;
  
  /** 权重值（用于排序/优先级） */
  public int weight;
  
  public AutomapCell(int cellNo, int xPixel, int yPixel) {
    this.cellNo = cellNo;
    this.xPixel = xPixel;
    this.yPixel = yPixel;
    this.weight = 0;
    this.saved = false;
  }
  
  public AutomapCell(int cellNo, int xPixel, int yPixel, int weight) {
    this.cellNo = cellNo;
    this.xPixel = xPixel;
    this.yPixel = yPixel;
    this.weight = weight;
    this.saved = false;
  }
  
  @Override
  public String toString() {
    return "AutomapCell{cellNo=" + cellNo + ", x=" + xPixel + ", y=" + yPixel + "}";
  }
}
