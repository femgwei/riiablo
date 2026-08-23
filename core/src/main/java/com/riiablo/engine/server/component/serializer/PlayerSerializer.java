package com.riiablo.engine.server.component.serializer;

import com.google.flatbuffers.FlatBufferBuilder;

import com.riiablo.engine.server.component.Player;
import com.riiablo.net.packet.d2gs.ComponentP;
import com.riiablo.net.packet.d2gs.EntitySync;
import com.riiablo.net.packet.d2gs.PlayerP;
import com.riiablo.save.CharData;

public class PlayerSerializer implements FlatBuffersSerializer<Player, PlayerP> {
  public static final PlayerP table = new PlayerP();

  @Override
  public byte getDataType() {
    return ComponentP.PlayerP;
  }

  @Override
  public int putData(FlatBufferBuilder builder, Player c) {
    CharData data = c.data;
    int charNameOffset = builder.createString(data.name);
    // PlayerP is also the authoritative progression snapshot.  The client
    // keeps its local CharData for presentation, but combat/XP is resolved by
    // the server in networked games, so serializing only the name/class leaves
    // the experience bar permanently stale.
    long experience = data.getStats().aggregate().getValue(
        com.riiablo.attributes.Stat.experience, 0L);
    int level = data.getStats().aggregate().getValue(
        com.riiablo.attributes.Stat.level, data.level & 0xFF);

    PlayerP.startPlayerP(builder);
    PlayerP.addLevel(builder, level);
    PlayerP.addExperience(builder, Math.max(0L, experience));
    PlayerP.addCharName(builder, charNameOffset);
    PlayerP.addCharClass(builder, data.charClass);
    return PlayerP.endPlayerP(builder);
  }

  @Override
  public PlayerP getTable(EntitySync sync, int j) {
    sync.component(table, j);
    return table;
  }

  @Override
  public Player getData(EntitySync sync, int j, Player c) {
    throw new UnsupportedOperationException("Not supported!");
  }
}
