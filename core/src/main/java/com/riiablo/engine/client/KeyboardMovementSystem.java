package com.riiablo.engine.client;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.Keys;
import com.riiablo.engine.server.Actioneer;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.Velocity;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.map.RenderSystem;

/**
 * 键盘移动系统 - 支持方向键控制玩家移动
 * 
 * <p>该系统允许玩家使用键盘方向键来控制角色移动。
 * 这是对原有鼠标点击移动的补充，提供更直观的控制方式。
 * 
 * <p>移动方向采用等距视角（isometric）坐标转换：
 * <ul>
 *   <li>上箭头 - 向屏幕上方移动（实际是地图的东北方向）</li>
 *   <li>下箭头 - 向屏幕下方移动（实际是地图的西南方向）</li>
 *   <li>左箭头 - 向屏幕左方移动（实际是地图的西北方向）</li>
 *   <li>右箭头 - 向屏幕右方移动（实际是地图的东南方向）</li>
 * </ul>
 * 
 * <p>支持8方向移动（组合按键）。
 * 
 * <p>注意：默认不启用 WASD 移动，因为这些键已被其他功能占用：
 * <ul>
 *   <li>W - 武器切换 (SwapWeapons)</li>
 *   <li>A - 角色面板 (Character)</li>
 *   <li>S - 技能面板 (Spells)</li>
 * </ul>
 * 
 * @author riiablo team
 */
