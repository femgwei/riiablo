package com.riiablo.engine.server.skill;

import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;

import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;

/**
 * 技能冷却管理器 - 基于 D2MOD 移植
 * 
 * <p>管理技能冷却时间：
 * <ul>
 *   <li>技能冷却开始和结束</li>
 *   <li>冷却时间查询</li>
 *   <li>冷却时间减少效果</li>
 *   <li>共享冷却组</li>
 * </ul>
 * 
 * <p>参考：D2MOD/source/D2Game/src/SKILLS/Skills.cpp
 * 
 * @author riiablo team
 */
public class SkillCooldownManager {
  private static final Logger log = LogManager.getLogger(SkillCooldownManager.class);

  //==========================================================================
  // 常量
  //==========================================================================

  /** 游戏帧率（每秒帧数） */
  public static final int FRAMES_PER_SECOND = 25;

  /** 无冷却 */
  public static final int NO_COOLDOWN = 0;

  //==========================================================================
  // 实例字段
  //==========================================================================

  /** 每个实体的技能冷却数据 */
  private final IntMap<EntityCooldownData> entityCooldowns = new IntMap<>();

  //==========================================================================
  // 内部类
  //==========================================================================

  /**
   * 实体冷却数据
   */
  private static class EntityCooldownData {
    /** 技能ID -> 冷却结束时间（帧） */
    final IntIntMap skillCooldowns = new IntIntMap();
    /** 冷却组ID -> 冷却结束时间（帧） */
    final IntIntMap groupCooldowns = new IntIntMap();
  }

  //==========================================================================
  // 核心方法
  //==========================================================================

  /**
   * 开始技能冷却
   * 
   * @param entityId 实体ID
   * @param skillId 技能ID
   * @param currentFrame 当前游戏帧
   */
  public void startCooldown(int entityId, int skillId, long currentFrame) {
    int cooldownFrames = getSkillCooldown(skillId);
    if (cooldownFrames <= 0) {
      return;
    }

    EntityCooldownData data = getOrCreateCooldownData(entityId);
    int endFrame = (int) (currentFrame + cooldownFrames);
    data.skillCooldowns.put(skillId, endFrame);

    // 检查是否有共享冷却组
    int groupId = getSkillCooldownGroup(skillId);
    if (groupId > 0) {
      data.groupCooldowns.put(groupId, endFrame);
    }

    log.debug("Started cooldown for skill {}: {} frames (ends at frame {})", 
        skillId, cooldownFrames, endFrame);
  }

  /**
   * 检查技能是否在冷却中
   * 
   * @param entityId 实体ID
   * @param skillId 技能ID
   * @param currentFrame 当前游戏帧
   * @return true 如果技能在冷却中
   */
  public boolean isOnCooldown(int entityId, int skillId, long currentFrame) {
    EntityCooldownData data = entityCooldowns.get(entityId);
    if (data == null) {
      return false;
    }

    // 检查技能自身冷却
    int skillEndFrame = data.skillCooldowns.get(skillId, 0);
    if (skillEndFrame > currentFrame) {
      return true;
    }

    // 检查共享冷却组
    int groupId = getSkillCooldownGroup(skillId);
    if (groupId > 0) {
      int groupEndFrame = data.groupCooldowns.get(groupId, 0);
      if (groupEndFrame > currentFrame) {
        return true;
      }
    }

    return false;
  }

  /**
   * 获取剩余冷却时间（帧）
   * 
   * @param entityId 实体ID
   * @param skillId 技能ID
   * @param currentFrame 当前游戏帧
   * @return 剩余冷却帧数，0表示无冷却
   */
  public int getRemainingCooldown(int entityId, int skillId, long currentFrame) {
    EntityCooldownData data = entityCooldowns.get(entityId);
    if (data == null) {
      return 0;
    }

    int remaining = 0;

    // 检查技能自身冷却
    int skillEndFrame = data.skillCooldowns.get(skillId, 0);
    if (skillEndFrame > currentFrame) {
      remaining = (int) (skillEndFrame - currentFrame);
    }

    // 检查共享冷却组
    int groupId = getSkillCooldownGroup(skillId);
    if (groupId > 0) {
      int groupEndFrame = data.groupCooldowns.get(groupId, 0);
      if (groupEndFrame > currentFrame) {
        remaining = Math.max(remaining, (int) (groupEndFrame - currentFrame));
      }
    }

    return remaining;
  }

  /**
   * 获取冷却进度百分比
   * 
   * @param entityId 实体ID
   * @param skillId 技能ID
   * @param currentFrame 当前游戏帧
   * @return 冷却进度（0.0 = 刚开始冷却，1.0 = 冷却完成）
   */
  public float getCooldownProgress(int entityId, int skillId, long currentFrame) {
    int total = getSkillCooldown(skillId);
    if (total <= 0) {
      return 1.0f;
    }

    int remaining = getRemainingCooldown(entityId, skillId, currentFrame);
    if (remaining <= 0) {
      return 1.0f;
    }

    return 1.0f - (float) remaining / total;
  }

