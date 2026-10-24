package com.riiablo.engine.server.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure token/implementation policy tests; no ECS world or MPQ is required. */
class MonsterAiPolicyTest {
  @Test
  void preservesOrdinaryNativeTokens() {
    assertEquals("Andariel", MonsterAiPolicy.implementationToken("Andariel"));
    assertEquals("com.riiablo.engine.server.ai.Andariel",
        MonsterAiPolicy.implementationClassName("Andariel"));
    assertFalse(MonsterAiPolicy.isTokenAlias("Andariel"));
  }

  @Test
  void resolvesD2TokensThatCannotBeJavaClassNames() {
    assertEquals("TrapMelee", MonsterAiPolicy.implementationToken("Trap-Melee"));
    assertEquals("TrapRightArrow", MonsterAiPolicy.implementationToken("Trap-RightArrow"));
    assertEquals("com.riiablo.engine.server.ai.TrapPoison",
        MonsterAiPolicy.implementationClassName("Trap-Poison"));
    assertTrue(MonsterAiPolicy.isTokenAlias("Trap-Nova"));
  }

  @Test
  void keepsIdleSingletonAndDedicatedImplementationsExplicit() {
    assertSame(AI.IDLE, MonsterAiPolicy.create(12, "Idle"));
    assertTrue(MonsterAiPolicy.create(12, "7TIllusion") instanceof SevenTIllusion);
    assertTrue(MonsterAiPolicy.create(12, "Trap-LeftArrow") instanceof TrapLeftArrow);
  }

  @Test
  void missingOrInvalidTokensUseGenericAuthoritativeFallback() {
    assertTrue(MonsterAiPolicy.create(12, "MissingNativeAi") instanceof GenericMonster);
    assertTrue(MonsterAiPolicy.create(12, null) instanceof GenericMonster);
    assertNull(MonsterAiPolicy.implementationToken(null));
  }

  private static void assertSame(Object expected, Object actual) {
    assertTrue(expected == actual, "expected singleton instance");
  }
}
