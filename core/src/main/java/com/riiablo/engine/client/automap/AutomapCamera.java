package com.riiablo.engine.client.automap;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;

import com.riiablo.camera.IsometricCamera;

/**
 * Automap 专用摄像头
 * 
 * <p>用于控制小地图的视角和缩放。与主摄像头分离，但默认跟随主摄像头位置。
 * 
 * <p>特性：
 * <ul>
 *   <li>独立的缩放控制 (zoom) - 通过 +/- 键调整</li>
 *   <li>相对偏移 (offset) - 通过方向键调整，相对于主摄像头的偏移</li>
 *   <li>跟随主摄像头 - 每帧更新时同步主摄像头位置 + 偏移</li>
 * </ul>
 * 
 * @author riiablo team
 */
public class AutomapCamera extends OrthographicCamera {
  private static final String TAG = "AutomapCamera";
  
  //==========================================================================
  // 常量
  //==========================================================================
  
  /** 默认 Automap DC6 缩放值。 */
  public static final float DEFAULT_ZOOM = 0.5f;
  
  /** 最小缩放值 (显示最大范围) */
  public static final float MIN_ZOOM = 0.1f;
  
  /** 最大缩放值 (显示最小范围，最详细) */
  public static final float MAX_ZOOM = 2.0f;
  
  /** 缩放步进值 */
  public static final float ZOOM_STEP = 0.1f;
  
  /** 平移步进值（世界子格坐标） */
  public static final float PAN_STEP = 2.0f;
  
  //==========================================================================
  // 字段
  //==========================================================================
  
  /** 相对主摄像头的 X 偏移 (世界坐标) */
  private float offsetX = 0f;
  
  /** 相对主摄像头的 Y 偏移 (世界坐标) */
  private float offsetY = 0f;
  
  /** 主摄像头引用 */
  private IsometricCamera mainCamera;
  
  /** 当前缩放值 */
  private float automapZoom = DEFAULT_ZOOM;

  /** Whether the camera is currently rendered through a half-size mini-map viewport. */
  private boolean miniMapMode;
  
  /** 是否已初始化 */
  private boolean initialized = false;

  /** 主世界子格坐标转换后的原生 Automap 像素位置。 */
  private final Vector2 projectedPosition = new Vector2();
  
  //==========================================================================
  // 构造函数
  //==========================================================================
  
  public AutomapCamera() {
    super();
  }
  
  /**
   * 初始化 automap 摄像头
   * 
   * @param mainCamera 主摄像头引用
   */
  public void initialize(IsometricCamera mainCamera) {
    this.mainCamera = mainCamera;
    
    // 复制主摄像头的视口设置
    this.viewportWidth = mainCamera.viewportWidth;
    this.viewportHeight = mainCamera.viewportHeight;
    this.near = mainCamera.near;
    this.far = mainCamera.far;
    
    // 设置初始缩放
    this.zoom = effectiveZoom();
    
    // 同步位置
    syncWithMainCamera();
    
    this.initialized = true;
    
    if (Gdx.app != null) {
      Gdx.app.log(TAG, "AutomapCamera initialized: zoom=" + automapZoom
          + ", viewport=" + viewportWidth + "x" + viewportHeight);
    }
  }
  
  /**
   * 检查是否已初始化
   */
  public boolean isInitialized() {
    return initialized;
  }
  
  //==========================================================================
  // 更新方法
  //==========================================================================
  
  /**
   * 每帧更新 - 同步主摄像头位置 + 偏移
   * 
   * <p>应在主摄像头更新后调用
   */
  public void syncWithMainCamera() {
    if (mainCamera == null) return;
    
    // Automap DC6 cells use an 8x4 pixel diamond per DT1 tile, not the
    // gameplay renderer's 160x80 tile projection. Project the main camera's
    // world-subtile position before assigning the orthographic camera.
    AutomapProjection.worldToAutomap(
        mainCamera.position.x + offsetX,
        mainCamera.position.y + offsetY,
        projectedPosition);
    this.position.set(projectedPosition.x, projectedPosition.y, 0f);
    
    // 设置缩放
    this.zoom = effectiveZoom();
    
    // 更新投影矩阵
    this.update();
  }
  
