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

/** Lossless 1.10f {@code Missiles.txt} view driven by D2MOO's missile loader. */
public final class NativeMissiles implements Iterable<NativeMissiles.Entry> {
  public static final NativeTxtSchema SCHEMA = NativeTxtSchema.builder("Missiles.txt")
      .strings("Missile", "SrvCalc1", "CltCalc1", "SHitCalc1", "CHitCalc1", "DmgCalc1",
          "TravelSound", "HitSound", "ProgSound", "ProgOverlay", "ExplosionMissile",
          "SubMissile1", "SubMissile2", "SubMissile3", "HitSubMissile1", "HitSubMissile2",
          "HitSubMissile3", "HitSubMissile4", "CltSubMissile1", "CltSubMissile2", "CltSubMissile3",
          "CltHitSubMissile1", "CltHitSubMissile2", "CltHitSubMissile3", "CltHitSubMissile4",
          "Skill", "DmgSymPerCalc", "EType", "EDmgSymPerCalc", "CelFile")
      .integers("pCltDoFunc", "pCltHitFunc", "pSrvDoFunc", "pSrvHitFunc", "pSrvDmgFunc",
          "Param1", "Param2", "Param3", "Param4", "Param5", "CltParam1", "CltParam2",
          "CltParam3", "CltParam4", "CltParam5", "sHitPar1", "sHitPar2", "sHitPar3",
          "cHitPar1", "cHitPar2", "cHitPar3", "dParam1", "dParam2", "ResultFlags", "HitFlags",
          "HitClass", "Range", "LevRange", "KnockBack", "animrate", "xoffset", "yoffset", "zoffset",
          "MinDamage", "MinLevDam1", "MinLevDam2", "MinLevDam3", "MinLevDam4", "MinLevDam5",
          "MaxDamage", "MaxLevDam1", "MaxLevDam2", "MaxLevDam3", "MaxLevDam4", "MaxLevDam5",
          "EMin", "MinELev1", "MinELev2", "MinELev3", "MinELev4", "MinELev5", "EMax",
          "MaxELev1", "MaxELev2", "MaxELev3", "MaxELev4", "MaxELev5", "ELen", "ELevLen1",
          "ELevLen2", "ELevLen3", "CltSrcTown", "SrcDamage", "SrcMissDmg", "Vel", "VelLev",
          "MaxVel", "Accel", "Holy", "Light", "Flicker", "Red", "Green", "Blue", "InitSteps",
          "Activate", "LoopAnim", "AnimLen", "RandStart", "SubLoop", "SubStart", "SubStop",
          "CollideType", "CollideKill", "CollideFriend", "Collision", "ClientCol", "NextHit",
          "NextDelay", "Size", "ToHit", "AlwaysExplode", "Trans", "Qty", "SpecialSetup",
          "HitShift", "DamageRate", "NumDirections", "AnimSpeed", "LocalBlood")
      .booleans("LastCollide", "Explosion", "Pierce", "CanSlow", "CanDestroy", "NoMultiShot",
          "NoUniqueMod", "ClientSend", "GetHit", "SoftHit", "ApplyMastery", "ReturnFire", "Town",
          "SrcTown", "MissileSkill", "Half2HSrc")
      .build();

  private final LosslessTxtTable source;
  private final List<Entry> entries;
  private final Map<String, Entry> byName;
  private final List<NativeTxtSchema.Issue> schemaIssues;

  private NativeMissiles(LosslessTxtTable source) {
    this.source = source;
    schemaIssues = SCHEMA.validate(source);
    List<Entry> entries = new ArrayList<>();
    Map<String, Entry> byName = new LinkedHashMap<>();
    for (int row = 0; row < source.rowCount(); row++) {
      String missile = source.get(row, "Missile");
      if (missile.isEmpty() || "Expansion".equalsIgnoreCase(missile)) continue;
      Entry entry = new Entry(entries.size(), source.row(row).sourceLine(), source, row);
      entries.add(entry);
      String key = missile.toLowerCase(Locale.ROOT);
      if (!byName.containsKey(key)) byName.put(key, entry);
    }
    this.entries = Collections.unmodifiableList(entries);
    this.byName = Collections.unmodifiableMap(byName);
  }

  public static NativeMissiles load(FileHandle handle) {
    if (handle == null) throw new GdxRuntimeException("Missiles.txt was not found");
    try {
      return parse(handle.readBytes());
    } catch (IOException e) {
      throw new GdxRuntimeException("Couldn't read Missiles.txt", e);
    }
  }

  public static NativeMissiles parse(byte[] bytes) throws IOException {
    return new NativeMissiles(LosslessTxtTable.parse(bytes));
  }

  public LosslessTxtTable source() { return source; }
  public List<NativeTxtSchema.Issue> schemaIssues() { return schemaIssues; }
  public int size() { return entries.size(); }
  public Entry get(int id) { return id >= 0 && id < entries.size() ? entries.get(id) : null; }
  public Entry get(String missile) {
    return missile == null ? null : byName.get(missile.toLowerCase(Locale.ROOT));
  }

  @Override
  public Iterator<Entry> iterator() { return entries.iterator(); }

  public static final class Entry {
    public final int id;
    public final int txtId;
    public final int sourceLine;
    public final String missile;
    private final LosslessTxtTable source;
    private final int row;

    private Entry(int id, int sourceLine, LosslessTxtTable source, int row) {
      this.id = id;
      Integer txtId = source.getInt(row, "id");
      this.txtId = txtId == null ? 0 : txtId;
      this.sourceLine = sourceLine;
      this.missile = source.get(row, "missile");
      this.source = source;
      this.row = row;
    }

    public String string(String field) { return source.get(row, field); }
    public Integer integer(String field) { return source.getInt(row, field); }
    public int nativeInteger(String field) { return source.getNativeInt(row, field); }
    public boolean bool(String field) { return source.getNativeBit(row, field); }
    /** Byte-valued native flags are not restricted to boolean at the data layer. */
    public boolean nonZero(String field) {
      Integer value = integer(field);
      return value != null && value != 0;
    }
  }
}
