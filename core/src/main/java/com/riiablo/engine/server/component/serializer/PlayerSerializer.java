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
    int skillPoints = data.getStats().aggregate().getValue(
        com.riiablo.attributes.Stat.newskills, 0);
    int statPoints = data.getStats().aggregate().getValue(
        com.riiablo.attributes.Stat.statpts, 0);

    com.riiablo.CharacterClass characterClass = data.classId != null
        ? data.classId : com.riiablo.CharacterClass.get(data.charClass & 0xFF);
    int learnedCount = 0;
    for (int skillId = characterClass.firstSpell; skillId < characterClass.lastSpell; skillId++) {
      if (data.getBaseSkillLevel(skillId) > 0) learnedCount++;
    }
    short[] skillIds = new short[learnedCount];
    byte[] skillLevels = new byte[learnedCount];
    for (int skillId = characterClass.firstSpell, i = 0;
        skillId < characterClass.lastSpell; skillId++) {
      int skillLevel = data.getBaseSkillLevel(skillId);
      if (skillLevel <= 0) continue;
      skillIds[i] = (short) skillId;
      skillLevels[i] = (byte) Math.min(0xFF, skillLevel);
      i++;
    }
    int skillIdsOffset = PlayerP.createSkillIdsVector(builder, skillIds);
    int skillLevelsOffset = PlayerP.createSkillLevelsVector(builder, skillLevels);

    short[] questRecords = com.riiablo.engine.server.quest.QuestSnapshot.records(data);
    long questRevision = com.riiablo.engine.server.quest.QuestSnapshot.revision(questRecords);
    int questRecordsOffset = PlayerP.createQuestRecordsVector(builder, questRecords);

    PlayerP.startPlayerP(builder);
    PlayerP.addSkillLevels(builder, skillLevelsOffset);
    PlayerP.addSkillIds(builder, skillIdsOffset);
    PlayerP.addSkillPoints(builder, Math.max(0, Math.min(0xFFFF, skillPoints)));
    PlayerP.addStatPoints(builder, Math.max(0, Math.min(0xFFFF, statPoints)));
    PlayerP.addLevel(builder, level);
    PlayerP.addExperience(builder, Math.max(0L, experience));
    PlayerP.addCharName(builder, charNameOffset);
    PlayerP.addCharClass(builder, data.charClass);
    PlayerP.addWalletPresent(builder, true);
    PlayerP.addGoldBank(builder, Math.max(0, Math.min(0xFFFFFFFFL,
        data.getStats().aggregate().getValue(com.riiablo.attributes.Stat.goldbank, 0L))));
    PlayerP.addGold(builder, Math.max(0, Math.min(0xFFFFFFFFL,
        data.getStats().aggregate().getValue(com.riiablo.attributes.Stat.gold, 0L))));
    PlayerP.addQuestRecords(builder, questRecordsOffset);
    PlayerP.addQuestRevision(builder, questRevision);
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
