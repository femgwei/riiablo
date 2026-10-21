package com.riiablo.presentation;

import com.riiablo.audio.Audio;

/** Listener-relative audio playback services for one-shot presentation events. */
public interface ClientSpatialAudioServices {
  int listener();
  Audio audio();
}
