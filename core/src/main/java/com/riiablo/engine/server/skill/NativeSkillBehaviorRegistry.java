package com.riiablo.engine.server.skill;

import com.badlogic.gdx.utils.IntMap;
import com.riiablo.codec.excel.Skills;

/**
 * Closed registry of native skill behavior families.
 *
 * <p>This is the riiablo equivalent of dark-magic's exact-ID behavior
 * manifest.  Similar-looking callback numbers are not enough: the skill id,
 * start callback and do callback must all agree with a declaration before a
 * family is returned.  New skills should be added here with a focused test
 * and native evidence instead of being inferred from a neighboring row.
 */
public final class NativeSkillBehaviorRegistry {
  private static final String EVIDENCE = "d2moo-1.10f-row-and-srv-dispatch";
  private static final IntMap<NativeSkillBehavior> AMAZON = new IntMap<>();

  static {
    // Amazon 6..35.  Callback numbers are the D2MOO SkillAma dispatch table;
    // missile names are retained as evidence for the later missile-family
    // resolver and are not used to infer support on their own.
    add(6,  "missile.arrow",                 0,  0, "magicarrow");
    add(7,  "missile.elemental-arrow",       4,  0, "firearrow");
    add(8,  "state.point-area",              0,  6);
    add(9,  "passive.stat-list",             0,  0);
    add(10, "melee.multi-hit",               5,  7);
    add(11, "missile.elemental-arrow-freeze",4,  0, "coldarrow");
    add(12, "missile.multishot",             4,  8, "multipleshotarrow", "multipleshotbolt");
    add(13, "passive.stat-list",             0,  0);
    add(14, "melee.elemental",               6,  2);
    add(15, "missile.poison-javelin",        4,  0, "poisonjav");
    add(16, "missile.impact-area",           4,  0, "explodingarrow");
    add(17, "state.point-area",              0,  6);
    add(18, "passive.stat-list",             0,  0);
    add(19, "melee.durability",              7,  2);
    add(20, "missile.elemental-javelin",     4,  0, "lightningjavelin");
    add(21, "missile.freeze",                4,  0, "icearrow");
    add(22, "missile.guided",                4, 10, "guidedarrow");
    add(23, "passive.stat-list",             0,  0);
    add(24, "melee.multibolt",               6, 11, "chargedstrikebolt");
    add(25, "missile.poison-cloud",          4,  0, "plaguejavelin");
    add(26, "missile.multishot",             8, 12, "strafearrow", "strafebolt");
    add(27, "missile.impact-area-periodic",  4,  0, "immolationarrow");
    add(28, "summon.decoy",                  0, 15);
    add(29, "passive.stat-list",             0,  0);
    add(30, "melee.multi-target",            9, 13);
    add(31, "missile.impact-area-freeze",   4,  0, "freezingarrow");
    add(32, "summon.valkyrie",               0, 16);
    add(33, "passive.stat-list",             0,  0);
    add(34, "melee.chain",                  10, 14, "lightningstrike");
    add(35, "missile.split",                 4,  0, "lightningfury");
  }

  private NativeSkillBehaviorRegistry() {}

  private static void add(int id, String family, int start, int done, String... missiles) {
    AMAZON.put(id, new NativeSkillBehavior(id, family, start, done, EVIDENCE, missiles));
  }

  /** Returns the exact Amazon declaration, or {@code null} for unknown rows. */
  public static NativeSkillBehavior resolveAmazon(Skills.Entry skill) {
    if (skill == null) return null;
    NativeSkillBehavior behavior = AMAZON.get(skill.Id);
    return behavior != null && behavior.matches(skill) ? behavior : null;
  }

  /** Returns the declaration for any currently registered native row. */
  public static NativeSkillBehavior resolve(Skills.Entry skill) {
    return resolveAmazon(skill);
  }

  /** Number of exact rows currently covered by the registry. */
  public static int amazonSize() { return AMAZON.size; }
}
