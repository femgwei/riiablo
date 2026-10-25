package com.riiablo.engine.replay;

import com.artemis.World;
import com.artemis.annotations.Wire;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.engine.server.combat.NativeMonsterModeElements;
import com.riiablo.engine.server.component.MapWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.NativePathIdentity;
import com.riiablo.map.Map;
import java.io.*;
import java.util.*;

/** Explicit Monster component image. Not AI objects, collision caches, or Map/RoomEx state. */
public final class MonsterRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 16384;
  private final byte[] bytes;
  private MonsterRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public int monsterId() { return decode(null).row; }
  public boolean converted() { return decode(null).monster.converted; }
  public int conversionOwner() { return decode(null).monster.conversionOwnerId; }
  public boolean hasSeed() { return decode(null).monster.nativeSeed != null; }
  public boolean matchesSeed(int low, int high) {
    Monster m = decode(null).monster;
    return m.nativeSeed != null && m.rngState == low
        && m.nativeSeed.getNLowSeed() == low && m.nativeSeed.getNHighSeed() == high;
  }

  public static MonsterRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("monster runtime length");
    }
    MonsterRuntimeCheckpoint result = new MonsterRuntimeCheckpoint(bytes.clone());
    result.decode(null); return result;
  }

  public static MonsterRuntimeCheckpoint capture(World world, int id, Set<Integer> covered) {
    Monster m = world.getMapper(Monster.class).get(id);
    if (m == null) return null;
    if (m.monstats == null) throw new IllegalStateException("monster runtime row missing");
    int owner = lifetime(world, m.nativeOwnerId, m.nativeOwnerIdentity);
    int mount = lifetime(world, m.nativeRequestedMountId, m.nativeRequestedMountIdentity);
    checkReference(m.nativeOwnerId, owner, covered); checkReference(m.nativeRequestedMountId, mount, covered);
    int[] zone = null;
    if (m.spawnZone != null) {
      Map map = boundMap(world, id);
      if (!map.getZones().contains(m.spawnZone, true)) throw new IllegalStateException("foreign monster spawn zone");
      zone = zoneKey(m.spawnZone);
      if (resolveZone(map, zone) != m.spawnZone) throw new IllegalStateException("ambiguous monster spawn zone");
    }
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(VERSION); out.writeInt(m.monstats.hcIdx); CheckpointIO.writeString(out, m.monstats.Id);
      out.writeBoolean(m.monstats2 != null);
      if (m.monstats2 != null) CheckpointIO.writeString(out, m.monstats2.Id);
      writeState(out, m);
      out.writeByte(owner); out.writeByte(mount);
      out.writeBoolean(zone != null);
      if (zone != null) for (int value : zone) out.writeInt(value);
      out.flush(); return fromBytes(bytes.toByteArray());
    } catch (IOException impossible) { throw new IllegalStateException(impossible); }
  }

  public void validateCoverage(Set<Integer> covered) {
    Decoded d = decode(null);
    checkReference(d.monster.nativeOwnerId, d.ownerLifetime, covered);
    checkReference(d.monster.nativeRequestedMountId, d.mountLifetime, covered);
  }
  private static void checkReference(int id, int lifetime, Set<Integer> covered) {
    if (lifetime == 1 && !covered.contains(id)) throw new IllegalArgumentException("monster current reference outside checkpoint");
  }

  /** Every dependency is checked before the caller applies any unit-state record. */
  public void validateRestore(World world, int id) {
    Decoded d = decode(null); Monster live = world.getMapper(Monster.class).get(id);
    if (live == null || live.monstats == null || live.monstats.hcIdx != d.row
        || !Objects.equals(live.monstats.Id, d.rowKey)
        || (live.monstats2 != null) != d.hasRow2
        || d.hasRow2 && !Objects.equals(live.monstats2.Id, d.row2)) {
      throw new IllegalStateException("monster runtime row identity mismatch: " + id);
    }
    token(world, d.monster.nativeOwnerId, d.ownerLifetime);
    token(world, d.monster.nativeRequestedMountId, d.mountLifetime);
    if (d.zone != null) resolveZone(boundMap(world, id), d.zone);
  }

  public void restore(World world, int id) {
    validateRestore(world, id);
    Monster live = world.getMapper(Monster.class).get(id);
    Decoded d = decode(live); // Exact scalar assignment, no set(), clamps, RNG or mode entry.
    live.nativeOwnerIdentity = token(world, live.nativeOwnerId, d.ownerLifetime);
    live.nativeRequestedMountIdentity = token(world, live.nativeRequestedMountId, d.mountLifetime);
    live.spawnZone = d.zone == null ? null : resolveZone(boundMap(world, id), d.zone);
  }

  private static int lifetime(World world, int id, NativePathIdentity.Token token) {
    return token == null ? 0 : token.isCurrent(world, id) ? 1 : 2;
  }
  private static NativePathIdentity.Token token(World world, int id, int lifetime) {
    if (lifetime == 0) return null;
    if (lifetime == 2) return NativePathIdentity.retiredToken(world, id);
    NativePathIdentity identity = world.getMapper(NativePathIdentity.class).get(id);
    if (identity == null || identity.token() == null || !identity.token().isCurrent(world, id)) {
      throw new IllegalStateException("monster current lifetime missing: " + id);
    }
    return identity.token();
  }
  private static final class Binding { @Wire(name = "map", failOnNull = false) Map map; }
  private static Map boundMap(World world, int id) {
    Binding binding = new Binding(); world.inject(binding);
    MapWrapper wrapper = world.getMapper(MapWrapper.class).get(id);
    if (binding.map == null || wrapper == null || wrapper.map != binding.map) {
      throw new IllegalStateException("monster spawn map owner missing/mismatched");
    }
    return binding.map;
  }
  private static int[] zoneKey(Map.Zone z) {
    return new int[] {z.levelId(), z.levelSeed(), z.x(), z.y(), z.width(), z.height(), z.isSyntheticArena() ? 1 : 0};
  }
  private static Map.Zone resolveZone(Map map, int[] key) {
    Map.Zone match = null;
    for (Map.Zone zone : map.getZones()) if (Arrays.equals(key, zoneKey(zone))) {
      if (match != null) throw new IllegalStateException("ambiguous monster spawn zone");
      match = zone;
    }
    if (match == null) throw new IllegalStateException("monster spawn zone missing");
    return match;
  }

  private static void writeState(DataOutputStream o, Monster m) throws IOException {
    for (int value : m.componentVariants) o.writeInt(value);
    o.writeInt(m.rank); o.writeInt(m.championType); o.writeLong(m.affixes);
    CheckpointIO.writeFloat(o, m.chargedBoltsCooldown);
    o.writeBoolean(m.fireDeathExplosionScheduled); o.writeBoolean(m.coldDeathNovaScheduled);
    if (!Double.isFinite(m.lightningEnchantedLastFrame)) throw new IllegalArgumentException("nonfinite monster frame");
    o.writeDouble(m.lightningEnchantedLastFrame); o.writeInt(m.uniqueId);
    o.writeInt(m.baalWaveIndex); o.writeInt(m.baalWaveSuperUniqueId); o.writeInt(m.baalWaveRoomId); o.writeBoolean(m.baalWaveLeader);
    o.writeInt(m.minionOwnerId); o.writeInt(m.nativeSpawnClassId); o.writeInt(m.nativeOwnerId); o.writeInt(m.nativeRequestedMountId);
    o.writeInt(m.nativeSelfResurrections); o.writeBoolean(m.nativeSelfResurrect); o.writeBoolean(m.nativeChargedBolts);
    o.writeInt(m.baalLinkedOwnerId); o.writeInt(m.baalClonesCreated);
    o.writeInt(m.rngState); o.writeBoolean(m.nativeSeed != null);
    if (m.nativeSeed != null) { o.writeInt(m.nativeSeed.getNLowSeed()); o.writeInt(m.nativeSeed.getNHighSeed()); }
    o.writeBoolean(m.modeElements != null);
    if (m.modeElements != null) {
      NativeMonsterModeElements e = m.modeElements;
      for (int value : e.minimum) o.writeInt(value);
      for (int value : e.maximum) o.writeInt(value);
      o.writeInt(e.coldLength); o.writeInt(e.poisonLength); o.writeInt(e.matchedRows); o.writeBoolean(e.unsupportedType);
      for (int value : e.drainMinimum) o.writeInt(value);
      for (int value : e.drainMaximum) o.writeInt(value);
    }
    o.writeInt(m.nativePackId); o.writeBoolean(m.nativePackLeader);
    o.writeInt(m.attack2MinDamage); o.writeInt(m.attack2MaxDamage); o.writeInt(m.attack2ToHit);
    o.writeBoolean(m.resurrected); o.writeInt(m.resurrectedBy); o.writeBoolean(m.playerRevive); o.writeBoolean(m.shatteredAtDeath);
    o.writeBoolean(m.frogNoPresence); o.writeBoolean(m.vultureAirborne);
    o.writeBoolean(m.nativeCorpseCollision); o.writeBoolean(m.nativeRestoredCollisionPattern);
    o.writeBoolean(m.frogSmallPresence); o.writeBoolean(m.frogSpawnMarkerInitialized); o.writeBoolean(m.frogSpawnMarkerMoved);
    o.writeInt(m.frogSpawnMarkerX); o.writeInt(m.frogSpawnMarkerY);
    CheckpointIO.writeFloat(o, m.spawnX); CheckpointIO.writeFloat(o, m.spawnY); o.writeBoolean(m.hasSpawnAnchor);
    o.writeBoolean(m.converted); o.writeInt(m.conversionOwnerId);
  }

  private static void readState(DataInputStream in, Monster m) throws IOException {
    for (int i = 0; i < m.componentVariants.length; i++) m.componentVariants[i] = in.readInt();
    m.rank = in.readInt(); m.championType = in.readInt(); m.affixes = in.readLong();
    m.chargedBoltsCooldown = CheckpointIO.readFloat(in);
    m.fireDeathExplosionScheduled = CheckpointIO.readBoolean(in); m.coldDeathNovaScheduled = CheckpointIO.readBoolean(in);
    m.lightningEnchantedLastFrame = in.readDouble(); m.uniqueId = in.readInt();
    if (!Double.isFinite(m.lightningEnchantedLastFrame)) throw new IllegalArgumentException("nonfinite monster frame");
    m.baalWaveIndex = in.readInt(); m.baalWaveSuperUniqueId = in.readInt(); m.baalWaveRoomId = in.readInt(); m.baalWaveLeader = CheckpointIO.readBoolean(in);
    m.minionOwnerId = in.readInt(); m.nativeSpawnClassId = in.readInt(); m.nativeOwnerId = in.readInt(); m.nativeRequestedMountId = in.readInt();
    m.nativeSelfResurrections = in.readInt(); m.nativeSelfResurrect = CheckpointIO.readBoolean(in); m.nativeChargedBolts = CheckpointIO.readBoolean(in);
    m.baalLinkedOwnerId = in.readInt(); m.baalClonesCreated = in.readInt();
    m.rngState = in.readInt();
    if (CheckpointIO.readBoolean(in)) {
      int low = in.readInt(), high = in.readInt();
      if (m.nativeSeed == null) m.nativeSeed = new D2Seed(low, high); else m.nativeSeed.setSeeds(low, high);
    } else m.nativeSeed = null;
    m.modeElements = null;
    if (CheckpointIO.readBoolean(in)) {
      NativeMonsterModeElements e = m.modeElements = new NativeMonsterModeElements();
      for (int i = 0; i < e.minimum.length; i++) e.minimum[i] = in.readInt();
      for (int i = 0; i < e.maximum.length; i++) e.maximum[i] = in.readInt();
      e.coldLength = in.readInt(); e.poisonLength = in.readInt(); e.matchedRows = in.readInt(); e.unsupportedType = CheckpointIO.readBoolean(in);
      for (int i = 0; i < e.drainMinimum.length; i++) e.drainMinimum[i] = in.readInt();
      for (int i = 0; i < e.drainMaximum.length; i++) e.drainMaximum[i] = in.readInt();
    }
    m.nativePackId = in.readInt(); m.nativePackLeader = CheckpointIO.readBoolean(in);
    m.attack2MinDamage = in.readInt(); m.attack2MaxDamage = in.readInt(); m.attack2ToHit = in.readInt();
    m.resurrected = CheckpointIO.readBoolean(in); m.resurrectedBy = in.readInt(); m.playerRevive = CheckpointIO.readBoolean(in); m.shatteredAtDeath = CheckpointIO.readBoolean(in);
    m.frogNoPresence = CheckpointIO.readBoolean(in); m.vultureAirborne = CheckpointIO.readBoolean(in);
    m.nativeCorpseCollision = CheckpointIO.readBoolean(in); m.nativeRestoredCollisionPattern = CheckpointIO.readBoolean(in);
    m.frogSmallPresence = CheckpointIO.readBoolean(in); m.frogSpawnMarkerInitialized = CheckpointIO.readBoolean(in); m.frogSpawnMarkerMoved = CheckpointIO.readBoolean(in);
    m.frogSpawnMarkerX = in.readInt(); m.frogSpawnMarkerY = in.readInt();
    m.spawnX = CheckpointIO.readFloat(in); m.spawnY = CheckpointIO.readFloat(in); m.hasSpawnAnchor = CheckpointIO.readBoolean(in);
    m.converted = CheckpointIO.readBoolean(in); m.conversionOwnerId = in.readInt();
    if (m.nativeOwnerId < -1 || m.nativeRequestedMountId < -1 || m.conversionOwnerId < -1) throw new IllegalArgumentException("monster reference ID");
  }

  private Decoded decode(Monster live) {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("monster runtime version");
      Decoded d = new Decoded(); d.monster = live == null ? new Monster() : live;
      d.row = in.readInt(); d.rowKey = CheckpointIO.readString(in);
      d.hasRow2 = CheckpointIO.readBoolean(in); if (d.hasRow2) d.row2 = CheckpointIO.readString(in);
      if (d.row < 0) throw new IllegalArgumentException("monster row identity");
      readState(in, d.monster);
      d.ownerLifetime = in.readUnsignedByte(); d.mountLifetime = in.readUnsignedByte();
      if (d.ownerLifetime > 2 || d.mountLifetime > 2
          || d.ownerLifetime != 0 && d.monster.nativeOwnerId < 0
          || d.mountLifetime != 0 && d.monster.nativeRequestedMountId < 0) throw new IllegalArgumentException("monster lifetime");
      if (CheckpointIO.readBoolean(in)) {
        d.zone = new int[7]; for (int i = 0; i < d.zone.length; i++) d.zone[i] = in.readInt();
        if (d.zone[0] < 0 || d.zone[4] <= 0 || d.zone[5] <= 0 || d.zone[6] < 0 || d.zone[6] > 1) {
          throw new IllegalArgumentException("monster spawn zone identity");
        }
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing monster runtime");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated monster runtime", e); }
  }
  private static final class Decoded {
    Monster monster;
    int row, ownerLifetime, mountLifetime;
    String rowKey, row2;
    boolean hasRow2;
    int[] zone;
  }
}
