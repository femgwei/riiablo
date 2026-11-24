package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.skill.SkillId;
import com.riiablo.engine.server.state.StateId;
import org.junit.jupiter.api.Test;

/** Small regression gate for the first private-desktop Raven(221) capture. */
class DruidRavenOriginalParityTest extends RiiabloTest {
  @Test
  void originalMotionSeparatesCooldownApproachAndOwnerCircleWithoutSpendingIdlePecks() {
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/druid-raven-motion-1.10f.json"));
    JsonValue town = fixture.get("cases").get("town");
    JsonValue combat = fixture.get("cases").get("combat");
    assertTrue(town.getBoolean("background_verified"));
    assertTrue(combat.getBoolean("background_verified"));
    assertTrue(town.get("native_cleanup").getBoolean("detached"));
    assertTrue(combat.get("native_cleanup").getBoolean("detached"));
    assertTrue(town.getBoolean("owned_game_closed") && combat.getBoolean("owned_game_closed"));
    com.riiablo.codec.excel.MonStats.Entry row = Riiablo.files.monstats.get("druidhawk");
    int[] params = {row.aip1[0],row.aip2[0],row.aip3[0],row.aip4[0],row.aip5[0]};
    for (int i = 0; i < params.length; i++) assertEquals(params[i],
        town.get("raven_stats").get("ai_parameters").getInt(i));
    assertEquals(row.aidel[0], town.get("raven_stats").getInt("ai_delay"));
    assertEquals(0, town.get("attack_frames").size);
    assertTrue(town.getInt("distinct_positions") > 30);
    boolean circle = false, approach = false, cooldownMovement = false;
    for (JsonValue d : town.get("decisions")) {
      assertEquals(12, d.getInt("budget_after"));
      circle |= d.getInt("path_type_after") == 5 || d.getInt("path_type_after") == 6;
    }
    assertTrue(circle);
    for (JsonValue d : combat.get("decisions")) {
      if (d.getBoolean("attack")) {
        assertTrue(d.getLong("attack_due_before") < d.getLong("frame"));
        assertEquals(10L * params[2], d.getLong("attack_due_after") - d.getLong("frame"));
        assertEquals(d.getInt("budget_before") - 1, d.getInt("budget_after"));
      } else {
        approach |= d.getInt("path_type_after") == 13 && !d.getBoolean("in_combat");
        cooldownMovement |= d.getInt("mode_after") == Engine.Monster.MODE_WL
            && d.getLong("frame") <= d.getLong("attack_due_before");
      }
    }
    assertTrue(approach && cooldownMovement);
    for (JsonValue duration : combat.get("attack_durations"))
      if (!duration.isNull()) assertEquals(25, duration.asInt());
  }

  @Test
  void nativeRecoveryPredicateReplaysBothDefenderSeedWordsAndClientModes() {
    JsonValue fixture = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/druid-raven-hit-recovery-1.10f.json"));
    int hard = 0, soft = 0;
    for (JsonValue sample : fixture.get("cases")) {
      assertTrue(sample.getBoolean("background_verified"));
      assertTrue(sample.getBoolean("owned_game_closed"));
      assertTrue(sample.get("native_cleanup").getBoolean("detached"));
      boolean sawGh = false;
      for (JsonValue mode : sample.get("client_modes")) sawGh |= mode.asInt() == Engine.Monster.MODE_GH;
      assertEquals(sample.getBoolean("natural_life"), sawGh);
      for (JsonValue c : sample.get("recovery_cases")) {
        com.riiablo.engine.server.component.Monster unit = new com.riiablo.engine.server.component.Monster();
        NativeUnitRandom random = NativeUnitRandom.monster(unit, 1234, 1);
        random.restore(new NativeUnitRandom.Snapshot(
            (int) c.get("seed_before").getLong("low"), (int) c.get("seed_before").getLong("high")));
        java.util.List<Integer> masks = new java.util.ArrayList<>();
        boolean skip = com.riiablo.engine.server.combat.NativeHitRecovery.shouldSkip(
            c.getInt("total_fixed"), c.getInt("poison_fixed"), c.getInt("maxhp_fixed"),
            c.getInt("hit_class"), false, true, mask -> { masks.add(mask); return random.nextMask(mask); });
        assertEquals(c.getBoolean("skip_hard_recovery"), skip);
        assertEquals((int) c.get("seed_after").getLong("low"), random.state());
        assertEquals((int) c.get("seed_after").getLong("high"), random.high());
        assertEquals(c.get("rng_masks").size, masks.size());
        for (int i = 0; i < masks.size(); i++) assertEquals(c.get("rng_masks").getInt(i), masks.get(i).intValue());
        if (skip) { soft++; assertEquals(19, c.getInt("target_ai_state_after")); }
        else { hard++; assertEquals(Engine.Monster.MODE_GH, c.getInt("target_mode_after")); }
      }
    }
    assertEquals(2, hard);
    assertEquals(4, soft);
  }

