package com.riiablo.engine.server;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.D2;
import org.junit.jupiter.api.Test;

/** Temporary local probe; not part of the product test suite. */
class EanimProbeTest extends RiiabloTest {
  @Test
  void dumpMonsterAttackKeyframeIndexes() {
    D2 d2 = D2.loadFromFile(Riiablo.mpqs.resolve("data\\global\\eanimdata.d2"));
    for (String cof : new String[] {"FAA1HTH", "FAA2HTH", "CRA11HS", "CRA12HT", "FSA2HTH"}) {
      D2.Entry entry = d2.getEntry(cof);
      StringBuilder indexes = new StringBuilder();
      if (entry != null && entry.data != null) {
        for (int i = 0; i < entry.data.length; i++) {
          if (entry.data[i] == 1) {
            if (indexes.length() > 0) indexes.append(',');
            indexes.append(i);
          }
        }
      }
      System.out.println("EAnim " + cof + " frames=" + (entry == null ? -1 : entry.framesPerDir)
          + " data=" + (entry == null || entry.data == null ? -1 : entry.data.length)
          + " atkIndexes=" + indexes);
    }
  }
}
