package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;
import static com.riiablo.engine.server.combat.NativeCombatConstants.*;
import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.DifficultyLevels;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MonsterPoisonSnapshotTest extends RiiabloTest {
  @ParameterizedTest
  @CsvSource({"0,true,-37,false,137,350", "1,true,-37,false,137,350",
      "2,true,-37,true,137,350", "1,false,-37,false,120,307",
      "2,false,-37,true,150,384", "0,false,-37,false,100,256"})
  void lengthPenaltyIsSeparateFromAlreadyFoldedElementalPenalty(int difficulty, boolean expansion,
      int tablePenalty, boolean included, int expectedLength, int expectedRate) {
    DifficultyLevels.Entry row = new DifficultyLevels.Entry(); row.ResistPenalty = tablePenalty;
    Attributes source = Attributes.obtainStandard(), target = Attributes.obtainStandard();
    int elementPenalty = expansion ? tablePenalty : difficulty == 2 ? -50 : difficulty == 1 ? -20 : 0;
    target.base().put(Stat.poisonresist, included ? elementPenalty : 0); target.reset();
    target.setDifficultyResistanceIncluded(included);
    CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(source, target, false,
        true, NativeCombatContext.forGameplay(difficulty, row, expansion));
    request.missile = request.alwaysHit = true;
    request.missileElemental = new NativeMissileElementalDamage(DAMAGE_POISON, 256, 256, 100);
    request.missilePhysical = new NativeMissilePhysicalDamage(0, 0, 0, 0, 0, false);
    CombatSnapshotFactory.Snapshot snapshot = CombatSnapshotFactory.create(request);
    NativeDamagePacket p = new NativeAttackResolver(new NativeCombatRolls(n -> 0))
        .resolve(snapshot.attacker, snapshot.defender);
    assertEquals(expectedLength, p.poisonDuration);
    assertEquals(expectedRate / 256f, p.poisonDamagePerFrame);
    assertEquals(0f, p.totalDamageHp());
  }
}