  @Test
  void originalPeckPublishesDimVisionCastAndPersistentOverlays() {
    JsonValue evidence = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/druid-raven-feedback-1.10f.json"));
    assertTrue(evidence.getBoolean("background_verified"));
    assertTrue(evidence.getBoolean("owned_game_closed"));
    assertTrue(evidence.get("native_cleanup").getBoolean("detached"));
    assertEquals(-1, evidence.get("raven_stats").getInt("item_stupidity"));
    JsonValue applied = evidence.get("first_server_application");
    assertEquals(SkillId.DIM_VISION, applied.getInt("dimvision_skill"));
    assertEquals(evidence.get("raven_stats").getInt("unit_id"), applied.getInt("dimvision_owner_id"));
    int duration = com.riiablo.engine.server.skill.NecromancerSkills.curseDuration(
        Riiablo.files.skills.get(SkillId.DIM_VISION), applied.getInt("dimvision_level"),
        Riiablo.files.DifficultyLevels.get(0));
    assertEquals(duration, evidence.getInt("applied_duration"));
    JsonValue overlays = evidence.get("client").get("overlay_transitions");
    assertEquals(69, overlays.get(1).get("overlay_ids").getInt(0));
    assertEquals(129, overlays.get(2).get("overlay_ids").getInt(0));
    com.riiablo.codec.excel.States.Entry definition = Riiablo.files.States.get(StateId.DIMVISION);
    assertEquals(69, Riiablo.files.Overlay.index(definition.castOverlay));
    assertEquals(129, Riiablo.files.Overlay.index(definition.overlays[0]));
  }

  private JsonValue fixture() {
    return new JsonReader().parse(
        getClass().getResourceAsStream("/original/druid-raven-1.10f.json"));
  }

  private JsonValue targetFixture() {
    return new JsonReader().parse(getClass().getResourceAsStream(
        "/original/druid-raven-target-1.10f.json"));
  }

