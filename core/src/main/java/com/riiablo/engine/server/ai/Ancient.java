package com.riiablo.engine.server.ai;

import com.badlogic.gdx.math.Vector2;
import com.riiablo.Riiablo;
import com.riiablo.codec.excel.Skills;
import com.riiablo.codec.excel.States;
import com.riiablo.engine.Engine;
import com.riiablo.engine.server.component.UnitStates;
import com.riiablo.engine.server.quest.Act5QuestSystem;
import com.riiablo.engine.server.state.StateId;
import com.riiablo.attributes.Stat;

/** Fn133 dispatches the distinct Talic, Madawc and Korlic handlers. */
public class Ancient extends NativeDecisionAI {
  public Ancient(int entityId) { super(entityId); }

  @Override protected int findNearestTargetWithAidist(float[] distance) {
    // Talic alone retains the ordinary tick target before the boss fallback.
    // Keep explicit control effects at the shared AI authority boundary.
    if (mNativeAiTargetOverride.has(entityId)) return super.findNearestTargetWithAidist(distance);
    if ("ancientbarb1".equals(monster.monstats.Id)) {
      int ordinary = super.findNearestTargetWithAidist(distance);
      if (ordinary != Engine.INVALID_ENTITY) return ordinary;
    }
    int selected = Engine.INVALID_ENTITY;
    int best = 0;
    com.artemis.utils.IntBag units = getEnemyEntities().getEntities();
    java.util.ArrayList<Integer> ordered = bossCandidates(units);
    for (int id : ordered) {
      if (!isValidEnemyTargetWithoutAiScope(id)) continue;
      int score = bossTargetScore(id);
      if (score > best) { best = score; selected = id; }
    }
    selected = node9Fallback(selected, units);
    distance[0] = selected < 0 ? Float.MAX_VALUE : nativeAiDistance(
        mPosition.get(entityId).position, mPosition.get(selected).position);
    return selected;
  }

  private int node9Fallback(int primary, com.artemis.utils.IntBag units) {
    int candidate = Engine.INVALID_ENTITY;
    int best = 0;
    long order = 0;
    com.artemis.ComponentMapper<com.riiablo.engine.server.component.NativeTargetNode> nodes =
        Riiablo.engine.getMapper(com.riiablo.engine.server.component.NativeTargetNode.class);
    for (int i = units.size() - 1; i >= 0; i--) {
      int id = units.get(i);
      if (!sameLevel(id) || !isValidEnemyTargetWithoutAiScope(id)) continue;
      com.riiablo.engine.server.component.NativeTargetNode node = nodes.get(id);
      if (node == null || !node.isNode9() || node.order() == 0) continue;
      int score = bossTargetScore(id);
      if (score > best || score == best && node.order() > order) {
        best = score;
        order = node.order();
        candidate = id;
      }
    }
    if (candidate < 0 || primary >= 0 && isInNativeMeleeRange(primary)) return primary;
    if (candidate >= 0 && nativeAiDistance(mPosition.get(entityId).position,
        mPosition.get(candidate).position) < 5f) return candidate;
    return primary;
  }

  /** Target nodes visit player slots in order, then newest children first.
   * Strict score comparison preserves the first node on a tie. */
  private java.util.ArrayList<Integer> bossCandidates(com.artemis.utils.IntBag units) {
    java.util.ArrayList<Integer> result = new java.util.ArrayList<>();
    com.riiablo.engine.server.NativePlayerTargetSlots slots = Riiablo.engine.getSystem(
        com.riiablo.engine.server.NativePlayerTargetSlots.class);
    if (slots == null) {
      for (int i = 0; i < units.size(); i++) if (sameLevel(units.get(i))) result.add(units.get(i));
      return result;
    }
    com.artemis.ComponentMapper<com.riiablo.engine.server.component.NativeTargetNode> nodes =
        Riiablo.engine.getMapper(com.riiablo.engine.server.component.NativeTargetNode.class);
    for (int slot = 0; slot < com.riiablo.engine.server.NativePlayerTargetSlots.SLOT_COUNT; slot++) {
      int player = slots.playerAtSlot(slot);
      if (player < 0 || !mPosition.has(player) || !sameLevel(player)) continue;
      result.add(player);
      java.util.ArrayList<Integer> children = new java.util.ArrayList<>();
      for (int i = 0; i < units.size(); i++) {
        int id = units.get(i);
        com.riiablo.engine.server.component.NativeTargetNode node = nodes.get(id);
        if (id != player && node != null && node.ownerId() == player
            && slots.acceptsChildInsertion(player, node.order())) children.add(id);
      }
      children.sort((a, b) -> Long.compare(nodes.get(b).order(), nodes.get(a).order()));
      result.addAll(children);
    }
    return result;
  }

