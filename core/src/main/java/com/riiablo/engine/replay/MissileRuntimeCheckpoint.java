package com.riiablo.engine.replay;

import com.artemis.World;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.NativePathIdentity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Versioned server-only projectile runtime, separate from EntitySync/D2S.
 * Explicit field codec: no reflection, Java serialization, owner lookup or RNG draw.
 * Row/asset handles are rebuilt by the native factory; lifetime references and
 * shared cast hit sets are rebound only after every entity has been assembled.
 */
public final class MissileRuntimeCheckpoint {
  private static final int VERSION = 1;
  public static final int MAX_BYTES = 1024 * 1024;
  private static final int MAX_TARGETS = 65536;
  private final byte[] bytes;

  private MissileRuntimeCheckpoint(byte[] bytes) { this.bytes = bytes; }
  public byte[] bytes() { return bytes.clone(); }
  public int missileId() { return decode(null).missileId; }
  public int ownerId() { return decode(null).missile.ownerId; }

  /** Schema audit must fail when Missile gains a field without an explicit decision. */
  public static Set<String> coveredFields() {
    return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
      "range", "ownerId", "nativeBaalWave", "rngState", "roomId",
      "authoritative", "presentationOnly", "distanceTraveled", "attackMinDamage", "attackMaxDamage",
      "attackRating", "masteryAttackRatingPercent", "masteryDamagePercent", "masteryCriticalChance", "damageSnapshot",
      "ordinaryWeaponSnapshot", "damageLevel", "skillId", "freezesTarget", "damageChannel",
      "onHitStateId", "onHitStateDuration", "impactDcc", "impactPalette", "impactSound",
      "freezeExplosionId", "usesAttackRating", "persistent", "areaRadiusOverride", "remainingFrames",
      "tickInterval", "tickFrames", "fixedElementalRate", "fixedElementalType", "elementalMinRateFixed",
      "elementalMaxRateFixed", "elementalPiercePercent", "elementalDamageRate", "elementalAttackerPlayer", "fireWallMaker",
      "fireWallSegmentsCreated", "diabWallMaker", "diabWallMoved", "diabWallFire", "diabWallParentId",
      "diabWallCastId", "fixedPoisonRate", "poisonMinRateFixed", "poisonMaxRateFixed", "poisonDurationFrames",
      "poisonPiercePercent", "poisonAttackerPlayer", "poisonCloudLoops", "poisonCloudTraveling", "targetId",
      "homing", "homingNextTurnFrame", "attachedEntityId", "attached", "nativeFrame",
      "clientFrame", "clientFlightInitialized", "clientRecyclerVineSpawned", "clientRecyclerExplosionSpawned", "nativeLifetimeFrames",
      "deathMaul", "wakeMaker", "wakeSpawned", "wakeTargetX", "wakeTargetY",
      "wakeDirectionX", "wakeDirectionY", "damageOwnerId", "chargedBoltPath", "chargedBoltMainDirection",
      "chargedBoltSeedLow", "chargedBoltSeedHigh", "chargedBoltNextTurnDistance", "chargedBoltTargetX", "chargedBoltTargetY",
      "blessedHammerPath", "blessedHammerPointIndex", "fistOfHeavensDelay", "fistOfHeavensTriggered", "chainHitsRemaining",
      "blizzardCenter", "blizzardSeed", "frozenOrbController", "frozenOrbTargetPhase", "frozenOrbNova",
      "frozenOrbTargetX", "frozenOrbTargetY", "meteorCenter", "druidFissureController", "druidFissureNextFrame",
      "druidFissureSeed", "druidVolcanoController", "druidVolcanoNextFrame", "druidVolcanoSeed", "thunderStormStrike",
      "thunderStormResolved", "chaosIcePath", "chaosIceSeed", "chaosIceX", "chaosIceY",
      "chaosIceNextTurnFrame", "hitFunctionTriggered", "impactPresentationTriggered", "rabiesController", "rabiesSourceId",
      "rabiesNextPulseFrame", "rabiesRawDamageFixed", "rabiesPoisonPierce", "rabiesAttackerPlayer", "rabiesContagionVisual",
      "damageMultiplier", "pierceEnabled", "pierceChance", "pierceRemaining", "lastCollideResolved",
      "start", "poisonCloudTarget", "blessedHammerOrigin", "frozenOrbOrigin", "acceleration",
      "missile", "missileDescriptor", "nativeBaalOwnerIdentity", "nativeSeed", "damage",
      "nativePhysical", "nativeElemental", "nextHitFrame", "hitTargets", "sharedHitTargets")));
  }

  public static MissileRuntimeCheckpoint fromBytes(byte[] bytes) {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
      throw new IllegalArgumentException("missile runtime length");
    }
    MissileRuntimeCheckpoint snapshot = new MissileRuntimeCheckpoint(bytes.clone());
    snapshot.decode(null); // Structural validation is resource-independent.
    return snapshot;
  }

  public static Map<Integer, MissileRuntimeCheckpoint> capture(World world, int[] orderedIds) {
    Map<Integer, MissileRuntimeCheckpoint> result = new TreeMap<>();
    IdentityHashMap<IntSet, Integer> groups = new IdentityHashMap<>();
    for (int id : orderedIds) {
      Missile missile = world.getMapper(Missile.class).get(id);
      if (missile == null) continue;
      if (missile.missile == null) throw new IllegalStateException("missile row missing: " + id);
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(VERSION);
        out.writeInt(missile.missile.Id);
        writeState(out, missile);
        out.writeInt(group(groups, missile.hitTargets));
        writeTargets(out, missile.hitTargets);
        out.writeInt(group(groups, missile.sharedHitTargets));
        if (missile.sharedHitTargets != null) writeTargets(out, missile.sharedHitTargets);
        out.writeByte(missile.nativeBaalOwnerIdentity == null ? 0
            : missile.nativeBaalOwnerIdentity.isCurrent(world, missile.ownerId) ? 1 : 2);
        out.flush();
        result.put(id, fromBytes(bytes.toByteArray()));
      } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    validate(result);
    return Collections.unmodifiableMap(result);
  }

  private static int group(IdentityHashMap<IntSet, Integer> groups, IntSet set) {
    if (set == null) return 0;
    Integer group = groups.get(set);
    if (group == null) groups.put(set, group = groups.size() + 1);
    return group;
  }

  /** Validate aliases across entries before any world mutation. */
  public static void validate(Map<Integer, MissileRuntimeCheckpoint> snapshots) {
    Map<Integer, int[]> groups = new HashMap<>();
    Set<Integer> anchors = new HashSet<>();
    for (MissileRuntimeCheckpoint snapshot : snapshots.values()) {
      Decoded d = snapshot.decode(null);
      if (!anchors.add(d.hitGroup)) throw new IllegalArgumentException("duplicate hit-set anchor");
      checkGroup(groups, d.hitGroup, d.hitTargets);
      if (d.sharedGroup != 0) checkGroup(groups, d.sharedGroup, d.sharedTargets);
    }
  }

  private static void checkGroup(Map<Integer, int[]> groups, int group, int[] values) {
    int[] previous = groups.putIfAbsent(group, values);
    if (previous != null && !Arrays.equals(previous, values)) {
      throw new IllegalArgumentException("conflicting shared hit-set contents");
    }
  }

  public static void restore(World world, Map<Integer, MissileRuntimeCheckpoint> snapshots) {
    validate(snapshots);
    // Check required host identities before touching any runtime.
    for (Map.Entry<Integer, MissileRuntimeCheckpoint> entry : snapshots.entrySet()) {
      Decoded d = entry.getValue().decode(null);
      Missile live = world.getMapper(Missile.class).get(entry.getKey());
      if (live == null || live.missile == null || live.missile.Id != d.missileId) {
        throw new IllegalStateException("checkpoint missile identity mismatch: " + entry.getKey());
      }
      if (d.ownerLifetime == 1) {
        NativePathIdentity owner = world.getMapper(NativePathIdentity.class).get(d.missile.ownerId);
        if (owner == null || owner.token() == null || !owner.token().isCurrent(world, d.missile.ownerId)) {
          throw new IllegalStateException("checkpoint missile owner lifetime missing");
        }
      }
      for (int[][] list : d.stats) for (int[] stat : list) {
        if (live.damage.table().entry((short) stat[0]) == null) {
          throw new IllegalArgumentException("unknown missile stat");
        }
      }
    }
    Map<Integer, IntSet> groups = new HashMap<>();
    Map<Integer, Decoded> decoded = new TreeMap<>();
    for (Map.Entry<Integer, MissileRuntimeCheckpoint> entry : snapshots.entrySet()) {
      Missile live = world.getMapper(Missile.class).get(entry.getKey());
      Decoded d = entry.getValue().decode(live);
      live.hitTargets.clear();
      for (int target : d.hitTargets) live.hitTargets.add(target);
      groups.put(d.hitGroup, live.hitTargets);
      live.damage.clear();
      applyStats(live.damage.base(), d.stats[0]);
      applyStats(live.damage.aggregate(), d.stats[1]);
      applyStats(live.damage.remaining(), d.stats[2]);
      live.damage.restoreAggregateRevision(d.revision);
      live.nativeBaalOwnerIdentity = d.ownerLifetime == 0 ? null : d.ownerLifetime == 1
          ? world.getMapper(NativePathIdentity.class).get(live.ownerId).token()
          : NativePathIdentity.retiredToken(world, live.ownerId);
      decoded.put(entry.getKey(), d);
    }
    // Some shared sets have no missile-owned anchor; others alias hitTargets
    // on another projectile. Both must retain identity, not just equal contents.
    for (Decoded d : decoded.values()) {
      if (d.sharedGroup == 0) { d.missile.sharedHitTargets = null; continue; }
      IntSet set = groups.get(d.sharedGroup);
      if (set == null) {
        set = new IntSet();
        for (int target : d.sharedTargets) set.add(target);
        groups.put(d.sharedGroup, set);
      }
      d.missile.sharedHitTargets = set;
    }
  }

  private static void writeState(DataOutputStream out, Missile m) throws IOException {
    writeFloat(out, m.range);
    out.writeInt(m.ownerId);
    out.writeInt(m.nativeBaalWave);
    out.writeInt(m.rngState);
    out.writeInt(m.roomId);
    out.writeBoolean(m.authoritative);
    out.writeBoolean(m.presentationOnly);
    writeFloat(out, m.distanceTraveled);
    out.writeInt(m.attackMinDamage);
    out.writeInt(m.attackMaxDamage);
    out.writeInt(m.attackRating);
    out.writeInt(m.masteryAttackRatingPercent);
    out.writeInt(m.masteryDamagePercent);
    out.writeInt(m.masteryCriticalChance);
    out.writeBoolean(m.damageSnapshot);
    out.writeBoolean(m.ordinaryWeaponSnapshot);
    out.writeInt(m.damageLevel);
    out.writeInt(m.skillId);
    out.writeBoolean(m.freezesTarget);
    out.writeInt(m.damageChannel);
    out.writeInt(m.onHitStateId);
    out.writeInt(m.onHitStateDuration);
    writeString(out, m.impactDcc);
    writeString(out, m.impactPalette);
    writeString(out, m.impactSound);
    out.writeInt(m.freezeExplosionId);
    out.writeBoolean(m.usesAttackRating);
    out.writeBoolean(m.persistent);
    writeFloat(out, m.areaRadiusOverride);
    out.writeInt(m.remainingFrames);
    out.writeInt(m.tickInterval);
    out.writeInt(m.tickFrames);
    out.writeBoolean(m.fixedElementalRate);
    out.writeInt(m.fixedElementalType);
    out.writeInt(m.elementalMinRateFixed);
    out.writeInt(m.elementalMaxRateFixed);
    out.writeInt(m.elementalPiercePercent);
    out.writeInt(m.elementalDamageRate);
    out.writeBoolean(m.elementalAttackerPlayer);
    out.writeBoolean(m.fireWallMaker);
    out.writeInt(m.fireWallSegmentsCreated);
    out.writeBoolean(m.diabWallMaker);
    out.writeBoolean(m.diabWallMoved);
    out.writeBoolean(m.diabWallFire);
    out.writeInt(m.diabWallParentId);
    out.writeInt(m.diabWallCastId);
    out.writeBoolean(m.fixedPoisonRate);
    out.writeInt(m.poisonMinRateFixed);
    out.writeInt(m.poisonMaxRateFixed);
    out.writeInt(m.poisonDurationFrames);
    out.writeInt(m.poisonPiercePercent);
    out.writeBoolean(m.poisonAttackerPlayer);
    out.writeInt(m.poisonCloudLoops);
    out.writeBoolean(m.poisonCloudTraveling);
    out.writeInt(m.targetId);
    out.writeBoolean(m.homing);
    out.writeInt(m.homingNextTurnFrame);
    out.writeInt(m.attachedEntityId);
    out.writeBoolean(m.attached);
    out.writeInt(m.nativeFrame);
    out.writeInt(m.clientFrame);
    out.writeBoolean(m.clientFlightInitialized);
    out.writeBoolean(m.clientRecyclerVineSpawned);
    out.writeBoolean(m.clientRecyclerExplosionSpawned);
    out.writeInt(m.nativeLifetimeFrames);
    out.writeBoolean(m.deathMaul);
    out.writeBoolean(m.wakeMaker);
    out.writeBoolean(m.wakeSpawned);
    writeFloat(out, m.wakeTargetX);
    writeFloat(out, m.wakeTargetY);
    writeFloat(out, m.wakeDirectionX);
    writeFloat(out, m.wakeDirectionY);
    out.writeInt(m.damageOwnerId);
    out.writeBoolean(m.chargedBoltPath);
    out.writeInt(m.chargedBoltMainDirection);
    out.writeInt(m.chargedBoltSeedLow);
    out.writeInt(m.chargedBoltSeedHigh);
    writeFloat(out, m.chargedBoltNextTurnDistance);
    out.writeInt(m.chargedBoltTargetX);
    out.writeInt(m.chargedBoltTargetY);
    out.writeBoolean(m.blessedHammerPath);
    out.writeInt(m.blessedHammerPointIndex);
    out.writeBoolean(m.fistOfHeavensDelay);
    out.writeBoolean(m.fistOfHeavensTriggered);
    out.writeInt(m.chainHitsRemaining);
    out.writeBoolean(m.blizzardCenter);
    out.writeInt(m.blizzardSeed);
    out.writeBoolean(m.frozenOrbController);
    out.writeInt(m.frozenOrbTargetPhase);
    out.writeBoolean(m.frozenOrbNova);
    out.writeInt(m.frozenOrbTargetX);
    out.writeInt(m.frozenOrbTargetY);
    out.writeBoolean(m.meteorCenter);
    out.writeBoolean(m.druidFissureController);
    out.writeInt(m.druidFissureNextFrame);
    out.writeInt(m.druidFissureSeed);
    out.writeBoolean(m.druidVolcanoController);
    out.writeInt(m.druidVolcanoNextFrame);
    out.writeInt(m.druidVolcanoSeed);
    out.writeBoolean(m.thunderStormStrike);
    out.writeBoolean(m.thunderStormResolved);
    out.writeBoolean(m.chaosIcePath);
    out.writeInt(m.chaosIceSeed);
    out.writeInt(m.chaosIceX);
    out.writeInt(m.chaosIceY);
    out.writeInt(m.chaosIceNextTurnFrame);
    out.writeBoolean(m.hitFunctionTriggered);
    out.writeBoolean(m.impactPresentationTriggered);
    out.writeBoolean(m.rabiesController);
    out.writeInt(m.rabiesSourceId);
    out.writeInt(m.rabiesNextPulseFrame);
    out.writeInt(m.rabiesRawDamageFixed);
    out.writeInt(m.rabiesPoisonPierce);
    out.writeBoolean(m.rabiesAttackerPlayer);
    out.writeBoolean(m.rabiesContagionVisual);
    writeFloat(out, m.damageMultiplier);
    out.writeBoolean(m.pierceEnabled);
    out.writeInt(m.pierceChance);
    out.writeInt(m.pierceRemaining);
    out.writeBoolean(m.lastCollideResolved);
    writeVector(out, m.start);
    writeVector(out, m.poisonCloudTarget);
    writeVector(out, m.blessedHammerOrigin);
    writeVector(out, m.frozenOrbOrigin);
    out.writeInt(m.acceleration.counter());
    out.writeInt(m.acceleration.acceleration());
    out.writeInt(m.acceleration.maximum());
    out.writeBoolean(m.nativeSeed != null);
    if (m.nativeSeed != null) {
      out.writeInt(m.nativeSeed.getNLowSeed()); out.writeInt(m.nativeSeed.getNHighSeed());
    }
    writePhysical(out, m.nativePhysical);
    writeElemental(out, m.nativeElemental);
    if (m.damage.list().numLists() != 0 || m.damage.hasCombatShieldContext()
        || m.damage.hasCombatRunningContext() || m.damage.difficultyResistanceIncluded()) {
      throw new IllegalStateException("unsupported projectile damage attribute context");
    }
    out.writeLong(m.damage.aggregateRevision());
    writeStats(out, m.damage.base());
    writeStats(out, m.damage.aggregate());
    writeStats(out, m.damage.remaining());
    TreeMap<Integer, Integer> nextHit = new TreeMap<>();
    for (IntIntMap.Entry entry : m.nextHitFrame.entries()) nextHit.put(entry.key, entry.value);
    if (nextHit.size() > MAX_TARGETS) throw new IllegalArgumentException("next-hit count");
    out.writeInt(nextHit.size());
    for (Map.Entry<Integer, Integer> entry : nextHit.entrySet()) {
      out.writeInt(entry.getKey()); out.writeInt(entry.getValue());
    }
  }

  private Decoded decode(Missile target) {
    try {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
      if (in.readInt() != VERSION) throw new IllegalArgumentException("missile runtime version");
      Decoded d = new Decoded(target == null ? new Missile() : target);
      d.missileId = in.readInt();
      if (d.missileId < 0) throw new IllegalArgumentException("missile row");
      Missile m = d.missile;
      m.range = readFloat(in);
      m.ownerId = in.readInt();
      m.nativeBaalWave = in.readInt();
      m.rngState = in.readInt();
      m.roomId = in.readInt();
      m.authoritative = presence(in);
      m.presentationOnly = presence(in);
      m.distanceTraveled = readFloat(in);
      m.attackMinDamage = in.readInt();
      m.attackMaxDamage = in.readInt();
      m.attackRating = in.readInt();
      m.masteryAttackRatingPercent = in.readInt();
      m.masteryDamagePercent = in.readInt();
      m.masteryCriticalChance = in.readInt();
      m.damageSnapshot = presence(in);
      m.ordinaryWeaponSnapshot = presence(in);
      m.damageLevel = in.readInt();
      m.skillId = in.readInt();
      m.freezesTarget = presence(in);
      m.damageChannel = in.readInt();
      m.onHitStateId = in.readInt();
      m.onHitStateDuration = in.readInt();
      m.impactDcc = readString(in);
      m.impactPalette = readString(in);
      m.impactSound = readString(in);
      m.freezeExplosionId = in.readInt();
      m.usesAttackRating = presence(in);
      m.persistent = presence(in);
      m.areaRadiusOverride = readFloat(in);
      m.remainingFrames = in.readInt();
      m.tickInterval = in.readInt();
      m.tickFrames = in.readInt();
      m.fixedElementalRate = presence(in);
      m.fixedElementalType = in.readInt();
      m.elementalMinRateFixed = in.readInt();
      m.elementalMaxRateFixed = in.readInt();
      m.elementalPiercePercent = in.readInt();
      m.elementalDamageRate = in.readInt();
      m.elementalAttackerPlayer = presence(in);
      m.fireWallMaker = presence(in);
      m.fireWallSegmentsCreated = in.readInt();
      m.diabWallMaker = presence(in);
      m.diabWallMoved = presence(in);
      m.diabWallFire = presence(in);
      m.diabWallParentId = in.readInt();
      m.diabWallCastId = in.readInt();
      m.fixedPoisonRate = presence(in);
      m.poisonMinRateFixed = in.readInt();
      m.poisonMaxRateFixed = in.readInt();
      m.poisonDurationFrames = in.readInt();
      m.poisonPiercePercent = in.readInt();
      m.poisonAttackerPlayer = presence(in);
      m.poisonCloudLoops = in.readInt();
      m.poisonCloudTraveling = presence(in);
      m.targetId = in.readInt();
      m.homing = presence(in);
      m.homingNextTurnFrame = in.readInt();
      m.attachedEntityId = in.readInt();
      m.attached = presence(in);
      m.nativeFrame = in.readInt();
      m.clientFrame = in.readInt();
      m.clientFlightInitialized = presence(in);
      m.clientRecyclerVineSpawned = presence(in);
      m.clientRecyclerExplosionSpawned = presence(in);
      m.nativeLifetimeFrames = in.readInt();
      m.deathMaul = presence(in);
      m.wakeMaker = presence(in);
      m.wakeSpawned = presence(in);
      m.wakeTargetX = readFloat(in);
      m.wakeTargetY = readFloat(in);
      m.wakeDirectionX = readFloat(in);
      m.wakeDirectionY = readFloat(in);
      m.damageOwnerId = in.readInt();
      m.chargedBoltPath = presence(in);
      m.chargedBoltMainDirection = in.readInt();
      m.chargedBoltSeedLow = in.readInt();
      m.chargedBoltSeedHigh = in.readInt();
      m.chargedBoltNextTurnDistance = readFloat(in);
      m.chargedBoltTargetX = in.readInt();
      m.chargedBoltTargetY = in.readInt();
      m.blessedHammerPath = presence(in);
      m.blessedHammerPointIndex = in.readInt();
      m.fistOfHeavensDelay = presence(in);
      m.fistOfHeavensTriggered = presence(in);
      m.chainHitsRemaining = in.readInt();
      m.blizzardCenter = presence(in);
      m.blizzardSeed = in.readInt();
      m.frozenOrbController = presence(in);
      m.frozenOrbTargetPhase = in.readInt();
      m.frozenOrbNova = presence(in);
      m.frozenOrbTargetX = in.readInt();
      m.frozenOrbTargetY = in.readInt();
      m.meteorCenter = presence(in);
      m.druidFissureController = presence(in);
      m.druidFissureNextFrame = in.readInt();
      m.druidFissureSeed = in.readInt();
      m.druidVolcanoController = presence(in);
      m.druidVolcanoNextFrame = in.readInt();
      m.druidVolcanoSeed = in.readInt();
      m.thunderStormStrike = presence(in);
      m.thunderStormResolved = presence(in);
      m.chaosIcePath = presence(in);
      m.chaosIceSeed = in.readInt();
      m.chaosIceX = in.readInt();
      m.chaosIceY = in.readInt();
      m.chaosIceNextTurnFrame = in.readInt();
      m.hitFunctionTriggered = presence(in);
      m.impactPresentationTriggered = presence(in);
      m.rabiesController = presence(in);
      m.rabiesSourceId = in.readInt();
      m.rabiesNextPulseFrame = in.readInt();
      m.rabiesRawDamageFixed = in.readInt();
      m.rabiesPoisonPierce = in.readInt();
      m.rabiesAttackerPlayer = presence(in);
      m.rabiesContagionVisual = presence(in);
      m.damageMultiplier = readFloat(in);
      m.pierceEnabled = presence(in);
      m.pierceChance = in.readInt();
      m.pierceRemaining = in.readInt();
      m.lastCollideResolved = presence(in);
      readVector(in, m.start);
      readVector(in, m.poisonCloudTarget);
      readVector(in, m.blessedHammerOrigin);
      readVector(in, m.frozenOrbOrigin);
      m.acceleration.restore(in.readInt(), in.readInt(), in.readInt());
      m.nativeSeed = presence(in) ? new D2Seed(in.readInt(), in.readInt()) : null;
      m.nativePhysical = readPhysical(in);
      m.nativeElemental = readElemental(in);
      d.revision = in.readLong();
      if (d.revision < 0) throw new IllegalArgumentException("damage revision");
      d.stats = new int[][][] {readStats(in), readStats(in), readStats(in)};
      int count = count(in, MAX_TARGETS);
      m.nextHitFrame.clear();
      int previous = -1;
      for (int i = 0; i < count; i++) {
        int id = in.readInt(), frame = in.readInt();
        if (id < 0 || id <= previous || frame < 0) throw new IllegalArgumentException("next-hit order");
        previous = id; m.nextHitFrame.put(id, frame);
      }
      d.hitGroup = in.readInt();
      if (d.hitGroup <= 0 || d.hitGroup > 1000000) throw new IllegalArgumentException("hit group");
      d.hitTargets = readTargets(in);
      d.sharedGroup = in.readInt();
      if (d.sharedGroup < 0 || d.sharedGroup > 1000000) throw new IllegalArgumentException("shared group");
      d.sharedTargets = d.sharedGroup == 0 ? new int[0] : readTargets(in);
      d.ownerLifetime = in.readUnsignedByte();
      if (d.ownerLifetime > 2 || (d.ownerLifetime != 0 && m.ownerId < 0)
          || m.ownerId < -1 || m.nativeFrame < 0 || m.nativeLifetimeFrames < 0) {
        throw new IllegalArgumentException("missile runtime identity/clock");
      }
      if (in.read() != -1) throw new IllegalArgumentException("trailing missile runtime");
      return d;
    } catch (IOException e) { throw new IllegalArgumentException("truncated missile runtime", e); }
  }

  private static final class Decoded {
    final Missile missile;
    int missileId, hitGroup, sharedGroup, ownerLifetime;
    int[] hitTargets, sharedTargets;
    int[][][] stats;
    long revision;
    Decoded(Missile missile) { this.missile = missile; }
  }

  private static void writePhysical(DataOutputStream out,
      com.riiablo.engine.server.combat.NativeMissilePhysicalDamage p) throws IOException {
    out.writeBoolean(p != null);
    if (p == null) return;
    out.writeInt(p.minimum); out.writeInt(p.maximum); out.writeInt(p.damagePercent);
    out.writeInt(p.demonPercent); out.writeInt(p.undeadPercent); out.writeBoolean(p.bypassUndead);
    out.writeBoolean(p.criticalCaptured); out.writeBoolean(p.creationCritical);
    out.writeBoolean(p.ownerCriticalCaptured); out.writeBoolean(p.ownerCritical);
  }

  private static com.riiablo.engine.server.combat.NativeMissilePhysicalDamage readPhysical(
      DataInputStream in) throws IOException {
    if (!presence(in)) return null;
    return com.riiablo.engine.server.combat.NativeMissilePhysicalDamage.restore(
        in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
        presence(in), presence(in), presence(in), presence(in), presence(in));
  }

  private static void writeElemental(DataOutputStream out,
      com.riiablo.engine.server.combat.NativeMissileElementalDamage p) throws IOException {
    out.writeBoolean(p != null);
    if (p == null) return;
    out.writeInt(p.type);
    for (int i = 0; i < 6; i++) { out.writeInt(p.minimum(i)); out.writeInt(p.maximum(i)); }
    out.writeInt(p.coldLength); out.writeInt(p.poisonLength); out.writeInt(p.poisonCount);
  }

  private static com.riiablo.engine.server.combat.NativeMissileElementalDamage readElemental(
      DataInputStream in) throws IOException {
    if (!presence(in)) return null;
    int type = in.readInt();
    int[] minimum = new int[6], maximum = new int[6];
    for (int i = 0; i < 6; i++) { minimum[i] = in.readInt(); maximum[i] = in.readInt(); }
    if (minimum[0] != 0 || maximum[0] != 0) throw new IllegalArgumentException("physical element slot");
    return com.riiablo.engine.server.combat.NativeMissileElementalDamage.restore(type,
        minimum, maximum, in.readInt(), in.readInt(), in.readInt());
  }

  private static void writeStats(DataOutputStream out, StatListRef list) throws IOException {
    if (list.size() > 32) throw new IllegalArgumentException("projectile stat capacity");
    out.writeInt(list.size());
    for (StatRef stat : list) {
      out.writeInt(stat.id()); out.writeInt(stat.encodedParams()); out.writeInt(stat.encodedValues());
    }
  }

  private static int[][] readStats(DataInputStream in) throws IOException {
    int[][] values = new int[count(in, 32)][3];
    int previousId = -1, previousParams = 0;
    for (int[] stat : values) {
      stat[0] = in.readInt(); stat[1] = in.readInt(); stat[2] = in.readInt();
      if (stat[0] < 0 || stat[0] > 511 || stat[0] < previousId
          || stat[0] == previousId && stat[1] <= previousParams) {
        throw new IllegalArgumentException("projectile stat identity");
      }
      previousId = stat[0]; previousParams = stat[1];
    }
    return values;
  }

  private static void applyStats(StatListRef list, int[][] stats) {
    for (int[] stat : stats) list.putEncoded((short) stat[0], stat[1], stat[2]);
  }

  private static void writeTargets(DataOutputStream out, IntSet targets) throws IOException {
    int[] ids = new int[targets.size];
    int index = 0;
    for (IntSet.IntSetIterator iterator = targets.iterator(); iterator.hasNext;) ids[index++] = iterator.next();
    Arrays.sort(ids);
    if (ids.length > MAX_TARGETS) throw new IllegalArgumentException("hit target count");
    out.writeInt(ids.length);
    for (int id : ids) out.writeInt(id);
  }

  private static int[] readTargets(DataInputStream in) throws IOException {
    int[] ids = new int[count(in, MAX_TARGETS)];
    int previous = -1;
    for (int i = 0; i < ids.length; i++) {
      ids[i] = in.readInt();
      if (ids[i] < 0 || ids[i] <= previous) throw new IllegalArgumentException("hit target order");
      previous = ids[i];
    }
    return ids;
  }

  private static int count(DataInputStream in, int max) throws IOException {
    int count = in.readInt();
    if (count < 0 || count > max) throw new IllegalArgumentException("missile collection count");
    return count;
  }

  private static boolean presence(DataInputStream in) throws IOException {
    int value = in.readUnsignedByte();
    if (value > 1) throw new IllegalArgumentException("missile presence/boolean");
    return value == 1;
  }

  private static void writeFloat(DataOutputStream out, float value) throws IOException {
    if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite missile value");
    out.writeFloat(value);
  }

  private static float readFloat(DataInputStream in) throws IOException {
    float value = in.readFloat();
    if (!Float.isFinite(value)) throw new IllegalArgumentException("nonfinite missile value");
    return value;
  }

  private static void writeVector(DataOutputStream out, Vector2 vector) throws IOException {
    writeFloat(out, vector.x); writeFloat(out, vector.y);
  }

  private static void readVector(DataInputStream in, Vector2 vector) throws IOException {
    vector.set(readFloat(in), readFloat(in));
  }

  private static void writeString(DataOutputStream out, String value) throws IOException {
    if (value == null) { out.writeInt(-1); return; }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 65536) throw new IllegalArgumentException("missile string length");
    out.writeInt(bytes.length); out.write(bytes);
  }

  private static String readString(DataInputStream in) throws IOException {
    int length = in.readInt();
    if (length == -1) return null;
    if (length < 0 || length > 65536) throw new IllegalArgumentException("missile string length");
    byte[] bytes = new byte[length]; in.readFully(bytes);
    return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
  }
}
