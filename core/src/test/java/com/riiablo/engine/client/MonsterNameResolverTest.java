package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.riiablo.Riiablo;
import com.riiablo.RiiabloTest;
import com.riiablo.codec.excel.MonStats;
import com.riiablo.codec.excel.SuperUniques;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.monster.MonsterRank;

class MonsterNameResolverTest extends RiiabloTest {
  @Test
  void resolvesCorpsefireInsteadOfItsZombieBaseName() {
    SuperUniques.Entry corpsefire = null;
    for (SuperUniques.Entry entry : Riiablo.files.SuperUniques) {
      if (entry != null && entry.hcIdx == 40) {
        corpsefire = entry;
        break;
      }
    }
    assertNotNull(corpsefire);
    MonStats.Entry zombie = Riiablo.files.monstats.get(corpsefire.MonClass);
    assertNotNull(zombie);
    Monster monster = new Monster().set(zombie, null)
        .setRank(MonsterRank.SUPER_UNIQUE, 0L, -1, corpsefire.hcIdx);

    assertEquals(Riiablo.string.lookup(corpsefire.Name),
        MonsterNameResolver.displayName(monster));
  }
}
