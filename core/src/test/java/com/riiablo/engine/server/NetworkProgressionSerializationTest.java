package com.riiablo.engine.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.serializer.PlayerSerializer;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.net.packet.d2gs.D2GS;
import com.riiablo.net.packet.d2gs.D2GSData;
import com.riiablo.net.packet.d2gs.SelectSkillRequest;
import com.riiablo.save.CharData;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

/** Headless regression for the server progression snapshot sent to clients. */
class NetworkProgressionSerializationTest extends RiiabloTest {
  @Test
  void playerSnapshotCarriesExperienceAndLevelForHudSync() {
    CharData data = CharData.obtain().clear()
        .set(Riiablo.NORMAL, false, "NetworkHero", Riiablo.AMAZON);
    data.level = 3;
    data.getStats().base().put(Stat.experience, 1234L);
    data.getStats().base().put(Stat.level, 3);
    data.getStats().aggregate().put(Stat.experience, 1234L);
    data.getStats().aggregate().put(Stat.level, 3);
    data.getStats().base().put(Stat.newskills, 2);
    data.getStats().aggregate().put(Stat.newskills, 2);
    data.getStats().base().put(Stat.statpts, 5);
    data.getStats().aggregate().put(Stat.statpts, 5);
    data.getStats().base().put(Stat.gold, 321);
    data.getStats().aggregate().put(Stat.gold, 321);
    data.getStats().base().put(Stat.goldbank, 6543);
    data.getStats().aggregate().put(Stat.goldbank, 6543);
    data.setSkillLevel(6, 3);
    data.getQuests(Riiablo.ACT1)[1] = (short) 0x1234;
    data.getQuests(Riiablo.ACT1)[6] = (short) 0x8001;

    Player player = new Player();
    player.data = data;
    FlatBufferBuilder builder = new FlatBufferBuilder(256);
    int offset = new PlayerSerializer().putData(builder, player);
    builder.finish(offset);
    ByteBuffer packet = builder.dataBuffer();
    PlayerP snapshot = PlayerP.getRootAsPlayerP(packet);

    assertEquals("NetworkHero", snapshot.charName());
    assertEquals(Riiablo.AMAZON, snapshot.charClass());
    assertEquals(1234L, snapshot.experience());
    assertEquals(3, snapshot.level());
    assertEquals(2, snapshot.skillPoints());
    assertEquals(5, snapshot.statPoints());
    assertEquals(321L, snapshot.gold());
    assertEquals(6543L, snapshot.goldBank());
    assertTrue(snapshot.walletPresent());
    assertEquals(1, snapshot.skillIdsLength());
    assertEquals(1, snapshot.skillLevelsLength());
    assertEquals(6, snapshot.skillIds(0));
    assertEquals(3, snapshot.skillLevels(0));
    assertEquals(Riiablo.NUM_ACTS * 8, snapshot.questRecordsLength());
    assertEquals(0x1234, snapshot.questRecords(1));
    assertEquals(0x8001, snapshot.questRecords(6));
    assertTrue(snapshot.questRevision() != 0L);
    System.out.println("[XP_NETWORK_CHAIN] phase=serialize character=" + snapshot.charName()
        + " experience=" + snapshot.experience() + " level=" + snapshot.level()
        + " skillPoints=" + snapshot.skillPoints() + " skill=6@3");
  }

  @Test
  void selectSkillRequestCarriesAuthoritativeAuraIntent() {
    FlatBufferBuilder builder = new FlatBufferBuilder(96);
    int requestOffset = SelectSkillRequest.createSelectSkillRequest(builder,
        0x10203040L, 1, com.riiablo.engine.server.skill.SkillId.MIGHT);
    int root = D2GS.createD2GS(builder, D2GSData.SelectSkillRequest, requestOffset);
    D2GS.finishD2GSBuffer(builder, root);

    D2GS packet = D2GS.getRootAsD2GS(builder.dataBuffer());
    SelectSkillRequest request = (SelectSkillRequest) packet.data(new SelectSkillRequest());
    assertEquals(D2GSData.SelectSkillRequest, packet.dataType());
    assertEquals(0x10203040L, request.requestId());
    assertEquals(1, request.button());
    assertEquals(com.riiablo.engine.server.skill.SkillId.MIGHT, request.skillId());
    System.out.println("[AURA_SYNC] request=270544960 button=RIGHT aura=MIGHT status=PASS");
  }
}
