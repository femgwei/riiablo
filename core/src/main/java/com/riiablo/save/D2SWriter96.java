package com.riiablo.save;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Iterator;

import com.badlogic.gdx.utils.Array;

import com.riiablo.Riiablo;
import com.riiablo.attributes.StatListRef;
import com.riiablo.attributes.StatRef;
import com.riiablo.attributes.StatListWriter;
import com.riiablo.codec.COF;
import com.riiablo.codec.excel.Armor;
import com.riiablo.engine.server.component.Class;
import com.riiablo.io.BitOutput;
import com.riiablo.io.ByteOutput;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.item.ItemCodes;
import com.riiablo.item.ItemWriter;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

public class D2SWriter96 {
  private static final Logger log = LogManager.getLogger(D2SWriter96.class);

  private static final int VERSION = D2S.VERSION_110;
  private static final int NUM_STATS = 16;
  private static final int NUM_SKILLS = D2S.SkillData.NUM_TREES * D2S.SkillData.NUM_SKILLS;
  private static final int STAT_ID_BITS = 9;
  private static final int STAT_END = (1 << STAT_ID_BITS) - 1;
  private static final int UNKNOWN_PLAY_TIME = -1;

  /**
   * 从CharData创建D2S结构
   */
  public static D2S createD2S(CharData charData) {
    if (!D2S.isOriginalNameCompatible(charData.name)) {
      throw new IllegalArgumentException(
          "Character name is not compatible with Diablo II 1.13c: " + charData.name);
    }

    D2S d2s = new D2S();
    d2s.version = VERSION;
    d2s.alternate = charData.getItems().alternate;
    d2s.name = charData.name;
    d2s.flags = charData.flags;
    d2s.charClass = charData.charClass;
    d2s.level = charData.level;
    d2s.timestamp = (int) (System.currentTimeMillis() / 1000);
    d2s.hotkeys = charData.hotkeys.clone();
    d2s.actions = new int[D2S.NUM_ACTIONS][D2S.NUM_BUTTONS];
    for (int i = 0; i < D2S.NUM_ACTIONS; i++) {
      d2s.actions[i] = charData.actions[i].clone();
    }
    // 从装备中提取composites和colors
    d2s.composites = extractComposites(charData);
    d2s.colors = extractColors(charData);
    d2s.towns = charData.towns.clone();
    d2s.towns[charData.diff] = (byte) ((d2s.towns[charData.diff] & 0x7F) | 0x80);
    d2s.mapSeed = charData.mapSeed;

    // 佣兵数据
    d2s.merc = new D2S.MercData();
    d2s.merc.flags = charData.mercData.flags;
    d2s.merc.seed = charData.mercData.seed;
    d2s.merc.name = charData.mercData.name;
    d2s.merc.type = charData.mercData.type;
    d2s.merc.experience = charData.mercData.xp;
    if (charData.mercData.seed != 0) {
      d2s.merc.items = new D2S.ItemData();
      d2s.merc.items.items = new Array<>();
      for (int i = 0; i < charData.mercData.itemData.itemData.size; i++) {
        d2s.merc.items.items.add(charData.mercData.itemData.getItem(i));
      }
    }

    // 任务数据
    d2s.quests = createQuestData(charData);

    // 传送点数据
    d2s.waypoints = createWaypointData(charData);

    // NPC数据
    d2s.npcs = createNPCData(charData);

    // 属性数据
    d2s.stats = createStatData(charData);

    // 技能数据
    d2s.skills = createSkillData(charData);

    // 物品数据
    d2s.items = new D2S.ItemData();
    d2s.items.items = new Array<>();
    for (int i = 0; i < charData.itemData.itemData.size; i++) {
      d2s.items.items.add(charData.itemData.getItem(i));
    }

    // 尸体数据（目前为空）
    d2s.corpse = new D2S.ItemData();
    d2s.corpse.items = new Array<>();

    // 石魔数据
    d2s.golem = new D2S.GolemData();
    d2s.golem.exists = charData.hasGolemItem();
    d2s.golem.item = charData.getGolemItem();

    d2s.bodyRead = true;

    return d2s;
  }

  private static D2S.QuestData createQuestData(CharData charData) {
    D2S.QuestData quests = new D2S.QuestData();
    quests.flags = new byte[D2S.NUM_DIFFS][];

    for (int d = 0; d < D2S.NUM_DIFFS; d++) {
      byte[] flags = new byte[D2S.QuestData.NUM_QUESTFLAGS];

      // 将questData转换回字节数组
      int byteIndex = 0;
      for (int a = 0; a < Riiablo.NUM_ACTS; a++) {
        for (int q = 0; q < 8; q++) {
          short questFlag = charData.questData[d][a][q];
          flags[byteIndex++] = (byte) (questFlag & 0xFF);
          flags[byteIndex++] = (byte) ((questFlag >> 8) & 0xFF);
        }
      }

      quests.flags[d] = flags;
    }

    return quests;
  }

