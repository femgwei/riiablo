package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.d2moo.common.drlg.D2Seed;
import com.riiablo.engine.server.ai.AI;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Missile;
import com.riiablo.engine.server.component.UnitRandomSeed;
import org.junit.jupiter.api.Test;

class NativeUnitRandomTest {
  static class Brain extends AI {
    Brain(int id, Monster unit) { super(id); monster = unit; }
    int decide() { return rollAiPercentage(); }
  }

  @Test void literalNativeVectorPersistsBothWordsAcrossConsumers() {
    Monster unit = new Monster(); unit.rngState = 1;
    int[] lows = {1791398751,791599131,671516612,(int)3064641593L,(int)3217527747L};
    int[] highs = {0,747178749,330169957,280084454,1278238622};
    for (int i=0;i<lows.length;i++) {
      NativeUnitRandom rng = NativeUnitRandom.monster(unit,123,42);
      assertEquals(Integer.toUnsignedLong(lows[i]),rng.nextUnsigned());
      assertEquals(lows[i],unit.rngState); assertEquals(highs[i],rng.high());
    }
  }

  @Test void aiUsesFull64AndCombatUsesLow32OfTheSamePersistentStream() {
    Monster unit = new Monster(); unit.rngState = 1;
    Brain ai = new Brain(42,unit);
    assertEquals(51,ai.decide());
    assertEquals(31,NativeUnitRandom.monster(unit,999,42).nextInt(100));
    assertEquals(84,ai.decide());
    assertEquals(93,NativeUnitRandom.monster(unit,999,42).nextInt(100));
    assertEquals(59,ai.decide());
  }

  @Test void checkpointRestoresBothNativeSeedWords() {
    Monster source = new Monster();
    source.nativeSeed = new D2Seed(0x13572468, 0x24681357);
    source.rngState = source.nativeSeed.getNLowSeed();
    NativeUnitRandom rng = NativeUnitRandom.monster(source, 1, 42);
    rng.nextUnsigned();
    NativeUnitRandom.Snapshot checkpoint = rng.snapshot();
    long expected = rng.nextUnsigned();

    Monster restored = new Monster();
    restored.nativeSeed = new D2Seed(1, 2);
    restored.rngState = 1;
    NativeUnitRandom restoredRng = NativeUnitRandom.monster(restored, 999, 42);
    restoredRng.restore(checkpoint);
    assertEquals(expected, restoredRng.nextUnsigned());
    assertEquals(restoredRng.state(), restored.rngState);
    assertEquals(restoredRng.high(), restored.nativeSeed.getNHighSeed());
  }

  @Test void unsignedOracleChecksAlternatingConsumersAndAllBoundClasses() {
    for (int seed : new int[]{0,1,-1,Integer.MIN_VALUE,777,0x110f}) {
      Monster unit = new Monster(); unit.nativeSeed = new D2Seed(seed,666); unit.rngState = seed;
      NativeSeedReference expected = new NativeSeedReference(seed);
      int[] bounds = {0,-1,1,2,100,256,1001,Integer.MAX_VALUE};
      for(int i=0;i<500;i++) {
        NativeUnitRandom rng = NativeUnitRandom.monster(unit,123,42);
        int bound = bounds[i%bounds.length];
        assertEquals(expected.nextInt(bound),rng.nextInt(bound));
        assertEquals(expected.percentage(),new Brain(42,unit).decide());
        assertEquals(expected.state(),unit.rngState); assertEquals(expected.high(),rng.high());
      }
    }
  }

  @Test void zeroLowIsValidAndNonpositiveBoundsDoNotDraw() {
    Monster unit = new Monster(); unit.nativeSeed = new D2Seed(0,123); unit.rngState = 0;
    NativeUnitRandom rng = NativeUnitRandom.monster(unit,999,42);
    assertEquals(0,rng.nextInt(0)); assertEquals(0,rng.nextInt(-2)); assertEquals(123,rng.high());
    assertEquals(123,rng.nextUnsigned());
    assertEquals(123,NativeUnitRandom.monster(unit,999,42).state());
  }

