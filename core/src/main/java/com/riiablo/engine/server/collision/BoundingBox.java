package com.riiablo.engine.server.collision;

/**
 * 边界框 - 基于 D2MOO D2BoundingBoxStrc 移植
 * 
 * <p>用于碰撞检测的轴对齐边界框（AABB）。
 * 
 * <p>参考：D2MOO/source/D2Common/include/D2Collision.h
 * 
 * @author riiablo team
 */
public class BoundingBox {

  //==========================================================================
  // 边界
  //==========================================================================

  /** 左边界 */
  public int left;
  
  /** 下边界 */
  public int bottom;
  
  /** 右边界 */
  public int right;
  
  /** 上边界 */
  public int top;

  //==========================================================================
  // 构造函数
  //==========================================================================

  /**
   * 创建空边界框
   */
  public BoundingBox() {
    reset();
  }

  /**
   * 创建边界框
   * 
   * @param left 左边界
   * @param bottom 下边界
   * @param right 右边界
   * @param top 上边界
   */
  public BoundingBox(int left, int bottom, int right, int top) {
    set(left, bottom, right, top);
  }

  /**
   * 复制边界框
   * 
   * @param other 源边界框
   */
  public BoundingBox(BoundingBox other) {
    set(other);
  }

  //==========================================================================
  // 设置方法
  //==========================================================================

  /**
   * 设置边界
   * 
   * @param left 左边界
   * @param bottom 下边界
   * @param right 右边界
   * @param top 上边界
   */
  public void set(int left, int bottom, int right, int top) {
    this.left = left;
    this.bottom = bottom;
    this.right = right;
    this.top = top;
  }

  /**
   * 从另一个边界框复制
   * 
   * @param other 源边界框
   */
  public void set(BoundingBox other) {
    this.left = other.left;
    this.bottom = other.bottom;
    this.right = other.right;
    this.top = other.top;
  }

  /**
   * 重置边界框
   */
  public void reset() {
    left = 0;
    bottom = 0;
    right = 0;
    top = 0;
  }

  /**
   * 根据中心点和大小创建边界框
   * 
   * <p>对应 D2MOO COLLISION_CreateBoundingBox
   * 
   * @param centerX 中心 X 坐标
   * @param centerY 中心 Y 坐标
   * @param sizeX X 方向大小
   * @param sizeY Y 方向大小
   */
  public void setFromCenter(int centerX, int centerY, int sizeX, int sizeY) {
    int halfX = sizeX / 2;
    int halfY = sizeY / 2;
    left = centerX - halfX;
    right = centerX + halfX;
    bottom = centerY - halfY;
    top = centerY + halfY;
  }

  //==========================================================================
  // 查询方法
  //==========================================================================

  /**
   * 获取宽度
   * 
   * @return 宽度
   */
  public int getWidth() {
    return right - left;
  }

  /**
   * 获取高度
   * 
   * @return 高度
   */
  public int getHeight() {
    return top - bottom;
  }

  /**
   * 获取中心 X
   * 
   * @return 中心 X 坐标
   */
  public int getCenterX() {
    return (left + right) / 2;
  }

  /**
   * 获取中心 Y
   * 
   * @return 中心 Y 坐标
   */
  public int getCenterY() {
    return (bottom + top) / 2;
  }

  /**
   * 获取面积
   * 
   * @return 面积
   */
  public int getArea() {
    return getWidth() * getHeight();
  }

  /**
   * 检查边界框是否有效
   * 
   * @return true 如果有效
   */
  public boolean isValid() {
    return right >= left && top >= bottom;
  }

  //==========================================================================
  // 碰撞检测
  //==========================================================================

  /**
   * 检查点是否在边界框内
   * 
   * @param x X 坐标
   * @param y Y 坐标
   * @return true 如果在内部
   */
  public boolean contains(int x, int y) {
    return x >= left && x <= right && y >= bottom && y <= top;
  }

  /**
   * 检查是否与另一个边界框相交
   * 
   * @param other 另一个边界框
   * @return true 如果相交
   */
  public boolean intersects(BoundingBox other) {
    return left <= other.right && right >= other.left &&
           bottom <= other.top && top >= other.bottom;
  }

  /**
   * 检查是否完全包含另一个边界框
   * 
   * @param other 另一个边界框
   * @return true 如果完全包含
   */
  public boolean contains(BoundingBox other) {
    return left <= other.left && right >= other.right &&
           bottom <= other.bottom && top >= other.top;
  }

  //==========================================================================
  // 操作方法
  //==========================================================================

  /**
   * 扩展边界框以包含指定点
   * 
   * @param x X 坐标
   * @param y Y 坐标
   */
  public void expand(int x, int y) {
    if (x < left) left = x;
    if (x > right) right = x;
    if (y < bottom) bottom = y;
    if (y > top) top = y;
  }

  /**
   * 扩展边界框以包含另一个边界框
   * 
   * @param other 另一个边界框
   */
  public void expand(BoundingBox other) {
    if (other.left < left) left = other.left;
    if (other.right > right) right = other.right;
    if (other.bottom < bottom) bottom = other.bottom;
    if (other.top > top) top = other.top;
  }

  /**
   * 按指定量扩展边界框
   * 
   * @param amount 扩展量
   */
  public void inflate(int amount) {
    left -= amount;
    bottom -= amount;
    right += amount;
    top += amount;
  }

  /**
   * 平移边界框
   * 
   * @param dx X 方向偏移
   * @param dy Y 方向偏移
   */
  public void translate(int dx, int dy) {
    left += dx;
    right += dx;
    bottom += dy;
    top += dy;
  }

  //==========================================================================
  // 调试信息
  //==========================================================================

  @Override
  public String toString() {
    return "BoundingBox{" +
        "left=" + left +
        ", bottom=" + bottom +
        ", right=" + right +
        ", top=" + top +
        ", width=" + getWidth() +
        ", height=" + getHeight() +
        '}';
  }
}
