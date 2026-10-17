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

/** Lossless 1.10f {@code MonStats.txt} view driven by D2MOO's monster loader. */
public final class NativeMonStats implements Iterable<NativeMonStats.Entry> {
  public static final NativeTxtSchema SCHEMA = NativeTxtSchema.builder("MonStats.txt")
      .strings(
          "Id", "BaseId", "NextInClass", "NameStr", "DescStr", "Code", "MonSound",
          "UMonSound", "MonStatsEx", "MonType", "MonProp", "AI", "spawn", "spawnmode",
          "minion1", "minion2", "MissA1", "MissA2", "MissS1", "MissS2", "MissS3",
          "MissS4", "MissC", "MissSQ", "TreasureClass1", "TreasureClass2",
          "TreasureClass3", "TreasureClass4", "TreasureClass1(N)", "TreasureClass2(N)",
          "TreasureClass3(N)", "TreasureClass4(N)", "TreasureClass1(H)",
          "TreasureClass2(H)", "TreasureClass3(H)", "TreasureClass4(H)", "SkillDamage",
          "El1Mode", "El1Type", "El2Mode", "El2Type", "El3Mode", "El3Type", "Skill1",
          "Sk1mode", "Skill2", "Sk2mode", "Skill3", "Sk3mode", "Skill4", "Sk4mode",
          "Skill5", "Sk5mode", "Skill6", "Sk6mode", "Skill7", "Sk7mode", "Skill8",
          "Sk8mode")
      .integers(
          "TransLvl", "spawnx", "spawny", "PartyMin", "PartyMax", "Rarity", "MinGrp",
          "MaxGrp", "sparsePopulate", "Velocity", "Run", "Align", "TCQuestId", "TCQuestCP",
          "threat", "aidel", "aidel(N)", "aidel(H)", "aidist", "aidist(N)", "aidist(H)",
          "aip1", "aip1(N)", "aip1(H)", "aip2", "aip2(N)", "aip2(H)", "aip3",
          "aip3(N)", "aip3(H)", "aip4", "aip4(N)", "aip4(H)", "aip5", "aip5(N)",
          "aip5(H)", "aip6", "aip6(N)", "aip6(H)", "aip7", "aip7(N)", "aip7(H)",
          "aip8", "aip8(N)", "aip8(H)", "Level", "Level(N)", "Level(H)", "Drain",
          "Drain(N)", "Drain(H)", "ToBlock", "ToBlock(N)", "ToBlock(H)", "Crit", "MinHP",
          "MinHP(N)", "MinHP(H)", "MaxHP", "MaxHP(N)", "MaxHP(H)", "AC", "AC(N)",
          "AC(H)", "Exp", "Exp(N)", "Exp(H)", "A1TH", "A1TH(N)", "A1TH(H)", "A1MinD",
          "A1MinD(N)", "A1MinD(H)", "A1MaxD", "A1MaxD(N)", "A1MaxD(H)", "A2TH",
          "A2TH(N)", "A2TH(H)", "A2MinD", "A2MinD(N)", "A2MinD(H)", "A2MaxD",
          "A2MaxD(N)", "A2MaxD(H)", "S1TH", "S1TH(N)", "S1TH(H)", "S1MinD",
          "S1MinD(N)", "S1MinD(H)", "S1MaxD", "S1MaxD(N)", "S1MaxD(H)", "El1Pct",
          "El1Pct(N)", "El1Pct(H)", "El1MinD", "El1MinD(N)", "El1MinD(H)", "El1MaxD",
          "El1MaxD(N)", "El1MaxD(H)", "El1Dur", "El1Dur(N)", "El1Dur(H)", "El2Pct",
          "El2Pct(N)", "El2Pct(H)", "El2MinD", "El2MinD(N)", "El2MinD(H)", "El2MaxD",
          "El2MaxD(N)", "El2MaxD(H)", "El2Dur", "El2Dur(N)", "El2Dur(H)", "El3Pct",
          "El3Pct(N)", "El3Pct(H)", "El3MinD", "El3MinD(N)", "El3MinD(H)", "El3MaxD",
          "El3MaxD(N)", "El3MaxD(H)", "El3Dur", "El3Dur(N)", "El3Dur(H)",
          "ColdEffect", "ColdEffect(N)", "ColdEffect(H)", "ResDm", "ResDm(N)", "ResDm(H)",
          "ResMa", "ResMa(N)", "ResMa(H)", "ResFi", "ResFi(N)", "ResFi(H)", "ResLi",
          "ResLi(N)", "ResLi(H)", "ResCo", "ResCo(N)", "ResCo(H)", "ResPo", "ResPo(N)",
          "ResPo(H)", "SendSkills", "Sk1lvl", "Sk2lvl", "Sk3lvl", "Sk4lvl", "Sk5lvl",
          "Sk6lvl", "Sk7lvl", "Sk8lvl", "DamageRegen", "SplEndDeath", "SplGetModeChart",
          "SplEndGeneric", "SplClientEnd")
      .booleans(
          "enabled", "rangedtype", "placespawn", "isSpawn", "isMelee", "noRatio",
          "SetBoss", "BossXfer", "boss", "primeevil", "opendoors", "npc", "interact",
          "inventory", "inTown", "lUndead", "hUndead", "demon", "flying", "killable",
          "switchai", "noaura", "nomultishot", "neverCount", "petIgnore", "deathDmg",
          "genericSpawn", "zoo", "NoShldBlock")
      .build();

