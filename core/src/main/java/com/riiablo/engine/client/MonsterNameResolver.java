package com.riiablo.engine.client;

import com.riiablo.Riiablo;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.monster.MonsterRank;

/** Resolves the authoritative monster quality identity into a client label. */
final class MonsterNameResolver {
  private MonsterNameResolver() {}

  static String displayName(Monster monster) {
    return displayName(ClientGameplayContext.fromGlobals(), monster);
  }

  static String displayName(ClientGameplayContext context, Monster monster) {
    if (monster == null || monster.monstats == null) return "";
    String key = monster.monstats.NameStr;
    if (monster.rank == MonsterRank.SUPER_UNIQUE && monster.uniqueId >= 0
        && context != null && context.files() != null && context.files().SuperUniques != null) {
      for (SuperUniques.Entry entry : context.files().SuperUniques) {
        if (entry != null && entry.hcIdx == monster.uniqueId) {
          key = entry.Name == null || entry.Name.isEmpty()
              ? entry.Superunique : entry.Name;
          break;
        }
      }
    }
    if (key == null || key.isEmpty()) return monster.monstats.Id;
    if (context == null || context.strings() == null) return key;
    String localized = context.strings().lookup(key);
    return localized == null || localized.startsWith("ERROR: ") ? key : localized;
  }
}
