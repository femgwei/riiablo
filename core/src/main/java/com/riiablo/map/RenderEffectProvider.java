package com.riiablo.map;

import com.badlogic.gdx.math.Vector2;

/** Optional client effects consumed by the map renderer without owning their implementation. */
public interface RenderEffectProvider {
  boolean hideOriginal(int entity);
  boolean ground(int entity);
  boolean orbit(int entity);
  /** Nullable screen-space offset; the renderer only reads it. */
  Vector2 screenOffset(int entity);
}
