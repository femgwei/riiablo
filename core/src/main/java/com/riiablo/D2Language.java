package com.riiablo;

import java.util.Locale;

/** Diablo II resource-language profile used by strings, fonts, and Riiablo UI text. */
public enum D2Language {
  ENGLISH("eng", "latin", Locale.ENGLISH),
  CHINESE("chi", "chi", Locale.TRADITIONAL_CHINESE);

  public final String resourceCode;
  public final String fontDirectory;
  public final Locale locale;

  D2Language(String resourceCode, String fontDirectory, Locale locale) {
    this.resourceCode = resourceCode;
    this.fontDirectory = fontDirectory;
    this.locale = locale;
  }

  public static D2Language resolve(String requested, Locale systemLocale) {
    if (requested != null) {
      String normalized = requested.trim().replace('-', '_').toLowerCase(Locale.ROOT);
      if (normalized.equals("chi") || normalized.equals("zh")
          || normalized.equals("zh_cn") || normalized.equals("zh_tw")
          || normalized.equals("chinese")) {
        return CHINESE;
      }
      if (normalized.equals("eng") || normalized.equals("en")
          || normalized.equals("en_us") || normalized.equals("english")) {
        return ENGLISH;
      }
    }
    return systemLocale != null && "zh".equalsIgnoreCase(systemLocale.getLanguage())
        ? CHINESE
        : ENGLISH;
  }

  /**
   * Resolves language with the runtime precedence used by the client:
   * command-line override, persisted Client.Language cvar, then system locale.
   */
  public static D2Language resolve(String commandLine, String configured, Locale systemLocale) {
    if (commandLine != null && !commandLine.trim().isEmpty()) {
      return resolve(commandLine, systemLocale);
    }
    if (configured != null && !configured.trim().isEmpty()) {
      return resolve(configured, systemLocale);
    }
    return resolve(null, systemLocale);
  }
}
