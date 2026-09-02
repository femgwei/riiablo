package com.riiablo.engine.server;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.skill.AuraManager;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/** Bridges the D2MOO-style AuraManager to authoritative ECS state snapshots. */
public class AuraEcsSystem extends BaseSystem implements AuraManager.AuraCallback {
  private static final Logger log = LogManager.getLogger(AuraEcsSystem.class);
  private final AuraManager auras = new AuraManager();
  protected ComponentMapper<Position> mPosition;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<UnitStates> mUnitStates;
  protected ComponentMapper<AttributesWrapper> mAttributes;

  @Override protected void initialize() {
    auras.setCallback(this);
  }

  @Override protected void processSystem() {
    auras.update(world.delta);
  }

  public boolean selectAura(int entityId, int skillId) {
    if (!mPlayer.has(entityId)) return false;
    Player player = mPlayer.get(entityId);
    int level = player.data != null ? player.data.getSkill(skillId) : 0;
    if (mUnitStates.has(entityId) && mUnitStates.get(entityId).stateList != null) {
      level += mUnitStates.get(entityId).stateList.getTotalSkillModifier();
    }
    if (level <= 0) return false;
    boolean activated = auras.activateAura(entityId, skillId, level);
    if (activated) {
      log.info("[AURA] phase=activate caster={} skill={} level={} status=PASS",
          entityId, skillId, level);
    }
    return activated;
  }

  public AuraManager manager() { return auras; }

  public void clearAura(int entityId) {
    auras.deactivateAura(entityId);
  }

  @Override public void onAuraActivated(int casterId, int skillId, int skillLevel) {}
  @Override public void onAuraDeactivated(int casterId, int skillId) {
    log.info("[AURA] phase=deactivate caster={} skill={}", casterId, skillId);
  }
  @Override public void onEntityEnterAura(int entityId, int casterId, int skillId, int[] values) {
    log.debug("[AURA] phase=enter entity={} caster={} skill={}", entityId, casterId, skillId);
  }
  @Override public void onEntityLeaveAura(int entityId, int casterId, int skillId) {
    log.debug("[AURA] phase=leave entity={} caster={} skill={}", entityId, casterId, skillId);
  }
  @Override public float[] getEntityPosition(int entityId) {
    if (!mPosition.has(entityId)) return null;
    if (mAttributes.has(entityId)) {
      Attributes attrs = mAttributes.get(entityId).attrs;
      if (attrs != null && attrs.getValue(Stat.hitpoints, 0f) <= 0f) return null;
    }
    Vector2 p = mPosition.get(entityId).position;
    return new float[] {p.x, p.y};
  }
  @Override public Array<Integer> getEntitiesInRange(float x, float y, float range) {
    Array<Integer> result = new Array<>();
    IntBag entities = world.getAspectSubscriptionManager().get(Aspect.all(Position.class)).getEntities();
    float range2 = range * range;
    for (int i = 0; i < entities.size(); i++) {
      int id = entities.get(i);
      if (mPosition.has(id) && mPosition.get(id).position.dst2(x, y) <= range2) result.add(id);
    }
    return result;
  }
  @Override public boolean isAlly(int a, int b) {
    return (mPlayer.has(a) && mPlayer.has(b)) || (mMonster.has(a) && mMonster.has(b));
  }
  @Override public boolean consumeMana(int casterId, int amount) {
    if (!mAttributes.has(casterId)) return false;
    Attributes attrs = mAttributes.get(casterId).attrs;
    if (attrs == null) return false;
    if (attrs.getValue(Stat.mana, 0f) < amount) return false;
    attrs.get(Stat.mana).sub(amount);
    return true;
  }
  @Override public void applyState(int targetId, int stateId, int duration,
      int[] statIds, int[] values) {
    if (!mUnitStates.has(targetId)) return;
    UnitStates states = mUnitStates.get(targetId);
    if (states.stateList == null) states.init(targetId);
    com.riiablo.engine.server.state.UnitState state = states.stateList.addState(
        stateId, duration, 1, -1);
    if (state == null) return;
    state.needsSync = true;
    // Reapplication replaces the strongest value selected by AuraManager;
    // it must not accumulate each refresh/stronger-caster transition.
    state.damageModifier = 0;
    state.defenseModifier = 0;
    state.attackModifier = 0;
    state.velocityModifier = 0;
    state.fireResistModifier = 0;
    state.coldResistModifier = 0;
    state.lightResistModifier = 0;
    state.poisonResistModifier = 0;
    state.magicResistModifier = 0;
    if (values != null) {
      for (int i = 0; i < values.length && i < statIds.length; i++) {
        if (values[i] == 0) continue;
        // AuraManager's stat ids are D2 stat ids; map them to runtime state
        // modifiers consumed by movement and combat adapters.
        switch (statIds[i]) {
          case Stat.damagepercent: state.damageModifier += values[i]; break;
          case Stat.attackrate:
          case Stat.item_tohit_percent: state.attackModifier += values[i]; break;
          case Stat.item_armor_percent:
          case Stat.armorclass: state.defenseModifier += values[i]; break;
          case Stat.velocitypercent: state.velocityModifier += values[i]; break;
          case Stat.fireresist: state.fireResistModifier += values[i]; break;
          case Stat.coldresist: state.coldResistModifier += values[i]; break;
          case Stat.lightresist: state.lightResistModifier += values[i]; break;
          case Stat.poisonresist: state.poisonResistModifier += values[i]; break;
          case Stat.magicresist: state.magicResistModifier += values[i]; break;
          default: break;
        }
      }
    }
    log.info("[AURA] phase=apply entity={} state={} duration={} status=PASS",
        targetId, stateId, duration);
  }
  @Override public void removeState(int targetId, int stateId) {
    if (mUnitStates.has(targetId) && mUnitStates.get(targetId).stateList != null)
      mUnitStates.get(targetId).stateList.removeState(stateId);
  }
}
