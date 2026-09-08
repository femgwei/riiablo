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

/** Lossless 1.10f {@code Skills.txt} view driven by D2MOO's pSkillTbl schema. */
public final class NativeSkills implements Iterable<NativeSkills.Entry> {
  public static final NativeTxtSchema SCHEMA = NativeTxtSchema.builder("Skills.txt")
      .strings(
          "skill", "charclass", "skilldesc",
          "prgcalc1", "prgcalc2", "prgcalc3",
          "srvmissile", "srvmissilea", "srvmissileb", "srvmissilec", "srvoverlay",
          "aurastate", "auratargetstate", "auralencalc", "aurarangecalc",
          "aurastat1", "aurastat2", "aurastat3", "aurastat4", "aurastat5", "aurastat6",
          "aurastatcalc1", "aurastatcalc2", "aurastatcalc3", "aurastatcalc4",
          "aurastatcalc5", "aurastatcalc6",
          "auraevent1", "auraevent2", "auraevent3", "auratgtevent",
          "passivestate", "passiveitype", "passivestat1", "passivestat2", "passivestat3",
          "passivestat4", "passivestat5", "passivecalc1", "passivecalc2", "passivecalc3",
          "passivecalc4", "passivecalc5", "passiveevent",
          "summon", "pettype", "summode", "petmax", "sumskill1", "sumskill2", "sumskill3",
          "sumskill4", "sumskill5", "sumsk1calc", "sumsk2calc", "sumsk3calc", "sumsk4calc",
          "sumsk5calc", "sumoverlay",
          "cltmissile", "cltmissilea", "cltmissileb", "cltmissilec", "cltmissiled",
          "stsound", "stsoundclass", "dosound", "dosound a", "dosound b", "castoverlay",
          "tgtoverlay", "tgtsound", "prgoverlay", "prgsound", "cltoverlaya", "cltoverlayb",
          "cltcalc1", "cltcalc2", "cltcalc3",
          "anim", "seqtrans", "monanim", "range",
          "itypea1", "itypea2", "itypea3", "itypeb1", "itypeb2", "itypeb3",
          "etypea1", "etypea2", "etypeb1", "etypeb2",
          "perdelay", "itemcastsound", "itemcastoverlay", "skpoints",
          "reqskill1", "reqskill2", "reqskill3", "delay", "calc1", "calc2", "calc3", "calc4",
          "tohitcalc", "dmgsympercalc", "etype", "edmgsympercalc", "elensympercalc",
          "state1", "state2", "state3")
      .integers(
          "ID", "srvstfunc", "srvdofunc", "srvprgfunc1", "srvprgfunc2", "srvprgfunc3",
          "prgdam", "aurafilter", "auraeventfunc1", "auraeventfunc2", "auraeventfunc3",
          "auratgteventfunc", "passiveeventfunc", "sumumod",
          "cltstfunc", "cltdofunc", "cltprgfunc1", "cltprgfunc2", "cltprgfunc3",
          "seqnum", "seqinput", "weapsel", "maxlvl", "ResultFlags", "HitFlags", "HitClass",
          "ItemEffect", "ItemCltEffect", "ItemTarget", "lineofsight", "attackrank", "SelectProc",
          "reqlevel", "reqstr", "reqdex", "reqint", "reqvit", "startmana", "minmana",
          "manashift", "mana", "lvlmana",
          "Param1", "Param2", "Param3", "Param4", "Param5", "Param6", "Param7", "Param8",
          "ToHit", "LevToHit", "HitShift", "SrcDam", "MinDam", "MaxDam",
          "MinLevDam1", "MinLevDam2", "MinLevDam3", "MinLevDam4", "MinLevDam5",
          "MaxLevDam1", "MaxLevDam2", "MaxLevDam3", "MaxLevDam4", "MaxLevDam5",
          "EMin", "EMax", "EMinLev1", "EMinLev2", "EMinLev3", "EMinLev4", "EMinLev5",
          "EMaxLev1", "EMaxLev2", "EMaxLev3", "EMaxLev4", "EMaxLev5",
          "ELen", "ELevLen1", "ELevLen2", "ELevLen3", "restrict", "aitype", "aibonus",
          "cost mult", "cost add")
      .booleans(
          "lob", "decquant", "immediate", "stsuccessonly", "stsounddelay", "weaponsnd", "warp",
          "progressive", "finishing", "prgstack", "InTown", "Kick", "passive", "aura",
          "periodic", "general", "scroll", "ItemTgtDo", "ItemCheckStart", "ItemCltCheckStart",
          "InGame", "noammo", "enhanceable", "durability", "UseAttackRate", "TargetableOnly",
          "SearchEnemyXY", "SearchEnemyNear", "SearchOpenXY", "TargetCorpse", "TargetPet",
          "TargetAlly", "TargetItem", "AttackNoMana", "leftskill", "interrupt", "TgtPlaceCheck",
          "repeat", "usemanaondo")
      .build();

  private final LosslessTxtTable source;
  private final List<Entry> entries;
  private final Map<String, Entry> byName;
  private final List<NativeTxtSchema.Issue> schemaIssues;

  private NativeSkills(LosslessTxtTable source) {
    this.source = source;
    this.schemaIssues = SCHEMA.validate(source);
    List<Entry> entries = new ArrayList<>();
    Map<String, Entry> byName = new LinkedHashMap<>();
    for (int row = 0; row < source.rowCount(); row++) {
      String skill = source.get(row, "skill");
      if (skill.isEmpty() || "Expansion".equalsIgnoreCase(skill)) continue;
      Entry entry = new Entry(entries.size(), source.row(row).sourceLine(), source, row);
      entries.add(entry);
      String key = canonical(skill);
      if (!byName.containsKey(key)) byName.put(key, entry);
    }
    this.entries = Collections.unmodifiableList(entries);
    this.byName = Collections.unmodifiableMap(byName);
  }

  public static NativeSkills load(FileHandle handle) {
    if (handle == null) throw new GdxRuntimeException("Skills.txt was not found");
    try {
      return parse(handle.readBytes());
    } catch (IOException e) {
      throw new GdxRuntimeException("Couldn't read Skills.txt", e);
    }
  }

  public static NativeSkills parse(byte[] bytes) throws IOException {
    return new NativeSkills(LosslessTxtTable.parse(bytes));
  }

  public LosslessTxtTable source() { return source; }
  public List<NativeTxtSchema.Issue> schemaIssues() { return schemaIssues; }
  public int size() { return entries.size(); }
  public Entry get(int id) { return id >= 0 && id < entries.size() ? entries.get(id) : null; }
  public Entry get(String skill) { return skill == null ? null : byName.get(canonical(skill)); }

  @Override
  public Iterator<Entry> iterator() { return entries.iterator(); }

  private static String canonical(String value) { return value.toLowerCase(Locale.ROOT); }

  public static final class Entry {
    public final int id;
    public final int txtId;
    public final int sourceLine;
    public final String skill;
    private final LosslessTxtTable source;
    private final int row;

    private Entry(int id, int sourceLine, LosslessTxtTable source, int row) {
      this.id = id;
      Integer txtId = source.getInt(row, "id");
      this.txtId = txtId == null ? 0 : txtId;
      this.sourceLine = sourceLine;
      this.skill = source.get(row, "skill");
      this.source = source;
      this.row = row;
    }

    public String string(String field) { return source.get(row, field); }
    public Integer integer(String field) { return source.getInt(row, field); }
    public int nativeInteger(String field) { return source.getNativeInt(row, field); }
    public boolean bool(String field) { return source.getNativeBit(row, field); }
  }
}
