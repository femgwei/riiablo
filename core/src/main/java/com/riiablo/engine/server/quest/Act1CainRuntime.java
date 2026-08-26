package com.riiablo.engine.server.quest;

import java.util.Random;

/** Game-scoped A1Q4 state which must never be written into one player's D2S. */
public final class Act1CainRuntime {
  public enum StoneResult {
    WRONG,
    ADVANCED,
    LAST_STONE,
    COMPLETE
  }

  private final int[] stoneOrder = new int[Act1CainQuest.STONE_COUNT];
  private int operated;
  private boolean initialized;
  private boolean portalOpened;
  private boolean cainReleased;

  public void initialize(long gameSeed) {
    if (initialized) return;
    for (int i = 0; i < stoneOrder.length; i++) {
      stoneOrder[i] = Act1CainQuest.FIRST_STONE_OBJECT + i;
    }

    // D2MOO assigns the five native class ids to random sequence slots. Its
    // recovered loop does not retry collisions and can leave zero slots, so
    // use the equivalent unbiased permutation while guaranteeing all stones.
    Random random = new Random(gameSeed ^ 0xA1C41F055L);
    for (int i = stoneOrder.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      int swap = stoneOrder[i];
      stoneOrder[i] = stoneOrder[j];
      stoneOrder[j] = swap;
    }
    initialized = true;
  }

  public StoneResult inspect(int objectClassId) {
    if (portalOpened) return StoneResult.COMPLETE;
    if (!initialized || operated >= stoneOrder.length
        || !Act1CainQuest.isExpectedStone(objectClassId, stoneOrder, operated)) {
      return StoneResult.WRONG;
    }
    return operated == stoneOrder.length - 1
        ? StoneResult.LAST_STONE : StoneResult.ADVANCED;
  }

  public void advance() {
    if (!initialized || portalOpened || operated >= stoneOrder.length) return;
    operated++;
  }

  public void markPortalOpened() {
    portalOpened = true;
    operated = stoneOrder.length;
  }

  public int[] stoneOrder() {
    return stoneOrder.clone();
  }

  public int operated() {
    return operated;
  }

  public boolean portalOpened() {
    return portalOpened;
  }

  public boolean cainReleased() {
    return cainReleased;
  }

  public void markCainReleased() {
    cainReleased = true;
  }
}
