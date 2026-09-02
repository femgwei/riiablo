package com.riiablo.engine.server.item;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Function;

import com.riiablo.item.Item;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Location;
import com.riiablo.item.StoreLoc;
import com.riiablo.save.ItemData;

/**
 * Server-authoritative implementation of D2Game's gem shrine operation.
 *
 * <p>The native operation consumes the first upgradeable gem in the player's
 * inventory and creates the better gem on the ground.  Generation is done
 * before ownership is changed so a missing base item or a failed entity
 * creation cannot silently destroy the player's gem.</p>
 */
public final class NativeGemShrineService {
  private NativeGemShrineService() {}

  private static final Map<String, String> BETTER = new HashMap<>();
  static {
    String[][] tiers = {
        {"gcr", "gfr", "gsr", "glr", "gpr"},
        {"gcg", "gfg", "gsg", "glg", "gpg"},
        {"gcb", "gfb", "gsb", "glb", "gpb"},
        {"gcw", "gfw", "gsw", "glw", "gpw"},
        {"gcy", "gfy", "gsy", "gly", "gpy"},
        {"gcv", "gfv", "gsv", "glv", "gpv"},
        {"skc", "skf", "sku", "skl", "skz"}
    };
    for (String[] tier : tiers) {
      for (int i = 0; i + 1 < tier.length; i++) BETTER.put(tier[i], tier[i + 1]);
    }
  }

  private static final String[] CHIPPED = {"gcr", "gcg", "gcb", "gcw", "gcy", "gcv", "skc"};

  public enum Outcome {
    UPGRADED,
    DROPPED_CHIPPED,
    NO_ITEM,
    GENERATION_FAILED,
    DROP_FAILED,
    REMOVE_FAILED
  }

  public static final class Result {
    public final Outcome outcome;
    public final String sourceCode;
    public final String outputCode;
    public final int groundEntityId;

    Result(Outcome outcome, String sourceCode, String outputCode, int groundEntityId) {
      this.outcome = outcome;
      this.sourceCode = sourceCode;
      this.outputCode = outputCode;
      this.groundEntityId = groundEntityId;
    }

    public boolean mutated() {
      return outcome == Outcome.UPGRADED || outcome == Outcome.DROPPED_CHIPPED;
    }
  }

  /** Returns the native dwBetterGem mapping, or {@code null} at perfect tier. */
  public static String betterCode(String code) {
    return code == null ? null : BETTER.get(code.toLowerCase());
  }

  public static boolean isGemCode(String code) {
    return code != null && (BETTER.containsKey(code.toLowerCase())
        || BETTER.containsValue(code.toLowerCase()));
  }

  /**
   * Applies one shrine use.  {@code createGround} must return the created ECS
   * entity id or a negative value on failure.  {@code rollbackGround} is
   * called if the inventory removal fails after an entity was created.
   */
  public static Result apply(ItemData items, ItemGenerator generator,
      Function<Item, Integer> createGround, IntConsumer rollbackGround) {
    return apply(items, generator, createGround, rollbackGround, 0);
  }

  public static Result apply(ItemData items, ItemGenerator generator,
      Function<Item, Integer> createGround, IntConsumer rollbackGround,
      int chippedIndex) {
    if (items == null || generator == null || createGround == null) {
      return new Result(Outcome.GENERATION_FAILED, null, null, -1);
    }

    Item candidate = firstInventoryGem(items);
    String sourceCode = candidate == null ? null : candidate.code;
    String outputCode = candidate == null ? CHIPPED[0] : betterCode(candidate.code);
    // A perfect gem is still a valid GEM item, but native dwBetterGem is
    // zero for it: leave it untouched and use the chipped-gem fallback.
    Item source = outputCode == null ? null : candidate;
    if (source == null) {
      // D2MOO drops one random chipped gem when no upgradeable gem exists.
      // A stable first entry is used here; callers may randomize CHIPPED via
      // chooseChippedCode when they need to reproduce a specific RNG stream.
      outputCode = chooseChippedCode(chippedIndex);
    }

    final Item output;
    try {
      output = generator.generate(outputCode);
      if (output == null) return new Result(Outcome.GENERATION_FAILED,
          sourceCode, outputCode, -1);
      output.version = Item.VERSION_110;
      output.quality = com.riiablo.item.Quality.NORMAL;
      output.flags |= Item.ITEMFLAG_IDENTIFIED;
      if (source != null && source.ilvl > 0) output.ilvl = source.ilvl;
    } catch (Throwable t) {
      return new Result(Outcome.GENERATION_FAILED, sourceCode, outputCode, -1);
    }

    final int entityId;
    try {
      entityId = createGround.apply(output);
    } catch (Throwable t) {
      return new Result(Outcome.DROP_FAILED, sourceCode, outputCode, -1);
    }
    if (entityId < 0) return new Result(Outcome.DROP_FAILED, sourceCode, outputCode, -1);
    output.id = entityId;

    if (source != null) {
      if (!items.removeInventoryItem(source)) {
        if (rollbackGround != null) rollbackGround.accept(entityId);
        return new Result(Outcome.REMOVE_FAILED, sourceCode, outputCode, -1);
      }
      return new Result(Outcome.UPGRADED, sourceCode, outputCode, entityId);
    }
    return new Result(Outcome.DROPPED_CHIPPED, null, outputCode, entityId);
  }

  public static String chooseChippedCode(int index) {
    return CHIPPED[Math.floorMod(index, CHIPPED.length)];
  }

  private static Item firstInventoryGem(ItemData items) {
    for (Item item : items.getItems()) {
      if (item == null || !isGemCode(item.code)
          || item.location != Location.STORED || item.storeLoc != StoreLoc.INVENTORY) continue;
      return item;
    }
    return null;
  }

  /** Exposed for deterministic tests and native RNG adapters. */
  static String[] chippedCodes() {
    return Arrays.copyOf(CHIPPED, CHIPPED.length);
  }
}
