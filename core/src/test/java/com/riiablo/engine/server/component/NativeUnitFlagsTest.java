package com.riiablo.engine.server.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import org.junit.jupiter.api.Test;

class NativeUnitFlagsTest {
  @Test
  void regularMonsterStartsTargetableWithoutRewardSuppression() {
    NativeUnitFlags flags = new NativeUnitFlags().reset().set(NativeUnitFlags.MONSTER_TARGET);

    assertEquals(NativeUnitFlags.MONSTER_TARGET, flags.flags());
    assertTrue(flags.has(NativeUnitFlags.TARGETABLE));
    assertTrue(flags.has(NativeUnitFlags.CAN_BE_ATTACKED));
    assertTrue(flags.has(NativeUnitFlags.IS_VALID_TARGET));
    assertFalse(flags.has(NativeUnitFlags.NO_EXPERIENCE));
    assertFalse(flags.has(NativeUnitFlags.NO_TREASURE_CLASS));
  }

  @Test
  void valuesMatchD2CommonUnitFlags() {
    assertEquals(0x00000002, NativeUnitFlags.TARGETABLE);
    assertEquals(0x00000004, NativeUnitFlags.CAN_BE_ATTACKED);
    assertEquals(0x00000008, NativeUnitFlags.IS_VALID_TARGET);
    assertEquals(0x00000200, NativeUnitFlags.IS_MERCENARY);
    assertEquals(0x00020000, NativeUnitFlags.NO_TREASURE_CLASS);
    assertEquals(0x01000000, NativeUnitFlags.IS_INITIALIZED);
    assertEquals(0x02000000, NativeUnitFlags.IS_RESURRECT);
    assertEquals(0x04000000, NativeUnitFlags.NO_EXPERIENCE);
    assertEquals(0x80000000, NativeUnitFlags.IS_REVIVE);
  }

  @Test
  void resurrectionAndSelfResurrectionUseNoXpAndNoTcButNotIsResurrect() {
    NativeUnitFlags flags = new NativeUnitFlags().reset().markMonsterResurrection();

    assertTrue(flags.has(NativeUnitFlags.NO_EXPERIENCE));
    assertTrue(flags.has(NativeUnitFlags.NO_TREASURE_CLASS));
    assertFalse(flags.has(NativeUnitFlags.IS_RESURRECT));
    assertEquals(NativeUnitFlags.MONSTER_RESURRECTION, flags.flags());

    flags.reset().markSelfResurrection();
    assertEquals(NativeUnitFlags.SELF_RESURRECTION, flags.flags());
    assertFalse(flags.has(NativeUnitFlags.TARGETABLE));
    assertFalse(flags.has(NativeUnitFlags.CAN_BE_ATTACKED));
    assertFalse(flags.has(NativeUnitFlags.IS_VALID_TARGET));
    assertFalse(flags.has(NativeUnitFlags.IS_RESURRECT));
  }

  @Test
  void completingSelfResurrectionRestoresOnlyMonsterTargetBits() {
    NativeUnitFlags flags = new NativeUnitFlags().reset().markSelfResurrection();

    flags.set(NativeUnitFlags.MONSTER_TARGET);

    assertEquals(NativeUnitFlags.MONSTER_RESURRECTION, flags.flags());
    assertTrue(flags.has(NativeUnitFlags.MONSTER_TARGET));
    assertTrue(flags.has(NativeUnitFlags.NO_RESURRECTION_REWARD));
  }

  @Test
  void presetResurrectIsAnIndependentPolicy() {
    NativeUnitFlags flags = new NativeUnitFlags().reset().markPresetResurrect();

    assertTrue(flags.has(NativeUnitFlags.IS_RESURRECT));
    assertFalse(flags.has(NativeUnitFlags.NO_EXPERIENCE));
    assertFalse(flags.has(NativeUnitFlags.NO_TREASURE_CLASS));
  }

  @Test
  void nativeSummonPoliciesRemainCallSiteSpecific() {
    assertEquals(NativeUnitFlags.NO_EXPERIENCE, NativeUnitFlags.MAGGOT_LAY_SUMMON);
    assertEquals(NativeUnitFlags.NO_EXPERIENCE | NativeUnitFlags.NO_TREASURE_CLASS,
        NativeUnitFlags.NEST_SUMMON);
  }

  @Test
  void mercenaryPolicyIncludesIdentityAndSuppressesHostileRewards() {
    NativeUnitFlags flags = new NativeUnitFlags().reset().markMercenary();

    assertTrue(flags.has(NativeUnitFlags.IS_MERCENARY));
    assertTrue(flags.has(NativeUnitFlags.NO_EXPERIENCE));
    assertTrue(flags.has(NativeUnitFlags.NO_TREASURE_CLASS));
    assertEquals(NativeUnitFlags.MERCENARY, flags.flags());
  }

  @Test
  void entityDeletionRemovesFlagsBeforeNumericIdCanBeReused() {
    World world = new World(new WorldConfigurationBuilder().build());
    try {
      ComponentMapper<NativeUnitFlags> flags = world.getMapper(NativeUnitFlags.class);
      int entityId = world.create();
      flags.create(entityId).reset().markMonsterResurrection();
      assertTrue(flags.has(entityId));

      world.delete(entityId);
      world.process();

      assertFalse(flags.has(entityId));
    } finally {
      world.dispose();
    }
  }
}
