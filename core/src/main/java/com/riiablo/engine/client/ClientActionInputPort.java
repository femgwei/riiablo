package com.riiablo.engine.client;

import com.riiablo.codec.excel.Skills;
import com.riiablo.item.Item;

/** Read-only character and table facts needed to validate pointer actions. */
public interface ClientActionInputPort {
  int action(int button);
  Skills.Entry skill(int skillId);
  Item equippedThrowableWeapon();
  Item equippedRangedWeapon();
}
