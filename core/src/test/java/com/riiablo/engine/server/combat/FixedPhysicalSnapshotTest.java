package com.riiablo.engine.server.combat;

import static org.junit.jupiter.api.Assertions.*;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import org.junit.jupiter.api.Test;

class FixedPhysicalSnapshotTest extends RiiabloTest {
  @Test void factoryOptsInOnlyNonPrecomputedMonsterMeleeAndCopiesEndpointStats() {
    Attributes a = Attributes.obtainStandard();
    a.base().put(Stat.mindamage, 10);
    a.base().put(Stat.maxdamage, 10);
    a.base().put(Stat.strength, 50);
    a.base().put(Stat.damagepercent, 50);
    a.base().put(Stat.item_normaldamage, 2);
    a.base().put(Stat.item_mindamage_percent, 10);
    a.base().put(Stat.item_maxdamage_percent, 20);
    a.reset();
    Attributes d = Attributes.obtainStandard();
    d.reset();
    CombatSnapshotFactory.Request request = new CombatSnapshotFactory.Request(
        a, d, false, true, NativeCombatContext.normal());
    NativeAttackData snapshot = CombatSnapshotFactory.create(request).attacker;
    assertTrue(snapshot.fixedPointPhysical);
    assertEquals(2, snapshot.normalDamage);
    assertEquals(10, snapshot.minimumDamagePercent);
    assertEquals(20, snapshot.maximumDamagePercent);
    snapshot.alwaysHit = true;
    assertEquals(6451, new NativeAttackResolver(new NativeCombatRolls(bound -> 0))
        .resolve(snapshot, new NativeDefenseData()).physicalDamage);
    request.missile = true;
    assertFalse(CombatSnapshotFactory.create(request).attacker.fixedPointPhysical);
    request.missile = false;
    request.attackerPlayer = true;
    assertFalse(CombatSnapshotFactory.create(request).attacker.fixedPointPhysical);
    request.attackerPlayer = false;
    request.precomputedPhysicalDamage = true;
    assertFalse(CombatSnapshotFactory.create(request).attacker.fixedPointPhysical);
  }
}
