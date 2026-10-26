package com.riiablo.presentation;


import com.artemis.ComponentMapper;
import com.artemis.annotations.Wire;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.engine.server.event.DamageAppliedEvent;
import com.riiablo.engine.client.event.ClientPlayerHitEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

public class DamageHandler extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(DamageHandler.class);

  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Class> mClass;
  @Wire(name = "clientContext")
  protected ClientGameplayContext clientContext ;

  @Subscribe
  public void onDamageEvent(DamageAppliedEvent event) {
    log.info("[DAMAGE_SOUND] attacker={} victim={} damage={} hitSound={} fallback={}",
        event.attacker, event.victim, event.damage,
        event.hitSound == null ? "" : event.hitSound,
        event.hitSound == null || event.hitSound.isEmpty());
    
    // 检查是否是玩家攻击怪物
    boolean isPlayerAttacking = event.attacker >= 0 && mPlayer.has(event.attacker);
    boolean isMonsterVictim = mMonster.has(event.victim);
    
    if (isPlayerAttacking && isMonsterVictim) {
      // 获取怪物血量
      float monsterHp = 0;
      float monsterMaxHp = 0;
      if (mAttributesWrapper.has(event.victim)) {
        AttributesWrapper attrsWrapper = mAttributesWrapper.get(event.victim);
        if (attrsWrapper != null && attrsWrapper.attrs != null) {
          Attributes attrs = attrsWrapper.attrs;
          if (attrs.contains(Stat.hitpoints)) {
            monsterHp = attrs.get(Stat.hitpoints).asFixed();
          }
          if (attrs.contains(Stat.maxhp)) {
            monsterMaxHp = attrs.get(Stat.maxhp).asFixed();
          }
        }
      }
      
      // 获取怪物名称
      String monsterName = "Unknown";
      if (mClass.has(event.victim)) {
        Class.Type type = mClass.get(event.victim).type;
        if (type != null) {
          monsterName = type.name();
        }
      }
      
      // 记录日志：玩家攻击伤害和怪物血量
      // Presentation observes committed life after all defensive callbacks.
      log.info("Player attack: damage={}, monster={}, monsterHp={}/{}, monsterId={}, statId={}", 
          event.damage, monsterName, monsterHp, monsterMaxHp, event.victim,
          mAttributesWrapper.has(event.victim) && mAttributesWrapper.get(event.victim).attrs != null 
              && mAttributesWrapper.get(event.victim).attrs.contains(Stat.hitpoints) 
              ? mAttributesWrapper.get(event.victim).attrs.get(Stat.hitpoints).id() : -1);
    }

    // 怪物攻击玩家：打印怪物伤害日志，便于排查秒杀等问题
    boolean isMonsterAttacking = event.attacker >= 0 && mMonster.has(event.attacker);
    boolean isPlayerVictim = mPlayer.has(event.victim);
    if (isMonsterAttacking && isPlayerVictim) {
      float playerHp = 0;
      float playerMaxHp = 0;
      if (mAttributesWrapper.has(event.victim)) {
        AttributesWrapper aw = mAttributesWrapper.get(event.victim);
        if (aw != null && aw.attrs != null) {
          if (aw.attrs.contains(Stat.hitpoints)) playerHp = aw.attrs.get(Stat.hitpoints).asFixed();
          if (aw.attrs.contains(Stat.maxhp)) playerMaxHp = aw.attrs.get(Stat.maxhp).asFixed();
        }
      }
      String monsterName = "Unknown";
      if (mClass.has(event.attacker)) {
        Class.Type t = mClass.get(event.attacker).type;
        if (t != null) monsterName = t.name();
      }
      log.info("Monster attack: damage={}, monster={}, playerHp={}/{}, attackerId={}, victimId={}",
          event.damage, monsterName, playerHp, playerMaxHp, event.attacker, event.victim);
    }

    // Character pain sounds are not part of DamageEvent.hitSound (that field is
    // the attacker's weapon/missile impact sound).  Play the victim's class
    // voice when the event carries actual damage.  In particular, this covers
    // local player damage events, whose entities do not have an AIWrapper.
    if (event.lifeLost > 0f && isPlayerVictim
        && event.kind != DamageEvent.DAMAGE_OVER_TIME) {
      PlayerHitSound.play(clientContext, mPlayer.get(event.victim));
    }
    
    // Presentation never invokes AI.hit(), which also invalidates authoritative paths.
    if (event.lifeLost > 0f && event.kind != DamageEvent.DAMAGE_OVER_TIME
        && !event.suppressMonsterHitSound && mMonster.has(event.victim)) {
      Monster monster = mMonster.get(event.victim);
      if (monster.monstats != null && monster.monstats.MonSound != null)
        SpatialAudio.play(world, clientContext, event.victim, monster.monstats.MonSound + "_hit_1", true);
    }
    
    // MissileImpactPresentationSystem owns Missiles.txt HitSound playback.
    // DamageEvent may also represent melee, DOT, or a server-side effect;
    // retain the legacy generic impact for those paths only.  An empty
    // missile HitSound is native silence, not a request for a blunt sample.
    if (event.damage > 0f && event.kind != DamageEvent.MISSILE
        && event.kind != DamageEvent.DAMAGE_OVER_TIME) {
      String sound = event.hitSound;
      if (sound == null || sound.isEmpty()) sound = "impact_blunt_1";
      if (clientContext.audio() != null) clientContext.audio().play(sound, true);
    }
  }

  /** Plays local pain feedback only after the network vitals snapshot is committed. */
  @Subscribe
  public void onNetworkPlayerHit(ClientPlayerHitEvent event) {
    if (event == null || !mPlayer.has(event.entityId)) return;
    PlayerHitSound.play(clientContext, mPlayer.get(event.entityId));
  }
}

