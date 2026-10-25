package com.riiablo.engine.server.ai;

import static com.riiablo.engine.server.ai.AiRuntimeStateTest.get;
import static com.riiablo.engine.server.ai.AiRuntimeStateTest.put;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.engine.server.NativeRng;
import com.riiablo.engine.server.ai.Act1NativeAiIntegrationTest.Fixture;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.PathWrapper;
import com.riiablo.engine.server.component.Sequence;
import com.riiablo.engine.server.component.SummonedPet;
import com.riiablo.map.DS1;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NpcAndPetCheckpointIntegrationTest extends RiiabloTest {
  @ParameterizedTest @ValueSource(strings = {"Npc", "Navi"})
  void npcPathAndTableBindingsStayTargetOwnedAndMismatchesFailWithoutWrites(String type) throws Exception {
    try (Fixture source = fixture(type, 10); Fixture target = fixture(type, 10)) {
      Npc a = (Npc) source.ai, b = (Npc) target.ai;
      DS1.Path original = path(), bound = path(); attach(source, original); attach(target, bound);
      a.targetId = 1; a.actionTimer = .25f; a.actionPerformed = true; a.state = "WAITING";
      com.riiablo.codec.excel.MonStats.Entry distinct = new com.riiablo.codec.excel.MonStats.Entry();
      distinct.Id = b.monstats.Id; distinct.hcIdx = b.monstats.hcIdx; b.monstats = distinct;
      Object subscription = b.mercenaries, vector = b.tmpVec2, row = b.monstats;
      AiRuntimeState image = AiRuntimeState.fromBytes(AiRuntimeState.capture(a, 0).bytes());
      image.validateRestore(b, 0); assertEquals(-1, b.targetId);
      image.restore(b, 0);
      assertSame(bound, target.world.getMapper(PathWrapper.class).get(0).path);
      assertSame(row, b.monstats); assertSame(subscription, b.mercenaries); assertSame(vector, b.tmpVec2);
      assertSame(target.world, b.world); assertEquals(1, b.targetId); assertEquals(.25f, b.actionTimer);
      assertArrayEquals(image.bytes(), AiRuntimeState.capture(b, 0).bytes());
      for (int mismatch = 0; mismatch < 6; mismatch++) {
        DS1.Path changed = path(); attach(target, changed);
        switch (mismatch) {
          case 0: target.world.getMapper(PathWrapper.class).get(0).path = null; break;
          case 1: changed.x++; break;
          case 2: changed.points[1].x++; break;
          case 3: changed.points[1].y++; break;
          case 4: changed.points[1].action++; break;
          case 5: changed.numPoints = 1; b.targetId = 0; break;
        }
        b.actionTimer = 19f;
        byte[] before = AiRuntimeState.capture(b, 0).bytes();
        assertThrows(IllegalStateException.class, () -> image.restore(b, 0));
        assertArrayEquals(before, AiRuntimeState.capture(b, 0).bytes());
      }
      attach(target, bound); b.name = "foreign-row";
      assertThrows(IllegalStateException.class, () -> image.restore(b, 0));
      assertEquals(19f, b.actionTimer);
      b.name = a.name; b.monstats = null;
      assertThrows(IllegalStateException.class, () -> image.restore(b, 0));
      b.monstats = distinct; distinct.hcIdx++;
      assertThrows(IllegalStateException.class, () -> image.restore(b, 0));
      distinct.hcIdx--; distinct.Id = "wrong-key";
      assertThrows(IllegalStateException.class, () -> image.restore(b, 0));
      distinct.Id = a.monstats.Id;
      target.world.getMapper(Interactable.class).get(0).count = 1;
      assertThrows(IllegalStateException.class, () -> image.validateRestore(b, 0));
      assertThrows(IllegalStateException.class, () -> image.restore(b, 0));
      assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(b, 0));
      assertEquals(19f, b.actionTimer);
      verifyNoInteractions(source.paths, target.paths);
    }
  }

  @Test void npcWaitAndRandomPatrolSelectionContinueWithoutResettingTheCursor() {
    try (Fixture source = fixture("Npc", 10); Fixture target = fixture("Npc", 10)) {
      attach(source, path()); attach(target, path());
      Npc a = (Npc) source.ai, b = (Npc) target.ai;
      a.targetId = 1; a.actionTimer = .2f; a.actionPerformed = false; a.state = "WAITING";
      AiRuntimeState.capture(a, 0).restore(b, 0);
      a.update(.1f); b.update(.1f); assertEquals(1, b.targetId); assertFalse(b.actionPerformed);
      verifyNoInteractions(source.paths, target.paths);
      a.update(.15f); b.update(.15f); assertTrue(b.actionPerformed);
      a.update(.04f); b.update(.04f);
      assertEquals(a.targetId, b.targetId); assertEquals("PATHING", b.getState());
      verify(target.paths).findPath(eq(0), eq(b.tmpVec2), eq(true));
      assertEquals(source.ai.monster.nativeSeed.getNLowSeed(), target.ai.monster.nativeSeed.getNLowSeed());
      a.actionTimer = Float.POSITIVE_INFINITY;
      AiRuntimeState.capture(a, 0).restore(b, 0); b.update(100f);
      assertEquals(Float.POSITIVE_INFINITY, b.actionTimer);
    }
  }

  @Test void npcMalformedBindingsClocksAndCursorAreRejected() {
    try (Fixture f = fixture("Npc", 10)) {
      attach(f, path()); Npc ai = (Npc) f.ai;
      byte[] bytes = AiRuntimeState.capture(ai, 0).bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] cut = java.util.Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(cut));
      }
      ai.targetId = 2; assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(ai, 0));
      ai.targetId = -2; assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(ai, 0));
      ai.targetId = 0;
      for (float bad : new float[] {Float.NaN, Float.NEGATIVE_INFINITY}) {
        ai.actionTimer = bad; assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(ai, 0));
      }
      ai.actionTimer = 0;
      DS1.Path malformed = path(); malformed.points[0] = null; attach(f, malformed);
      assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(ai, 0));
      malformed.numPoints = 1025; assertThrows(IllegalStateException.class, () -> AiRuntimeState.capture(ai, 0));
      // The last path section is presence/count/x/y/two (x,y,action) tuples.
      byte[] badCount = bytes.clone(); ByteBuffer.wrap(badCount).putInt(bytes.length - 36, -1);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(badCount));
    }
  }

  @Test void naviPrivateRngAndBurstResumeWithoutInitializationOrGlobalRandomDraws() throws Exception {
    java.util.Random previous = MathUtils.random;
    RandomXS128 global = mock(RandomXS128.class);
    try (Fixture source = fixture("Navi", 10); Fixture target = fixture("Navi", 10)) {
      attach(source, null); attach(target, null); enemy(source); enemy(target);
      Navi a = (Navi) source.ai, b = (Navi) target.ai;
      put(a, Navi.class, "nextThink", .15f); put(a, Navi.class, "burstPause", .3f);
      put(a, Navi.class, "burstRemaining", 1);
      NativeRng rng = NativeRng.fromSnapshot(new NativeRng.Snapshot("legacy", -1, 0, 17));
      put(a, Navi.class, "attackRng", rng);
      Object bound = get(b, Navi.class, "attackRng");
      MathUtils.random = global;
      AiRuntimeState image = AiRuntimeState.fromBytes(AiRuntimeState.capture(a, 0).bytes());
      byte[] bytes = image.bytes();
      for (int i = 0; i < bytes.length; i++) {
        byte[] cut = java.util.Arrays.copyOf(bytes, i);
        assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(cut));
      }
      int key = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).indexOf("legacy");
      assertTrue(key > 6);
      byte[] badDraws = bytes.clone(); ByteBuffer.wrap(badDraws).putLong(key + 6 + 8 + 4, -1L);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(badDraws));
      byte[] badPresence = bytes.clone(); badPresence[key - 6] = 2;
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(badPresence));
      image.validateRestore(b, 0); assertEquals(17, rng.drawCount()); image.restore(b, 0);
      assertSame(bound, get(b, Navi.class, "attackRng"));
      NativeRng resumed = (NativeRng) bound; assertEquals(0, resumed.state()); assertEquals(17, resumed.drawCount());
      for (int frame = 0; frame < 100; frame++) {
        Riiablo.engine = source.world; a.update(.04f);
        Riiablo.engine = target.world; b.update(.04f);
        if (frame < 10) assertNull(target.casting());
        assertEquals(source.casting() != null, target.casting() != null);
        if (target.casting() != null) assertEquals(2, target.casting().targetId);
        source.finishAction(); target.finishAction();
        assertArrayEquals(AiRuntimeState.capture(a, 0).bytes(), AiRuntimeState.capture(b, 0).bytes());
      }
      assertTrue(rng.drawCount() > 18, "must continue through later bursts and pauses");
      put(a, Navi.class, "attackRng", null); AiRuntimeState.capture(a, 0).restore(b, 0);
      assertNull(get(b, Navi.class, "attackRng"));
      verifyNoInteractions(global);
    } finally { MathUtils.random = previous; }
  }

  @Test void naviAndPetRejectInvalidBurstRngAndLocalState() throws Exception {
    Navi navi = new Navi(3);
    for (float invalid : new float[] {-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      put(navi, Navi.class, "burstPause", invalid);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(navi, 3));
    }
    put(navi, Navi.class, "burstPause", 0f);
    for (int invalid : new int[] {-1, 3}) {
      put(navi, Navi.class, "burstRemaining", invalid);
      assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(navi, 3));
    }
    put(navi, Navi.class, "burstRemaining", 0);
    put(navi, Navi.class, "attackRng", NativeRng.fromSnapshot(new NativeRng.Snapshot("wrong-stream", -1, 12, 0)));
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(navi, 3));
    NecroPet pet = new NecroPet(3); pet.targetId = -2;
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(pet, 3));
    pet.targetId = -1; pet.nextThink = Float.POSITIVE_INFINITY;
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.capture(pet, 3));
    pet.nextThink = 0; byte[] bytes = AiRuntimeState.capture(pet, 3).bytes();
    bytes[bytes.length - 19] = 2; // first private strict boolean (before float/int/string).
    assertThrows(IllegalArgumentException.class, () -> AiRuntimeState.fromBytes(bytes));
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void necroPetKeepsBranchTargetAndDelayButRechecksOwnerAtTheNextDecision(boolean branch) throws Exception {
    try (Fixture source = fixture("NecroPet", 10); Fixture target = fixture("NecroPet", 10)) {
      NecroPet a = (NecroPet) source.ai, b = (NecroPet) target.ai;
      a.useSkillBranch(branch); a.nextThink = .2f; a.targetId = 999; a.state = "CAST";
      AiRuntimeState.capture(a, 0).restore(b, 0);
      assertEquals(branch, get(b, NecroPet.class, "skillBranch"));
      assertEquals(true, get(b, NecroPet.class, "skillBranchConfigured"));
      b.update(.1f); assertEquals(999, b.targetId); assertEquals("CAST", b.state);
      verifyNoInteractions(target.paths); assertNull(target.casting());
      b.update(.15f); assertEquals(-1, b.targetId); assertEquals("IDLE_NO_OWNER", b.state);
      assertEquals(NecroPet.THINK_DELAY, b.nextThink);
      b.onOwnerWarp(); assertEquals(0f, b.nextThink); assertEquals("IDLE", b.state);
      b.kill(); Sequence action = target.sequence(); action.started = true;
      b.kill(); assertSame(action, target.sequence()); assertTrue(action.started); assertEquals("DEAD", b.state);
    }
  }

  @Test void hireableMarkerRemainsPassiveAfterRestore() {
    try (Fixture source = fixture("Hireable", 10); Fixture target = fixture("Hireable", 10)) {
      AiRuntimeState.capture(source.ai, 0).restore(target.ai, 0);
      target.ai.update(100f); assertEquals("HIREABLE", target.ai.getState());
      verifyNoInteractions(target.paths); assertNull(target.casting());
    }
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void restoredNecroPetBranchControlsItsNextLiveOwnerCombatDecision(boolean skill) {
    // Use a real skill-bearing row to exercise both Fn067 branches independently of the factory policy.
    try (Fixture source = fixture("FallenShaman", 10); Fixture target = fixture("FallenShaman", 10)) {
      NecroPet a = pet(source), b = pet(target); enemy(source); enemy(target);
      a.useSkillBranch(skill); b.useSkillBranch(!skill);
      a.targetId = 2; a.nextThink = .2f; a.state = "IDLE_COMBAT";
      AiRuntimeState.capture(a, 0).restore(b, 0);
      Riiablo.engine = target.world;
      b.update(.1f); assertNull(target.casting()); assertEquals(2, b.targetId);
      verifyNoInteractions(target.paths);
      target.seedFirstRoll(80, true); b.update(.15f);
      assertEquals(skill ? "CAST" : "ATTACK", b.state);
      assertNotNull(target.casting()); assertEquals(2, target.casting().targetId);
      assertEquals(skill ? Riiablo.files.skills.get(b.monster.monstats.Skill1).Id
          : com.riiablo.skill.SkillCodes.attack, target.casting().skillId);
      target.finishAction(); target.world.getMapper(SummonedPet.class).get(0).ownerId = -1;
      b.update(.5f); assertEquals("IDLE_NO_OWNER", b.state); assertEquals(-1, b.targetId);
    }
  }

  private static NecroPet pet(Fixture f) {
    NecroPet pet = new NecroPet(0); f.world.getInjector().inject(pet); pet.pathfinder = f.paths; pet.initialize();
    f.world.getMapper(SummonedPet.class).create(0).ownerId = f.target;
    return pet;
  }

  private static void attach(Fixture f, DS1.Path path) {
    f.world.getMapper(PathWrapper.class).create(0).path = path;
    f.world.getMapper(Interactable.class).create(0).count = 0;
    f.world.process();
  }
  private static DS1.Path path() {
    DS1.Path path = new DS1.Path(); path.x = 10; path.y = 10; path.numPoints = 2;
    path.points = new DS1.Path.Point[] {new DS1.Path.Point(), new DS1.Path.Point()};
    path.points[0].x = 11; path.points[0].y = 10; path.points[0].action = 1;
    path.points[1].x = 12; path.points[1].y = 10; path.points[1].action = 2;
    return path;
  }
  private static void enemy(Fixture f) {
    assertEquals(2, f.ally("zombie1", 100f, 12f, 10f));
    f.world.getMapper(com.riiablo.engine.server.component.Class.class).create(2).type =
        com.riiablo.engine.server.component.Class.Type.MON;
    f.world.process();
  }
  private static Fixture fixture(String type, float distance) {
    return LegacyFsmCheckpointIntegrationTest.fixture(type, distance);
  }
}
