package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Objects;
import com.riiablo.codec.excel.Shrines;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.CofManager;
import com.riiablo.engine.server.ObjectInteractor;
import com.riiablo.engine.server.component.CofReference;
import com.riiablo.engine.server.component.Interactable;
import com.riiablo.engine.server.component.NativeObjectState;
import com.riiablo.engine.server.event.WellInteractionEvent;
import com.riiablo.map.Map;
import com.riiablo.map.NativePresetObjectResolver;

import net.mostlyoriginal.api.event.common.EventSystem;

class NativeShrineSystemTest extends RiiabloTest {
  @Test
  void loadsNativeShrinesTableByRowId() {
    assertNotNull(Riiablo.files.Shrines);
    assertTrue(Riiablo.files.Shrines.size() >= 20);
    Shrines.Entry refill = Riiablo.files.Shrines.get(1);
    assertNotNull(refill);
    assertEquals(1, refill.Code);
    assertTrue(refill.EffectClass >= 0);
  }

  @Test
  void resolvesReservedPresetShrineRangesAndRemaps() {
    Shrines table = Riiablo.files.Shrines;
    Objects.Entry base = shrineObject(0);

    assertEquals(12, NativeShrineResolver.resolve(table, base, 577, 1, 7, 2, 3));
    assertEquals(14, NativeShrineResolver.resolve(table, base, 579, 1, 7, 2, 3));
    for (int seed = 0; seed < 50; seed++) {
      int id = NativeShrineResolver.resolve(table, base, 574, 1, seed, 2, 3);
      assertTrue(id == 2 || id == 3);
      int healing = NativeShrineResolver.resolve(table, base, 578, 1, seed, 2, 3);
      assertTrue(healing >= 1 && healing <= 3);
    }
  }

  @Test
  void selectsRequestedEffectClassAtEligibleLevel() {
    Shrines table = Riiablo.files.Shrines;
    Objects.Entry base = shrineObject(1); // Parm0=1 maps to effect class 2.
    int id = NativeShrineResolver.resolve(table, base, 136, 100, 123, 20, 30);
    Shrines.Entry selected = table.get(id);

    assertNotNull(selected);
    assertEquals(2, selected.EffectClass);
    assertTrue(selected.LevelMin <= 100);
  }

  @Test
  void appliesNativeBasicHealthAndManaEffects() {
    Attributes attrs = attributes(40f, 100f, 10f, 80f, 20f, 100f);
    assertTrue(NativeShrineSystem.applyBasicEffect(attrs, 1, 0, 0));
    assertStats(attrs, 100f, 80f, 20f);

    attrs = attributes(40f, 100f, 10f, 80f, 20f, 100f);
    assertTrue(NativeShrineSystem.applyBasicEffect(attrs, 2, 0, 0));
    assertStats(attrs, 100f, 10f, 20f);

    attrs = attributes(40f, 100f, 10f, 80f, 20f, 100f);
    assertTrue(NativeShrineSystem.applyBasicEffect(attrs, 3, 0, 0));
    assertStats(attrs, 40f, 80f, 20f);

    attrs = attributes(80f, 100f, 10f, 80f, 20f, 100f);
    assertTrue(NativeShrineSystem.applyBasicEffect(attrs, 4, 25, 200));
    assertStats(attrs, 60f, 50f, 20f);

    attrs = attributes(40f, 100f, 60f, 80f, 20f, 100f);
    assertTrue(NativeShrineSystem.applyBasicEffect(attrs, 5, 50, 200));
    assertStats(attrs, 100f, 30f, 20f);
    assertFalse(NativeShrineSystem.applyBasicEffect(attrs, 6, 0, 0));
  }

  @Test
  void appliesWellFractionMasksAndNativeModes() {
    Objects.Entry well = new Objects.Entry();
    well.Parm = new int[] {100, 64, 3, 3}; // 25%, 6 charges, hp+mana
    Attributes attrs = attributes(50f, 100f, 10f, 80f, 20f, 100f);

    assertTrue(NativeShrineSystem.applyWellEffect(attrs, well));
    assertEquals(WellInteractionEvent.RESTORED_LIFE
            | WellInteractionEvent.RESTORED_MANA
            | WellInteractionEvent.RESTORED_STAMINA,
        NativeShrineSystem.applyWellEffects(
            attributes(50f, 100f, 10f, 80f, 20f, 100f), well));
    assertStats(attrs, 75f, 30f, 45f);
    assertEquals(6, NativeShrineSystem.wellMaxCharges(well));
    assertEquals(101, NativeShrineSystem.wellRegenDelay(well));
    assertEquals(Engine.Object.MODE_NU,
        NativeShrineSystem.wellMode(Engine.Object.MODE_OP, 6, 3));
    assertEquals(Engine.Object.MODE_OP,
        NativeShrineSystem.wellMode(Engine.Object.MODE_NU, 3, 3));
    assertEquals(Engine.Object.MODE_ON,
        NativeShrineSystem.wellMode(Engine.Object.MODE_OP, 0, 3));
    assertEquals(Engine.Object.MODE_NU,
        NativeShrineSystem.wellMode(Engine.Object.MODE_NU, 5, 3));
    assertEquals(Engine.Object.MODE_ON,
        NativeShrineSystem.wellMode(Engine.Object.MODE_ON, 1, 3));
  }