  @Test void replayedHighAndLowWordsAreBothUnsigned() {
    int[] words = {0, 1, 666, -1, Integer.MIN_VALUE};
    for (int low : words) {
      for (int high : words) {
        Monster unit = new Monster();
        unit.nativeSeed = new D2Seed(low, high);
        unit.rngState = low;
        NativeSeedReference expected = new NativeSeedReference(low, high);
        for (int i = 0; i < 50; i++) {
          assertEquals(expected.nextUnsigned(), NativeUnitRandom.monster(unit, 999, 42).nextUnsigned());
          assertEquals(expected.state(), unit.rngState);
          assertEquals(expected.high(), unit.nativeSeed.getNHighSeed());
        }
      }
    }
  }

  @Test void explicitLegacyLowWriteReinitializesButRebindingDoesNot() {
    Monster unit = new Monster(); unit.rngState = 1;
    NativeUnitRandom.monster(unit,0,0).nextUnsigned();
    D2Seed seed = unit.nativeSeed;
    NativeUnitRandom.monster(unit,0,0); assertSame(seed,unit.nativeSeed);
    unit.rngState = 777;
    assertEquals(666,NativeUnitRandom.monster(unit,0,0).high()); assertNotSame(seed,unit.nativeSeed);
    assertEquals(346908807,NativeUnitRandom.monster(unit,0,0).nextUnsigned());
  }

  @Test void projectileIsolationAndActualPoolRecyclingClearTheFullSeed() {
    World world = new World(new WorldConfiguration());
    try {
      int a=world.create(), b=world.create();
      Missile pa=world.getMapper(Missile.class).create(a), pb=world.getMapper(Missile.class).create(b);
      pa.rngState=pb.rngState=1;
      assertEquals(51,NativeUnitRandom.missile(pa,0,a).nextInt(100));
      assertNull(pb.nativeSeed); assertEquals(1,pb.rngState);
      assertEquals(51,NativeUnitRandom.missile(pb,0,b).nextInt(100));
      int m=world.create(); Monster monster=world.getMapper(Monster.class).create(m);
      NativeUnitRandom.monster(monster,123,m).nextUnsigned();
      world.process(); world.delete(a); world.delete(m); world.process();
      int next=world.create(); Missile recycled=world.getMapper(Missile.class).create(next);
      assertSame(pa,recycled); assertNull(recycled.nativeSeed); assertEquals(0,recycled.rngState);
      int nextMonster=world.create(); Monster recycledMonster=world.getMapper(Monster.class).create(nextMonster);
      // Monster is not guaranteed to be woven/pool-reused in every backend.
      assertNull(recycledMonster.nativeSeed);
      assertEquals(0,recycledMonster.rngState);
      monster.set(null,null); assertNull(monster.nativeSeed); assertEquals(0,monster.rngState);
    } finally { world.dispose(); }
  }

  @Test void genericDefenderStreamPersistsAndIsRecycledWithoutLeakingIntoAttacker() {
    World world=new World(new WorldConfiguration());
    try {
      int attacker=world.create(),defender=world.create();
      Monster monster=world.getMapper(Monster.class).create(attacker); monster.rngState=1;
      UnitRandomSeed state=world.getMapper(UnitRandomSeed.class).create(defender); state.low=1;
      assertEquals(51,NativeUnitRandom.unit(world,999,defender).nextInt(100));
      assertEquals(31,NativeUnitRandom.unit(world,999,defender).nextInt(100));
      assertEquals(747178749,state.seed.getNHighSeed());
      assertNull(monster.nativeSeed); assertEquals(1,monster.rngState);
      assertEquals(51,NativeUnitRandom.unit(world,999,attacker).nextInt(100));
      world.process(); world.delete(defender); world.process();
      int next=world.create(); UnitRandomSeed reused=world.getMapper(UnitRandomSeed.class).create(next);
      assertSame(state,reused); assertNull(reused.seed); assertEquals(0,reused.low);
    } finally { world.dispose(); }
  }
}
