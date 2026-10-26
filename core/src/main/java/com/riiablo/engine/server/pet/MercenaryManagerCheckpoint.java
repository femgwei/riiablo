package com.riiablo.engine.server.pet;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.riiablo.engine.replay.CheckpointIO;
import com.riiablo.engine.server.pet.MercenaryManager.*;
import java.io.*;
import java.util.*;

/** Raw manager graph; no entity factories, payment, reward callbacks or roster refresh. */
public final class MercenaryManagerCheckpoint {
  public static final int MAX_BYTES = 8 * 1024 * 1024;
  private final byte[] bytes;
  private MercenaryManagerCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public static MercenaryManagerCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("hireling manager length");
    MercenaryManagerCheckpoint image = new MercenaryManagerCheckpoint(bytes.clone()); image.decode(); return image;
  }
  public java.util.Map<Integer, ActiveMercenary> activeRecords() {
    return Collections.unmodifiableMap(sorted(decode().manager.playerMercs));
  }
  public Set<Integer> rosterOwners() { return Collections.unmodifiableSet(sorted(decode().manager.playerLists).keySet()); }

  public static MercenaryManagerCheckpoint capture(MercenaryManager manager) {
    if (manager.rosterRng == null) throw new IllegalStateException("hireling manager requires owned clock/RNG");
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(1); out.writeLong(manager.runtimeMillis); out.writeInt(manager.rosterDifficulty);
      out.writeInt(manager.rosterRng.state()); out.writeLong(manager.rosterRng.drawCount());
      out.writeBoolean(manager.nativeExperienceTable != null);
      if (manager.nativeExperienceTable != null) out.write(manager.nativeExperienceTable.checkpointIdentity());
      for (long xp : manager.expTable) out.writeLong(xp);
      List<MercenaryDefinition> graph = graph(manager);
      IdentityHashMap<MercenaryDefinition, Integer> ids = new IdentityHashMap<>();
      for (int i = 0; i < graph.size(); i++) ids.put(graph.get(i), i);
      out.writeInt(graph.size()); for (MercenaryDefinition d : graph) writeDefinition(out, d);
      out.writeInt(manager.mercDefinitions.size);
      for (java.util.Map.Entry<Integer, Array<MercenaryDefinition>> e : sorted(manager.mercDefinitions).entrySet()) {
        out.writeInt(e.getKey()); out.writeInt(e.getValue().size);
        for (MercenaryDefinition d : e.getValue()) out.writeInt(ids.get(d));
      }
      writeLists(out, manager.npcLists, ids);
      out.writeInt(manager.playerLists.size);
      for (java.util.Map.Entry<Integer, IntMap<NpcMercenaryList>> e : sorted(manager.playerLists).entrySet()) {
        out.writeInt(e.getKey()); writeLists(out, e.getValue(), ids);
      }
      out.writeInt(manager.playerMercs.size);
      for (java.util.Map.Entry<Integer, ActiveMercenary> e : sorted(manager.playerMercs).entrySet()) {
        ActiveMercenary m = e.getValue(); out.writeInt(e.getKey()); out.writeInt(m.ownerId); out.writeInt(m.entityId);
        out.writeInt(ids.get(m.definition)); out.writeInt(m.nameId); out.writeInt(m.seed); out.writeInt(m.state);
        out.writeInt(m.level); out.writeLong(m.experience); out.writeInt(m.currentLife); out.writeInt(m.maxLife);
        if (m.equipment == null || m.equipment.length != 3) throw new IllegalStateException("hireling equipment shape");
        for (int item : m.equipment) {
          if (item != 0) throw new IllegalStateException("hireling equipment entity graph uncovered");
          out.writeInt(item);
        }
        out.writeLong(m.hireTime);
      }
      out.flush(); return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  public void validateRestore(MercenaryManager manager) {
    Decoded d = decode();
    if (manager.rosterRng == null || (manager.nativeExperienceTable != null) != (d.table != null)
        || d.table != null && !Arrays.equals(d.table, manager.nativeExperienceTable.checkpointIdentity())
        || !Arrays.equals(d.manager.expTable, manager.expTable)) {
      throw new IllegalStateException("hireling manager data/runtime topology mismatch");
    }
    for (ActiveMercenary live : manager.checkpointActiveMercenaries().values()) {
      if (live.equipment == null || live.equipment.length != 3) throw new IllegalStateException("hireling equipment shape");
      for (int item : live.equipment) if (item != 0) throw new IllegalStateException("cannot overwrite uncovered hireling equipment");
    }
  }

  public void restore(MercenaryManager manager) {
    validateRestore(manager); Decoded d = decode();
    List<MercenaryDefinition> live = graph(manager), desired = graph(d.manager);
    IdentityHashMap<MercenaryDefinition, MercenaryDefinition> rebound = new IdentityHashMap<>();
    for (int i = 0; i < desired.size(); i++) {
      MercenaryDefinition dest = i < live.size() ? live.get(i) : new MercenaryDefinition();
      copyDefinition(desired.get(i), dest); rebound.put(desired.get(i), dest);
    }
    for (int key : removed(manager.mercDefinitions, d.manager.mercDefinitions)) manager.mercDefinitions.remove(key);
    for (java.util.Map.Entry<Integer, Array<MercenaryDefinition>> e : sorted(d.manager.mercDefinitions).entrySet()) {
      Array<MercenaryDefinition> values = manager.mercDefinitions.get(e.getKey());
      if (values == null) manager.mercDefinitions.put(e.getKey(), values = new Array<>());
      values.clear(); for (MercenaryDefinition value : e.getValue()) values.add(rebound.get(value));
    }
    restoreLists(manager.npcLists, d.manager.npcLists, rebound);
    for (int key : removed(manager.playerLists, d.manager.playerLists)) manager.playerLists.remove(key);
    for (java.util.Map.Entry<Integer, IntMap<NpcMercenaryList>> e : sorted(d.manager.playerLists).entrySet()) {
      IntMap<NpcMercenaryList> lists = manager.playerLists.get(e.getKey());
      if (lists == null) manager.playerLists.put(e.getKey(), lists = new IntMap<>());
      restoreLists(lists, e.getValue(), rebound);
    }
    for (int key : removed(manager.playerMercs, d.manager.playerMercs)) manager.playerMercs.remove(key);
    for (java.util.Map.Entry<Integer, ActiveMercenary> e : sorted(d.manager.playerMercs).entrySet()) {
      ActiveMercenary a = e.getValue(), b = manager.playerMercs.get(e.getKey());
      if (b == null) manager.playerMercs.put(e.getKey(), b = new ActiveMercenary());
      b.ownerId = a.ownerId; b.entityId = a.entityId; b.definition = rebound.get(a.definition);
      b.nameId = a.nameId; b.seed = a.seed; b.state = a.state; b.level = a.level; b.experience = a.experience;
      b.currentLife = a.currentLife; b.maxLife = a.maxLife; b.hireTime = a.hireTime;
      System.arraycopy(a.equipment, 0, b.equipment, 0, 3);
    }
    manager.runtimeMillis = d.manager.runtimeMillis; manager.rosterDifficulty = d.manager.rosterDifficulty;
    manager.rosterRng.restore(d.state, d.draws);
  }

  private Decoded decode() {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != 1) throw new IllegalArgumentException("hireling manager version");
      Decoded d = new Decoded(); MercenaryManager m = d.manager;
      m.mercDefinitions.clear(); m.runtimeMillis = in.readLong(); m.rosterDifficulty = in.readInt();
      d.state = in.readInt(); d.draws = in.readLong();
      if (m.runtimeMillis < 0 || m.rosterDifficulty < -1 || m.rosterDifficulty > 2 || d.draws < 0) {
        throw new IllegalArgumentException("hireling manager clock/difficulty/RNG");
      }
      if (CheckpointIO.readBoolean(in)) { d.table = new byte[32]; in.readFully(d.table); }
      for (int i = 0; i < m.expTable.length; i++) m.expTable[i] = in.readLong();
      List<MercenaryDefinition> defs = new ArrayList<>(); int n = count(in);
      for (int i = 0; i < n; i++) defs.add(readDefinition(in));
      int count = count(in), previous = -1;
      for (int i = 0; i < count; i++) {
        int key = ordered(in, previous); previous = key; Array<MercenaryDefinition> values = new Array<>();
        int length = count(in); for (int j = 0; j < length; j++) values.add(definition(in, defs));
        m.mercDefinitions.put(key, values);
      }
      readLists(in, m.npcLists, defs, m.runtimeMillis);
      count = count(in); previous = -1;
      for (int i = 0; i < count; i++) {
        int key = ordered(in, previous); previous = key; IntMap<NpcMercenaryList> lists = new IntMap<>();
        readLists(in, lists, defs, m.runtimeMillis); m.playerLists.put(key, lists);
      }
      count = count(in); previous = -1; Set<Integer> entities = new HashSet<>();
      for (int i = 0; i < count; i++) {
        int key = ordered(in, previous); previous = key; ActiveMercenary a = new ActiveMercenary();
        a.ownerId = in.readInt(); a.entityId = in.readInt(); a.definition = definition(in, defs);
        a.nameId = in.readInt(); a.seed = in.readInt(); a.state = in.readInt(); a.level = in.readInt();
        a.experience = in.readLong(); a.currentLife = in.readInt(); a.maxLife = in.readInt();
        for (int j = 0; j < 3; j++) if (in.readInt() != 0) throw new IllegalArgumentException("hireling equipment graph");
        a.hireTime = in.readLong();
        if (a.ownerId != key || a.entityId < -1 || a.entityId >= 0 && !entities.add(a.entityId)
            || a.state != MercenaryManager.STATE_HIRED && a.state != MercenaryManager.STATE_DEAD
            || a.entityId == -1 && a.state != MercenaryManager.STATE_DEAD || a.level < 1 || a.level > 98
            || a.experience < 0 || a.experience > 0xffffffffL || a.currentLife < 0 || a.maxLife < 0
            || a.hireTime < 0 || a.hireTime > m.runtimeMillis) throw new IllegalArgumentException("hireling active record");
        m.playerMercs.put(key, a);
      }
      if (graph(m).size() != defs.size()) throw new IllegalArgumentException("unreachable hireling definition");
      if (in.read() != -1) throw new IllegalArgumentException("trailing hireling manager");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated hireling manager", e); }
  }

  private static void writeLists(DataOutputStream out, IntMap<NpcMercenaryList> lists,
      IdentityHashMap<MercenaryDefinition, Integer> ids) throws IOException {
    out.writeInt(lists.size);
    for (java.util.Map.Entry<Integer, NpcMercenaryList> e : sorted(lists).entrySet()) {
      NpcMercenaryList l = e.getValue(); out.writeInt(e.getKey()); out.writeInt(l.npcId); out.writeInt(l.mercType);
      out.writeLong(l.lastRefreshTime); out.writeInt(l.available.size);
      for (AvailableMercenary a : l.available) {
        out.writeInt(ids.get(a.definition)); out.writeInt(a.seed); out.writeInt(a.nameId);
        out.writeBoolean(a.hired); out.writeInt(a.level);
      }
    }
  }
  private static void readLists(DataInputStream in, IntMap<NpcMercenaryList> lists,
      List<MercenaryDefinition> defs, long now) throws IOException {
    int n = count(in), previous = -1;
    for (int i = 0; i < n; i++) {
      int key = ordered(in, previous); previous = key; NpcMercenaryList l = new NpcMercenaryList();
      l.npcId = in.readInt(); l.mercType = in.readInt(); l.lastRefreshTime = in.readLong();
      if (key != l.npcId || l.mercType != MercenaryManager.mercenaryTypeForNpc(key)
          || l.lastRefreshTime < 0 || l.lastRefreshTime > now) throw new IllegalArgumentException("hireling roster identity/time");
      int length = count(in);
      for (int j = 0; j < length; j++) {
        AvailableMercenary a = new AvailableMercenary(); a.definition = definition(in, defs);
        a.seed = in.readInt(); a.nameId = in.readInt(); a.hired = CheckpointIO.readBoolean(in); a.level = in.readInt();
        if (a.level < 1 || a.definition.mercType != l.mercType) throw new IllegalArgumentException("hireling offer identity/level");
        l.available.add(a);
      }
      lists.put(key, l);
    }
  }
  private static void restoreLists(IntMap<NpcMercenaryList> live, IntMap<NpcMercenaryList> source,
      IdentityHashMap<MercenaryDefinition, MercenaryDefinition> defs) {
    for (int key : removed(live, source)) live.remove(key);
    for (java.util.Map.Entry<Integer, NpcMercenaryList> e : sorted(source).entrySet()) {
      NpcMercenaryList a = e.getValue(), b = live.get(e.getKey());
      if (b == null) live.put(e.getKey(), b = new NpcMercenaryList());
      b.npcId = a.npcId; b.mercType = a.mercType; b.lastRefreshTime = a.lastRefreshTime;
      while (b.available.size > a.available.size) b.available.pop();
      for (int i = 0; i < a.available.size; i++) {
        AvailableMercenary x = a.available.get(i), y;
        if (i < b.available.size) y = b.available.get(i); else b.available.add(y = new AvailableMercenary());
        y.definition = defs.get(x.definition); y.seed = x.seed; y.nameId = x.nameId; y.hired = x.hired; y.level = x.level;
      }
    }
  }
  private static List<MercenaryDefinition> graph(MercenaryManager m) {
    List<MercenaryDefinition> defs = new ArrayList<>(); Set<MercenaryDefinition> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Array<MercenaryDefinition> list : sorted(m.mercDefinitions).values()) for (MercenaryDefinition d : list) add(defs, seen, d);
    List<IntMap<NpcMercenaryList>> domains = new ArrayList<>(); domains.add(m.npcLists); domains.addAll(sorted(m.playerLists).values());
    for (IntMap<NpcMercenaryList> lists : domains) for (NpcMercenaryList l : sorted(lists).values())
      for (AvailableMercenary a : l.available) add(defs, seen, a.definition);
    for (ActiveMercenary a : sorted(m.playerMercs).values()) add(defs, seen, a.definition);
    return defs;
  }
  private static void add(List<MercenaryDefinition> graph, Set<MercenaryDefinition> seen, MercenaryDefinition d) {
    if (d == null) throw new IllegalStateException("null hireling definition"); if (seen.add(d)) graph.add(d);
  }
  private static MercenaryDefinition definition(DataInputStream in, List<MercenaryDefinition> defs) throws IOException {
    int id = in.readInt(); if (id < 0 || id >= defs.size()) throw new IllegalArgumentException("hireling definition reference"); return defs.get(id);
  }
  private static void writeDefinition(DataOutputStream o, MercenaryDefinition d) throws IOException {
    for (int value : new int[] {d.hirelingId, d.nativeGold, d.nativeLevel, d.mercType, d.nameId,
        d.baseStrength, d.baseDexterity, d.baseLife, d.baseDefense, d.baseAttackRating, d.baseDamageMin, d.baseDamageMax,
        d.strengthPerLevel, d.dexterityPerLevel, d.lifePerLevel, d.defensePerLevel, d.attackRatingPerLevel,
        d.damageMinPerLevel, d.damageMaxPerLevel, d.hireCostMultiplier, d.resurrectCostMultiplier}) o.writeInt(value);
    CheckpointIO.writeString(o, d.description); array(o, d.skills); array(o, d.baseResistances);
  }
  private static MercenaryDefinition readDefinition(DataInputStream i) throws IOException {
    MercenaryDefinition d = new MercenaryDefinition();
    d.hirelingId = i.readInt(); d.nativeGold = i.readInt(); d.nativeLevel = i.readInt(); d.mercType = i.readInt(); d.nameId = i.readInt();
    d.baseStrength = i.readInt(); d.baseDexterity = i.readInt(); d.baseLife = i.readInt(); d.baseDefense = i.readInt();
    d.baseAttackRating = i.readInt(); d.baseDamageMin = i.readInt(); d.baseDamageMax = i.readInt();
    d.strengthPerLevel = i.readInt(); d.dexterityPerLevel = i.readInt(); d.lifePerLevel = i.readInt(); d.defensePerLevel = i.readInt();
    d.attackRatingPerLevel = i.readInt(); d.damageMinPerLevel = i.readInt(); d.damageMaxPerLevel = i.readInt();
    d.hireCostMultiplier = i.readInt(); d.resurrectCostMultiplier = i.readInt();
    d.description = CheckpointIO.readString(i); d.skills = array(i); d.baseResistances = array(i);
    if (d.hirelingId < -1 || d.mercType < 0 || d.mercType > 3) throw new IllegalArgumentException("hireling definition type"); return d;
  }
  private static void copyDefinition(MercenaryDefinition a, MercenaryDefinition b) {
    b.hirelingId = a.hirelingId; b.nativeGold = a.nativeGold; b.nativeLevel = a.nativeLevel; b.mercType = a.mercType; b.nameId = a.nameId;
    b.baseStrength = a.baseStrength; b.baseDexterity = a.baseDexterity; b.baseLife = a.baseLife; b.baseDefense = a.baseDefense;
    b.baseAttackRating = a.baseAttackRating; b.baseDamageMin = a.baseDamageMin; b.baseDamageMax = a.baseDamageMax;
    b.strengthPerLevel = a.strengthPerLevel; b.dexterityPerLevel = a.dexterityPerLevel; b.lifePerLevel = a.lifePerLevel;
    b.defensePerLevel = a.defensePerLevel; b.attackRatingPerLevel = a.attackRatingPerLevel;
    b.damageMinPerLevel = a.damageMinPerLevel; b.damageMaxPerLevel = a.damageMaxPerLevel;
    b.hireCostMultiplier = a.hireCostMultiplier; b.resurrectCostMultiplier = a.resurrectCostMultiplier;
    b.description = a.description; b.skills = copy(a.skills, b.skills); b.baseResistances = copy(a.baseResistances, b.baseResistances);
  }
  private static int[] copy(int[] a, int[] b) {
    if (a == null) return null; if (b == null || a.length != b.length) return a.clone(); System.arraycopy(a, 0, b, 0, a.length); return b;
  }
  private static void array(DataOutputStream o, int[] values) throws IOException {
    o.writeBoolean(values != null); if (values != null) { o.writeInt(values.length); for (int value : values) o.writeInt(value); }
  }
  private static int[] array(DataInputStream i) throws IOException {
    if (!CheckpointIO.readBoolean(i)) return null; int[] values = new int[CheckpointIO.count(i, 1024)];
    for (int j = 0; j < values.length; j++) values[j] = i.readInt(); return values;
  }
  private static int count(DataInputStream in) throws IOException {
    int count = CheckpointIO.count(in, 100000); if (count > in.available() / 4) throw new IllegalArgumentException("hireling count"); return count;
  }
  private static int ordered(DataInputStream in, int previous) throws IOException {
    int key = in.readInt(); if (key <= previous) throw new IllegalArgumentException("hireling key order"); return key;
  }
  private static <T> TreeMap<Integer, T> sorted(IntMap<T> map) {
    TreeMap<Integer, T> result = new TreeMap<>(); for (IntMap.Entry<T> e : map) result.put(e.key, e.value); return result;
  }
  private static <T> Set<Integer> removed(IntMap<T> live, IntMap<T> desired) {
    Set<Integer> keys = new TreeSet<>(sorted(live).keySet()); keys.removeAll(sorted(desired).keySet()); return keys;
  }
  private static final class Decoded {
    final MercenaryManager manager = new MercenaryManager(); byte[] table; int state; long draws;
  }
}
