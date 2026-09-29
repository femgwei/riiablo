package com.riiablo.skill;

/** Deterministic loadout used by the laboratory before real item entities are attached. */
final class SkillCharacterPreset {
  final byte classId;
  final String name;
  final String weapon;
  final boolean weaponRequired;

  SkillCharacterPreset(byte classId, String name, String weapon, boolean weaponRequired) {
    this.classId = classId;
    this.name = name;
    this.weapon = weapon;
    this.weaponRequired = weaponRequired;
  }
}
