package com.riiablo.codec.excel;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lossless 1.10f {@code States.txt} projection matching D2MOO's state fields. */
public final class States implements Iterable<States.Entry> {
  private final LosslessTxtTable source;
  private final List<Entry> entries;
  private final Map<String, Entry> byName;

  private States(LosslessTxtTable source) {
    this.source = source;
    List<Entry> entries = new ArrayList<>();
    Map<String, Entry> byName = new LinkedHashMap<>();
    for (int row = 0; row < source.rowCount(); row++) {
      String state = source.get(row, "state");
      if (state.isEmpty() || "Expansion".equalsIgnoreCase(state)) continue;
      Entry entry = new Entry(entries.size(), source.row(row).sourceLine(), source, row);
      entries.add(entry);
      String key = canonical(state);
      if (!byName.containsKey(key)) byName.put(key, entry);
    }
    this.entries = Collections.unmodifiableList(entries);
    this.byName = Collections.unmodifiableMap(byName);
  }

  public static States load(FileHandle handle) {
    if (handle == null) throw new GdxRuntimeException("States.txt was not found");
    try {
      return parse(handle.readBytes());
    } catch (IOException e) {
      throw new GdxRuntimeException("Couldn't read States.txt", e);
    }
  }

  public static States parse(byte[] bytes) throws IOException {
    return new States(LosslessTxtTable.parse(bytes));
  }

  public LosslessTxtTable source() {
    return source;
  }

  public int size() {
    return entries.size();
  }

  public Entry get(int id) {
    return id >= 0 && id < entries.size() ? entries.get(id) : null;
  }

  public Entry get(String state) {
    return state == null ? null : byName.get(canonical(state));
  }

  @Override
  public Iterator<Entry> iterator() {
    return entries.iterator();
  }

