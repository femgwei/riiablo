package com.riiablo.engine.server.component;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PositionNativePrecisionTest {
  @Test void legacyCoordinatesNeverEstablishNativePrecision() {
    Position p = new Position();
    p.position.set(10, 20);
    assertFalse(p.hasNativePrecision());
    assertThrows(IllegalStateException.class, p::nativePrecisionX);
    assertThrows(IllegalStateException.class, p::nativePrecisionY);
  }

  @Test void explicitNativeCellInitializesItsCentreWithoutMovingHostCellCentre() {
    Position p = new Position().initializeNativeCell(10, 65535);
    assertEquals(0x000A8000, p.nativePrecisionX());
    assertEquals(0xFFFF8000, p.nativePrecisionY());
    assertEquals(new Vector2(10, 65535), p.position);
  }

  @Test void rawLowWordsSurviveLossyFloatProjection() {
    Position p = new Position().setNativePrecision(0xFFFF0001, 0x80000003);
    assertTrue(p.hasNativePrecision());
    assertEquals(0xFFFF0001, p.nativePrecisionX());
    assertEquals(0x80000003, p.nativePrecisionY());
    assertEquals(65534.5f, p.position.x);
    assertEquals(32767.5f, p.position.y);
    p.setNativePrecision(0, -1);
    assertEquals(0, p.nativePrecisionX());
    assertEquals(-1, p.nativePrecisionY());
  }

  @Test void visibleLegacyWriteInvalidatesAndReturningToOldPositionDoesNotRestoreTrust() {
    Position p = new Position().initializeNativeCell(10, 20);
    p.position.x += 1;
    assertFalse(p.hasNativePrecision());
    p.position.x -= 1;
    assertFalse(p.hasNativePrecision());
    assertThrows(IllegalStateException.class, p::nativePrecisionX);
    p.setNativePrecision(0x000A8001, 0x00148002);
    assertTrue(p.hasNativePrecision());
  }

  @Test void invalidCellDoesNotOverwriteKnownStateAndPoolResetClearsIt() {
    Position p = new Position().initializeNativeCell(10, 20);
    assertThrows(IllegalArgumentException.class, () -> p.initializeNativeCell(-1, 20));
    assertThrows(IllegalArgumentException.class, () -> p.initializeNativeCell(10, 65536));
    assertEquals(0x000A8000, p.nativePrecisionX());
    p.reset();
    assertTrue(p.position.isZero());
    assertFalse(p.hasNativePrecision());
    assertThrows(IllegalStateException.class, p::nativePrecisionY);
  }
}
