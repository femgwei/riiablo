package com.d2moo.testutil;

import com.riiablo.RiiabloTest;
import com.riiablo.item.ItemGenerator;
import com.riiablo.item.Quality;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UniqueDropIntegrationTest extends RiiabloTest {
  @Test void generatorRejectsRepeatedUniqueButNewGameAllowsIt() {
    ItemGenerator game = new ItemGenerator();
    assertEquals(Quality.UNIQUE, game.generateLootItem("cap", 99, Quality.UNIQUE, 1234, 0).quality);
    assertEquals(Quality.RARE, game.generateLootItem("cap", 99, Quality.UNIQUE, 1234, 0).quality);
    assertEquals(Quality.UNIQUE,
        new ItemGenerator().generateLootItem("cap", 99, Quality.UNIQUE, 1234, 0).quality);
  }
}
