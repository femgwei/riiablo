package com.riiablo.engine.client;

import com.artemis.ComponentMapper;
import net.mostlyoriginal.api.event.common.Subscribe;
import net.mostlyoriginal.api.system.core.PassiveSystem;

import com.riiablo.Riiablo;
import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.engine.server.component.AIWrapper;
import com.riiablo.engine.server.component.AttributesWrapper;
import com.riiablo.engine.server.component.Class;
import com.riiablo.engine.server.component.Monster;
import com.riiablo.engine.server.component.Player;
import com.riiablo.engine.server.event.DamageEvent;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

public class DamageHandler extends PassiveSystem {
  private static final Logger log = LogManager.getLogger(DamageHandler.class);

  protected ComponentMapper<AIWrapper> mAIWrapper;
  protected ComponentMapper<Player> mPlayer;
  protected ComponentMapper<Monster> mMonster;
  protected ComponentMapper<AttributesWrapper> mAttributesWrapper;
  protected ComponentMapper<Class> mClass;

  @Subscribe
  public void onDamageEvent(DamageEvent event) {
    log.info("[DAMAGE_SOUND] attacker={} victim={} damage={} hitSound={} fallback={}",
        event.attacker, event.victim, event.damage,
        event.hitSound == null ? "" : event.hitSound,
        event.hitSound == null || event.hitSound.isEmpty());
    
    // 检查是否是玩家攻击怪物
    boolean isPlayerAttacking = mPlayer.has(event.attacker);
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
      // 注意：DamageEvent在伤害应用之前发送，所以这里读取的是伤害前的血量
      log.info("Player attack: damage={}, monster={}, monsterHp={}/{}, monsterId={}, statId={}", 
          event.damage, monsterName, monsterHp, monsterMaxHp, event.victim,
          mAttributesWrapper.has(event.victim) && mAttributesWrapper.get(event.victim).attrs != null 
              && mAttributesWrapper.get(event.victim).attrs.contains(Stat.hitpoints) 
              ? mAttributesWrapper.get(event.victim).attrs.get(Stat.hitpoints).id() : -1);
    }

    // 怪物攻击玩家：打印怪物伤害日志，便于排查秒杀等问题
    boolean isMonsterAttacking = mMonster.has(event.attacker);
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
    
    // Trigger hit reaction for entities with AI (monsters, NPCs, etc.)
    // Players don't have AI components, so we need to check first
    if (mAIWrapper.has(event.victim)) {
      AIWrapper aiWrapper = mAIWrapper.get(event.victim);
      if (aiWrapper != null && aiWrapper.ai != null) {
        aiWrapper.ai.hit();
      }
    }
    
    String sound = event.hitSound;
    if (sound == null || sound.isEmpty()) sound = "impact_blunt_1";
    if (Riiablo.audio != null) Riiablo.audio.play(sound, true);
  }
}
