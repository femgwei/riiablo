package com.riiablo.engine.server.quest;

import com.artemis.annotations.Wire;
import com.riiablo.Files;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.ServerGameplayContext;
import com.riiablo.save.CharData;
import com.artemis.BaseSystem;

/** Host bindings shared by quest event adapters; quest rules remain pure. */
public abstract class ContextualQuestSystem extends BaseSystem {
  @Override protected void processSystem() {}
  @Wire(name = "serverContext", failOnNull = false)
  protected ServerGameplayContext serverContext;

  protected final Files files() {
    return serverContext != null ? serverContext.files() : Riiablo.files;
  }

  protected final int gameSeed() {
    return serverContext != null ? serverContext.gameSeed() : Riiablo.gameSeed;
  }

  protected final CharData character() {
    return serverContext != null ? serverContext.character() : Riiablo.charData;
  }

  protected final void persistCharacter(CharData data) {
    if (!data.managed) return;
    if (serverContext != null) serverContext.persistCharacter(data);
    else ServerGameplayContext.persistLegacyCharacter(data);
  }
}
