package com.riiablo.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MusicControllerTest {
  @Test
  void identifiesOptionalMissingTrackFailure() {
    RuntimeException root = new IllegalArgumentException("file cannot be null");
    RuntimeException wrapped = new RuntimeException("Couldn't load dependencies of asset", root);
    assertTrue(MusicController.isMissingMusicFailure(wrapped));
  }

  @Test
  void retainsUnexpectedAudioFailures() {
    assertFalse(MusicController.isMissingMusicFailure(
        new RuntimeException("decoder rejected corrupted stream")));
  }
}
