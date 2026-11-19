package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.BaseSystem;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;

/** Autonomous AI and real animation markers; only allocation/terrain and scenario stats are fixtures.
 * These are host integration checks, not additional original-executable observations. */
class FallenRuntimeAttackChainTest extends RiiabloTest {
  static final class Trace extends BaseSystem {
    int tick, attempts, hits, misses, markers, packets;
    float damage;
    final List<String> events = new ArrayList<>();
    @Subscribe public void marker(AnimDataKeyframeEvent e) {
      if (e.keyframe == Engine.KEYFRAME_ATK) { markers++; events.add("{\"tick\":" + tick + ",\"event\":\"attack_marker\"}"); }
    }
    @Subscribe public void melee(MeleeAttackEvent e) {
      attempts++; if (e.hit && !e.blocked) hits++; else misses++;
      events.add("{\"tick\":" + tick + ",\"event\":\"melee\",\"hit\":" + e.hit + ",\"blocked\":" + e.blocked + "}");
    }
    @Subscribe public void damage(DamageEvent e) {
      packets++; damage += e.damage;
      events.add("{\"tick\":" + tick + ",\"event\":\"damage\",\"damage\":" + e.damage + "}");
    }
    @Override protected void processSystem() {}
  }

  static final class Run implements AutoCloseable {
    final Trace trace = new Trace();
    final MonsterBehaviorScenarioIntegrationTest.Scene scene =
        new MonsterBehaviorScenarioIntegrationTest.Scene("Fallen", 8, true, true, trace);
    final List<String> frames = new ArrayList<>();
    Run(int criticalChance) throws Exception {
      // Do not mutate the shared MPQ table. A controlled Crit value isolates the branch.
      Monster monster = scene.world.getMapper(Monster.class).get(scene.actor);
      MonStats.Entry copy = new MonStats.Entry();
      for (Field field : MonStats.Entry.class.getFields()) {
        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) field.set(copy, field.get(monster.monstats));
      }
      copy.Crit = criticalChance; monster.monstats = copy;
    }
    void tick() {
      trace.tick++;
      scene.tick(false); // Never inject a keyframe or bypass Actioneer.
      Monster m = scene.world.getMapper(Monster.class).get(scene.actor);
      frames.add("{\"tick\":" + trace.tick + ",\"mode\":"
          + scene.world.getMapper(CofReference.class).get(scene.actor).mode
          + ",\"x\":" + scene.position(scene.actor).x + ",\"y\":" + scene.position(scene.actor).y
          + ",\"hp\":" + scene.life(scene.player) + ",\"seed_low\":" + m.rngState
          + ",\"seed_high\":" + (m.nativeSeed == null ? 0 : m.nativeSeed.getNHighSeed()) + "}");
    }
    void untilAttack() {
      for (int n = 0; n < 600 && !scene.world.getMapper(Casting.class).has(scene.actor); n++) tick();
      assertTrue(scene.world.getMapper(Casting.class).has(scene.actor), scene.diagnostic());
      assertTrue(scene.position(scene.actor).x > 10, "autonomous approach must move the actor");
      assertEquals(0, trace.attempts, "damage must wait for the animation marker");
    }
    void save(String name) throws Exception {
      Path path = Paths.get("build/reports/fallen-runtime-chain", name + ".json");
      Files.createDirectories(path.getParent());
      String json = "{\"scope\":\"host AI and native animation integration; fixture terrain/stats\",\"frames\":["
          + String.join(",", frames) + "],\"events\":[" + String.join(",", trace.events) + "]}";
      Files.write(path, json.getBytes(StandardCharsets.UTF_8));
    }
    @Override public void close() { scene.close(); }
  }

  private float successfulAttack(int critical, String report) throws Exception {
    try (Run run = new Run(critical)) {
      run.untilAttack();
      for (int n = 0; n < 120 && run.trace.hits == 0; n++) run.tick();
      assertTrue(run.trace.markers > 0);
      assertTrue(run.trace.hits > 0, run.scene.diagnostic());
      assertEquals(1, run.trace.packets, "one packet for the first successful attack");
      float loss = MonsterBehaviorScenarioIntegrationTest.Scene.LIFE - run.scene.life(run.scene.player);
      assertEquals(run.trace.damage, loss, 0f, "event damage must reach authoritative HP exactly once");
      assertTrue(loss > 0);
      assertNotNull(run.scene.world.getMapper(Monster.class).get(run.scene.actor).nativeSeed);
      run.save(report);
      return loss;
    }
  }

  @Test void autonomousApproachAndNativeMarkerReachHpOnce() throws Exception {
    successfulAttack(0, "normal-hit");
  }

  @Test void monsterCriticalDoublesTheSameAutonomousAttack() throws Exception {
    float normal = successfulAttack(0, "critical-control");
    float critical = successfulAttack(100, "critical-hit");
    assertEquals(normal * 2, critical, 0f);
  }

  @Test void nativeAnimationMissProducesAttemptButNoDamagePacket() throws Exception {
    try (Run run = new Run(0)) {
      Attributes target = run.scene.world.getMapper(AttributesWrapper.class).get(run.scene.player).attrs;
      target.base().put(Stat.level, 99); target.base().put(Stat.armorclass, 1000000); target.reset();
      run.untilAttack();
      for (int n = 0; n < 80 && run.trace.attempts == 0; n++) run.tick();
      assertEquals(1, run.trace.attempts); assertEquals(1, run.trace.misses);
      assertEquals(0, run.trace.packets);
      assertEquals(MonsterBehaviorScenarioIntegrationTest.Scene.LIFE, run.scene.life(run.scene.player));
      run.save("miss");
    }
  }

  @Test void leavingRangeBeforeNativeMarkerCancelsDamage() throws Exception {
    try (Run run = new Run(0)) {
      run.untilAttack();
      run.scene.position(run.scene.player).set(1000, 1000);
      for (int n = 0; n < 24; n++) run.tick();
      assertTrue(run.trace.markers > 0, "native animation must actually reach its action point");
      assertEquals(0, run.trace.packets);
      assertEquals(MonsterBehaviorScenarioIntegrationTest.Scene.LIFE, run.scene.life(run.scene.player));
      run.save("escaped-target");
    }
  }
}
