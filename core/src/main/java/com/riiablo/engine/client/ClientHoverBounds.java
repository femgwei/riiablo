package com.riiablo.engine.client;

/** Read-only screen-space bounds supplied by the presentation hover adapter. */
public final class ClientHoverBounds {
  public final float xMin;
  public final float yMax;
  public final float width;
  public final float height;

  public ClientHoverBounds(float xMin, float yMax, float width, float height) {
    this.xMin = xMin;
    this.yMax = yMax;
    this.width = width;
    this.height = height;
  }
}
