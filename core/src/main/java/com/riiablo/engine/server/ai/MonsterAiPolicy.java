package com.riiablo.engine.server.ai;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * D2 monster-AI name policy.
 *
 * <p>MonStats stores an AI token, while Java implementations use class names.
 * The token aliases and the missing-implementation fallback are world-rule
 * policy, not a concern of the generic AI mechanism.  Keeping this boundary
 * explicit also makes an unresolved native AI observable instead of silently
 * selecting the shared idle singleton.</p>
 */
public final class MonsterAiPolicy {
  private static final Logger log = LogManager.getLogger(MonsterAiPolicy.class);
  private static final String AI_PACKAGE = "com.riiablo.engine.server.ai.";

  private static final Map<String, String> TOKEN_ALIASES;
  static {
    Map<String, String> aliases = new HashMap<>();
    aliases.put("7TIllusion", "SevenTIllusion");
    aliases.put("Trap-Melee", "TrapMelee");
    aliases.put("Trap-Missile", "TrapMissile");
    aliases.put("Trap-Nova", "TrapNova");
    aliases.put("Trap-Poison", "TrapPoison");
    aliases.put("Trap-RightArrow", "TrapRightArrow");
    aliases.put("Trap-LeftArrow", "TrapLeftArrow");
    TOKEN_ALIASES = Collections.unmodifiableMap(aliases);
  }

  private MonsterAiPolicy() {}

  /** Returns the Java implementation token for a MonStats AI value. */
  public static String implementationToken(String configuredToken) {
    String alias = TOKEN_ALIASES.get(configuredToken);
    return alias != null ? alias : configuredToken;
  }

  /** Returns the class name used for native AI resolution. */
  public static String implementationClassName(String configuredToken) {
    return AI_PACKAGE + String.valueOf(implementationToken(configuredToken));
  }

  /** Returns whether the token is an explicit D2 token alias. */
  public static boolean isTokenAlias(String configuredToken) {
    return TOKEN_ALIASES.containsKey(configuredToken);
  }

  /**
   * Creates the configured native AI, or the authoritative generic fallback
   * when the dedicated implementation is unavailable.
   */
  public static AI create(int entityId, String configuredToken) {
    String className = implementationClassName(configuredToken);
    try {
      Class<?> clazz = Class.forName(className);
      if (clazz == Idle.class) return AI.IDLE;
      Constructor<?> constructor = clazz.getConstructor(int.class);
      return (AI) constructor.newInstance(entityId);
    } catch (ClassNotFoundException e) {
      log.warn("[AI_FALLBACK] entityId={} ai={} className={} using GenericMonster",
          entityId, configuredToken, className);
      return new GenericMonster(entityId, configuredToken);
    } catch (Throwable t) {
      log.error("[AI_FALLBACK] failed to load entityId={} ai={} className={} error={} using GenericMonster",
          entityId, configuredToken, className, ExceptionUtils.getRootCauseMessage(t), t);
      return new GenericMonster(entityId, configuredToken);
    }
  }
}