  @Test
  void successfulOriginalPecksMatchSkillDamageRangeAndExactHpLoss() {
    JsonValue root = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/druid-raven-combat-1.10f.json"));
    int hits = 0;
    for (JsonValue sample : root.get("cases")) {
      assertTrue(sample.getBoolean("background_verified"));
      assertTrue(sample.getBoolean("owned_game_closed"));
      assertTrue(sample.get("native_cleanup").getBoolean("detached"));
      assertEquals(0, sample.get("target_intervention").getInt("target_hp_regen_after"));
      int targetId = sample.get("target_intervention").getInt("target_id");
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.RAVEN);
      int min = com.riiablo.engine.server.missile.MissileDamageResolver.skillPhysicalDamage(
          skill, sample.getInt("level"), true, name -> 0);
      int max = com.riiablo.engine.server.missile.MissileDamageResolver.skillPhysicalDamage(
          skill, sample.getInt("level"), false, name -> 0);
      int sum = 0;
      for (JsonValue hit : sample.get("attacks")) {
        assertEquals(targetId, hit.getInt("target_id"));
        assertEquals(Engine.Monster.MODE_A1, hit.getInt("server_mode"));
        assertTrue(hit.getBoolean("hit"));
        int loss = hit.getInt("hp_loss_fixed");
        assertEquals(loss, hit.getInt("hp_before_fixed") - hit.getInt("hp_after_fixed"));
        assertEquals(loss, hit.getInt("physical_fixed"));
        assertTrue(loss >= min * 256 && loss <= max * 256,
            "captured native packet must fit Riiablo's Raven skill damage range");
        assertEquals(6, hit.get("call_sequence").size);
        sum += loss;
        hits++;
      }
      assertEquals(sum, sample.getInt("total_hp_loss_fixed"));
      assertEquals(sum, sample.getInt("hp_first_fixed") - sample.getInt("hp_last_fixed"));
    }
    assertEquals(7, hits);
  }

  @Test
  void capturedOriginalCreatesTheDataBackedDruidHawkUnit() {
    JsonValue root = fixture();
    JsonValue capture = root.get("capture");
    JsonValue unit = capture.get("new_units").get(0);
    assertTrue(root.getBoolean("background_verified"));
    assertTrue(root.getBoolean("owned_game_closed"));
    assertEquals(SkillId.RAVEN, capture.getInt("skill"));
    assertEquals(1, capture.getInt("new_units_count"));
    assertEquals(1, unit.getInt("type"));
    assertEquals(419, unit.getInt("class_id"));
    assertEquals(419, Riiablo.files.monstats.get("druidhawk").hcIdx);
    assertEquals(2, unit.getInt("mode"));
    assertEquals(502, capture.getInt("raven_sample_count"));
    JsonValue modes = capture.get("raven_mode_values");
    assertEquals(3, modes.size);
    assertEquals(1, modes.getInt(0));
    assertEquals(2, modes.getInt(1));
    assertEquals(8, modes.getInt(2));
  }

  @Test
  void originalBudgetEndsWithDeathWhileDurableTargetIsStillAlive() {
    JsonValue evidence = new JsonReader().parse(getClass().getResourceAsStream(
        "/original/druid-raven-lifecycle-1.10f.json"));
    for (JsonValue sample : evidence.get("cases")) {
      assertTrue(sample.getBoolean("target_alive_through_observation"));
      assertTrue(sample.getBoolean("owned_game_closed"));
      assertTrue(sample.get("native_summary").getBoolean("detached"));
      int level = sample.getInt("level");
      com.riiablo.codec.excel.Skills.Entry skill = Riiablo.files.skills.get(SkillId.RAVEN);
      int expected = skill.Param[4] + (level - 1) * skill.Param[5];
      for (JsonValue raven : sample.get("ravens")) {
        assertTrue(raven.getBoolean("lifecycle_complete"));
        assertEquals(expected, raven.getInt("attack_entries"));
        assertEquals(expected, raven.getInt("client_attack_entries"));
        assertTrue(raven.getInt("death_frame") < raven.getInt("corpse_frame"));
        boolean deathSeen = false, corpseSeen = false;
        for (JsonValue mode : raven.get("mode_transitions")) {
          deathSeen |= mode.getInt("mode") == Engine.Monster.MODE_DT;
          corpseSeen |= mode.getInt("mode") == Engine.Monster.MODE_DD;
        }
        assertTrue(deathSeen && corpseSeen);
      }
    }
  }

  @Test
  void controlledOriginalTargetProducesRavenA1ModeEntries() {
    JsonValue root = targetFixture();
    JsonValue capture = root.get("capture");
    assertTrue(root.get("native_target").getBoolean("detached"));
    assertTrue(root.get("native_target").getBoolean("cleanup_confirmed"));
    assertEquals(4, capture.getInt("raven_attack_mode"));
    assertEquals(4, capture.get("raven_attack_entries").getInt("24"));
    assertEquals(4, capture.get("raven_mode_values").getInt(2));
  }
}
