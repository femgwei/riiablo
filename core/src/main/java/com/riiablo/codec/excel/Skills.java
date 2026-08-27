package com.riiablo.codec.excel;

import com.badlogic.gdx.utils.ObjectIntMap;
import com.riiablo.CharacterClass;

@Excel.Binned
public class Skills extends Excel<Skills.Entry> {
  @Override
  protected void init() {
    // Skills.txt is keyed by numeric Id, but MonStats references skills by
    // their textual name. Build the secondary name index required by monster
    // AI and other data-driven consumers.
    STRING_TO_ID = new ObjectIntMap<>();
    for (Entry entry : this) {
      if (entry.skill != null && !entry.skill.isEmpty()) {
        STRING_TO_ID.put(entry.skill, entry.Id);
      }
    }
  }

  public static int getClassId(String charClass) {
    if (charClass.isEmpty()) return -1;
    switch (charClass.charAt(0)) {
      case 'a': return charClass.charAt(1) == 'm' ? CharacterClass.AMAZON.id : CharacterClass.ASSASSIN.id;
      case 'b': return CharacterClass.BARBARIAN.id;
      case 'd': return CharacterClass.DRUID.id;
      case 'n': return CharacterClass.NECROMANCER.id;
      case 'p': return CharacterClass.PALADIN.id;
      case 's': return CharacterClass.SORCERESS.id;
      default:  return -1;
    }
  }

  public static CharacterClass getClass(String charClass) {
    int classId = getClassId(charClass);
    return classId != -1 ? CharacterClass.get(classId) : null;
  }

  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return skill;
    }

    @Key
    @Column
    public int     Id;
    @Column public String  skill;
    @Column public String  charclass;
    @Column public String  skilldesc;
    @Column public String  stsound;
    @Column public String  stsoundclass;
    @Column public String  dosound;
    @Column public String  castoverlay;
    @Column public String  aurastate;
    @Column public String  auralencalc;
    @Column public String  anim;
    @Column public String  seqtrans;
    @Column public String  monanim;
    @Column public int     seqnum;
    @Column public int     seqinput;
    @Column public int     reqlevel;
    @Column public int     maxlvl;
    @Column public String  reqskill1;
    @Column public String  reqskill2;
    @Column public String  reqskill3;
    @Column public int     startmana;
    @Column public int     minmana;
    @Column public int     manashift;
    @Column public int     mana;
    @Column public int     lvlmana;
    @Column(startIndex = 1, endIndex = 9)
    public int     Param[];
    @Column public boolean leftskill;
    @Column public boolean passive;
    @Column public boolean aura;
    @Column public int     srvstfunc;
    @Column public int     srvdofunc;
    /** D2 skill formula expressions (calc1..calc4). */
    @Column public String  calc1;
    @Column public String  calc2;
    @Column public String  calc3;
    @Column public String  calc4;
    @Column public String  srvmissilea;
    @Column public String  srvmissileb;
    @Column public String  srvmissilec;
    @Column public String  srvmissiled;
    @Column public int     cltstfunc;
    @Column public int     cltdofunc;
    @Column public String  cltmissilea;
    @Column public String  cltmissileb;
    @Column public String  cltmissilec;
    @Column public String  cltmissiled;
  }
}