  @Test
  void usesNativeShrineResetFrameConstant() {
    assertEquals(0, NativeShrineSystem.resetFrames(0));
    assertEquals(1200, NativeShrineSystem.resetFrames(1));
    assertEquals(6000, NativeShrineSystem.resetFrames(5));
  }

  @Test
  void reactivatesShrineAfterPersistedCooldown() {
    World world = objectWorld();
    try {
      int entityId = world.create();
      Objects.Entry shrine = shrineObject(0);
      shrine.OperateFn = 2;
      shrine.OperateRange = 4;
      world.getMapper(com.riiablo.engine.server.component.Object.class)
          .create(entityId).base = shrine;
      NativeObjectState state = world.getMapper(NativeObjectState.class).create(entityId)
          .set(0, 136, 136, Engine.Object.MODE_ON, false, false,
              NativePresetObjectResolver.Kind.SHRINE);
      state.persistActivated(true);
      state.persistShrineCooldownFrames(2f);
      world.getMapper(CofReference.class).create(entityId).mode = Engine.Object.MODE_ON;
      world.getMapper(Interactable.class).create(entityId).set(4f, new ObjectInteractor());

      world.delta = 1f / 25f;
      world.process();
      assertFalse(world.getMapper(Interactable.class).has(entityId));
      assertTrue(state.activated);
      assertEquals(1f, state.shrineCooldownFrames, 0.001f);

      world.process();
      assertFalse(state.activated);
      assertEquals(0f, state.shrineCooldownFrames, 0.001f);
      assertEquals(Engine.Object.MODE_NU,
          world.getMapper(CofReference.class).get(entityId).mode);
      assertTrue(world.getMapper(Interactable.class).has(entityId));
      assertEquals(4f, world.getMapper(Interactable.class).get(entityId).range);
    } finally {
      world.dispose();
    }
  }

  @Test
  void regeneratesOneWellChargeAtNativeThinkEvent() {
    World world = objectWorld();
    try {
      int entityId = world.create();
      Objects.Entry well = new Objects.Entry();
      well.OperateFn = 22;
      well.Parm = new int[] {100, 64, 3, 3};
      world.getMapper(com.riiablo.engine.server.component.Object.class)
          .create(entityId).base = well;
      NativeObjectState state = world.getMapper(NativeObjectState.class).create(entityId)
          .set(0, 84, 84, Engine.Object.MODE_ON, false, false,
              NativePresetObjectResolver.Kind.ORDINARY);
      state.persistWellCharges(0);
      state.persistWellRegenFrames(1f);
      world.getMapper(CofReference.class).create(entityId).mode = Engine.Object.MODE_ON;

      world.delta = 1f / 25f;
      world.process();
      assertEquals(1, state.wellCharges);
      assertEquals(101f, state.wellRegenFrames, 0.001f);
      assertEquals(Engine.Object.MODE_ON,
          world.getMapper(CofReference.class).get(entityId).mode);
    } finally {
      world.dispose();
    }
  }

  private static Objects.Entry shrineObject(int parm0) {
    Objects.Entry entry = new Objects.Entry();
    entry.Parm = new int[8];
    entry.Parm[0] = parm0;
    return entry;
  }

  private static World objectWorld() {
    return new World(new WorldConfigurationBuilder()
        .with(new EventSystem(), new CofManager(), new ObjectInteractor(),
            new NativeShrineSystem())
        .build()
        .register("map", new Map(7, 0)));
  }

  private static Attributes attributes(float hp, float maxHp, float mana,
      float maxMana, float stamina, float maxStamina) {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().put(Stat.hitpoints, hp);
    attrs.base().put(Stat.maxhp, maxHp);
    attrs.base().put(Stat.mana, mana);
    attrs.base().put(Stat.maxmana, maxMana);
    attrs.base().put(Stat.stamina, stamina);
    attrs.base().put(Stat.maxstamina, maxStamina);
    attrs.reset();
    return attrs;
  }

  private static void assertStats(Attributes attrs, float hp, float mana, float stamina) {
    assertEquals(hp, attrs.aggregate().getValue(Stat.hitpoints, 0f), 0.001f);
    assertEquals(mana, attrs.aggregate().getValue(Stat.mana, 0f), 0.001f);
    assertEquals(stamina, attrs.aggregate().getValue(Stat.stamina, 0f), 0.001f);
    assertEquals(hp, attrs.base().getValue(Stat.hitpoints, 0f), 0.001f);
    assertEquals(mana, attrs.base().getValue(Stat.mana, 0f), 0.001f);
  }
}
