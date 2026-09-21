package com.riiablo.engine.client;

import com.riiablo.CharacterClass;
import com.riiablo.Riiablo;
import com.riiablo.engine.server.component.Player;

/** Plays the character-specific pain sound used when a player loses hit points. */
final class PlayerHitSound {
  private PlayerHitSound() {}

  static String soundFor(CharacterClass characterClass) {
    if (characterClass == null) return null;
    switch (characterClass) {
      case AMAZON:      return "amazon_hit_1";
      case SORCERESS:   return "sorceress_hit_1";
      case NECROMANCER: return "necromancer_hit_1";
      case PALADIN:     return "paladin_hit_1";
      case BARBARIAN:   return "barbarian_hit_1";
      case DRUID:       return "druid_hit_1";
      case ASSASSIN:    return "assassin_hit_1";
      default:          return null;
    }
  }

  /**
   * Plays one randomly selected member of the class's five-sound hit group.
   * Returns false when the player is not fully initialized or audio is unavailable.
   */
  static boolean play(Player player) {
    if (player == null || player.data == null || Riiablo.audio == null) return false;
    String sound = soundFor(player.data.classId);
    if (sound == null) return false;
    Riiablo.audio.play(sound, true);
    return true;
  }
}
