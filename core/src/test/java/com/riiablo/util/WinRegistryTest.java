package com.riiablo.util;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WinRegistryTest {
  @Test
  void invalidRootDoesNotInvokeLegacyReflection() {
    assertNull(WinRegistry.readStringViaRegExe(1234, "Software\\riiablo", "Missing"));
  }
}
