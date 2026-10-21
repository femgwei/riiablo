package com.riiablo.presentation;

import com.riiablo.codec.excel.Shrines;

/** Native shrine icons and localized labels shared by initial and snapshot presentation. */
public final class ShrinePresentation {
  private ShrinePresentation() {}

  /** Maps the native Shrines.txt code to its stock overhead icon. */
  static String overlay(ClientGameplayContext context, int shrineId) {
    if (context == null || context.files() == null || context.files().Shrines == null) return null;
    Shrines.Entry shrine = context.files().Shrines.get(shrineId);
    if (shrine == null) return null;
    switch (shrine.Code) {
      case 6: return "shrine_armor";
      case 7: return "shrine_combat";
      case 8: return "shrine_resist_fire";
      case 9: return "shrine_resist_cold";
      case 10: return "shrine_resist_lightning";
      case 11: return "shrine_resist_poison";
      case 12: return "shrine_skill";
      case 13: return "shrine_mana_regen";
      case 14: return "shrine_stamina";
      case 15: return "shrine_experience";
      default: return null;
    }
  }

  /**
   * Resolves the native shrine tooltip name.  Retail Shrines.txt stores
   * {@code view name} as the placeholder "blah"; the actual localized names
   * are the string-table keys ShrId0..ShrId22.
   */
  public static String displayName(ClientGameplayContext context, Shrines.Entry shrine) {
    if (shrine == null) return null;
    String key = "ShrId" + shrine.Code;
    String localized = context == null || context.strings() == null ? null : context.strings().lookup(key);
    if (localized != null && !localized.isEmpty()
        && !localized.regionMatches(true, 0, "ERROR:", 0, 6)) {
      return localized;
    }

    String view = shrine.ViewName;
    if (view != null && !view.isEmpty() && !view.equalsIgnoreCase("blah")) {
      String resolved = context == null || context.strings() == null ? null : context.strings().lookup(view);
      if (resolved != null && !resolved.isEmpty()
          && !resolved.regionMatches(true, 0, "ERROR:", 0, 6)) return resolved;
      return view;
    }
    return com.riiablo.engine.server.object.ShrineType.getName(shrine.Code);
  }
}
