package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.riiablo.engine.server.MonsterModeElementProfileTest.*;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.riiablo.Files;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.combat.MonsterModeDamageResolver;
import com.riiablo.engine.server.combat.NativeMonsterModeElements;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.missile.MissileDamageResolver;
import com.riiablo.map.Map;
import net.mostlyoriginal.api.event.common.EventSystem;
import org.junit.jupiter.api.Test;

/** Real COF entry and projectile adapter; expectations never re-roll during creation. */
class MonsterModeElementLifecycleTest extends RiiabloTest {
  static final class Fixture implements AutoCloseable {
    final MonStats.Entry row = row();
    final Files files = files(row);
    final CofManager cofs = new CofManager();
    final World world;
    final int id;
    final Monster monster;
    final Attributes attrs = Attributes.obtainStandard();
    Fixture(com.artemis.BaseSystem... systems) {
      Map map = mock(Map.class); when(map.getDifficulty()).thenReturn(1);
      world = new World(new WorldConfigurationBuilder().with(new EventSystem(), cofs).with(systems)
          .build().register("serverContext", new ServerGameplayContext(files, 1234))
          .register("map", map));
      id = world.create();
      monster = world.getMapper(Monster.class).create(id).set(row, null);
      attrs.base().put(Stat.level, 40); attrs.base().put(Stat.firemindam, 2);
      attrs.base().put(Stat.firemaxdam, 3); attrs.reset();
      world.getMapper(AttributesWrapper.class).create(id).attrs = attrs;
      world.getMapper(CofReference.class).create(id).mode = Engine.Monster.MODE_NU;
      monster.rngState = 1234567;
    }
    Missile projectile(int seed) {
      Missile p = new Missile().set(missile(), new Vector2(), 100); p.rngState = seed;
      assertTrue(MissileDamageResolver.initialize(files, p, attrs, monster,
          world.getMapper(CofReference.class).get(id).mode, 1, 1));
      return p;
    }
    @Override public void close() { world.dispose(); }
  }

  @Test void entrySelectsOnceAndManyMissilesShareItWithoutConsumingEitherSeed() {
    try (Fixture f = new Fixture()) {
      f.row.El1Pct = new int[] {50,50,50}; f.row.El1Type = "rand";
      NativeRng expected = new NativeRng(f.monster.rngState);
      NativeMonsterModeElements selected = MonsterModeDamageResolver.resolveElements(
          f.files, expected::nextInt, f.row, 40, 1, Engine.Monster.MODE_A1, 1, true);
      f.cofs.setMode(f.id, Engine.Monster.MODE_A1, true);
      assertEquals(expected.state(), f.monster.rngState);
      assertArrayEquals(selected.minimum, f.monster.modeElements.minimum);
      int seed = f.monster.rngState;
      Missile a = f.projectile(135), b = f.projectile(246);
      assertNotNull(a.nativeElemental); assertNotNull(b.nativeElemental);
      for (int type = 1; type < 6; type++) {
        assertEquals(a.nativeElemental.minimum(type), b.nativeElemental.minimum(type));
        assertEquals(a.nativeElemental.maximum(type), b.nativeElemental.maximum(type));
      }
      assertEquals(135, a.rngState); assertEquals(246, b.rngState);
      assertEquals(seed, f.monster.rngState);
    }
  }

  @Test void repeatedActionRefreshesButNoopAndGetHitRetainAndNeutralClears() {
    try (Fixture f = new Fixture()) {
      f.row.El1Type = "rand";
      f.cofs.setMode(f.id, Engine.Monster.MODE_A1, true);
      NativeMonsterModeElements first = f.monster.modeElements;
      int seed = f.monster.rngState;
      f.cofs.setMode(f.id, Engine.Monster.MODE_A1);
      assertSame(first, f.monster.modeElements); assertEquals(seed, f.monster.rngState);
      f.cofs.setMode(f.id, Engine.Monster.MODE_GH);
      assertSame(first, f.monster.modeElements); assertEquals(seed, f.monster.rngState);
      f.cofs.setMode(f.id, Engine.Monster.MODE_A1, true);
      assertNotSame(first, f.monster.modeElements); assertNotEquals(seed, f.monster.rngState);
      f.cofs.setMode(f.id, Engine.Monster.MODE_NU);
      for (int value : f.monster.modeElements.maximum) assertEquals(0, value);
      assertEquals(0, f.monster.modeElements.coldLength);
      assertEquals(0, f.monster.modeElements.poisonLength);
    }
  }