  private static D2S.WaypointData createWaypointData(CharData charData) {
    D2S.WaypointData waypoints = new D2S.WaypointData();
    waypoints.flags = new byte[D2S.NUM_DIFFS][];

    for (int d = 0; d < D2S.NUM_DIFFS; d++) {
      byte[] flags = new byte[D2S.WaypointData.NUM_WAYPOINTFLAGS];

      // 打包传送点位标志
      int bitIndex = 0;
      for (int a = 0; a < Riiablo.NUM_ACTS; a++) {
        int waypointBits = charData.waypointData[d][a];
        int numBits = (a == Riiablo.ACT4) ? 3 : 9;
        for (int b = 0; b < numBits; b++) {
          if ((waypointBits & (1 << b)) != 0) {
            flags[bitIndex / 8] |= (1 << (bitIndex % 8));
          }
          bitIndex++;
        }
      }

      waypoints.flags[d] = flags;
    }

    return waypoints;
  }

  private static D2S.NPCData createNPCData(CharData charData) {
    D2S.NPCData npcs = new D2S.NPCData();
    npcs.flags = new byte[D2S.NPCData.NUM_GREETINGS][D2S.NUM_DIFFS][];

    for (int d = 0; d < D2S.NUM_DIFFS; d++) {
      // 介绍问候语
      byte[] introFlags = new byte[D2S.NPCData.NUM_INTROS];
      long introData = charData.npcIntroData[d];
      for (int i = 0; i < D2S.NPCData.NUM_INTROS; i++) {
        introFlags[i] = (byte) ((introData >> (i * 8)) & 0xFF);
      }
      npcs.flags[D2S.NPCData.GREETING_INTRO][d] = introFlags;

      // 返回问候语
      byte[] returnFlags = new byte[D2S.NPCData.NUM_INTROS];
      long returnData = charData.npcReturnData[d];
      for (int i = 0; i < D2S.NPCData.NUM_INTROS; i++) {
        returnFlags[i] = (byte) ((returnData >> (i * 8)) & 0xFF);
      }
      npcs.flags[D2S.NPCData.GREETING_RETURN][d] = returnFlags;
    }

    return npcs;
  }

  private static D2S.StatData createStatData(CharData charData) {
    D2S.StatData stats = new D2S.StatData();
    stats.attrs = charData.getStats();
    return stats;
  }

  private static D2S.SkillData createSkillData(CharData charData) {
    D2S.SkillData skills = new D2S.SkillData();
    skills.skills = new byte[D2S.SkillData.NUM_TREES * D2S.SkillData.NUM_SKILLS];

    // 将技能映射转换回字节数组
    int firstSpell = charData.classId.firstSpell;
    for (int i = 0; i < skills.skills.length; i++) {
      int spellId = firstSpell + i;
      skills.skills[i] = (byte) charData.skillData.get(spellId, 0);
    }

    return skills;
  }

  private static final byte[] SIGNATURE = D2S.SIGNATURE;
  private static final byte[] QUESTS_SIGNATURE = {0x57, 0x6F, 0x6F, 0x21};
  private static final byte[] WAYPOINTS_SIGNATURE = {0x57, 0x53};
  private static final byte[] WAYPOINTS_DIFF_SIGNATURE = {0x02, 0x01};
  private static final byte[] NPCS_SIGNATURE = {0x01, 0x77};
  private static final byte[] STATS_SIGNATURE = {0x67, 0x66};
  private static final byte[] SKILLS_SIGNATURE = {0x69, 0x66};
  private static final byte[] ITEMS_SIGNATURE = {0x4A, 0x4D};
  private static final byte[] MERC_SIGNATURE = {0x6A, 0x66};
  private static final byte[] GOLEM_SIGNATURE = {0x6B, 0x66};

  protected StatListWriter statListWriter = new StatListWriter();
  protected ItemWriter itemWriter = new ItemWriter();