  private static String canonical(String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  private static int integer(LosslessTxtTable table, int row, String column) {
    Integer value = table.getInt(row, column);
    return value == null ? 0 : value;
  }

  public static final class Entry {
    public final int id;
    public final int sourceLine;
    public final String state;
    public final int group;

    public final boolean noSend;
    public final boolean hide;
    public final boolean transform;
    public final boolean aura;
    public final boolean progressive;
    public final boolean active;
    public final boolean removeOnHit;
    public final boolean damageBlue;
    public final boolean damageRed;
    public final boolean attackBlue;
    public final boolean attackRed;
    public final boolean curse;
    public final boolean curable;
    public final boolean playerStayDeath;
    public final boolean monsterStayDeath;
    public final boolean bossStayDeath;
    public final boolean disguise;
    public final boolean bossInvulnerable;
    public final boolean meleeOnly;
    public final boolean restrict;
    public final boolean blue;
    public final boolean armorBlue;
    public final boolean fireResistBlue;
    public final boolean coldResistBlue;
    public final boolean lightningResistBlue;
    public final boolean poisonResistBlue;
    public final boolean staminaBarBlue;
    public final boolean armorRed;
    public final boolean fireResistRed;
    public final boolean coldResistRed;
    public final boolean lightningResistRed;
    public final boolean poisonResistRed;
    public final boolean experience;
    public final boolean shatter;
    public final boolean life;
    public final boolean undead;
    public final boolean green;
    public final boolean noOverlays;
    public final boolean notOnDead;
    public final boolean noClear;

    public final String[] overlays;
    public final String progressiveOverlay;
    public final String castOverlay;
    public final String removeOverlay;
    public final String stat;
    public final int setFunc;
    public final int removeFunc;
    public final String missile;
    public final String skill;
    public final int colorPriority;
    public final int colorShift;
    public final int lightRed;
    public final int lightGreen;
    public final int lightBlue;
    public final String onSound;
    public final String offSound;
    public final String itemType;
    public final String itemTransform;
    public final int gfxType;
    public final int gfxClass;
    public final String clientEvent;
    public final int clientEventFunc;
    public final int clientActiveFunc;
    public final int serverActiveFunc;

    private Entry(int id, int sourceLine, LosslessTxtTable table, int row) {
      this.id = id;
      this.sourceLine = sourceLine;
      state = table.get(row, "state");
      group = integer(table, row, "group");
      noSend = table.getBoolean(row, "nosend");
      hide = table.getBoolean(row, "hide");
      transform = table.getBoolean(row, "transform");
      aura = table.getBoolean(row, "aura");
      progressive = table.getBoolean(row, "pgsv");
      active = table.getBoolean(row, "active");
      removeOnHit = table.getBoolean(row, "remhit");
      damageBlue = table.getBoolean(row, "damblue");
      damageRed = table.getBoolean(row, "damred");
      attackBlue = table.getBoolean(row, "attblue");
      attackRed = table.getBoolean(row, "attred");
      curse = table.getBoolean(row, "curse");
      curable = table.getBoolean(row, "curable");
      playerStayDeath = table.getBoolean(row, "plrstaydeath");
      monsterStayDeath = table.getBoolean(row, "monstaydeath");
      bossStayDeath = table.getBoolean(row, "bossstaydeath");
      disguise = table.getBoolean(row, "disguise");
      bossInvulnerable = table.getBoolean(row, "bossinv");
      meleeOnly = table.getBoolean(row, "meleeonly");
      restrict = table.getBoolean(row, "restrict");
      blue = table.getBoolean(row, "blue");
      armorBlue = table.getBoolean(row, "armblue");
      fireResistBlue = table.getBoolean(row, "rfblue");
      coldResistBlue = table.getBoolean(row, "rcblue");
      lightningResistBlue = table.getBoolean(row, "rlblue");
      poisonResistBlue = table.getBoolean(row, "rpblue");
      staminaBarBlue = table.getBoolean(row, "stambarblue");
      armorRed = table.getBoolean(row, "armred");
      fireResistRed = table.getBoolean(row, "rfred");
      coldResistRed = table.getBoolean(row, "rcred");
      lightningResistRed = table.getBoolean(row, "rlred");
      poisonResistRed = table.getBoolean(row, "rpred");
      experience = table.getBoolean(row, "exp");
      shatter = table.getBoolean(row, "shatter");
      life = table.getBoolean(row, "life");
      undead = table.getBoolean(row, "udead");
      green = table.getBoolean(row, "green");
      noOverlays = table.getBoolean(row, "nooverlays");
      notOnDead = table.getBoolean(row, "notondead");
      noClear = table.getBoolean(row, "noclear");
      overlays = new String[] {
          table.get(row, "overlay1"), table.get(row, "overlay2"),
          table.get(row, "overlay3"), table.get(row, "overlay4")};
      progressiveOverlay = table.get(row, "pgsvoverlay");
      castOverlay = table.get(row, "castoverlay");
      removeOverlay = table.get(row, "removerlay");
      stat = table.get(row, "stat");
      setFunc = integer(table, row, "setfunc");
      removeFunc = integer(table, row, "remfunc");
      missile = table.get(row, "missile");
      skill = table.get(row, "skill");
      colorPriority = integer(table, row, "colorpri");
      colorShift = integer(table, row, "colorshift");
      lightRed = integer(table, row, "light-r");
      lightGreen = integer(table, row, "light-g");
      lightBlue = integer(table, row, "light-b");
      onSound = table.get(row, "onsound");
      offSound = table.get(row, "offsound");
      itemType = table.get(row, "itemtype");
      itemTransform = table.get(row, "itemtrans");
      gfxType = integer(table, row, "gfxtype");
      gfxClass = integer(table, row, "gfxclass");
      clientEvent = table.get(row, "cltevent");
      clientEventFunc = integer(table, row, "clteventfunc");
      clientActiveFunc = integer(table, row, "cltactivefunc");
      serverActiveFunc = integer(table, row, "srvactivefunc");
    }
  }
}
