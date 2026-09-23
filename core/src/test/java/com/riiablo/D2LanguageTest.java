package com.riiablo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class D2LanguageTest {
  @Test
  void commandLineOverridesCvarAndSystemLocale() {
    assertEquals(D2Language.ENGLISH,
        D2Language.resolve("en", "zh", Locale.SIMPLIFIED_CHINESE));
  }

  @Test
  void cvarOverridesSystemLocaleWhenCommandLineIsEmpty() {
    assertEquals(D2Language.CHINESE,
        D2Language.resolve("", "zh", Locale.ENGLISH));
  }

  @Test
  void emptyOverridesFallBackToSystemLocale() {
    assertEquals(D2Language.CHINESE,
        D2Language.resolve("", "", Locale.SIMPLIFIED_CHINESE));
    assertEquals(D2Language.ENGLISH,
        D2Language.resolve(null, null, Locale.ENGLISH));
  }
}
