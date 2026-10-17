package com.riiablo.codec.excel;

/**
 * Monster sound banks from {@code MonSounds.txt}.
 *
 * <p>The sound bank is separate from {@link MonStats}: MonStats.MonSound is
 * only the bank id.  Keeping this table available lets the client reproduce
 * the native neutral, walking and encounter sound timing instead of guessing
 * sound names from the monster id.</p>
 */
@Excel.Binned
public class MonSounds extends Excel<MonSounds.Entry> {
  @Override
  protected void put(int id, Entry value) {
    super.put(id, value);
  }

  public static class Entry extends Excel.Entry {
    @Key @Column public String Id;

    @Column public String Attack1;
    @Column public String Weapon1;
    @Column public int Att1Del;
    @Column public int Wea1Del;
    @Column public int Att1Prb;
    @Column public int Wea1Vol;
    @Column public String Attack2;
    @Column public String Weapon2;
    @Column public int Att2Del;
    @Column public int Wea2Del;
    @Column public int Att2Prb;
    @Column public int Wea2Vol;

    @Column public String HitSound;
    @Column public String DeathSound;
    @Column public int HitDelay;
    @Column public int DeaDelay;

    @Column public String Skill1;
    @Column public String Skill2;
    @Column public String Skill3;
    @Column public String Skill4;

    @Column public String Footstep;
    @Column public String FootstepLayer;
    @Column public int FsCnt;
    @Column public int FsOff;
    @Column public int FsPrb;

    @Column public String Neutral;
    @Column public int NeuTime;
    @Column public String Init;
    @Column public String Taunt;
    @Column public String Flee;

    @Column public String CvtMo1;
    @Column public String CvtSk1;
    @Column public String CvtTgt1;
    @Column public String CvtMo2;
    @Column public String CvtSk2;
    @Column public String CvtTgt2;
    @Column public String CvtMo3;
    @Column public String CvtSk3;
    @Column public String CvtTgt3;

    @Override
    public String toString() {
      return Id;
    }
  }
}
