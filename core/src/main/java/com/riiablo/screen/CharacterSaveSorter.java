package com.riiablo.screen;

import com.badlogic.gdx.files.FileHandle;

import java.util.Arrays;

/** Orders character saves the same way the character-selection screen presents them. */
final class CharacterSaveSorter {
  private CharacterSaveSorter() {}

  /** Sorts saves from most recently modified to oldest, with a stable name fallback. */
  static void sort(FileHandle[] saves) {
    Arrays.sort(saves, (left, right) -> {
      int byModified = Long.compare(right.lastModified(), left.lastModified());
      return byModified != 0
          ? byModified
          : left.name().compareToIgnoreCase(right.name());
    });
  }
}
