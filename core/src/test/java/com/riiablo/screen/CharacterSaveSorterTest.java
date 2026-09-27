package com.riiablo.screen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.badlogic.gdx.files.FileHandle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharacterSaveSorterTest {
  @TempDir Path temp;

  @Test
  void sortsNewestSaveFirst() throws Exception {
    Path older = Files.createFile(temp.resolve("older.d2s"));
    Path newer = Files.createFile(temp.resolve("newer.d2s"));
    Files.setLastModifiedTime(older, FileTime.fromMillis(1_000));
    Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000));

    FileHandle[] saves = {new FileHandle(older.toFile()), new FileHandle(newer.toFile())};
    CharacterSaveSorter.sort(saves);

    assertArrayEquals(new String[] {"newer.d2s", "older.d2s"},
        new String[] {saves[0].name(), saves[1].name()});
  }

  @Test
  void usesNameAsDeterministicTieBreaker() throws Exception {
    Path bravo = Files.createFile(temp.resolve("bravo.d2s"));
    Path alpha = Files.createFile(temp.resolve("alpha.d2s"));
    FileTime sameTime = FileTime.fromMillis(3_000);
    Files.setLastModifiedTime(bravo, sameTime);
    Files.setLastModifiedTime(alpha, sameTime);

    FileHandle[] saves = {new FileHandle(bravo.toFile()), new FileHandle(alpha.toFile())};
    CharacterSaveSorter.sort(saves);

    assertArrayEquals(new String[] {"alpha.d2s", "bravo.d2s"},
        new String[] {saves[0].name(), saves[1].name()});
  }
}
