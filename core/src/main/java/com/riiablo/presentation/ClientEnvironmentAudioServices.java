package com.riiablo.presentation;

import com.riiablo.audio.Audio;
import com.riiablo.codec.excel.SoundEnviron;
import com.riiablo.codec.excel.Sounds;
import com.riiablo.map.EnvironmentCycle;

/** Narrow environment-audio services consumed by the client presentation system. */
public interface ClientEnvironmentAudioServices {
  EnvironmentCycle environment();
  int listener();
  Audio audio();
  SoundEnviron soundEnviron();
  Sounds sounds();
}
