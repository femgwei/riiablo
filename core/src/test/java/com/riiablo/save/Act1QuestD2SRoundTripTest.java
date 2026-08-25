package com.riiablo.save;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.StatListReader;
import com.riiablo.engine.server.quest.Act1DenOfEvilQuest;
import com.riiablo.io.ByteInput;
import com.riiablo.item.ItemReader;
import org.junit.jupiter.api.Test;

class Act1QuestD2SRoundTripTest extends RiiabloTest {
  @Test
  void preservesNativeAct1QuestWordForEveryDifficulty() {
    CharData source = CharData.obtain().set(
        Riiablo.NORMAL, false, "QuestRoundTrip", Riiablo.AMAZON);
    setRecord(source, Riiablo.NORMAL, (short) 0xE01F);
    setRecord(source, Riiablo.NIGHTMARE, (short) 0xA005);
    setRecord(source, Riiablo.HELL, (short) 0x4002);

    byte[] encoded = new D2SWriter96().writeD2S(D2SWriter96.createD2S(source));
    ByteInput in = ByteInput.wrap(encoded);
    D2S decoded = D2SReader.INSTANCE.readD2S(in);
    D2SReader.INSTANCE.readRemaining(
        decoded, in, new StatListReader(), new ItemReader());
    CharData restored = CharData.obtain().set(Riiablo.NORMAL, false).load(decoded);

    assertRecord(restored, Riiablo.NORMAL, 0xE01F);
    assertRecord(restored, Riiablo.NIGHTMARE, 0xA005);
    assertRecord(restored, Riiablo.HELL, 0x4002);
    assertEquals(0, in.bytesRemaining());
  }

  private static void setRecord(CharData data, int difficulty, short record) {
    data.diff = difficulty;
    data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD] = record;
  }

  private static void assertRecord(CharData data, int difficulty, int expected) {
    data.diff = difficulty;
    assertEquals(expected, Short.toUnsignedInt(
        data.getQuests(Riiablo.ACT1)[Act1DenOfEvilQuest.RECORD]));
  }
}
