package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.*;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DeathEvent;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import com.riiablo.engine.server.state.UnitState;
import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.map.Map;
import com.riiablo.save.CharData;
import net.mostlyoriginal.api.event.common.EventSystem;
import net.mostlyoriginal.api.event.common.Subscribe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real state -> net HPREGEN -> settlement and production-order potion handoff. */
class PoisonRegenIntegrationTest extends RiiabloTest {
  @ParameterizedTest @CsvSource({"true,60,2620", "false,60,2620", "true,-60,2500", "false,-60,2500"})
  void nativeRegenRunsWithoutPoison(boolean player, int rate, int expected) {
    try (Fixture f = new Fixture(player,2560)) {
      f.attrs.base().put(Stat.hpregen,rate); f.attrs.reset();
      f.tick(); assertEquals(expected,f.hp());
      assertEquals(rate < 0 ? 1 : 0,f.probe.requests);
    }
  }

  @ParameterizedTest @CsvSource({"0,511,0,2049", "300,256,128,2476", "500,256,128,2676"})
  void burningAndPoisonShareOneNetRegenerationWrite(int healing, int poison, int burning, int expected) {
    try (Fixture f = new Fixture(true,2560)) {
      f.attrs.base().put(Stat.hpregen,healing); f.attrs.reset();
      if (poison > 0) f.poison(poison,1,f.source);
      if (burning > 0) f.dot(StateId.BURNING,burning,1,f.otherSource());
      f.tick(); assertEquals(expected,f.hp());
      assertEquals(expected < 2560 ? 1 : 0,f.probe.requests);
      if (expected < 2560) assertEquals(f.source,f.probe.attacker);
    }
  }

  @Test void burningUsesPlayerFloorAndMergesWithRealPotionOnlyOnce() {
    try (Fixture f = new Fixture(true,128)) {
      f.dot(StateId.BURNING,511,2,f.source); f.tick(); assertEquals(256,f.hp());
      assertEquals(0,f.probe.deaths);
    }
    try (Fixture f = new Fixture(true,2560)) {
      f.drink("hp1");
      // hp1 Amazon: 45 HP / 192 frames = 60 raw/frame (independent literal).
      int rate = 60;
      f.dot(StateId.BURNING,20,1,f.source); f.tick();
      assertEquals(2560 + rate - 20,f.hp());
      f.tick(); assertEquals(2560 + 2 * rate - 20,f.hp());
    }
  }

  @Test void nativeHpRegenOpenWoundsListIsMergedWithoutLegacyLevelDamage() {
    try (Fixture f = new Fixture(true,2560)) {
      f.states.addState(StateId.OPENWOUNDS,2,50,f.source).setNativeModifier(Stat.hpregen,-80);
      f.poison(20,1,f.otherSource()); f.tick(); assertEquals(2460,f.hp());
      f.tick(); assertEquals(2380,f.hp());
      assertEquals(2,f.probe.requests);
    }
  }

  @Test void playerNonpoisonNegativeRegenRespectsOneHpAndZeroNetDoesNotRaiseFractionalLife() {
    try (Fixture f = new Fixture(true,128)) {
      f.tick(); assertEquals(128,f.hp());
      f.attrs.base().put(Stat.hpregen,-1); f.attrs.reset();
      f.tick(); assertEquals(256,f.hp()); assertEquals(0,f.probe.deaths);
    }
  }

  @Test void nativeNonpoisonRegenHonorsPreventHealAndSnapshotOnly() {
    try (Fixture f = new Fixture(false,2560)) {
      f.attrs.base().put(Stat.hpregen,60); f.attrs.reset();
      f.states.addState(StateId.PREVENTHEAL,2); f.tick(); assertEquals(2560,f.hp());
      // A life-mask state removes BASE regen, including a negative base value.
      // Use an additional negative stat-list to exercise PREVENTHEAL's gate.
      f.attrs.base().put(Stat.hpregen,0); f.attrs.reset();
      f.states.addState(StateId.PRAYER,2,1,f.source).setNativeModifier(Stat.hpregen,-60);
      f.tick(); assertEquals(2500,f.hp());
      f.component.snapshotOnly = true; f.tick(); assertEquals(2500,f.hp());
    }
  }

  @Test void nativeRoomBackedFractionalMonsterIsProtectedWhenRoomIsAbsent() {
    try (Fixture f = new Fixture(false,128)) {
      f.world.getMapper(MapWrapper.class).create(f.target);
      f.poison(256,1,f.source); f.tick(); assertEquals(128,f.hp()); assertEquals(0,f.probe.deaths);
    }
  }