  /**
   * 调整视口大小（响应屏幕大小变化）
   */
  public void resize(int width, int height) {
    this.viewportWidth = width;
    this.viewportHeight = height;
    this.update();
  }
  
  //==========================================================================
  // 缩放控制
  //==========================================================================
  
  /**
   * 放大 (增加 zoom 值，显示更详细的小范围)
   */
  public void zoomIn() {
    automapZoom = Math.min(MAX_ZOOM, automapZoom + ZOOM_STEP);
    Gdx.app.debug(TAG, "Zoom in: " + automapZoom);
  }
  
  /**
   * 缩小 (减少 zoom 值，显示更大范围)
   */
  public void zoomOut() {
    automapZoom = Math.max(MIN_ZOOM, automapZoom - ZOOM_STEP);
    Gdx.app.debug(TAG, "Zoom out: " + automapZoom);
  }
  
  /**
   * 重置缩放到默认值
   */
  public void resetZoom() {
    automapZoom = DEFAULT_ZOOM;
    Gdx.app.debug(TAG, "Zoom reset: " + automapZoom);
  }
  
  /**
   * 获取当前缩放值
   */
  public float getAutomapZoom() {
    return automapZoom;
  }
  
  /**
   * 设置缩放值
   */
  public void setAutomapZoom(float zoom) {
    this.automapZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
  }

  /** Selects full-screen or half-size viewport projection. */
  public void setMiniMapMode(boolean miniMapMode) {
    this.miniMapMode = miniMapMode;
    this.zoom = effectiveZoom();
    if (initialized) update();
  }

  public boolean isMiniMapMode() {
    return miniMapMode;
  }

  private float effectiveZoom() {
    // AutomapRenderer applies a 0.5 viewport in mini-map mode while retaining
    // the camera's native viewport dimensions.  Keeping the same zoom makes
    // every native DC6 cell, terrain edge, and entity marker exactly 1/2 the
    // full-screen pixel size.  Multiplying zoom here would compound the
    // viewport reduction and incorrectly shrink content to 1/4.
    return automapZoom;
  }
  
  //==========================================================================
  // 平移控制 (相对偏移)
  //==========================================================================
  
  /**
   * 向上平移 (增加 Y 偏移)
   */
  public void panUp() {
    offsetY += PAN_STEP;
    Gdx.app.debug(TAG, "Pan up: offsetY=" + offsetY);
  }
  
  /**
   * 向下平移 (减少 Y 偏移)
   */
  public void panDown() {
    offsetY -= PAN_STEP;
    Gdx.app.debug(TAG, "Pan down: offsetY=" + offsetY);
  }
  
  /**
   * 向左平移 (减少 X 偏移)
   */
  public void panLeft() {
    offsetX -= PAN_STEP;
    Gdx.app.debug(TAG, "Pan left: offsetX=" + offsetX);
  }
  
  /**
   * 向右平移 (增加 X 偏移)
   */
  public void panRight() {
    offsetX += PAN_STEP;
    Gdx.app.debug(TAG, "Pan right: offsetX=" + offsetX);
  }
  
  /**
   * 重置偏移 (回到与主摄像头重合)
   */
  public void resetOffset() {
    offsetX = 0f;
    offsetY = 0f;
    Gdx.app.debug(TAG, "Offset reset");
  }
  
  /**
   * 重置所有设置 (缩放和偏移)
   */
  public void reset() {
    resetZoom();
    resetOffset();
    Gdx.app.debug(TAG, "AutomapCamera reset: zoom=" + automapZoom + ", offset=(" + offsetX + ", " + offsetY + ")");
  }
  
  //==========================================================================
  // Getters
  //==========================================================================
  
  public float getOffsetX() {
    return offsetX;
  }
  
  public float getOffsetY() {
    return offsetY;
  }
  
  public IsometricCamera getMainCamera() {
    return mainCamera;
  }
}
