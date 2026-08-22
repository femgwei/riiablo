package com.riiablo.map.d2moo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.d2moo.common.datatbls.D2LvlPrestTxt;
import com.d2moo.common.datatbls.D2LvlSubTxt;
import com.d2moo.common.datatbls.DataTbls;
import com.d2moo.common.util.D2FileReader;

class DataTblsHeaderParserTest {

  @AfterEach
  void unloadTables() {
    DataTbls.unloadLvlPrestTxt();
    DataTbls.unloadLvlSubTxt();
  }

  @Test
  void lvlPrestUsesHeaderNamesInsteadOfAssumedColumnPositions() {
    String txt = "Name\tDef\tLevelId\tPopulate\tLogicals\tOutdoors\tAnimate\tKillEdge\t"
        + "FillBlanks\tSizeX\tSizeY\tAutoMap\tScan\tPops\tPopPad\tFiles\tFile1\t"
        + "Dt1Mask\tBeta\tExpansion\n"
        + "Test preset\t42\t2\t1\t0\t1\t0\t1\t0\t8\t9\t1\t0\t3\t4\t1\t"
        + "Act1/Test.ds1\t255\t0\t1\n";
    D2FileReader.ArchiveReader archive = fileName -> txt.getBytes(StandardCharsets.UTF_8);

    DataTbls.loadLvlPrestTxt(archive, 0);

    D2LvlPrestTxt record = DataTbls.getLvlPrestTxtRecord(42);
    assertNotNull(record);
    assertEquals(2, record.getDwLevelId());
    assertEquals(8, record.getDwSizeX());
    assertEquals(9, record.getDwSizeY());
    assertEquals("Act1/Test.ds1", record.getSzFile(0));
    assertEquals(255, record.getDwDt1Mask());
    assertEquals(1, record.getDwExpansion());
  }

  @Test
  void lvlSubUsesHeaderNamesAndZeroBasedProbabilityColumns() {
    String txt = "Name\tType\tFile\tExpansion\tBordType\tGridSize\tDt1Mask\t"
        + "Prob0\tProb1\tProb2\tProb3\tProb4\t"
        + "Trials0\tTrials1\tTrials2\tTrials3\tTrials4\t"
        + "Max0\tMax1\tMax2\tMax3\tMax4\n"
        + "Test sub\t7\tAct1/Sub.ds1\t1\t-1\t2\t32\t"
        + "10\t20\t30\t40\t50\t1\t2\t3\t4\t5\t6\t7\t8\t9\t10\n";
    D2FileReader.ArchiveReader archive = fileName -> txt.getBytes(StandardCharsets.UTF_8);

    DataTbls.loadLvlSubTxt(archive);

    D2LvlSubTxt record = DataTbls.getLvlSubTxtRecord(7);
    assertNotNull(record);
    assertEquals("Act1/Sub.ds1", record.getSzFile());
    assertEquals(-1, record.getDwBordType());
    assertEquals(2, record.getDwGridSize());
    assertEquals(32, record.getDwDt1Mask());
    assertEquals(10, record.getNProb(0));
    assertEquals(50, record.getNProb(4));
    assertEquals(3, record.getNTrials(2));
    assertEquals(10, record.getNMax(4));
  }
}