  /** AITHINK_GetTargetScore with the null command used by all three Ancients. */
  int bossTargetScore(int id) {
    if (mMonster.has(id) && (mMonster.get(id).monstats == null
        || mMonster.get(id).monstats.threat <= 1)) return 0;
    boolean melee = isInNativeMeleeRange(id);
    int resist = (stat(id, Stat.fireresist) + stat(id, Stat.lightresist)
        + (melee ? stat(id, Stat.coldresist) : 0)
        + 4 * (stat(id, Stat.damageresist) + 2 * stat(id, Stat.magicresist))) / 15;
    int damage = (stat(id, Stat.maxdamage) + stat(id, Stat.firemaxdam)
        + stat(id, Stat.lightmaxdam) + stat(id, Stat.magicmaxdam)
        + stat(id, Stat.coldmaxdam) + (stat(id, Stat.poisonmaxdam) >> 8)) / 2;
    int skills = (handRank(id, com.badlogic.gdx.Input.Buttons.LEFT)
        + handRank(id, com.badlogic.gdx.Input.Buttons.RIGHT)) / 4;
    UnitStates states = mUnitStates.get(id);
    int cold = states != null && states.stateList != null
        && states.stateList.hasState(StateId.COLD) ? 100 : 0;
    com.riiablo.engine.server.component.MapWrapper wrapper = mMapWrapper.get(entityId);
    boolean barrier = false;
    if (!melee && wrapper != null && wrapper.map != null && wrapper.zone != null) {
      com.badlogic.gdx.ai.utils.Ray<Vector2> ray =
          new com.badlogic.gdx.ai.utils.Ray<>(new Vector2(), new Vector2());
      ray.set(mPosition.get(entityId).position, mPosition.get(id).position);
      barrier = wrapper.map.castRay(wrapper.zone, ray,
          com.riiablo.map.DT1.Tile.FLAG_BLOCK_JUMP, 0,
          new com.badlogic.gdx.ai.utils.Collision<>(new Vector2(), new Vector2()), false);
    }
    int range = melee ? 100 : barrier ? 0 : 75;
    int score = (resist + 5 * range + 2 * (damage + skills + 2 * cold)
        + (nativeLifePercent(id) < 20 ? 300 : 0)) / 22;
    return score == 0 ? 1 : score;
  }

  private int stat(int id, short stat) {
    com.riiablo.attributes.Attributes attrs = mAttributesWrapper.has(id)
        ? mAttributesWrapper.get(id).attrs : null;
    return attrs == null ? 0 : attrs.getValue(stat, 0);
  }

  private int handRank(int id, int button) {
    com.riiablo.save.CharData data = mPlayer.has(id) ? mPlayer.get(id).data : null;
    if (data == null) return 0;
    int skillId = data.getAction(button);
    int level = data.getSkill(skillId);
    if (level <= 0) return 0;
    UnitStates states = mUnitStates.get(id);
    if (states != null && states.stateList != null) level += states.stateList.getTotalSkillModifier();
    com.riiablo.codec.excel.NativeSkills.Entry row = Riiablo.files.NativeSkills.get(skillId);
    return row == null ? 0 : Math.max(0, level) * (row.nativeInteger("attackrank") & 255);
  }

