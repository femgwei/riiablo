package com.riiablo.engine.client;

import com.riiablo.Cursor;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.save.CharData;

/** Host-owned character and resource facts used by client item transactions. */
public interface ClientItemContextPort {
  CharData charData();
  int playerId();
  boolean inventoryVisible();
  boolean isGameActive();
  Cursor cursor();
  Files files();

  /** Compatibility source for isolated legacy fixtures without a presentation host. */
  static ClientItemContextPort fromGlobals() {
    return new ClientItemContextPort() {
      @Override public CharData charData() { return Riiablo.charData; }
      @Override public int playerId() {
        return Riiablo.game == null ? -1 : Riiablo.game.player;
      }
      @Override public boolean inventoryVisible() { return false; }
      @Override public boolean isGameActive() { return Riiablo.game != null; }
      @Override public Cursor cursor() { return Riiablo.cursor; }
      @Override public Files files() { return Riiablo.files; }
    };
  }
}
