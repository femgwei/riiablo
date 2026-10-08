package com.riiablo.engine.server.component.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.google.flatbuffers.FlatBufferBuilder;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.monster.MonsterAffix;
import com.riiablo.engine.server.monster.MonsterRank;
import com.riiablo.net.packet.d2gs.MonsterP;

class MonsterSerializerTest {
  @Test
  void sendsEliteIdentityNeededByClientPresentation() {
    MonStats.Entry stats = new MonStats.Entry();
    stats.hcIdx = 0;
    Monster monster = new Monster().set(stats, null).setRank(
        MonsterRank.SUPER_UNIQUE, MonsterAffix.SPECTRAL_HIT, -1, 40);
    FlatBufferBuilder builder = new FlatBufferBuilder(64);
    int offset = new MonsterSerializer().putData(builder, monster);
    builder.finish(offset);

    MonsterP packet = MonsterP.getRootAsMonsterP(ByteBuffer.wrap(builder.sizedByteArray()));
    assertEquals(MonsterRank.SUPER_UNIQUE, packet.rank());
    assertEquals(MonsterAffix.SPECTRAL_HIT, packet.affixes());
    assertEquals(-1, packet.championType());
    assertEquals(40, packet.uniqueId());
  }
}