public class KeyboardMovementSystem extends BaseSystem {
  private static final Logger log = LogManager.getLogger(KeyboardMovementSystem.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 移动距离（每帧的移动目标偏移） */
  private static final float MOVE_DISTANCE = 4.0f;

  //==========================================================================
  // 组件映射器
  //==========================================================================

  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Velocity> mVelocity;

  //==========================================================================
  // 依赖系统
  //==========================================================================

  protected RenderSystem renderer;
  protected Actioneer actioneer;

  //==========================================================================
  // 字段
  //==========================================================================

  /** 临时向量，避免每帧创建对象 */
  private final Vector2 tmpDirection = new Vector2();
  private final Vector2 tmpTarget = new Vector2();

  /** 是否启用键盘移动 */
  private boolean enabled = true;

  /** 上一帧是否在移动 */
  private boolean wasMoving = false;

  /** 是否启用 WASD 移动（默认关闭，因为与其他功能冲突） */
  private boolean wasdEnabled = false;

  /** 是否启用小键盘数字移动（8方向） */
  private boolean numpadEnabled = true;

  //==========================================================================
  // 系统方法
  //==========================================================================

  @Override
  protected void processSystem() {
    if (!enabled) {
      return;
    }

    int playerId = renderer.getSrc();
    if (playerId < 0) {
      return;
    }

    // 检查玩家是否可以移动
    if (!actioneer.canInterrupt(playerId)) {
      return;
    }

    // 读取方向输入
    float dirX = 0;
    float dirY = 0;

    // 检查方向键
    if (isUpPressed()) {
      dirY += 1;
    }
    if (isDownPressed()) {
      dirY -= 1;
    }
    if (isLeftPressed()) {
      dirX -= 1;
    }
    if (isRightPressed()) {
      dirX += 1;
    }

    // 如果有方向输入
    if (dirX != 0 || dirY != 0) {
      // 归一化方向向量
      tmpDirection.set(dirX, dirY).nor();

      // 转换为等距视角方向
      // 屏幕坐标到世界坐标的转换需要旋转45度
      // 屏幕上方对应地图的东北方向
      float worldDirX = tmpDirection.x - tmpDirection.y;
      float worldDirY = tmpDirection.x + tmpDirection.y;
      tmpDirection.set(worldDirX, worldDirY).nor();

      // 获取当前位置
      Position position = mPosition.get(playerId);
      if (position != null) {
        // 计算目标位置
        tmpTarget.set(position.position)
            .add(tmpDirection.x * MOVE_DISTANCE, tmpDirection.y * MOVE_DISTANCE);

        // 请求移动
        actioneer.moveTo(playerId, tmpTarget);

        if (!wasMoving) {
          log.debug("Started keyboard movement: dir=({},{})", tmpDirection.x, tmpDirection.y);
        }
        wasMoving = true;
      }
    } else if (wasMoving) {
      // 停止移动
      log.debug("Stopped keyboard movement");
      wasMoving = false;
    }
  }

  //==========================================================================
  // 输入检测
  //==========================================================================

  /**
   * 检查向上移动是否按下
   * 注意：方向键不用于移动人物，仅用于小地图平移
   */
  private boolean isUpPressed() {
    // WASD - W 键（仅当启用时）
    if (wasdEnabled && Gdx.input.isKeyPressed(Input.Keys.W)) {
      return true;
    }
    // 小键盘 8 键
    if (numpadEnabled && Gdx.input.isKeyPressed(Input.Keys.NUMPAD_8)) {
      return true;
    }
    return false;
  }

  /**
   * 检查向下移动是否按下
   * 注意：方向键不用于移动人物，仅用于小地图平移
   */
  private boolean isDownPressed() {
    // WASD - S 键（仅当启用时）
    if (wasdEnabled && Gdx.input.isKeyPressed(Input.Keys.S)) {
      return true;
    }
    // 小键盘 2 键
    if (numpadEnabled && Gdx.input.isKeyPressed(Input.Keys.NUMPAD_2)) {
      return true;
    }
    return false;
  }

  /**
   * 检查向左移动是否按下
   * 注意：方向键不用于移动人物，仅用于小地图平移
   */
  private boolean isLeftPressed() {
    // WASD - A 键（仅当启用时）
    if (wasdEnabled && Gdx.input.isKeyPressed(Input.Keys.A)) {
      return true;
    }
    // 小键盘 4 键
    if (numpadEnabled && Gdx.input.isKeyPressed(Input.Keys.NUMPAD_4)) {
      return true;
    }
    return false;
  }

  /**
   * 检查向右移动是否按下
   * 注意：方向键不用于移动人物，仅用于小地图平移
   */
  private boolean isRightPressed() {
    // WASD - D 键（仅当启用时）
    if (wasdEnabled && Gdx.input.isKeyPressed(Input.Keys.D)) {
      return true;
    }
    // 小键盘 6 键
    if (numpadEnabled && Gdx.input.isKeyPressed(Input.Keys.NUMPAD_6)) {
      return true;
    }
    return false;
  }

  /**
   * 检查跑步键是否按下
   */
  public boolean isRunPressed() {
    return Keys.Run.isPressed();
  }

  //==========================================================================
  // 配置方法
  //==========================================================================

  /**
   * 启用/禁用键盘移动
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
    if (!enabled) {
      wasMoving = false;
    }
  }

  /**
   * 检查是否启用
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 启用/禁用 WASD 移动
   * 
   * <p>注意：启用 WASD 移动会与以下功能冲突：
   * <ul>
   *   <li>W - 武器切换</li>
   *   <li>A - 角色面板</li>
   *   <li>S - 技能面板</li>
   * </ul>
   */
  public void setWasdEnabled(boolean wasdEnabled) {
    this.wasdEnabled = wasdEnabled;
    log.debug("WASD movement {}", wasdEnabled ? "enabled" : "disabled");
  }

  /**
   * 检查 WASD 移动是否启用
   */
  public boolean isWasdEnabled() {
    return wasdEnabled;
  }

  /**
   * 启用/禁用小键盘移动
   */
  public void setNumpadEnabled(boolean numpadEnabled) {
    this.numpadEnabled = numpadEnabled;
    log.debug("Numpad movement {}", numpadEnabled ? "enabled" : "disabled");
  }

  /**
   * 检查小键盘移动是否启用
   */
  public boolean isNumpadEnabled() {
    return numpadEnabled;
  }

  /**
   * 检查当前是否正在通过键盘移动
   */
  public boolean isMoving() {
    return wasMoving;
  }
}