  @ParameterizedTest
  @CsvSource({"512,511,256", "256,511,256", "128,1,256", "1024,1,1023"})
  void playerPoisonNeverKillsAndRetainsRawFraction(int life, int rate, int expected) {
    try (Fixture f = new Fixture(true, life)) {
      f.poison(rate, 1, f.source);
      f.tick();
      assertEquals(expected, f.hp());
      assertEquals(0, f.probe.deaths);
      assertNull(f.states.getState(StateId.POISON));
      if (life > expected) assertEquals((life - expected) / 256f, f.probe.lost);
    }
  }

  @Test
  void mutableDotCallbackCannotAmplifyPoisonIntoPlayerDeath() {
    try (Fixture f = new Fixture(true, 512)) {
      f.probe.amplify = true;
      f.poison(1, 2, f.source);
      f.tick();
      assertEquals(256, f.hp());
      assertEquals(1f, f.probe.lost);
      f.tick();
      assertEquals(256, f.hp());
      assertEquals(1, f.probe.requests);
      assertEquals(0, f.probe.deaths);
    }
  }

  @ParameterizedTest
  @CsvSource({"0,0,256,2304", "200,56,256,2560", "300,56,256,2660"})
  void aggregateAndStateRegenAreCombinedBeforeLifeWrite(
      int aggregate, int stateRate, int poison, int expected) {
    try (Fixture f = new Fixture(true, 2560)) {
      f.attrs.base().put(Stat.hpregen, aggregate); f.attrs.reset();
      f.states.addState(StateId.PRAYER, 10, 1, f.source)
          .setNativeModifier(Stat.hpregen, stateRate);
      f.poison(poison, 1, f.source);
      f.tick();
      assertEquals(expected, f.hp());
      assertEquals(expected < 2560 ? 1 : 0, f.probe.requests);
    }
  }

  @Test
  void zeroNetDoesNotApplyPlayerFloor() {
    try (Fixture f = new Fixture(true, 128)) {
      f.attrs.base().put(Stat.hpregen, 60); f.attrs.reset();
      f.poison(60, 1, f.source);
      f.tick();
      assertEquals(128, f.hp());
      assertEquals(0, f.probe.requests);
    }
  }

  @Test
  void monsterLifeMaskSuppressesOnlyBaseRegeneration() {
    try (Fixture f = new Fixture(false, 2560)) {
      assertTrue(Riiablo.files.States.get(StateId.POISON).life);
      f.attrs.base().put(Stat.hpregen, 100); f.attrs.reset();
      f.attrs.aggregate().put(Stat.hpregen, 130);
      f.states.addState(StateId.PRAYER, 10, 1, f.source)
          .setNativeModifier(Stat.hpregen, 20);
      f.poison(80, 1, f.source);
      f.tick();
      assertEquals(2530, f.hp(), "130 + 20 - 100 base - 80 poison = -30 raw");
      assertEquals(30 / 256f, f.probe.lost);
    }
  }

  @ParameterizedTest
  @CsvSource({"60,2560", "20,2560", "0,2540"})
  void monsterPreventHealBlocksNonnegativeButNotNegativeRate(int healing, int expected) {
    try (Fixture f = new Fixture(false, 2560)) {
      f.states.addState(StateId.PRAYER, 10, 1, f.source)
          .setNativeModifier(Stat.hpregen, healing);
      f.states.addState(StateId.PREVENTHEAL, 10, 1, f.source);
      f.poison(20, 1, f.source);
      f.tick();
      assertEquals(expected, f.hp());
    }
  }

