package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.Animation;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.States;
import com.riiablo.codec.D2;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.map.Map;
import org.junit.jupiter.api.Test;

class GreaterMummyAiIntegrationTest extends RiiabloTest {
  @Test
  void nativeResurrectionSequenceData() {
    String text = Riiablo.mpqs.resolve("data\\global\\excel\\monseq.txt").readString("windows-1252");
    int frames = 0;
    for (String line : text.split("\\r?\\n")) {
      String[] columns = line.split("\\t", -1);
      if (!"seq_mummyres".equals(columns[0])) continue;
      assertEquals("SC", columns[1]);
      assertEquals(frames, Integer.parseInt(columns[2]));
      assertEquals(frames == 13 ? "2" : "", columns[4]);
      frames++;
    }
    assertEquals(16, frames);
    D2 anim = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (!"GreaterMummy".equals(row.AI)) continue;
      assertEquals("seq_mummyres", row.Sk1mode, row.Id);
      assertEquals("seq_mummyres", row.Sk2mode, row.Id);
      assertEquals("seq_mummyres", row.Sk3mode, row.Id);
      D2.Entry sc = anim.getEntry((row.Code + "SCHTH").toUpperCase());
      assertNotNull(sc, row.Id);
      assertEquals(16, sc.framesPerDir, row.Id);
      // Variant COFs need not carry MonSeq's action marker (e.g. unraveler2).
    }
  }

  @Test
  void corpseDecisionUsesRealSkillAndDoesNotOverwriteActiveAnimation() {
    try (Act1NativeAiIntegrationTest.Fixture f =
        new Act1NativeAiIntegrationTest.Fixture("unraveler1", 0, 18f)) {
      f.ai.params[0] = 0;
      f.ai.params[1] = 100;
      f.ai.params[2] = 0;
      f.ai.params[3] = 0;
      f.ai.params[4] = 20;
      int corpse = f.ally("skeleton1", 0f, 14, 10);
      f.world.getMapper(CofReference.class).create(corpse).mode = Engine.Monster.MODE_DD;
      f.world.getMapper(NativeUnitFlags.class).create(corpse).set(NativeUnitFlags.TARGETABLE);
      f.world.getMapper(Corpse.class).create(corpse);
      f.world.process();
      f.ai.update(Animation.FRAME_DURATION);
      assertNotNull(f.casting(), "eligible corpse must initiate resurrection");
      assertEquals(Riiablo.files.skills.get(f.ai.monster.monstats.Skill1).Id, f.casting().skillId);
      assertEquals(corpse, f.casting().targetId);
      assertEquals(Engine.Monster.MODE_SC, f.sequence().mode1);
      assertTrue(f.sequence().nativeMummy);
      f.sequence().started = true;
      f.ai.update(10f);
      assertTrue(f.sequence().started);
      assertEquals(1, f.probe.starts);
    }
  }

  @Test
  void healingHasPriorityOverResurrectionAndShootingUsesSkillThree() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("unraveler1", difficulty, 18f)) {
        java.util.Arrays.fill(f.ai.params, 100);
        f.ai.params[0] = 0;
        int corpse = corpse(f, "skeleton1", 14);
        int ally = f.ally("skeleton1", 50f, 13, 10);
        f.world.getMapper(NativeUnitFlags.class).create(ally).set(NativeUnitFlags.TARGETABLE);
        f.world.process();
        f.ai.update(Animation.FRAME_DURATION);
        assertEquals(Riiablo.files.skills.get(f.ai.monster.monstats.Skill2).Id, f.casting().skillId);
        assertEquals(ally, f.casting().targetId);
        f.finishAction();
        f.ai.params[1] = f.ai.params[2] = 0;
        f.ai.update(1f);
        assertEquals(Riiablo.files.skills.get(f.ai.monster.monstats.Skill3).Id, f.casting().skillId);
        assertEquals(f.target, f.casting().targetId);
        assertNotEquals(corpse, f.casting().targetId);
        assertEquals(Engine.Monster.MODE_SC, f.sequence().mode1);
      }
    }
  }

  @Test
  void nativeCallbackFiltersFlagsAlignmentUniqueRoomsRangeAndCorpseAvailability() {
    for (int filter = 0; filter < 9; filter++) {
      try (Fixture f = new Fixture("unraveler1", 0, 18f)) {
        f.ai.params[4] = 8;
        int id = corpse(f, "skeleton1", 14);
        switch (filter) {
          case 0: f.world.getMapper(NativeUnitFlags.class).get(id).clear(NativeUnitFlags.TARGETABLE); break;
          case 1: f.world.getMapper(Monster.class).get(id).converted = true; break;
          case 2: f.world.getMapper(Monster.class).get(id).rank = MonsterRank.UNIQUE; break;
          case 3: f.world.getMapper(MapWrapper.class).create(id).set(new Map(2, 0), null); break;
          case 4: f.world.getMapper(Position.class).get(id).position.set(19, 10); break;
          case 5: f.world.getMapper(Corpse.class).get(id).usable = false; break;
          case 6: f.world.getMapper(Corpse.class).get(id).fading = true; break;
          case 7: f.world.getMapper(CofReference.class).get(id).mode = Engine.Monster.MODE_DT; break;
          case 8: f.world.getMapper(SummonedPet.class).create(id); break;
        }
        f.world.process();
        GreaterMummy ai = (GreaterMummy) f.ai;
        ai.scanAllies();
        assertEquals(filter == 5 || filter == 6 ? id : Engine.INVALID_ENTITY,
            ai.reviveTarget(), "filter=" + filter);
        assertEquals(Engine.INVALID_ENTITY, ai.healTarget(), "filter=" + filter);
        f.ai.params[0] = f.ai.params[2] = f.ai.params[3] = 0;
        f.ai.params[1] = 100;
        f.ai.update(Animation.FRAME_DURATION);
        assertNull(f.casting(), "invalid/unavailable corpse must not initiate resurrection");
      }
    }
  }

  @Test
  void deathFlagsPreserveCorpseSelectionButHiddenCorpsesLoseItAndNeverRegainIt() {
    int hidden = -1;
    for (States.Entry row : Riiablo.files.States) {
      if (row.hide && row.monsterStayDeath
          && row.id != com.riiablo.engine.server.state.StateId.FREEZE
          && row.id != com.riiablo.engine.server.state.StateId.SHATTER) { hidden = row.id; break; }
    }
    assertTrue(hidden >= 0);
    for (boolean hide : new boolean[] {false, true}) {
      net.mostlyoriginal.api.event.common.EventSystem events = new net.mostlyoriginal.api.event.common.EventSystem();
      com.artemis.World world = new com.artemis.World(new com.artemis.WorldConfigurationBuilder()
          .with(events, new com.riiablo.engine.server.CofManager(),
              new com.riiablo.engine.server.ServerMonsterCorpseSystem()).build());
      try {
        int id = world.create();
        MonStats.Entry row = Riiablo.files.monstats.get("skeleton1");
        world.getMapper(Monster.class).create(id).set(row, Riiablo.files.monstats2.get(row.MonStatsEx));
        NativeUnitFlags flags = world.getMapper(NativeUnitFlags.class).create(id)
            .reset().markMonsterResurrection();
        if (hide) world.getMapper(UnitStates.class).create(id).init(id).stateList.addState(hidden, 0, 1, -1);
        events.dispatch(com.riiablo.engine.server.event.DeathEvent.obtain(-1, id));
        assertEquals(!hide, flags.has(NativeUnitFlags.TARGETABLE));
        assertFalse(flags.has(NativeUnitFlags.CAN_BE_ATTACKED));
        assertFalse(flags.has(NativeUnitFlags.IS_VALID_TARGET));
        events.dispatch(com.riiablo.engine.server.event.ModeChangeEvent.obtain(id, Engine.Monster.MODE_DD));
        assertEquals(!hide, flags.has(NativeUnitFlags.TARGETABLE));
        assertTrue(flags.has(NativeUnitFlags.NO_RESURRECTION_REWARD));
      } finally { world.dispose(); }
    }
  }

  @Test
  void callbackUsesLastEligibleCandidateAndIncludesRangeBoundary() {
    try (Fixture f = new Fixture("unraveler1", 0, 18f)) {
      f.ai.params[4] = 8;
      corpse(f, "skeleton1", 12);
      int last = corpse(f, "skeleton1", 18);
      f.world.process();
      GreaterMummy ai = (GreaterMummy) f.ai;
      ai.scanAllies();
      assertEquals(last, ai.reviveTarget(), "callback does not sort nearest first");
    }
  }

  @Test
  void radamentAddsTenRangeAndOnlyNormalAllowsUniqueUndead() {
    for (int difficulty = 0; difficulty < 3; difficulty++) {
      try (Fixture f = new Fixture("radament", difficulty, 18f)) {
        f.ai.params[4] = 8;
        int id = corpse(f, "skeleton1", 28);
        f.world.getMapper(Monster.class).get(id).rank = MonsterRank.UNIQUE;
        f.world.process();
        GreaterMummy ai = (GreaterMummy) f.ai;
        ai.scanAllies();
        assertEquals(difficulty == 0 ? id : Engine.INVALID_ENTITY, ai.reviveTarget());
      }
    }
  }

  @Test
  void normalMummyAcceptsOnlyLowUndeadButRadamentAlsoAcceptsHighUndead() {
    String highUndead = null;
    for (MonStats.Entry row : Riiablo.files.monstats) {
      if (row.hUndead && !row.lUndead && row.Align == 0) { highUndead = row.Id; break; }
    }
    assertNotNull(highUndead);
    for (String source : new String[] {"unraveler1", "radament"}) {
      try (Fixture f = new Fixture(source, 0, 18f)) {
        f.ai.params[4] = 8;
        int id = corpse(f, highUndead, 14);
        f.world.process();
        GreaterMummy ai = (GreaterMummy) f.ai;
        ai.scanAllies();
        assertEquals(source.equals("radament") ? id : Engine.INVALID_ENTITY, ai.reviveTarget());
      }
    }
  }

  @Test
  void nativeHideAndUdeadMasksRejectCorpse() {
    for (boolean hide : new boolean[] {true, false}) {
      int stateId = -1;
      for (States.Entry row : Riiablo.files.States) {
        if (hide ? row.hide : row.undead) { stateId = row.id; break; }
      }
      assertTrue(stateId >= 0);
      try (Fixture f = new Fixture("unraveler1", 0, 18f)) {
        f.ai.params[4] = 8;
        int id = corpse(f, "skeleton1", 14);
        f.world.getMapper(UnitStates.class).create(id).init(id).stateList.addState(stateId, 100, 1, f.actor);
        f.world.process();
        GreaterMummy ai = (GreaterMummy) f.ai;
        ai.scanAllies();
        assertEquals(Engine.INVALID_ENTITY, ai.reviveTarget());
      }
    }
  }

  @Test
  void failedSkillDoesNotBecomeBasicAttackAndCloseRangeDoesNotBypassChance() {
    try (Fixture f = new Fixture("unraveler1", 0, 3f)) {
      java.util.Arrays.fill(f.ai.params, 0);
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting(), "zero melee chance must not force the old immediate A2 reaction");
    }
    try (Fixture f = new Fixture("unraveler1", 0, 18f)) {
      f.ai.params[0] = 0;
      f.ai.params[3] = 100;
      f.probe.reject = true;
      f.ai.update(Animation.FRAME_DURATION);
      assertNull(f.casting());
      assertEquals("IDLE", f.ai.getState());
    }
  }

  private int corpse(Fixture f, String row, float x) {
    int id = f.ally(row, 0f, x, 10);
    f.world.getMapper(CofReference.class).create(id).mode = Engine.Monster.MODE_DD;
    f.world.getMapper(NativeUnitFlags.class).create(id).set(NativeUnitFlags.TARGETABLE);
    f.world.getMapper(Corpse.class).create(id);
    return id;
  }
}
