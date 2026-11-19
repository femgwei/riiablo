package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.artemis.BaseSystem;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.event.AnimDataKeyframeEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.MeleeAttackEvent;
import java.util.*;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Cross-monster autonomous animation/attack smoke; no injected keyframes. */
class MultiMonsterRuntimeAttackChainTest extends RiiabloTest {
  static final class Trace extends BaseSystem {
    int ticks, markers, attempts, hits, packets;
    float damage;
    @Subscribe public void marker(AnimDataKeyframeEvent e) { if (e.keyframe == Engine.KEYFRAME_ATK) markers++; }
    @Subscribe public void melee(MeleeAttackEvent e) { attempts++; if (e.hit && !e.blocked) hits++; }
    @Subscribe public void damage(DamageEvent e) { packets++; damage += e.damage; }
    @Override protected void processSystem() {}
  }

  @ParameterizedTest(name = "{0}: autonomous native attack chain")
  @ValueSource(strings = {"Zombie", "Skeleton", "Fallen", "CorruptRogue", "Goatman", "Brute"})
  void autonomousMonsterReachesAnimationAndAuthoritativeDamage(String name) {
    Trace trace = new Trace();
    try (MonsterBehaviorScenarioIntegrationTest.Scene scene =
        new MonsterBehaviorScenarioIntegrationTest.Scene(name, 1, true, true, trace)) {
      float start = scene.life(scene.player);
      for (int i = 0; i < 600 && trace.hits == 0; i++) {
        trace.ticks++;
        scene.tick(false);
      }
      assertTrue(trace.markers > 0, name + " never reached an attack animation marker");
      assertTrue(trace.attempts > 0, name + " never emitted a melee attempt");
      assertTrue(trace.hits > 0, name + " had no successful autonomous attack: " + scene.diagnostic());
      assertEquals(1, trace.packets, name + " first hit must produce one packet");
      assertEquals(start - scene.life(scene.player), trace.damage, 0f,
          name + " damage packet must reach HP exactly once");
      Monster monster = scene.world.getMapper(Monster.class).get(scene.actor);
      assertNotNull(monster.nativeSeed, name + " must use the production unit seed");
    }
  }
}