  private final LosslessTxtTable source;
  private final List<Entry> entries;
  private final Map<String, Entry> byName;
  private final List<NativeTxtSchema.Issue> schemaIssues;

  private NativeMonStats(LosslessTxtTable source) {
    this.source = source;
    schemaIssues = SCHEMA.validate(source);
    List<Entry> entries = new ArrayList<>();
    Map<String, Entry> byName = new LinkedHashMap<>();
    for (int row = 0; row < source.rowCount(); row++) {
      String monster = source.get(row, "Id");
      if (monster.isEmpty() || "Expansion".equalsIgnoreCase(monster)) continue;
      Entry entry = new Entry(entries.size(), source.row(row).sourceLine(), source, row);
      entries.add(entry);
      String key = monster.toLowerCase(Locale.ROOT);
      if (!byName.containsKey(key)) byName.put(key, entry);
    }
    this.entries = Collections.unmodifiableList(entries);
    this.byName = Collections.unmodifiableMap(byName);
  }

  public static NativeMonStats load(FileHandle handle) {
    if (handle == null) throw new GdxRuntimeException("MonStats.txt was not found");
    try {
      return parse(handle.readBytes());
    } catch (IOException e) {
      throw new GdxRuntimeException("Couldn't read MonStats.txt", e);
    }
  }

  public static NativeMonStats parse(byte[] bytes) throws IOException {
    return new NativeMonStats(LosslessTxtTable.parse(bytes));
  }

  public LosslessTxtTable source() { return source; }
  public List<NativeTxtSchema.Issue> schemaIssues() { return schemaIssues; }
  public int size() { return entries.size(); }
  public Entry get(int id) { return id >= 0 && id < entries.size() ? entries.get(id) : null; }
  public Entry get(String monster) {
    return monster == null ? null : byName.get(monster.toLowerCase(Locale.ROOT));
  }

  @Override
  public Iterator<Entry> iterator() { return entries.iterator(); }

  public static final class Entry {
    /** Native MonStats index assigned by row order through TXTFIELD_NAMETOINDEX. */
    public final int id;
    public final int hcIdx;
    public final int sourceLine;
    public final String monster;
    private final LosslessTxtTable source;
    private final int row;

    private Entry(int id, int sourceLine, LosslessTxtTable source, int row) {
      this.id = id;
      this.hcIdx = source.getNativeInt(row, "hcIdx");
      this.sourceLine = sourceLine;
      this.monster = source.get(row, "Id");
      this.source = source;
      this.row = row;
    }

    public String string(String field) { return source.get(row, field); }
    public Integer integer(String field) { return source.getInt(row, field); }
    public int nativeInteger(String field) { return source.getNativeInt(row, field); }
    public boolean bool(String field) { return source.getNativeBit(row, field); }
  }
}
