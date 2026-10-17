package com.riiablo.codec.excel;

/** Audio environment records referenced by {@link Levels.Entry#SoundEnv}. */
@Excel.Binned
public class SoundEnviron extends Excel<SoundEnviron.Entry> {
  @Override
  protected void put(int id, Entry value) {
    super.put(value.Index, value);
  }

  public static class Entry extends Excel.Entry {
    @Override
    public String toString() {
      return Handle;
    }

    @Key
    @Column public String Handle;
    @Column public int Index;
    @Column public int Song;

    @Column(format = "Day Ambience")
    public int Day_Ambience;

    @Column(format = "Night Ambience")
    public int Night_Ambience;

    @Column(format = "Day Event")
    public int Day_Event;

    @Column(format = "Night Event")
    public int Night_Event;

    @Column(format = "Event Delay")
    public int Event_Delay;

    @Column public boolean Indoors;

    @Column(format = "Material 1")
    public int Material_1;

    @Column(format = "Material 2")
    public int Material_2;

    @Column(format = "EAX Environ")
    public int EAX_Environ;

    @Column(format = "EAX Env Size")
    public int EAX_Env_Size;

    @Column(format = "EAX Env Diff")
    public int EAX_Env_Diff;

    @Column(format = "EAX Room Vol")
    public int EAX_Room_Vol;

    @Column(format = "EAX Room HF")
    public int EAX_Room_HF;

    @Column(format = "EAX Decay Time")
    public int EAX_Decay_Time;

    @Column(format = "EAX Decay HF")
    public int EAX_Decay_HF;

    @Column(format = "EAX Reflect")
    public int EAX_Reflect;

    @Column(format = "EAX Reflect Delay")
    public int EAX_Reflect_Delay;

    @Column(format = "EAX Reverb")
    public int EAX_Reverb;

    @Column(format = "EAX Rev Delay")
    public int EAX_Rev_Delay;

    @Column(format = "EAX Room Roll")
    public int EAX_Room_Roll;

    @Column(format = "EAX Air Absorb")
    public int EAX_Air_Absorb;
  }
}
