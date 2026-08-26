package com.riiablo.engine.server.object;

import com.riiablo.Riiablo;
import com.riiablo.item.StoreLoc;
import com.riiablo.item.Type;
import com.riiablo.save.CharData;

/** Native locked-chest key check from {@code D2GAME_DoKeyCheck}. */
public final class NativeObjectKeyResolver {
  public enum Result {
    NOT_LOCKED(true),
    ASSASSIN_BYPASS(true),
    KEY_CONSUMED(true),
    MISSING_KEY(false);

    public final boolean allowsOpen;

    Result(boolean allowsOpen) {
      this.allowsOpen = allowsOpen;
    }
  }

  private NativeObjectKeyResolver() {}

  public static Result unlock(int interactType, CharData player) {
    if (!NativeObjectInteractTypeResolver.locked(interactType)) {
      return Result.NOT_LOCKED;
    }
    if (player == null) return Result.MISSING_KEY;
    if ((player.charClass & 0xFF) == Riiablo.ASSASSIN) {
      return Result.ASSASSIN_BYPASS;
    }
    return player.getItems().consumeStoredItemQuantity(StoreLoc.INVENTORY, Type.KEY)
        ? Result.KEY_CONSUMED
        : Result.MISSING_KEY;
  }
}
