package com.riiablo.engine.server;

import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.attributes.StatRef;
import com.riiablo.codec.excel.Missiles;
import com.riiablo.codec.excel.Skills;
import com.riiablo.engine.EntityFactory;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.component.Position;
import com.riiablo.engine.server.event.SkillCastEvent;
import com.riiablo.engine.server.event.SkillDoEvent;
import com.riiablo.item.BodyLoc;
import com.riiablo.item.Item;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.skill.SkillCodes;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.badlogic.gdx.math.Vector2;

/**
 * Server-authoritative part of the player skill pipeline.
 *
 * <p>Actioneer owns animation state and emits skill events. This system keeps
 * validation and projectile creation on the server, while the client remains
 * responsible for presentation. Effects are created on {@link SkillDoEvent}
 * so a projectile cannot be spawned before the cast animation reaches its
 * active frame.</p>
 */
public class ServerSkillSystem extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(ServerSkillSystem.class);

  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Position> mPosition;

  /** Registered as "factory" by D2GS. */
  @Wire(name = "factory")
  protected EntityFactory factory;

  @Subscribe
  public void onSkillCast(SkillCastEvent event) {
    // Monsters use their existing AI/casting path and do not have player mana.
    if (!mPlayer.has(event.entityId)) return;

    Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) {
      reject(event, 7, "skill data is missing");
      return;
    }

    Player player = mPlayer.get(event.entityId);
    int skillLevel = player.data != null ? player.data.getSkill(event.skillId) : 1;
    skillLevel = Math.max(1, skillLevel);
    int casterLevel = 1;
    Attributes attrs = mAttributesWrapper.has(event.entityId)
        ? mAttributesWrapper.get(event.entityId).attrs : null;
    if (attrs == null) {
      reject(event, 7, "caster attributes are missing");
      return;
    }
    StatRef level = attrs.get(Stat.level, StatRef.obtain());
    if (level != null) casterLevel = Math.max(1, level.asInt());
    if (casterLevel < skill.reqlevel) {
      reject(event, 5, "caster level is below skill requirement");
      return;
    }

    StatRef mana = attrs.get(Stat.mana, StatRef.obtain());
    if (mana == null) {
      reject(event, 1, "caster has no mana stat");
      return;
    }

    float manaCost = getManaCost(skill, skillLevel);
    event.manaCost = manaCost;
    if (manaCost > 0 && mana.asFixed() + 0.0001f < manaCost) {
      reject(event, 1, "not enough mana");
      return;
    }
    if (manaCost > 0) {
      mana.sub(manaCost);
      log.debug("Server skill accepted: entity={}, skill={}, level={}, manaCost={}, manaLeft={}",
          event.entityId, event.skillId, skillLevel, manaCost, mana.asFixed());
    }
  }

  @Subscribe
  public void onSkillDo(SkillDoEvent event) {
    if (!mPlayer.has(event.entityId) || !mPosition.has(event.entityId)) return;
    Skills.Entry skill = Riiablo.files.skills.get(event.skillId);
    if (skill == null) return;

    String missileName = skill.cltmissilea;
    if (missileName == null || missileName.isEmpty()) {
      missileName = resolveThrowableMissile(event.entityId, event.skillId, skill);
    }
    if (missileName == null || missileName.isEmpty()) return;

    Missiles.Entry missile = Riiablo.files.Missiles.get(missileName);
    if (missile == null) {
      log.warn("Server skill missile lookup failed: entity={}, skill={}, missile={}",
          event.entityId, event.skillId, missileName);
      return;
    }

    Vector2 start = mPosition.get(event.entityId).position;
    Vector2 target = new Vector2();
    if (event.targetId >= 0 && mPosition.has(event.targetId)) {
      target.set(mPosition.get(event.targetId).position);
    } else if (event.targetVec != null) {
      target.set(event.targetVec);
    } else {
      target.set(start).add(1, 0);
    }
    target.sub(start);
    if (target.isZero(0.0001f)) target.set(1, 0);
    target.nor();

    int missileId = factory != null
        ? factory.createMissile(missile, target, start, event.entityId)
        : -1;
    log.debug("Server skill projectile: entity={}, skill={}, missile={}, entityId={}, dir=({}, {})",
        event.entityId, event.skillId, missileName, missileId, target.x, target.y);
  }

  private float getManaCost(Skills.Entry skill, int level) {
    int shift = Math.max(0, Math.min(30, skill.manashift));
    float base = (1 << shift) / 256f * skill.mana;
    return Math.max(0, base + skill.lvlmana * level);
  }

  private String resolveThrowableMissile(int entityId, int skillId, Skills.Entry skill) {
    if (skillId != SkillCodes.throw_ && skillId != SkillCodes.left_hand_throw
        && skill.srvdofunc != 3 && skill.srvdofunc != 5) {
      return null;
    }
    Player player = mPlayer.get(entityId);
    if (player.data == null || player.data.getItems() == null) return null;
    Item weapon = player.data.getItems().getEquipped(BodyLoc.RARM);
    if (weapon == null) weapon = player.data.getItems().getEquipped(BodyLoc.LARM);
    return weapon != null ? weapon.code : null;
  }

  private void reject(SkillCastEvent event, int resultCode, String reason) {
    event.accepted = false;
    event.resultCode = resultCode;
    log.debug("Server skill rejected: entity={}, skill={}, resultCode={}, reason={}",
        event.entityId, event.skillId, resultCode, reason);
  }
}
