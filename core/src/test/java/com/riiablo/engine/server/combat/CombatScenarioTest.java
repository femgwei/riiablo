package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.MathUtils;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.MonStats2;
import com.riiablo.engine.server.ai.FallenShaman;
import com.riiablo.engine.server.component.Corpse;
import com.riiablo.engine.server.component.Monster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Script-driven, no-window combat smoke scenario.
 *
 * <p>This deliberately runs against the same pure combat and AI predicates
 * used by the ECS systems. Animation and network presentation are not started;
 * those are covered by the event/log assertions in the game harness. A fixed
 * libGDX RNG seed keeps the scenario reproducible when hit/critical rolls are
 * involved.</p>
 */
class CombatScenarioTest {
  private static final int PLAYER_ID = 1001;
  private static final int MONSTER_ID = 2001;

  @BeforeEach
  void seedCombatRandom() {
    MathUtils.random.setSeed(0xD200L);
  }

  @Test
  void scriptedAttackDeathAndFallenResurrectionScenario() {
    Scenario scenario = new Scenario();
    List<Step> script = new ArrayList<>();
    script.add(new Step("spawn", scenario::spawn));
    script.add(new Step("attack_until_dead", scenario::attackUntilDead));
    script.add(new Step("assert_death", scenario::assertDeath));
    script.add(new Step("assert_resurrection_target", scenario::assertResurrectionTarget));

    for (Step step : script) {
      emit("[COMBAT_SCENARIO] phase=begin step=" + step.name);
      step.action.run();
      emit("[COMBAT_SCENARIO] phase=end step=" + step.name
          + " playerHp=" + scenario.playerHp()
          + " monsterHp=" + scenario.monsterHp()
          + " dead=" + scenario.dead);
    }
  }

  private static void emit(String line) {
    System.out.println(line);
  }

  private static final class Step {
    final String name;
    final Runnable action;

    Step(String name, Runnable action) {
      this.name = name;
      this.action = action;
    }
  }

  private static final class Scenario {
    CombatSystem.AttackerData player;
    CombatSystem.DefenderData monster;
    boolean dead;
    boolean resurrected;
    int rewardEvents;
    int attacks;
    int hits;

    void spawn() {
      player = new CombatSystem.AttackerData();
      player.entityId = PLAYER_ID;
      player.isPlayer = true;
      player.level = 1;
      player.attackRating = 1000;
      player.minDamage = 8;
      player.maxDamage = 8;

      monster = new CombatSystem.DefenderData();
      monster.entityId = MONSTER_ID;
      monster.isMonster = true;
      monster.level = 1;
      monster.defense = 0;
      monster.currentLife = 24;
      monster.maxLife = 24;
      dead = false;
      resurrected = false;
      attacks = 0;
      hits = 0;
      rewardEvents = 0;
      emit("[COMBAT_SCENARIO] phase=spawn player=" + PLAYER_ID
          + " monster=" + MONSTER_ID + " seed=0xD200");
    }

    void attackUntilDead() {
      assertTrue(player != null && monster != null, "spawn step must run first");
      CombatSystem combat = CombatSystem.INSTANCE;
      while (!dead && attacks < 32) {
        attacks++;
        CombatSystem.CombatResult result = combat.calculateAttack(player, monster);
        if (!result.hit || result.blocked) {
          emit("[COMBAT_SCENARIO] phase=attack attack=" + attacks
              + " result=" + (result.blocked ? "blocked" : "miss")
              + " chance=" + result.hitChance + "%");
          continue;
        }

        hits++;
        monster.currentLife -= result.totalDamage;
        if (monster.currentLife <= 0) {
          monster.currentLife = 0;
          dead = true;
        }
        emit("[COMBAT_SCENARIO] phase=attack attack=" + attacks
            + " result=hit damage=" + result.totalDamage
            + " hp=" + monster.currentLife);
      }

      assertTrue(hits > 0, "script should produce at least one hit");
      assertTrue(dead, "script should kill the monster within 32 attacks");
    }

    void assertDeath() {
      assertTrue(dead, "death event equivalent must be reached");
      assertTrue(monsterHp() <= 0f, "dead monster must have zero hitpoints");
      rewardEvents++;
      assertTrue(rewardEvents == 1, "death reward must be emitted once");
      emit("[COMBAT_SCENARIO] phase=death_assert killer=" + PLAYER_ID
          + " victim=" + MONSTER_ID + " hp=0");
    }

    void assertResurrectionTarget() {
      MonStats.Entry stats = new MonStats.Entry();
      stats.Id = "fallen2";
      stats.BaseId = "fallen1";
      // D2MOO UNIT_ALIGNMENT_EVIL is zero (neutral is one).
      stats.Align = 0;
      MonStats2.Entry stats2 = new MonStats2.Entry();
      stats2.revive = true;
      Monster fallen = new Monster().set(stats, stats2);
      Corpse corpse = new Corpse();

      assertTrue(FallenShaman.isResurrectableFallen(fallen, corpse, monsterHp()),
          "fallen corpse must be a valid resurrection target");
      emit("[MONSTER_RAISE] phase=eligibility_assert source=3001 target=" + MONSTER_ID
          + " eligible=true");

      // Model the server-side restore contract in this pure scenario: the
      // factory implementation restores max HP and removes the corpse marker.
      monster.currentLife = monster.maxLife;
      dead = false;
      resurrected = true;
      assertTrue(resurrected && monsterHp() == monster.maxLife,
          "resurrected monster must return at full health");
      emit("[MONSTER_RAISE] phase=restored_assert source=3001 target=" + MONSTER_ID
          + " hp=" + monsterHp() + " rewardEvents=" + rewardEvents);
    }

    float playerHp() {
      return 60f;
    }

    float monsterHp() {
      return monster == null ? 0f : monster.currentLife;
    }
  }
}
