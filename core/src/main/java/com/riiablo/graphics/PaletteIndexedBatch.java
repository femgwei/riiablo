package com.riiablo.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import com.riiablo.codec.Index;

public class PaletteIndexedBatch extends SpriteBatch {
  public static final int MAX_LOCAL_LIGHTS = 8;
  private static final int PALETTE_TEXTURE_ID  = 1;
  private static final int COLORMAP_TEXTURE_ID = 2;

  private final int PALETTE_LOCATION;
  private final int BLENDMODE_LOCATION;
  private final int COLORMAP_LOCATION;
  private final int COLORMAPID_LOCATION;
  private final int GAMMA_LOCATION;
  private final int LIGHTING_ENABLED_LOCATION;
  private final int AMBIENT_LIGHT_LOCATION;
  private final int LIGHT_COUNT_LOCATION;
  private final int LOCAL_LIGHTS_LOCATION;
  private final int LOCAL_LIGHT_COLORS_LOCATION;
  private final ShaderProgram shader;

  private Texture palette;
  private Texture colormap;
  private int blendMode;
  private int colormapId;
  private Color color = Color.WHITE.cpy();
  private float gamma = 1.0f;
  private boolean disabled = false;
  private boolean lightingEnabled;
  private final Color ambientLight = Color.WHITE.cpy();
  private int lightCount;
  private final float[] localLights = new float[MAX_LOCAL_LIGHTS * 4];
  private final float[] localLightColors = new float[MAX_LOCAL_LIGHTS * 3];

  public PaletteIndexedBatch(int size, ShaderProgram shader) {
    super(size);
    this.shader = shader;
    PALETTE_LOCATION    = shader.getUniformLocation("ColorTable");
    COLORMAP_LOCATION   = shader.getUniformLocation("ColorMap");
    BLENDMODE_LOCATION  = shader.getUniformLocation("blendMode");
    COLORMAPID_LOCATION = shader.getUniformLocation("colormapId");
    GAMMA_LOCATION      = shader.getUniformLocation("gamma");
    LIGHTING_ENABLED_LOCATION = shader.getUniformLocation("lightingEnabled");
    AMBIENT_LIGHT_LOCATION = shader.getUniformLocation("ambientLight");
    LIGHT_COUNT_LOCATION = shader.getUniformLocation("lightCount");
    LOCAL_LIGHTS_LOCATION = shader.getUniformLocation("localLights");
    LOCAL_LIGHT_COLORS_LOCATION = shader.getUniformLocation("localLightColors");
  }

  public void setPalette(Texture palette) {
    this.palette = palette;
    if (isDrawing()) {
      flush();
      applyPalette();
    }
  }

  public void setColormap(Index colormap, int id) {
    setColormap(colormap != null ? colormap.texture : null, id);
  }

  public void setColormap(Texture colormap, int id) {
    if (id == 0 || colormap == null) {
      resetColormap();
      return;
    }

    this.colormap = colormap;
    this.colormapId = id;
    if (isDrawing()) {
      flush();
      applyColormap();
    }
  }

  public void resetColormap() {
    if (isDrawing()) {
      flush();
    }

    colormapId = 0;
    shader.setUniformi(COLORMAPID_LOCATION, colormapId);
  }

  public void setBlendMode(int blendMode, Color tint) {
    setBlendMode(blendMode, tint, false);
  }

  public void setBlendMode(int blendMode, Color tint, boolean force) {
    setBlendMode(blendMode);
    setColor(tint);
  }

  public void setBlendMode(int blendMode) {
    if (this.blendMode != blendMode) {
      if (isDrawing()) flush();
      shader.setUniformi(BLENDMODE_LOCATION, blendMode);
      this.blendMode = blendMode;
    }
    // D2's poison clouds use the fixed-function Screen equation rather than
    // ordinary alpha-over. The shader keeps palette index 0 black and uses
    // luminance for the PL2 mask; ONE avoids multiplying the source colour by
    // that mask a second time, while the destination factor implements
    // 1 - source.rgb.
    if (blendMode == BlendMode.SCREEN) {
      super.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_COLOR);
    } else if (blendMode == BlendMode.ADDITIVE) {
      // D2 Trans=1 matches Unity's Legacy Particles/Additive (Soft):
      // source alpha over the destination's inverse source colour.
      super.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_COLOR);
    } else {
      super.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
  }

  public void resetBlendMode() {
    setBlendMode(BlendMode.ID, Color.WHITE);
  }

  public void setAlpha(float a) {
    color.a = a;
    setColor(color);
  }

  public void resetColor() {
    setColor(Color.WHITE);
  }

  public float getGamma() {
    return gamma;
  }

  public void setGamma(float gamma) {
    if (this.gamma != gamma) {
      this.gamma = gamma;
    }
  }

  // TODO: support flush
  public void setDisabled(boolean disabled) {
    if (this.disabled != disabled) {
      if (isDrawing()) flush();
      this.disabled = disabled;
    }
  }

  public boolean isDisabled() {
    return disabled;
  }

  /**
   * Applies outdoor ambient light and world-space elliptical local lights.
   * Every light occupies four floats (centre x/y and radius x/y); colours use
   * three floats. Arrays are copied because RenderSystem reuses its buffers.
   */
  public void setLighting(Color ambient, int count, float[] lights, float[] colors) {
    if (isDrawing()) flush();
    lightingEnabled = true;
    ambientLight.set(ambient);
    lightCount = Math.max(0, Math.min(MAX_LOCAL_LIGHTS, count));
    if (lightCount > 0) {
      System.arraycopy(lights, 0, localLights, 0, lightCount * 4);
      System.arraycopy(colors, 0, localLightColors, 0, lightCount * 3);
    }
    applyLighting();
  }

  public void resetLighting() {
    if (isDrawing()) flush();
    lightingEnabled = false;
    ambientLight.set(Color.WHITE);
    lightCount = 0;
    applyLighting();
  }

  @Override
  public void begin() {
    if (disabled) {
      super.begin();
      return;
    }

    setShader(shader);
    super.begin();
    resetBlendMode();
    resetColormap();
    applyPalette();
    applyGamma();
    applyLighting();
  }

  public void begin(Texture palette) {
    setPalette(palette);
    begin();
  }

  private void applyPalette() {
    if (palette == null) return;
    palette.bind(PALETTE_TEXTURE_ID);
    shader.setUniformi(PALETTE_LOCATION, PALETTE_TEXTURE_ID);
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
  }

  private void applyColormap() {
    colormap.bind(COLORMAP_TEXTURE_ID);
    shader.setUniformi(COLORMAP_LOCATION, COLORMAP_TEXTURE_ID);
    Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
    shader.setUniformi(COLORMAPID_LOCATION, colormapId);
  }

  private void applyGamma() {
    shader.setUniformf(GAMMA_LOCATION, gamma);
  }

  private void applyLighting() {
    shader.setUniformi(LIGHTING_ENABLED_LOCATION, lightingEnabled ? 1 : 0);
    shader.setUniformf(AMBIENT_LIGHT_LOCATION,
        ambientLight.r, ambientLight.g, ambientLight.b);
    shader.setUniformi(LIGHT_COUNT_LOCATION, lightCount);
    if (lightCount > 0) {
      shader.setUniform4fv(LOCAL_LIGHTS_LOCATION, localLights, 0, lightCount * 4);
      shader.setUniform3fv(LOCAL_LIGHT_COLORS_LOCATION,
          localLightColors, 0, lightCount * 3);
    }
  }
}
