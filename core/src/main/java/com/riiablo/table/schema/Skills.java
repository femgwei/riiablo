package com.riiablo.table.schema;

import com.riiablo.CharacterClass;
import com.riiablo.table.annotation.Format;
import com.riiablo.table.annotation.PrimaryKey;
import com.riiablo.table.annotation.Schema;

@Schema
@SuppressWarnings("unused")
public class Skills {
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

  @Override
  public String toString() {
    return skill;
  }

  @PrimaryKey
  public int Id;
  public String skill;
  public String charclass;
  public String skilldesc;
  public String stsound;
  public String stsoundclass;
  public String dosound;
  public String castoverlay;
  public String aurastate;
  public String auratargetstate;
  public String auralencalc;
  public String aurarangecalc;
  public int aurafilter;

  @Format(
      startIndex = 1,
      endIndex = 7)
  public String aurastat[];

  @Format(
      startIndex = 1,
      endIndex = 7)
  public String aurastatcalc[];
  public String anim;
  public String seqtrans;
  public String monanim;
  public int seqnum;
  public int seqinput;
  public int reqlevel;
  public int maxlvl;
  public String reqskill1;
  public String reqskill2;
  public String reqskill3;
  public int startmana;
  public int minmana;
  public int manashift;
  public int mana;
  public int lvlmana;

  @Format(
      startIndex = 1,
      endIndex = 9)
  public int Param[];

  public boolean leftskill;
  public boolean passive;
  public String passivestate;
  public String passiveitype;

  @Format(startIndex = 1, endIndex = 6)
  public String passivestat[];

  @Format(startIndex = 1, endIndex = 6)
  public String passivecalc[];
  public String passiveevent;
  public int passiveeventfunc;
  public boolean aura;
  public boolean periodic;
  public String perdelay;
  public int srvstfunc;
  public int srvdofunc;
  public int HitShift;
  public int SrcDam;
  public int ToHit;
  public int LevToHit;
  public int ResultFlags;
  public int HitFlags;
  public int HitClass;
  public int MinDam;
  public int MaxDam;

  @Format(startIndex = 1, endIndex = 6)
  public int MinLevDam[];

  @Format(startIndex = 1, endIndex = 6)
  public int MaxLevDam[];
  public String EType;
  public int EMin;
  public int EMax;

  @Format(startIndex = 1, endIndex = 6)
  public int EMinLev[];

  @Format(startIndex = 1, endIndex = 6)
  public int EMaxLev[];
  public int ELen;

  @Format(startIndex = 1, endIndex = 4)
  public int ELevLen[];
  public String calc1;
  public String calc2;
  public String calc3;
  public String calc4;
  public String summon;
  public String pettype;
  public String petmax;
  public String srvmissile;
  public String srvmissilea;
  public String srvmissileb;
  public String srvmissilec;
  public String srvmissiled;
  public int cltstfunc;
  public int cltdofunc;
  public String cltmissile;
  public String cltmissilea;
  public String cltmissileb;
  public String cltmissilec;
  public String cltmissiled;
}
