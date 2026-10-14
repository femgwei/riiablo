package com.riiablo.engine.server.ai;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;

/** D2MOO AiThink.cpp AITHINK_Fn098_Smith (0x6FCE28F0). */
public class Smith extends NativeDecisionAI {
  public Smith(int entityId) {
    super(entityId);
  }

  static int velocityBonus(int lifePercent) {
    return (100 - Math.max(0, Math.min(100, lifePercent))) >> 1;
  }

  static int lifePercentage(int hp, int maxHp) {
    return maxHp == 0 ? 0 : (int) (100L * hp / maxHp);
  }

  @Override
  protected void think(int targetId, float distance, boolean combat) {
    if (combat) {
      attack(targetId);
      return;
    }
    int lifePercent = 100;
    if (mAttributesWrapper.has(entityId)) {
      Attributes attrs = mAttributesWrapper.get(entityId).attrs;
      StatRef hp = attrs.get(Stat.hitpoints);
      StatRef maxHp = attrs.get(Stat.maxhp);
      if (hp != null && maxHp != null) {
        float max = maxHp.asFixed();
        lifePercent = max <= 0f ? 0 : (int) (100f * hp.asFixed() / max);
      }
    }
    approach(targetId, velocityBonus(lifePercent));
  }
}