  @Test
  void monsterCanDieAndCreditsFirstPoisonOwnerOnlyOnceAfterStrongerReplacement() {
    try (Fixture f = new Fixture(false, 256)) {
      f.poison(1, 100, f.source);
      int second = f.otherSource();
      f.poison(512, 3, second);
      f.tick();
      assertEquals(0, f.hp());
      assertEquals(f.source, f.probe.killer);
      assertEquals(f.source, f.probe.attacker);
      f.tick(); f.tick();
      assertEquals(1, f.probe.deaths);
      assertEquals(1, f.probe.requests);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {StateId.POISON, StateId.BURNING})
  void replacementRetainsOwnerUntilRemovalThenNewInfectionGetsNewOwner(int stateId) {
    try (Fixture f = new Fixture(false, 2560)) {
      int second = f.otherSource();
      UnitState state = f.dot(stateId, 4, 100, f.source);
      assertSame(state, f.dot(stateId, 12, 10, second));
      assertEquals(f.source, state.sourceEntityId);
      assertEquals(10, state.duration);
      f.dot(stateId, 6, 200, second);
      assertEquals(10, state.duration);
      assertEquals(12 / 256f, state.exactDamagePerFrame);
      f.dot(stateId, 12, 1, second);
      assertEquals(f.source, state.sourceEntityId);
      assertEquals(1, state.duration);
      f.tick();
      assertNull(f.states.getState(stateId));
      assertEquals(second, f.dot(stateId, 1, 2, second).sourceEntityId);
      f.states.removeState(stateId);
      assertEquals(f.source, f.dot(stateId, 1, 2, f.source).sourceEntityId);
    }
  }

  @ParameterizedTest
  @CsvSource({"120,452", "60,512", "30,542"})
  void actualHealthPotionAndPoisonUseOneNetRateEvenAtOneHp(int poison, int expected) {
    try (Fixture f = new Fixture(true, 512)) {
      f.drink("hp1");
      f.poison(poison, 1, f.source);
      f.tick();
      // hp1 Amazon: 45 HP / 192 frames = 60 raw/frame.
      assertEquals(expected, f.hp());
      f.tick();
      assertEquals(expected + 60, f.hp(),
          "final poison tick must not consume/apply a second potion tick");
    }
  }

  @Test
  void poisonAtOneHpDoesNotTurnPotionIntoIndependentHealing() {
    try (Fixture f = new Fixture(true, 256)) {
      f.drink("hp1"); f.poison(120, 2, f.source);
      f.tick(); f.tick();
      assertEquals(256, f.hp());
      f.tick();
      assertEquals(316, f.hp());
    }
  }

  @Test
  void healthAndManaPotionsAdvanceExactlyOnceAcrossExpiryAndDispel() {
    try (Fixture f = new Fixture(true, 2560)) {
      f.drink("hp1"); f.drink("mp1"); f.poison(10, 1, f.source);
      f.tick();
      assertEquals(2610, f.hp());
      assertEquals(5120 + 60, f.attrs.get(Stat.mana).encodedValues());
      f.poison(10, 20, f.source);
      f.states.removeState(StateId.POISON);
      f.tick();
      assertEquals(2670, f.hp());
      f.frames(190);
      assertEquals(14070, f.hp(), "192 health-potion ticks minus one 10-raw poison tick");
      assertEquals(50f, f.attrs.get(Stat.mana).asFixed());
      f.tick(); assertEquals(14070, f.hp());
    }
  }

  @Test
  void netMaximumOvershootCancelsHealthPotionButExactEqualityDoesNot() {
    try (Fixture f = new Fixture(true, 25590)) {
      f.drink("hp1"); f.poison(50, 2, f.source);
      f.tick();
      assertEquals(25600, f.hp());
      f.attrs.get(Stat.hitpoints).setEncoded(2560);
      f.tick();
      assertEquals(2570, f.hp(), "exact max equality kept HEALTHPOT alive");
      f.attrs.get(Stat.hitpoints).setEncoded(25595);
      f.poison(50, 1, f.source);
      f.tick(); assertEquals(25600, f.hp());
      f.attrs.get(Stat.hitpoints).setEncoded(2560);
      f.tick(); assertEquals(2560, f.hp(), "net overshoot removed HEALTHPOT");
    }
  }

  @Test
  void negativeNetAtMaximumDoesNotPrematurelyCancelPotion() {
    try (Fixture f = new Fixture(true, 25600)) {
      f.drink("hp1"); f.poison(120, 1, f.source);
      f.tick(); assertEquals(25540, f.hp());
      f.tick(); assertEquals(25600, f.hp());
      f.attrs.get(Stat.hitpoints).setEncoded(2560);
      f.tick(); assertEquals(2620, f.hp());
    }
  }

  @Test
  void snapshotAndDeadUnitsDoNotTickPoisonOrConsumePotions() {
    try (Fixture f = new Fixture(true, 2560)) {
      f.drink("hp1"); f.poison(10, 1, f.source);
      f.component.snapshotOnly = true;
      f.tick(); assertEquals(2560, f.hp());
      assertEquals(1, f.states.getState(StateId.POISON).duration);
      f.component.snapshotOnly = false;
      f.attrs.get(Stat.hitpoints).setEncoded(0);
      f.tick(); assertEquals(0, f.hp());
      f.attrs.get(Stat.hitpoints).setEncoded(2560);
      f.frames(192);
      assertEquals(55f, f.attrs.get(Stat.hitpoints).asFixed());
      assertEquals(0, f.probe.deaths);
    }
  }

  @Test
  void friendlyPlayerPoisonStillRejectedWithoutSuppressingPotionTick() {
    try (Fixture f = new Fixture(true, 2560)) {
      f.world.getMapper(Monster.class).remove(f.source);
      f.world.getMapper(Player.class).create(f.source);
      f.drink("hp1"); f.poison(999, 1, f.source);
      f.tick();
      assertEquals(2620, f.hp());
      assertEquals(0, f.probe.requests);
    }
  }

  @Test void rejectedPoisonCannotSuppressIndependentMonsterBurningAndPotion() {
    try (Fixture f = new Fixture(true,2560)) {
      f.world.getMapper(Monster.class).remove(f.source);
      f.world.getMapper(Player.class).create(f.source);
      int monster = f.otherSource();
      f.poison(999,1,f.source); f.dot(StateId.BURNING,120,1,monster); f.drink("hp1");
      f.tick(); assertEquals(2500,f.hp()); assertEquals(monster,f.probe.attacker);
      f.tick(); assertEquals(2560,f.hp()); assertEquals(1,f.probe.requests);
    }
  }

  @Test void rejectedNativeNegativeStatListCannotBypassPvpGate() {
    try (Fixture f = new Fixture(true,2560)) {
      f.world.getMapper(Monster.class).remove(f.source);
      f.world.getMapper(Player.class).create(f.source);
      f.states.addState(StateId.OPENWOUNDS,1,50,f.source).setNativeModifier(Stat.hpregen,-999);
      f.drink("hp1"); f.tick(); assertEquals(2620,f.hp()); assertEquals(0,f.probe.requests);
    }
  }

  private static final class Probe extends BaseSystem {
    int requests, deaths, killer = -1, attacker = -1;
    float lost;
    boolean amplify;
    @Subscribe public void damage(DamageEvent event) {
      requests++; attacker = event.attacker;
      assertEquals(DamageEvent.DAMAGE_OVER_TIME, event.kind);
      if (amplify) event.damage = 10000f;
    }
    @Subscribe public void applied(DamageAppliedEvent event) { lost = event.lifeLost; }
    @Subscribe public void death(DeathEvent event) { deaths++; killer = event.killer; }
    @Override protected void processSystem() {}
  }

  private static final class Fixture implements AutoCloseable {
    final World world;
    final Probe probe = new Probe();
    final CharData character;
    final Attributes attrs;
    final int source, target;
    final UnitStates component;
    final StateList states;
    int potionId;
    Fixture(boolean player, int rawLife) {
      EntityFactory factory = mock(EntityFactory.class);
      world = new World(new WorldConfigurationBuilder()
          .with(new EventSystem(), probe, factory, new StateUpdater(), new PotionRecoverySystem())
          .build().register("factory", factory).register("map", mock(Map.class)));
      source = otherSource(); target = world.create();
      character = player ? CharData.obtain().set(Riiablo.NORMAL, false, "PoisonHero", Riiablo.AMAZON) : null;
      attrs = player ? character.getStats() : Attributes.obtainStandard();
      attrs.base().put(Stat.hitpoints, rawLife / 256f);
      attrs.base().put(Stat.maxhp, 100f);
      attrs.base().put(Stat.mana, 20f);
      attrs.base().put(Stat.maxmana, 100f);
      attrs.base().put(Stat.hpregen, 0);
      attrs.base().put(Stat.vitality, 0); attrs.base().put(Stat.energy, 0);
      attrs.reset();
      world.getMapper(AttributesWrapper.class).create(target).attrs = attrs;
      if (player) world.getMapper(Player.class).create(target).data = character;
      else world.getMapper(Monster.class).create(target);
      component = world.getMapper(UnitStates.class).create(target).init(target);
      states = component.stateList;
      world.setDelta(1f / 25f);
    }
    int otherSource() {
      int entity = world.create(); world.getMapper(Monster.class).create(entity); return entity;
    }
    UnitState dot(int state, int rawRate, int length, int owner) {
      world.getSystem(StateUpdater.class).applyStateExact(target, state, length, 1, owner, rawRate / 256f, 0);
      return states.getState(state);
    }
    UnitState poison(int rawRate, int length, int owner) { return dot(StateId.POISON, rawRate, length, owner); }
    void drink(String code) {
      Item potion = new ItemGenerator().generate(code); potion.id = ++potionId;
      assertTrue(character.getItems().addPotionToBelt(potion));
      assertTrue(character.useBeltPotion(0));
    }
    void tick() { world.process(); }
    void frames(int count) { for (int i = 0; i < count; i++) tick(); }
    int hp() { return attrs.get(Stat.hitpoints).encodedValues(); }
    @Override public void close() { world.dispose(); }
  }
}
