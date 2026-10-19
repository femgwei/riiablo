package com.riiablo.audio;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeSoundDataTest extends RiiabloTest {
  @Test void native110fSoundsHasGroupsButNoRedirectColumn() {
    String text = Riiablo.mpqs.resolve("data\\global\\excel\\sounds.txt").readString();
    java.util.List<String> columns = java.util.Arrays.asList(
        text.substring(0, text.indexOf('\n')).trim().split("\\t"));
    assertTrue(columns.contains("Group Size"));
    assertTrue(columns.contains("Index"));
    assertFalse(columns.stream().anyMatch(s -> "redirect".equalsIgnoreCase(s)),
        "dark-magic's Redirect policy must not be invented for the 1.10f table");
  }
}
