package com.riiablo.presentation;

import com.riiablo.audio.Audio;
import com.riiablo.graphics.PaletteIndexedBatch;
import com.riiablo.screen.GameScreen;
import java.util.function.Supplier;

/** Narrow presentation services that client systems may consume without host ownership details. */
public interface ClientPresentationServices {
  int playerId();
  Supplier<GameScreen> gameSupplier();
  Audio audio();
  PaletteIndexedBatch batch();
  void playEntitySound(int entityId, String sound, boolean loop);
}



