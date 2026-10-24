package com.riiablo.engine.server.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.MonStats;
import com.riiablo.engine.server.monster.MonsterType;
import org.junit.jupiter.api.Test;

class QuestNpcPolicyTest {
  @Test
  void preservesOrdinaryNpcOrdinal() {
    MonStats.Entry akara = new MonStats.Entry();
    akara.hcIdx = MonsterType.AKARA;
    akara.Id = "Akara";
    assertEquals(MonsterType.AKARA, QuestNpcPolicy.resolve(akara));
  }

  @Test
  void namedActFiveRowsOverrideCompatibilityOrdinals() {
    MonStats.Entry malah = new MonStats.Entry();
    malah.hcIdx = 514;
    malah.Id = "Malah";
    assertEquals(MonsterType.MALAH, QuestNpcPolicy.resolve(malah));

    MonStats.Entry qualKehk = new MonStats.Entry();
    qualKehk.hcIdx = 514;
    qualKehk.NameStr = "Qual-Kehk";
    assertEquals(MonsterType.QUAL_KEHK, QuestNpcPolicy.resolve(qualKehk));
  }

  @Test
  void resolvesTyraelThreeAndRejectsNull() {
    MonStats.Entry tyrael = new MonStats.Entry();
    tyrael.hcIdx = 521;
    tyrael.Id = "Tyrael3";
    assertEquals(MonsterType.TYRAEL3, QuestNpcPolicy.resolve(tyrael));
    assertEquals(MonsterType.INVALID, QuestNpcPolicy.resolve(null));
  }
}
