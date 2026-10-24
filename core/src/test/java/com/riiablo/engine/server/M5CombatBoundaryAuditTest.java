package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Final M5 source-level gate for the production combat and snapshot exits.
 *
 * <p>This deliberately audits wiring rather than gameplay values: the earlier
 * M5 batches own the 1.10f damage assertions, while this gate prevents a new
 * production path from silently bypassing settlement or sending mutable
 * damage requests as network state.
 */
class M5CombatBoundaryAuditTest {
  @Test
  void dedicatedServerBindsWorldScopedGameplayContext() throws IOException {
    String source = source("server/d2gs/src/main/java/com/riiablo/server/d2gs/D2GS.java");
    assertTrue(source.contains("new com.riiablo.engine.server.ServerGameplayContext("),
        "D2GS must create a world-scoped gameplay context");
    assertTrue(source.contains(".register(\"serverContext\", serverContext)"),
        "D2GS must register the context before constructing its World");
    assertTrue(source.indexOf("register(\"serverContext\", serverContext)")
            < source.indexOf("Riiablo.engine = world = new World(config)"),
        "serverContext registration must precede World construction");
  }

  @Test
  void productionCombatWritersUseSingleSettlementBoundary() throws IOException {
    for (String relative : List.of(
        "core/src/main/java/com/riiablo/engine/server/Actioneer.java",
        "core/src/main/java/com/riiablo/engine/server/MissileCollisionSystem.java",
        "core/src/main/java/com/riiablo/engine/server/ServerSkillSystem.java",
        "core/src/main/java/com/riiablo/engine/server/StateUpdater.java",
        "core/src/main/java/com/riiablo/engine/server/AuraEcsSystem.java",
        "core/src/main/java/com/riiablo/engine/server/AssassinTrapSystem.java",
        "core/src/main/java/com/riiablo/engine/server/object/NativeTrapFireSystem.java",
        "core/src/main/java/com/riiablo/engine/server/object/NativeShrineEffectSystem.java")) {
      String source = source(relative);
      assertTrue(source.contains("DamageSettlement.commit("),
          relative + " must settle damage through DamageSettlement");
    }
    String settlement = source("core/src/main/java/com/riiablo/engine/server/combat/DamageSettlement.java");
    assertTrue(settlement.contains("life.sub(damage)"),
        "DamageSettlement must remain the authoritative life-loss writer");
  }

  @Test
  void networkSnapshotExitCarriesVitalsAndNotMutableDamageRequests() throws IOException {
    String synchronizer = source(
        "server/d2gs/src/main/java/com/riiablo/server/d2gs/NetworkSynchronizer.java");
    assertTrue(synchronizer.contains("SerializationManager"));
    assertTrue(synchronizer.contains("serialize(entityId"));
    assertTrue(synchronizer.contains("MissileImpactEvent"));
    assertTrue(synchronizer.contains("SoundEvent"));
    assertFalse(synchronizer.contains("DamageEvent"),
        "network synchronizer must not transport mutable DamageEvent requests");
    assertFalse(synchronizer.contains("DamageAppliedEvent"),
        "network synchronizer must consume authoritative snapshots, not local damage callbacks");

    String serialization = source("core/src/main/java/com/riiablo/engine/server/SerializationManager.java");
    assertTrue(serialization.contains("new VitalsSerializer()"),
        "authoritative VitalsP serializer must be registered");
  }

  @Test
  void presentationConsumesCommittedFactsOnly() throws IOException {
    String damageHandler = source("core/src/main/java/com/riiablo/presentation/DamageHandler.java");
    String elemental = source(
        "core/src/main/java/com/riiablo/presentation/ElementalHitPresentationSystem.java");
    assertTrue(damageHandler.contains("DamageAppliedEvent"));
    assertTrue(elemental.contains("DamageAppliedEvent"));
    assertFalse(damageHandler.contains("onDamageEvent(DamageEvent"),
        "generic presentation must not subscribe to mutable pre-settlement requests");
    assertFalse(elemental.contains("onDamage(DamageEvent"),
        "elemental presentation must not subscribe to mutable pre-settlement requests");
  }

  private static String source(String relative) throws IOException {
    Path root = repositoryRoot();
    return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
  }

  private static Path repositoryRoot() {
    Path current = Paths.get("").toAbsolutePath().normalize();
    for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
      if (Files.isDirectory(candidate.resolve("core/src/main/java"))
          && Files.isDirectory(candidate.resolve("server/d2gs/src/main/java"))) {
        return candidate;
      }
    }
    throw new AssertionError("Unable to locate riiablo repository root from " + current);
  }
}
