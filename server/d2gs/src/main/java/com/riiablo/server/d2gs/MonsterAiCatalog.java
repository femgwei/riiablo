package com.riiablo.server.d2gs;

import com.riiablo.Files;
import com.riiablo.codec.excel.Levels;
import com.riiablo.codec.excel.MonPreset;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Table-reference inventory, NOT a claim that every preset occurs in a generated DS1. */
final class MonsterAiCatalog {
  static final class Row {
    final MonStats.Entry monster;
    final Set<Integer> acts = new TreeSet<>();
    final Set<Integer> levels = new TreeSet<>();
    final Set<String> sources = new TreeSet<>();
    Row(MonStats.Entry monster) { this.monster = monster; }
    int arenaLevel() {
      // Unlocated presets get an explicit act-local outdoor fixture, not an invented boss location.
      return levels.isEmpty() ? (acts.isEmpty() ? 2 : new int[] {2, 41, 76, 104, 110}[acts.iterator().next() - 1])
          : levels.iterator().next();
    }
  }

  final Map<String, Row> rows = new TreeMap<>();
  final Set<String> unresolved = new TreeSet<>();

  MonsterAiCatalog(Files files) {
    // Keep unreferenced hostile rows visible, rather than silently losing summons/controllers.
    for (MonStats.Entry monster : files.monstats) {
      if (eligible(monster)) rows.put(monster.Id, new Row(monster));
    }
    for (Levels.Entry level : files.Levels) {
      if (level.Id < 1 || level.Id > 132 || level.Act < 0 || level.Act > 4) continue;
      reference(files, level.mon, level, "Levels.mon");
      reference(files, level.nmon, level, "Levels.nmon");
      reference(files, level.umon, level, "Levels.umon");
      reference(files, level.cmon, level, "Levels.cmon");
    }
    for (MonPreset.Entry preset : files.MonPreset) {
      MonStats.Entry monster = files.monstats.get(preset.Place);
      if (monster == null) monster = com.riiablo.engine.server.ServerEntityFactory.resolvePlacementMonster(preset.Place);
      SuperUniques.Entry unique = files.SuperUniques.get(preset.Place);
      if (monster == null && unique != null) monster = files.monstats.get(unique.MonClass);
      if (monster == null) {
        unresolved.add("A" + preset.Act + ":" + preset.Place);
        continue;
      }
      Row row = rows.get(monster.Id);
      if (row != null) {
        row.acts.add(preset.Act);
        row.sources.add("MonPreset:" + preset.Place);
        // Presets have an act but no authoritative level. Do not invent a boss level.
      }
    }
    // Propagate act provenance through data-defined spawn/minion links to a fixed point.
    boolean changed;
    do {
      changed = false;
      for (Row parent : rows.values()) {
        for (String id : new String[] {parent.monster.spawn, parent.monster.minion1,
            parent.monster.minion2}) {
          Row child = rows.get(id);
          if (child == null || parent.acts.isEmpty()) continue;
          changed |= child.acts.addAll(parent.acts);
          child.sources.add("related:" + parent.monster.Id);
        }
      }
    } while (changed);
  }

  static boolean eligible(MonStats.Entry monster) {
    return monster != null && monster.enabled && monster.Align == 0 && !monster.npc;
  }

  private void reference(Files files, String[] ids, Levels.Entry level, String source) {
    if (ids == null) return;
    for (String id : ids) {
      if (id == null || id.isEmpty()) continue;
      MonStats.Entry monster = files.monstats.get(id);
      if (monster == null) { unresolved.add("L" + level.Id + ":" + id); continue; }
      Row row = rows.get(id);
      if (row == null) continue;
      row.acts.add(level.Act + 1);
      row.levels.add(level.Id);
      row.sources.add(source);
    }
  }

  String summary() {
    StringBuilder text = new StringBuilder("Universe: enabled MonStats, Align=0, npc=false. "
        + "Counts are table-reference candidates, not actual map spawn coverage.\n");
    for (int act = 0; act <= 5; act++) {
      int variants = 0;
      Set<String> families = new TreeSet<>(), ais = new TreeSet<>();
      for (Row row : rows.values()) {
        if (act != 0 && !row.acts.contains(act)) continue;
        variants++;
        families.add(value(row.monster.BaseId, row.monster.Id));
        ais.add(value(row.monster.AI, "<empty>"));
      }
      text.append(act == 0 ? "ALL" : "A" + act).append(" variants=").append(variants)
          .append(" families=").append(families.size()).append(" ais=").append(ais.size()).append('\n');
    }
    int unassigned = 0;
    for (Row row : rows.values()) if (row.acts.isEmpty()) unassigned++;
    return text.append("unassignedAct=").append(unassigned)
        .append(" unresolvedPlacements=").append(unresolved).append('\n').toString();
  }

  static String value(String value, String fallback) {
    return value == null || value.isEmpty() ? fallback : value;
  }
}