  /** Native AITHINK_AreUnitsInSameLevel cull; ordinary acquisition must not
   * select a unit from another level even when its coordinates are nearby. */
  @Override protected boolean acceptOrdinaryTargetCandidate(int targetId) {
    return sameLevel(targetId);
  }

  private boolean sameLevel(int targetId) {
    com.riiablo.engine.server.component.MapWrapper source = mMapWrapper.get(entityId);
    com.riiablo.engine.server.component.MapWrapper target = mMapWrapper.get(targetId);
    // Legacy arena fixtures have no map wrappers. Production candidates must
    // carry both a world and a level; do not compare only their coordinates.
    if (source == null || source.zone == null) return target == null || target.zone == null;
    return target != null && target.map == source.map && target.zone != null
        && source.zone.level != null && target.zone.level != null
        && source.zone.level.Id == target.zone.level.Id;
  }

  @Override protected boolean beforeDecision() {
    Act5QuestSystem quests = Riiablo.engine.getSystem(Act5QuestSystem.class);
    if (quests != null && !quests.isAncientCombatActive(entityId)) { idle(25); return false; }
    return true;
  }

  private boolean hasNamedState(int id, String name) {
    if (name == null || name.isEmpty()) return false;
    States.Entry state = Riiablo.files.States.get(name);
    UnitStates states = mUnitStates.get(id);
    return state != null && state.id > 0 && states != null && states.stateList != null
        && states.stateList.hasState(state.id);
  }

  private void meleeOrApproach(int targetId, boolean combat) {
    if (!combat) { approach(targetId, 0); delayDecision(10); return; }
    if (rollAiChance(params[2])) {
      attack(targetId, (rollAiRandomNumber() & 1L) != 0
          ? Engine.Monster.MODE_A1 : Engine.Monster.MODE_A2);
    } else idle(25);
  }

  @Override protected void thinkWithoutTarget() { idle(25); }
  @Override protected void think(int targetId, float distance, boolean combat) {
    String id = monster.monstats.Id;
    if ("ancientbarb1".equals(id)) {
      UnitStates states = mUnitStates.get(entityId);
      if (states != null && states.stateList != null
          && states.stateList.hasState(StateId.UNINTERRUPTABLE)) { idle(25); return; }
      if (hasSkill(0) && distance < params[0] && rollAiChance(params[1])) {
        Vector2 source = mPosition.get(entityId).position;
        Vector2 target = mPosition.get(targetId).position;
        int scale = Math.max(1, com.riiablo.engine.server.combat.NativeMeleeDistance.between(
            (int) source.x, (int) source.y, 1, (int) target.x, (int) target.y, 1));
        castAt(0, Engine.INVALID_ENTITY, new Vector2(
            (int) target.x + ((int) target.x - (int) source.x) * params[3] / scale,
            (int) target.y + ((int) target.y - (int) source.y) * params[3] / scale));
      } else meleeOrApproach(targetId, combat);
    } else if ("ancientbarb2".equals(id)) {
      if (combat && rollAiChance(params[3]) && escape(targetId, params[4], 0)) {
        delayDecision(25); return;
      }
      Skills.Entry shout = hasSkill(0) ? Riiablo.files.skills.get(monster.monstats.Skill1) : null;
      if (shout != null && !hasNamedState(entityId, shout.aurastate)
          && !hasNamedState(targetId, shout.auratargetstate) && rollAiChance(params[2])
          && castAt(0, Engine.INVALID_ENTITY, new Vector2())) return;
      if (distance < params[0] && rollAiChance(params[1])) { attack(targetId); return; }
      if (!pathfinder.findNativePath(entityId, targetId, (int) distance - params[0], 0)) idle(10);
    } else if ("ancientbarb3".equals(id)) {
      if (hasSkill(0) && distance < params[0] && rollAiChance(params[1])) {
        cast(0, targetId); return;
      }
      meleeOrApproach(targetId, combat);
    }
  }
}
