package com.riiablo.engine.client.automap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NativeAutomapGlyphTest {
  @Test
  void usesReferencePlayerNpcSilhouette() {
    assertEquals(20, NativeAutomapGlyph.WIDTH);
    assertEquals(10, NativeAutomapGlyph.HEIGHT);
    assertTrue(NativeAutomapGlyph.isSet(5, 0));
    assertTrue(NativeAutomapGlyph.isSet(0, 2));
    assertTrue(NativeAutomapGlyph.isSet(19, 2));
    assertTrue(NativeAutomapGlyph.isSet(3, 9));
    assertFalse(NativeAutomapGlyph.isSet(0, 0));
    assertFalse(NativeAutomapGlyph.isSet(10, 5));
  }
}
