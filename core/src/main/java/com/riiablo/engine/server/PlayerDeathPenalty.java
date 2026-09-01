package com.riiablo.engine.server;

import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.item.VendorPricing;
import com.riiablo.save.CharData;

/** Native player-death gold penalty from D2Game/PLAYER_ApplyDeathPenalty. */
public final class PlayerDeathPenalty {
  private PlayerDeathPenalty() {}

  public static Result apply(CharData player) {
    if (player == null || player.getStats() == null) return Result.EMPTY;
    int level = player.level;
    if (level <= 0) level = value(player.getStats().get(Stat.level));
    int carried = Math.max(0, value(player.getStats().get(Stat.gold)));
    int bank = Math.max(0, value(player.getStats().get(Stat.goldbank)));
    long total = (long) carried + bank;
    int penaltyPercent = Math.min(Math.max(level, 0), 20);
    int penalty = (int) Math.min(Integer.MAX_VALUE, total * penaltyPercent / 100L);

    // D2Game drops all carried gold. If that does not cover the native
    // penalty, the shortfall is removed from the stash and added to the pile.
    int dropped = (int) Math.min(Integer.MAX_VALUE, Math.max((long) carried, penalty));
    int bankAfter = (int) Math.max(0L, total - dropped);
    VendorPricing.setGoldSnapshot(player, 0, bankAfter);
    player.getStats().base().put(Stat.goldlost, penalty);
    player.getStats().aggregate().put(Stat.goldlost, penalty);
    return new Result(penaltyPercent, penalty, dropped, 0, bankAfter);
  }

  private static int value(StatRef value) {
    return value == null ? 0 : value.asInt();
  }

  public static final class Result {
    static final Result EMPTY = new Result(0, 0, 0, 0, 0);
    public final int penaltyPercent;
    public final int penaltyGold;
    public final int droppedGold;
    public final int carriedAfter;
    public final int bankAfter;

    Result(int penaltyPercent, int penaltyGold, int droppedGold,
           int carriedAfter, int bankAfter) {
      this.penaltyPercent = penaltyPercent;
      this.penaltyGold = penaltyGold;
      this.droppedGold = droppedGold;
      this.carriedAfter = carriedAfter;
      this.bankAfter = bankAfter;
    }
  }
}
