package com.riiablo.widget;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * A simple progress bar widget for displaying experience or other values.
 * Supports segmented and smooth fills with customizable colors.
 */
public class ProgressBar extends Widget {

  private float value;
  private float maxValue;
  private int segments;
  private int segmentSpacing;

  private Drawable background;
  private Drawable foreground;
  private Drawable segment;

  private float fillWidth;
  private float segmentWidth;

  /**
   * Create a horizontal progress bar
   * @param width Total width of the progress bar
   * @param height Height of the progress bar
   * @param segments Number of segments (0 for smooth), -1 for auto-segmented
   */
  public ProgressBar(float width, int height, int segments) {
    this.segments = segments;
    this.value = 0;
    this.maxValue = 100;
    this.segmentSpacing = 1;

    setWidth(width);
    setHeight(height);
    calculateFillWidth();
  }

  /**
   * Set the background drawable
   */
  public void setBackgroundDrawable(Drawable background) {
    this.background = background;
  }

  /**
   * Set the foreground drawable (the filled part for smooth bars)
   */
  public void setForegroundDrawable(Drawable foreground) {
    this.foreground = foreground;
  }

  /**
   * Set a segment drawable (for segmented progress bars)
   */
  public void setSegmentDrawable(Drawable segment) {
    this.segment = segment;
  }

  /**
   * Set the current value
   */
  public void setValue(float value) {
    this.value = Math.max(0, Math.min(value, maxValue));
    calculateFillWidth();
  }

  /**
   * Set the maximum value
   */
  public void setMaxValue(float maxValue) {
    this.maxValue = Math.max(1, maxValue);
    calculateFillWidth();
  }

  /**
   * Set both value and max value
   */
  public void setRange(float value, float maxValue) {
    this.maxValue = Math.max(1, maxValue);
    this.value = Math.max(0, Math.min(value, maxValue));
    calculateFillWidth();
  }

  private void calculateFillWidth() {
    float totalWidth = getWidth();
    if (maxValue > 0) {
      float percentage = value / maxValue;
      if (segments > 0) {
        // Fixed number of segments
        float availableWidth = totalWidth - (segments + 1) * segmentSpacing;
        segmentWidth = availableWidth / segments;
        int filledSegments = (int) (percentage * segments + 0.5f);
        fillWidth = filledSegments * (segmentWidth + segmentSpacing);
      } else if (segments < 0) {
        // Auto-segmented based on value
        int numSegments = Math.max(1, (int) maxValue);
        float availableWidth = totalWidth - (numSegments + 1) * segmentSpacing;
        segmentWidth = availableWidth / numSegments;
        int filledSegments = (int) (value + 0.5f);
        fillWidth = filledSegments * (segmentWidth + segmentSpacing);
      } else {
        // Smooth fill
        fillWidth = totalWidth * percentage;
      }
    } else {
      fillWidth = 0;
    }
  }

  @Override
  public void draw(Batch batch, float parentAlpha) {
    float x = getX();
    float y = getY();
    float w = getWidth();
    float h = getHeight();

    // Draw background
    if (background != null) {
      background.draw(batch, x, y, w, h);
    }

    // Draw foreground
    if (fillWidth > 0) {
      if (segment != null && segments != 0) {
        // Draw individual segments
        float currentX = x + segmentSpacing;
        int maxSegments = segments > 0 ? segments : (int) maxValue;
        for (int i = 0; i < maxSegments; i++) {
          if (currentX + segmentWidth < x + fillWidth) {
            segment.draw(batch, currentX, y + 1, segmentWidth, h - 2);
          }
          currentX += segmentWidth + segmentSpacing;
        }
      } else if (foreground != null) {
        // Draw smooth fill
        foreground.draw(batch, x, y, fillWidth, h);
      }
    }
  }

  /**
   * Get the current value
   */
  public float getValue() {
    return value;
  }

  /**
   * Get the maximum value
   */
  public float getMaxValue() {
    return maxValue;
  }

  /**
   * Get the progress as a percentage (0.0 to 1.0)
   */
  public float getPercentage() {
    return maxValue > 0 ? value / maxValue : 0;
  }
}