  @Test void temporarySourceIsAddedOnceAndFrozenWithoutChangingPermanentAttributes() {
    try (Fixture f = new Fixture()) {
      f.cofs.setMode(f.id, Engine.Monster.MODE_A1, true);
      int selected = f.monster.modeElements.minimum[1]; assertTrue(selected > 0);
      Missile a = f.projectile(1);
      assertEquals((selected + 2) * 256, a.nativeElemental.minimum(1));
      assertEquals(2, f.attrs.getValue(Stat.firemindam, 0));
      f.cofs.setMode(f.id, Engine.Monster.MODE_NU);
      assertEquals((selected + 2) * 256, a.nativeElemental.minimum(1));
      assertEquals(2 * 256, f.projectile(2).nativeElemental.minimum(1));
      f.monster.set(f.row, null);
      assertNull(f.monster.modeElements);
    }
  }

  @Test void networkModeSnapshotsDoNotDrawOrOverwriteActionStats() {
    try (Fixture f = new Fixture()) {
      f.row.El1Type = "rand";
      f.cofs.applyNetworkMode(f.id, Engine.Monster.MODE_A1, 7);
      assertEquals(1234567, f.monster.rngState); assertNull(f.monster.modeElements);
      f.cofs.applyNetworkMode(f.id, Engine.Monster.MODE_A1, 8);
      assertEquals(1234567, f.monster.rngState); assertNull(f.monster.modeElements);
    }
  }

  @Test void nativeSequenceSelectsLogicalSqRatherThanItsPresentationMode() {
    try (Fixture f = new Fixture()) {
      // MonMode.txt names slot 14 with its data code, not necessarily "SQ".
      f.row.El1Mode = f.files.MonMode.get(14).Code;
      assertEquals(14, f.files.MonMode.index(f.row.El1Mode));
      Sequence sequence = f.world.getMapper(Sequence.class).create(f.id);
      sequence.mode1 = Engine.Monster.MODE_A1;
      sequence.nativeMonster = new NativeMonsterSequence(Engine.Monster.MODE_A1,
          new int[] {0,1}, new byte[] {Engine.Monster.MODE_A1,Engine.Monster.MODE_A2},
          new byte[] {1,1});
      f.cofs.setMode(f.id, Engine.Monster.MODE_A1, true);
      NativeMonsterModeElements selected = f.monster.modeElements;
      assertEquals(1, selected.matchedRows);
      f.cofs.setSequenceMode(f.id, Engine.Monster.MODE_A2, true);
      assertSame(selected, f.monster.modeElements);
    }
  }

  static final class PointZeroProbe extends net.mostlyoriginal.api.system.core.PassiveSystem {
    int actions;
    NativeMonsterModeElements atAction;
    @net.mostlyoriginal.api.event.common.Subscribe
    public void action(com.riiablo.engine.server.event.AnimDataKeyframeEvent event) {
      actions++;
      atAction = world.getMapper(Monster.class).get(event.entityId).modeElements;
      assertNotNull(atAction, "stat-list must exist before point zero's callback");
      assertEquals(1, atAction.matchedRows);
    }
  }

  @Test void realSequenceHandlerInitializesBeforePointZeroAndDoesNotReenterNextTick() {
    PointZeroProbe probe = new PointZeroProbe();
    try (Fixture f = new Fixture(probe, new SequenceHandler())) {
      f.row.El1Mode = f.files.MonMode.get(14).Code;
      f.world.getMapper(AnimData.class).create(f.id);
      f.world.process();
      Sequence sequence = f.world.getMapper(Sequence.class).create(f.id)
          .sequence(Engine.Monster.MODE_A1, Engine.Monster.MODE_NU);
      sequence.nativeMonster = new NativeMonsterSequence(Engine.Monster.MODE_A1,
          new int[] {0,1}, new byte[] {Engine.Monster.MODE_A1,Engine.Monster.MODE_A2},
          new byte[] {Engine.KEYFRAME_ATK,0});
      f.world.process();
      assertEquals(1, probe.actions); assertSame(f.monster.modeElements, probe.atAction);
      f.world.process();
      assertEquals(1, probe.actions); assertSame(f.monster.modeElements, probe.atAction);
    }
  }

  @Test void unsupportedSelectedModeTypesCannotSilentlyEnableTheFiveChannelPolicy() {
    try (Fixture f = new Fixture()) {
      f.row.El1Type = "life";
      f.cofs.setMode(f.id, Engine.Monster.MODE_A1, true);
      assertTrue(f.monster.modeElements.unsupportedType);
      assertNull(f.projectile(1).nativeElemental);
    }
  }

  @Test void actualEcsDeletionDoesNotLeakActionStatsIntoReusedMonster() {
    try (Fixture f = new Fixture()) {
      f.world.process(); f.cofs.setMode(f.id, Engine.Monster.MODE_A1, true);
      assertNotNull(f.monster.modeElements);
      f.world.delete(f.id); f.world.process();
      int other = f.world.create();
      Monster recycled = f.world.getMapper(Monster.class).create(other).set(f.row, null);
      assertNull(recycled.modeElements); assertEquals(0, recycled.rngState);
    }
  }
}
