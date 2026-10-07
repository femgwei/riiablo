package com.riiablo.codec.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.files.FileHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SoundEnvironTest {
  private static final String HEADER = "Handle\tIndex\tSong\tDay Ambience\tNight Ambience\t"
      + "Day Event\tNight Event\tEvent Delay\tIndoors\tMaterial 1\tMaterial 2\t"
      + "EAX Environ\tEAX Env Size\tEAX Env Diff\tEAX Room Vol\tEAX Room HF\t"
      + "EAX Decay Time\tEAX Decay HF\tEAX Reflect\tEAX Reflect Delay\tEAX Reverb\t"
      + "EAX Rev Delay\tEAX Room Roll\tEAX Air Absorb\n";

  @Test
  void loadsClassicAreaAudioEnvironmentFields() throws Exception {
    String none = "ESOUNDENVIRON_NONE\t0\t0\t0\t0\t0\t0\t250\t0\t0\t0\t0\t0\t0\t"
        + "-10000\t-10000\t0\t0\t0\t0\t0\t0\t0\t0\n";
    String town = "ESOUNDENVIRON_TOWN_1\t1\t4672\t70\t71\t0\t0\t250\t0\t1\t5\t"
        + "0\t0\t0\t-10000\t-10000\t0\t0\t0\t0\t0\t0\t0\t0\n";
    String wilderness = "ESOUNDENVIRON_WILDERNESS\t2\t4678\t70\t71\t192\t197\t250\t"
        + "0\t1\t5\t17\t100000\t270\t-2100\t-2500\t1490\t210\t-2780\t226\t"
        + "-1434\t100\t0\t-5000\n";
    String tristram = "ESOUNDENVIRON_TRISTRAM\t3\t4676\t70\t71\t192\t197\t250\t0\t"
        + "1\t5\t17\t100000\t270\t-2100\t-2500\t1490\t210\t-2780\t226\t-1434\t"
        + "100\t0\t-5000\n";
    String cave = "ESOUNDENVIRON_CAVE\t4\t4656\t54\t54\t87\t87\t200\t1\t1\t0\t5\t"
        + "11600\t1000\t-1000\t-300\t2700\t640\t-711\t12\t83\t17\t0\t-5000\n";
    Path txt = Files.createTempFile("SoundEnviron", ".txt");
    try {
      Files.write(txt,
          (HEADER + none + town + wilderness + tristram + cave)
              .getBytes(StandardCharsets.ISO_8859_1));
      SoundEnviron table = Excel.load(SoundEnviron.class, new FileHandle(txt.toFile()));

      assertEquals(5, table.size());
      SoundEnviron.Entry entry = table.get(2);
      assertSame(entry, table.get("ESOUNDENVIRON_WILDERNESS"));
      assertEquals("ESOUNDENVIRON_WILDERNESS", entry.Handle);
      assertEquals(4678, entry.Song);
      assertEquals(70, entry.Day_Ambience);
      assertEquals(71, entry.Night_Ambience);
      assertEquals(192, entry.Day_Event);
      assertEquals(197, entry.Night_Event);
      assertEquals(250, entry.Event_Delay);
      assertFalse(entry.Indoors);
      assertEquals(1, entry.Material_1);
      assertEquals(5, entry.Material_2);
      assertEquals(17, entry.EAX_Environ);
      assertEquals(100000, entry.EAX_Env_Size);
      assertEquals(-2100, entry.EAX_Room_Vol);
      assertEquals(-5000, entry.EAX_Air_Absorb);
      assertTrue(table.get(4).Indoors);
      assertSame(table.get(4), table.get("ESOUNDENVIRON_CAVE"));
    } finally {
      Files.deleteIfExists(txt);
    }
  }
}
