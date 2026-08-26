package com.riiablo.engine.server.object;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.riiablo.codec.excel.Shrines;

class NativeShrineEffectResolverTest {
  @Test
  void classifiesNativeDispatchCodes() {
    assertEquals(NativeShrineEffectResolver.BASIC_HEALTH_MANA,
        NativeShrineEffectResolver.kindForCode(1));
    assertEquals(NativeShrineEffectResolver.COMBAT_BUFF,
        NativeShrineEffectResolver.kindForCode(7));
    assertEquals(NativeShrineEffectResolver.DEFENSIVE_BUFF,
        NativeShrineEffectResolver.kindForCode(15));
    assertEquals(NativeShrineEffectResolver.SKILL_BUFF,
        NativeShrineEffectResolver.kindForCode(12));
    assertEquals(NativeShrineEffectResolver.STAMINA,
        NativeShrineEffectResolver.kindForCode(14));
    assertEquals(NativeShrineEffectResolver.PORTAL,
        NativeShrineEffectResolver.kindForCode(17));
    assertEquals(NativeShrineEffectResolver.GEM,
        NativeShrineEffectResolver.kindForCode(18));
    assertEquals(NativeShrineEffectResolver.STORM,
        NativeShrineEffectResolver.kindForCode(19));
    assertEquals(NativeShrineEffectResolver.MONSTER,
        NativeShrineEffectResolver.kindForCode(20));
    assertEquals(NativeShrineEffectResolver.EXPLODING,
        NativeShrineEffectResolver.kindForCode(21));
    assertEquals(NativeShrineEffectResolver.POISON,
        NativeShrineEffectResolver.kindForCode(22));
    assertEquals(NativeShrineEffectResolver.UNKNOWN,
        NativeShrineEffectResolver.kindForCode(16));
  }

  @Test
  void preservesArgumentsAndNormalizesDuration() {
    Shrines.Entry row = new Shrines.Entry();
    row.Code = 17;
    row.EffectClass = 4;
    row.Arg0 = 9;
    row.Arg1 = 12;
    row.DurationInFrames = -1;

    NativeShrineEffectResolver.Effect effect =
        NativeShrineEffectResolver.resolve(row);
    assertEquals(17, effect.code);
    assertEquals(NativeShrineEffectResolver.PORTAL, effect.kind);
    assertEquals(4, effect.effectClass);
    assertEquals(9, effect.arg0);
    assertEquals(12, effect.arg1);
    assertEquals(0, effect.durationFrames);
  }

  @Test
  void nullRowsAreUnknown() {
    NativeShrineEffectResolver.Effect effect =
        NativeShrineEffectResolver.resolve(null);
    assertEquals(NativeShrineEffectResolver.UNKNOWN, effect.kind);
    assertEquals(0, effect.durationFrames);
  }
}
