package com.riiablo.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.graphics.Color;
import com.riiablo.graphics.BlendMode;
import org.junit.jupiter.api.Test;

class AnimationLayerAlphaTest {
  @Test
  void dynamicCofAlphaMultipliesNativeBlendOpacityWithoutMutatingSharedTint() {
    Color nativeTrans75 = new Color(1f, 1f, 1f, 0.25f);
    Animation.Layer layer = new Animation.Layer();

    layer.setBlendMode(BlendMode.ID, nativeTrans75);
    layer.setAlpha(1f);

    assertEquals(0.25f, layer.tint.a, 0f);
    assertEquals(0.25f, nativeTrans75.a, 0f);

    layer.setAlpha(0.5f);

    assertEquals(0.125f, layer.tint.a, 0f);
    assertEquals(0.25f, nativeTrans75.a, 0f);
  }
}
