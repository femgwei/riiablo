package com.riiablo.skill;

import com.badlogic.gdx.files.FileHandle;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A small, per-skill log that can be shared by the viewer and future ECS adapters. */
final class SkillSessionLog {
  private final FileHandle directory;
  private FileHandle file;
  private StringBuilder pending = new StringBuilder();

  SkillSessionLog(FileHandle directory) {
    this.directory = directory;
    directory.mkdirs();
  }

  void begin(String character, String skill, int level, long seed) {
    String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
    String safeCharacter = safe(character);
    String safeSkill = safe(skill);
    file = directory.child(safeCharacter + "_" + safeSkill + "_" + stamp + ".log");
    pending.setLength(0);
    append("# skill session\n");
    append("character=" + character + "\n");
    append("skill=" + skill + "\n");
    append("level=" + level + "\n");
    append("seed=" + seed + "\n");
    append("started=" + new Date() + "\n\n");
    flush();
  }

  void append(String line) {
    pending.append(line);
    if (!line.endsWith("\n")) pending.append('\n');
    flush();
  }

  void appendNote(String note) {
    if (note == null || note.trim().isEmpty()) return;
    append("\n# test notes\n" + note.trim() + "\n");
  }

  FileHandle file() {
    return file;
  }

  private void flush() {
    if (file == null || pending.length() == 0) return;
    file.writeString(pending.toString(), true, "UTF-8");
    pending.setLength(0);
  }

  private static String safe(String value) {
    if (value == null || value.isEmpty()) return "unknown";
    return value.replaceAll("[^A-Za-z0-9-]+", "_");
  }
}
