package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;

/** Real MPQ COFs and the real LibGDX async queue; no DCC/GL rendering. */
class Cof110fModeTransitionTest extends RiiabloTest {
  @Test void missingNativeChestOperationDoesNotBlockTownIdle() {
    assertTrue(Riiablo.mpqs.contains("data/global/objects/bo/cof/boNUHTH.cof"));
    assertFalse(Riiablo.mpqs.contains("data/global/objects/bo/cof/boOPHTH.cof"));
    assertFalse(Riiablo.mpqs.contains("data/global/objects/bm/cof/bmOPHTH.cof"));
    CofModeTransitionTest.missingCofDoesNotBlockPlayer(Riiablo.mpqs, false);
  }
}
