package com.riiablo.presentation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Static 1.10f byte oracle; this does not execute the original DLL. */
class FingerMageNativeClientEvidenceTest {
  @Test void dispatchAndImportedAccessorsIdentifyTheOriginalCallbacks() throws Exception {
    ByteBuffer pe = originalClient();
    assertEquals(0x6FB31DD0, pe.getInt(offset(pe, 0x6FB91260 + 15 * 4)));
    assertEquals(0x6FB366D0, pe.getInt(offset(pe, 0x6FB913D0 + 10 * 4)));
    assertEquals(0x80002B85, pe.getInt(offset(pe, 0x6FB6D29C)), "GetTargetX #11141");
    assertEquals(0x80002B74, pe.getInt(offset(pe, 0x6FB6D2AC)), "GetCurrentFrame #11124");
    assertEquals(0x80002B84, pe.getInt(offset(pe, 0x6FB6D5D0)), "SetTargetX #11140");
    assertCall(pe, 0x6FB31E43, 0x6FB6B4DE); // GetTargetX thunk
    assertCall(pe, 0x6FB31E6F, 0x6FB6AB9C); // SetTargetX thunk
    assertCall(pe, 0x6FB32067, 0x6FB6B4C6); // GetCurrentFrame thunk
    assertCall(pe, 0x6FB3213B, 0x6FB6AB9C); // new child's TargetX=0
  }

  @Test void countdownSpawnAndOverlayBranchesMatchTheAuditedInstructions() throws Exception {
    ByteBuffer pe = originalClient();
    // cmp TargetX,-CltParam2; jg survives, otherwise delete. Only then decrement.
    assertBytes(pe, 0x6FB31E4A, 0x8b, 0x43, 0x5c, 0xf7, 0xd8, 0x3b, 0xe8);
    assertBytes(pe, 0x6FB31E55, 0x7f, 0x0f);
    assertBytes(pe, 0x6FB31E66, 0x85, 0xed, 0x7f, 0x0a, 0x8d, 0x4d, 0xff);
    // max(CltParam1,1), CurrentFrame modulo interval, and TargetX > 0 spawn gate.
    assertBytes(pe, 0x6FB32057, 0x8b, 0x40, 0x58, 0x83, 0xf8, 0x01);
    assertBytes(pe, 0x6FB3206C, 0x99, 0xf7, 0xff, 0x85, 0xd2, 0x0f, 0x85);
    assertBytes(pe, 0x6FB32077, 0x85, 0xed, 0x0f, 0x8e);
    assertBytes(pe, 0x6FB32138, 0x6a, 0x00, 0x50);
    // CltHit10 reads signed ProgOverlay and attaches it to the target unit.
    assertBytes(pe, 0x6FB366D0, 0x56, 0x8b, 0xf2, 0x85, 0xf6);
    assertBytes(pe, 0x6FB3670D, 0x66, 0x8b, 0x40, 0x36, 0x66, 0x85, 0xc0, 0x7e);
    assertCall(pe, 0x6FB36727, 0x6FB5BA70);
  }

  private static ByteBuffer originalClient() throws Exception {
    String home = System.getenv("D2_HOME");
    Assumptions.assumeTrue(home != null, "optional binary oracle requires D2_HOME");
    Path dll = Paths.get(home, "D2Client.dll");
    Assumptions.assumeTrue(Files.isRegularFile(dll), "original D2Client.dll required");
    byte[] bytes = Files.readAllBytes(dll);
    StringBuilder hash = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
      hash.append(String.format("%02x", b & 0xff));
    }
    assertEquals("94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282",
        hash.toString(), "only the audited 1.10f image is an oracle");
    return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
  }

  private static void assertCall(ByteBuffer pe, int address, int target) {
    assertBytes(pe, address, 0xe8);
    assertEquals(target, address + 5 + pe.getInt(offset(pe, address) + 1));
  }

  private static void assertBytes(ByteBuffer pe, int address, int... expected) {
    int offset = offset(pe, address);
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], pe.get(offset + i) & 0xff,
          "1.10f VA 0x" + Integer.toHexString(address + i));
    }
  }

  private static int offset(ByteBuffer pe, int address) {
    int nt = pe.getInt(0x3c);
    int optional = nt + 24;
    int rva = address - pe.getInt(optional + 28);
    int sections = optional + (pe.getShort(nt + 20) & 0xffff);
    for (int i = 0; i < (pe.getShort(nt + 6) & 0xffff); i++) {
      int section = sections + 40 * i;
      int start = pe.getInt(section + 12);
      int rawSize = pe.getInt(section + 16);
      if (rva >= start && rva - start < rawSize) {
        return pe.getInt(section + 20) + rva - start;
      }
    }
    throw new IllegalArgumentException("unmapped source VA " + Integer.toHexString(address));
  }
}