  /**
   * 清除技能冷却
   * 
   * @param entityId 实体ID
   * @param skillId 技能ID
   */
  public void clearCooldown(int entityId, int skillId) {
    EntityCooldownData data = entityCooldowns.get(entityId);
    if (data != null) {
      data.skillCooldowns.remove(skillId, 0);
    }
  }

  /**
   * 清除实体的所有冷却
   * 
   * @param entityId 实体ID
   */
  public void clearAllCooldowns(int entityId) {
    entityCooldowns.remove(entityId);
  }

  /**
   * 减少所有冷却时间
   * 
   * @param entityId 实体ID
   * @param reduceFrames 减少的帧数
   */
  public void reduceAllCooldowns(int entityId, int reduceFrames) {
    EntityCooldownData data = entityCooldowns.get(entityId);
    if (data == null || reduceFrames <= 0) {
      return;
    }

    // 减少技能冷却
    for (IntIntMap.Entry entry : data.skillCooldowns.entries()) {
      int newEndFrame = Math.max(0, entry.value - reduceFrames);
      data.skillCooldowns.put(entry.key, newEndFrame);
    }

    // 减少组冷却
    for (IntIntMap.Entry entry : data.groupCooldowns.entries()) {
      int newEndFrame = Math.max(0, entry.value - reduceFrames);
      data.groupCooldowns.put(entry.key, newEndFrame);
    }
  }

  //==========================================================================
  // 技能冷却配置
  //==========================================================================

  /**
   * 获取技能基础冷却时间（帧）
   * 
   * <p>参考 D2MOD Skills.txt 数据
   * 
   * @param skillId 技能ID
   * @return 冷却帧数
   */
  public int getSkillCooldown(int skillId) {
    // TODO: 从 Skills.txt 读取
    // 这里提供一些示例冷却时间
    
    switch (skillId) {
      // 传送有短冷却
      case SkillId.TELEPORT:
        return 10; // 约0.4秒
      
      // 骨甲有冷却
      case SkillId.BONE_ARMOR:
        return 25; // 1秒
      
      // 陨石有较长冷却
      case SkillId.METEOR:
        return 30; // 1.2秒
      
      // 暴风雪有冷却
      case SkillId.BLIZZARD:
        return 45; // 1.8秒
      
      // 冰封球有短冷却
      case SkillId.FROZEN_ORB:
        return 25; // 1秒
      
      // 召唤火焰石魔有冷却
      case SkillId.FIRE_GOLEM:
        return 50; // 2秒
      
      // 复活有冷却
      case SkillId.REVIVE:
        return 25; // 1秒
      
      // 变身技能有冷却
      case SkillId.WEREWOLF:
      case SkillId.WEREBEAR:
        return 25; // 1秒
      
      // 天堂之拳有冷却
      case SkillId.FIST_OF_THE_HEAVENS:
        return 25; // 1秒
      
      // 闪避类被动无冷却
      case SkillId.DODGE:
      case SkillId.AVOID:
      case SkillId.EVADE:
        return 25; // 内部冷却
      
      default:
        return NO_COOLDOWN;
    }
  }

  /**
   * 获取技能所属的共享冷却组
   * 
   * @param skillId 技能ID
   * @return 冷却组ID，0表示无共享组
   */
  private int getSkillCooldownGroup(int skillId) {
    // 某些技能共享冷却
    switch (skillId) {
      // 变身技能共享冷却
      case SkillId.WEREWOLF:
      case SkillId.WEREBEAR:
        return 1;
      
      // 闪避类技能共享冷却
      case SkillId.DODGE:
      case SkillId.AVOID:
      case SkillId.EVADE:
        return 2;
      
      default:
        return 0;
    }
  }

  /**
   * 将帧数转换为秒
   */
  public float framesToSeconds(int frames) {
    return (float) frames / FRAMES_PER_SECOND;
  }

  /**
   * 将秒转换为帧数
   */
  public int secondsToFrames(float seconds) {
    return (int) (seconds * FRAMES_PER_SECOND);
  }

  //==========================================================================
  // 辅助方法
  //==========================================================================

  /**
   * 获取或创建实体冷却数据
   */
  private EntityCooldownData getOrCreateCooldownData(int entityId) {
    EntityCooldownData data = entityCooldowns.get(entityId);
    if (data == null) {
      data = new EntityCooldownData();
      entityCooldowns.put(entityId, data);
    }
    return data;
  }

  /**
   * 清理过期的冷却数据（可选的内存优化）
   * 
   * @param currentFrame 当前帧
   */
  public void cleanup(long currentFrame) {
    for (IntMap.Entry<EntityCooldownData> entry : entityCooldowns.entries()) {
      EntityCooldownData data = entry.value;
      
      // 移除过期的技能冷却
      IntIntMap.Keys keys = data.skillCooldowns.keys();
      while (keys.hasNext) {
        int skillId = keys.next();
        if (data.skillCooldowns.get(skillId, 0) <= currentFrame) {
          data.skillCooldowns.remove(skillId, 0);
        }
      }
      
      // 移除过期的组冷却
      keys = data.groupCooldowns.keys();
      while (keys.hasNext) {
        int groupId = keys.next();
        if (data.groupCooldowns.get(groupId, 0) <= currentFrame) {
          data.groupCooldowns.remove(groupId, 0);
        }
      }
    }
  }
}
