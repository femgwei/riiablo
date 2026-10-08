package com.riiablo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ColorsTest {
  @Test
  public void nativeTransparencyNamesConvertToSourceOpacity() {
    Colors colors = new Colors();

    assertEquals(0.75f, colors.trans25.a, 0f);
    assertEquals(0.50f, colors.trans50.a, 0f);
    assertEquals(0.25f, colors.trans75.a, 0f);
  }
}