  public byte[] writeD2S(D2S d2s) {
    if (!D2S.isOriginalNameCompatible(d2s.name)) {
      throw new IllegalArgumentException(
          "Character name is not compatible with Diablo II 1.13c: " + d2s.name);
    }

    // 估算缓冲区大小（D2S文件通常为1-4KB）
    ByteBuf buffer = Unpooled.buffer(8192);
    ByteOutput out = ByteOutput.wrap(buffer);

    log.trace("Writing d2s...");

    // 写入签名和版本
    out.writeBytes(SIGNATURE);
    out.write32(VERSION);

    // 记住大小和校验和的位置
    final int sizeOffset = out.bytesWritten();
    out.write32(0); // 大小占位符

    final int checksumOffset = out.bytesWritten();
    out.write32(0); // 校验和占位符

    // 写入头部
    writeHeader(d2s, out);

    // 写入任务数据
    writeQuestData(d2s.quests, out);

    // 写入传送点数据
    writeWaypointData(d2s.waypoints, out);

    // 写入NPC数据
    writeNPCData(d2s.npcs, out);

    // 写入属性数据
    writeStatData(d2s.stats, out);

    // 写入技能数据
    writeSkillData(d2s.skills, out);

    // 写入物品数据
    writeItemData(d2s.items, out);

    // 写入尸体数据（新建角色时为空）
    writeCorpseData(d2s.corpse, out);

    // 写入扩展版专用数据
    if (d2s.isExpansion()) {
      // 写入佣兵物品
      writeMercItemData(d2s.merc, out);

      // 写入石魔数据
      writeGolemData(d2s.golem, out);
    }

    // 获取最终大小
    final int totalSize = out.bytesWritten();

    // 写回大小
    buffer.setIntLE(sizeOffset, totalSize);

    // 计算并写入校验和
    byte[] data = new byte[totalSize];
    buffer.getBytes(0, data);
    int checksum = computeChecksum(data, checksumOffset);
    buffer.setIntLE(checksumOffset, checksum);

    // 获取最终字节
    buffer.getBytes(0, data);

    log.debug("D2S written: {} bytes, checksum: 0x{}", totalSize, Integer.toHexString(checksum));

    return data;
  }

  static void writeHeader(D2S d2s, ByteOutput out) {
    out.write32(d2s.alternate);
    out.writeChars(d2s.name, Riiablo.MAX_NAME_LENGTH + 1);
    out.write32(d2s.flags);
    out.write8(d2s.charClass);
    out.write8(NUM_STATS);
    out.write8(NUM_SKILLS);
    out.write8(d2s.level);
    out.write32(d2s.timestamp); // dwCreateTime
    out.write32(d2s.timestamp);
    out.write32(UNKNOWN_PLAY_TIME);

    // 快捷键
    for (int hotkey : d2s.hotkeys) {
      out.write32(hotkey);
    }

    // 动作（技能绑定）
    for (int[] actions : d2s.actions) {
      for (int action : actions) {
        out.write32(action);
      }
    }

    // Composites和colors
    out.writeBytes(d2s.composites != null ? d2s.composites : new byte[COF.Component.NUM_COMPONENTS]);
    out.writeBytes(d2s.colors != null ? d2s.colors : new byte[COF.Component.NUM_COMPONENTS]);

    // 城镇
    out.writeBytes(d2s.towns != null ? d2s.towns : new byte[Riiablo.NUM_DIFFS]);

    // 地图种子
    out.write32(d2s.mapSeed);

    // 佣兵头部数据
    writeMercHeader(d2s.merc, out);

    // 领域数据（144字节，未使用）
    out.skipBytes(144);
  }

  static void writeMercHeader(D2S.MercData merc, ByteOutput out) {
    if (merc == null) {
      out.skipBytes(D2SReader96.MERC_SIZE);
      return;
    }
    out.write32(merc.flags);
    out.write32(merc.seed);
    out.write16(merc.name);
    out.write16(merc.type);
    out.write32((int) merc.experience);
  }

  static void writeQuestData(D2S.QuestData quests, ByteOutput out) {
    out.writeBytes(QUESTS_SIGNATURE);
    out.write32(6); // 版本
    out.write16(D2SReader96.QUESTS_SIZE); // 大小

    if (quests == null || quests.flags == null) {
      // 写入空的任务数据
      for (int i = 0; i < D2S.NUM_DIFFS; i++) {
        out.skipBytes(D2S.QuestData.NUM_QUESTFLAGS);
      }
    } else {
      for (int i = 0; i < D2S.NUM_DIFFS; i++) {
        out.writeBytes(quests.flags[i]);
      }
    }
  }

