package com.riiablo.engine.server.combat;

import com.riiablo.RiiabloTest;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.engine.server.state.StateList;
import org.junit.jupiter.api.Test;
import static com.riiablo.engine.server.combat.CombatSystem.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises public production adapters, not unused DamageCalculator/DamageApplier. */
class NativeProductionMitigationIntegrationTest extends RiiabloTest {
  @Test void activeMitigationStatsReachAttackDirectReflectionRateAndStaticField() {
    CombatSystem combat = new CombatSystem(bound -> 0);
    Attributes defender = attributes();
    defender.base().put(Stat.fireresist, 50);
    defender.base().put(Stat.lightresist, 50);
    defender.reset();
    StateList states = new StateList(1);
    states.addState(StateId.CONVICTION, 0);
    states.getState(StateId.CONVICTION).setNativeModifier(Stat.magic_damage_reduction, 10);
    states.getState(StateId.CONVICTION).setNativeModifier(Stat.normal_damage_reduction, 100);
    states.getState(StateId.CONVICTION).setNativeModifier(Stat.item_absorbfire_percent, 25);
    states.getState(StateId.CONVICTION).setNativeModifier(Stat.item_absorbfire, 1);
    states.getState(StateId.CONVICTION).setNativeModifier(Stat.item_absorblight_percent, 25);
    states.getState(StateId.CONVICTION).setNativeModifier(Stat.item_absorblight, 1);
    int[] min = new int[DAMAGE_TYPE_COUNT], max = new int[DAMAGE_TYPE_COUNT];
    min[DAMAGE_FIRE] = max[DAMAGE_FIRE] = 100;
    CombatResult attack = combat.calculateAttack(attributes(), defender, false, false, false,
        1, 1, 1000, true, min, max, 0, 0, null, states);
    assertEquals(0, attack.physicalDamage);
    assertEquals(33, attack.elementalDamage[DAMAGE_FIRE]);
    assertEquals(12, attack.absorbedLife);
    CombatResult direct = combat.calculateFixedElementalDamage(defender, false, false,
        DAMAGE_FIRE, 100, 0, states, NativeCombatContext.normal());
    assertEquals(33, direct.totalDamage);
    assertEquals(12, direct.absorbedLife);
    assertEquals(0, combat.calculateFixedPhysicalDamage(defender, false, false, 100, states).totalDamage);
    assertEquals(0, combat.calculateNativeFixedPhysicalDamage(defender, false, 100, states).totalDamage);
    FixedElementalDamageResult rate = combat.calculateFixedElementalRateDamage(defender, false, false,
        DAMAGE_FIRE, 25600, 0, 1024, states, NativeCombatContext.normal());
    assertEquals(8384, rate.damageFixed);
    assertEquals(3136, rate.absorbedLifeFixed);
    StaticFieldDamageResult field = combat.calculateStaticFieldDamage(defender, false, false,
        DAMAGE_LIGHTNING, 25600, states, NativeCombatContext.normal());
    assertEquals(8384, field.damageFixed);
    assertEquals(3136, field.absorbedLifeFixed);
  }

  private static Attributes attributes() {
    Attributes attrs = Attributes.obtainStandard();
    attrs.base().clear();
    attrs.base().put(Stat.hitpoints, 100);
    attrs.base().put(Stat.maxhp, 100);
    attrs.base().put(Stat.level, 10);
    attrs.reset();
    return attrs;
  }
}
