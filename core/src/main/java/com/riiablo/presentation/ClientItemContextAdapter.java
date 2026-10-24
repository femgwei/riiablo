package com.riiablo.presentation;

import java.util.Objects;

import com.riiablo.Cursor;
import com.riiablo.Files;
import com.riiablo.engine.client.ClientItemContextPort;
import com.riiablo.save.CharData;

/** Bridges host character/inventory state into the client item transaction port. */
public final class ClientItemContextAdapter implements ClientItemContextPort {
  private final ClientGameplayContext context;

  public ClientItemContextAdapter(ClientGameplayContext context) {
    this.context = Objects.requireNonNull(context, "context");
  }

  @Override public CharData charData() { return context.charData(); }
  @Override public int playerId() { return context.playerId(); }
  @Override public boolean inventoryVisible() { return context.inventoryVisible(); }
  @Override public boolean isGameActive() { return context.isGameActive(); }
  @Override public Cursor cursor() { return context.cursor(); }
  @Override public Files files() { return context.files(); }
}