  static void writeWaypointData(D2S.WaypointData waypoints, ByteOutput out) {
    out.writeBytes(WAYPOINTS_SIGNATURE);
    out.write32(1); // 版本
    out.write16(D2SReader96.WAYPOINTS_SIZE); // 大小

    for (int i = 0; i < D2S.NUM_DIFFS; i++) {
      out.writeBytes(WAYPOINTS_DIFF_SIGNATURE);
      if (waypoints == null || waypoints.flags == null) {
        out.skipBytes(D2S.WaypointData.NUM_WAYPOINTFLAGS);
      } else {
        out.writeBytes(waypoints.flags[i]);
      }
    }
  }

  static void writeNPCData(D2S.NPCData npcs, ByteOutput out) {
    out.writeBytes(NPCS_SIGNATURE);
    out.write16(D2SReader96.NPCS_SIZE); // 大小

    if (npcs == null || npcs.flags == null) {
      // 写入空的NPC数据
      for (int i = 0; i < D2S.NPCData.NUM_GREETINGS; i++) {
        for (int j = 0; j < D2S.NUM_DIFFS; j++) {
          out.skipBytes(D2S.NPCData.NUM_INTROS);
        }
      }
    } else {
      for (int i = 0; i < D2S.NPCData.NUM_GREETINGS; i++) {
        for (int j = 0; j < D2S.NUM_DIFFS; j++) {
          out.writeBytes(npcs.flags[i][j]);
        }
      }
    }
  }

  void writeStatData(D2S.StatData stats, ByteOutput out) {
    out.writeBytes(STATS_SIGNATURE);

    BitOutput bits = out.unalign();

    if (stats != null && stats.attrs != null) {
      // D2MOO PLRSAVE2_WritePlayerStats uses ItemStatCost.CSvBits and
      // CSvParam for every base stat. StatListWriter implements that exact
      // data-driven representation and preserves 8-bit fixed-point values.
      StatListRef base = stats.attrs.base();
      for (Iterator<StatRef> it = base.iterator(); it.hasNext();) {
        StatRef stat = it.next();
        if (stat.entry().CSvBits <= 0) continue;
        bits.write15u(stat.id(), STAT_ID_BITS);
        statListWriter.write(base, stat, bits, true);
      }
    }
    bits.write15u(STAT_END, STAT_ID_BITS);
    bits.align();
  }

  static void writeSkillData(D2S.SkillData skills, ByteOutput out) {
    out.writeBytes(SKILLS_SIGNATURE);

    if (skills == null || skills.skills == null) {
      // 写入空技能数据（30字节）
      out.skipBytes(D2S.SkillData.NUM_TREES * D2S.SkillData.NUM_SKILLS);
    } else {
      out.writeBytes(skills.skills);
    }
  }

  void writeItemData(D2S.ItemData items, ByteOutput out) {
    out.writeBytes(ITEMS_SIGNATURE);

    if (items == null || items.items == null || items.items.size == 0) {
      out.write16(0); // 物品数量
    } else {
      out.write16(items.items.size);
      for (Item item : items.items) {
        itemWriter.writeItem(item, out);
      }
    }
  }

  void writeCorpseData(D2S.ItemData corpse, ByteOutput out) {
    out.writeBytes(ITEMS_SIGNATURE);

    // 尸体数量（0或1）
    if (corpse == null || corpse.items == null || corpse.items.size == 0) {
      out.write16(0);
    } else {
      out.write16(corpse.items.size);
      for (Item item : corpse.items) {
        itemWriter.writeItem(item, out);
      }
    }
  }

  void writeMercItemData(D2S.MercData merc, ByteOutput out) {
    out.writeBytes(MERC_SIGNATURE);

    if (merc == null || merc.seed == 0) {
      // 没有佣兵，无需写入物品
      return;
    }

    // 写入佣兵物品
    writeItemData(merc.items, out);
  }

  void writeGolemData(D2S.GolemData golem, ByteOutput out) {
    out.writeBytes(GOLEM_SIGNATURE);

    BitOutput bits = out.unalign();

    if (golem == null || !golem.exists) {
      bits.writeBoolean(false);
    } else {
      bits.writeBoolean(true);
      itemWriter.writeItem(golem.item, bits.align());
    }

    bits.align();
  }

  /**
   * 计算D2S文件的CRC校验和
   * 算法来自diablo_edit
   */
  static int computeChecksum(byte[] data, int checksumOffset) {
    // 计算前将校验和字段置零
    int checksum = 0;
    for (int i = 0; i < data.length; i++) {
      if (i >= checksumOffset && i < checksumOffset + 4) {
        // 跳过校验和字节（视为0）
        checksum = ((checksum << 1) | ((checksum >>> 31) & 1));
      } else {
        int b = data[i] & 0xFF;
        checksum = ((checksum << 1) | ((checksum >>> 31) & 1)) + b;
      }
    }
    return checksum;
  }

