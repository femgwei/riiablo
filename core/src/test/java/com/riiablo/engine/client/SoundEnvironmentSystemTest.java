package com.riiablo.engine.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.riiablo.codec.excel.SoundEnviron;
import org.junit.jupiter.api.Test;

class SoundEnvironmentSystemTest {
  private static final float EPSILON = 0.0001f;

  @Test
  void selectsDayAndNightAmbienceAndEvent() {
    SoundEnviron.Entry environment = new SoundEnviron.Entry();
    environment.Day_Ambience = 70;
    environment.Night_Ambience = 71;
    environment.Day_Event = 192;
    environment.Night_Event = 197;

    assertEquals(70, SoundEnvironmentSystem.ambienceId(environment, true));
    assertEquals(71, SoundEnvironmentSystem.ambienceId(environment, false));
    assertEquals(192, SoundEnvironmentSystem.eventId(environment, true));
    assertEquals(197, SoundEnvironmentSystem.eventId(environment, false));
  }

  @Test
  void convertsNativeSoundFramesAtTwentyFiveHertz() {
    assertEquals(10f, SoundEnvironmentSystem.framesToSeconds(250), EPSILON);
    assertEquals(0.48f, SoundEnvironmentSystem.framesToSeconds(12), EPSILON);
    assertEquals(0f, SoundEnvironmentSystem.framesToSeconds(-1), EPSILON);
  }
}