  /**
   * 从装备中提取composite值用于角色预览
   * 基于PlayerItemHandler的逻辑
   */
  private static byte[] extractComposites(CharData charData) {
    byte[] composites = new byte[COF.Component.NUM_COMPONENTS];
    ItemData itemData = charData.getItems();

    // D2MOO clears both arrays to 0xFF before inventory gfx are applied.
    Arrays.fill(composites, (byte) 0xFF);
    
    // 提取武器composites（RH, LH, SH）
    // 注意：盾牌可能在RARM或LARM槽位，武器在RARM/LARM槽位
    Item RARM = itemData.getEquipped(BodyLoc.RARM);
    Item LARM = itemData.getEquipped(BodyLoc.LARM);
    Item RH = null, LH = null, SH = null;
    
    if (RARM != null) {
      if (RARM.type.is(com.riiablo.item.Type.WEAP)) {
        RH = RARM;
      } else if (RARM.type.is(com.riiablo.item.Type.SHLD)) {
        SH = RARM;
      }
    }
    
    if (LARM != null) {
      if (LARM.type.is(com.riiablo.item.Type.WEAP)) {
        LH = LARM;
      } else if (LARM.type.is(com.riiablo.item.Type.SHLD)) {
        SH = LARM;
      }
    }
    
    if (RH != null && RH.base.alternateGfx != null) {
      int component = Class.Type.PLR.getComponent(RH.base.alternateGfx);
      if (component != 0 && component != ItemCodes.NIL) {
        composites[COF.Component.RH] = (byte) component;
      }
    }
    
    if (LH != null && LH.base.alternateGfx != null) {
      int component = Class.Type.PLR.getComponent(LH.base.alternateGfx);
      if (component != 0 && component != ItemCodes.NIL) {
        composites[COF.Component.LH] = (byte) component;
      }
    }
    
    if (SH != null && SH.base.alternateGfx != null) {
      int component = Class.Type.PLR.getComponent(SH.base.alternateGfx);
      if (component != 0 && component != ItemCodes.NIL) {
        composites[COF.Component.SH] = (byte) component;
      }
    }
    
    // 提取防具composites
    Item HEAD = itemData.getEquipped(BodyLoc.HEAD);
    if (HEAD != null && HEAD.base.alternateGfx != null) {
      int component = Class.Type.PLR.getComponent(HEAD.base.alternateGfx);
      if (component != 0 && component != ItemCodes.NIL) {
        composites[COF.Component.HD] = (byte) component;
      }
    }
    
    Item TORS = itemData.getEquipped(BodyLoc.TORS);
    if (TORS != null && TORS.type.is(com.riiablo.item.Type.ARMO)) {
      Armor.Entry armor = TORS.getBase();
      if (armor != null) {
        composites[COF.Component.TR] = (byte) (armor.Torso + 1);
        composites[COF.Component.LG] = (byte) (armor.Legs + 1);
        composites[COF.Component.RA] = (byte) (armor.rArm + 1);
        composites[COF.Component.LA] = (byte) (armor.lArm + 1);
        composites[COF.Component.S1] = (byte) (armor.lSPad + 1);
        composites[COF.Component.S2] = (byte) (armor.rSPad + 1);
      }
    }
    
    return composites;
  }

  /**
   * 从装备中提取颜色/变换值用于角色预览
   * 基于PlayerItemHandler的逻辑
   */
  private static byte[] extractColors(CharData charData) {
    byte[] colors = new byte[COF.Component.NUM_COMPONENTS];
    ItemData itemData = charData.getItems();

    Arrays.fill(colors, (byte) 0xFF);
    
    // 从装备中提取颜色
    Item HEAD = itemData.getEquipped(BodyLoc.HEAD);
    if (HEAD != null) {
      colors[COF.Component.HD] = (byte) ((HEAD.base.Transform << 5) | (HEAD.wrapper.charColorIndex & 0x1F));
    }
    
    Item TORS = itemData.getEquipped(BodyLoc.TORS);
    if (TORS != null) {
      byte packedTransform = (byte) ((TORS.base.Transform << 5) | (TORS.wrapper.charColorIndex & 0x1F));
      colors[COF.Component.TR] = packedTransform;
      colors[COF.Component.LG] = packedTransform;
      colors[COF.Component.RA] = packedTransform;
      colors[COF.Component.LA] = packedTransform;
      colors[COF.Component.S1] = packedTransform;
      colors[COF.Component.S2] = packedTransform;
    }
    
    return colors;
  }
}
