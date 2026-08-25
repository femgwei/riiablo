package com.riiablo.attributes;

/**
 * Experience table for calculating experience required for each level.
 * Uses hardcoded values from Diablo 2.
 */
public class ExperienceTable {

  private static ExperienceTable instance;

  public static final int MAX_LEVEL = 99;

  // Experience required for each level (1-based indexing)
  private final long[] amazon;
  private final long[] sorceress;
  private final long[] necromancer;
  private final long[] paladin;
  private final long[] barbarian;
  private final long[] druid;
  private final long[] assassin;

  private ExperienceTable() {
    // Initialize arrays
    amazon = new long[MAX_LEVEL + 1];
    sorceress = new long[MAX_LEVEL + 1];
    necromancer = new long[MAX_LEVEL + 1];
    paladin = new long[MAX_LEVEL + 1];
    barbarian = new long[MAX_LEVEL + 1];
    druid = new long[MAX_LEVEL + 1];
    assassin = new long[MAX_LEVEL + 1];

    // Hardcoded experience values for each level (Diablo 2 formula)
    // Using direct assignment with long values to avoid integer overflow
    // Level 1-60: Small values that fit in int
    amazon[1] = 500;      sorceress[1] = 500;      necromancer[1] = 500;      paladin[1] = 500;      barbarian[1] = 500;      druid[1] = 500;      assassin[1] = 500;
    amazon[2] = 1500;     sorceress[2] = 1500;     necromancer[2] = 1500;     paladin[2] = 1500;     barbarian[2] = 1500;     druid[2] = 1500;     assassin[2] = 1500;
    amazon[3] = 3750;     sorceress[3] = 3750;     necromancer[3] = 3750;     paladin[3] = 3750;     barbarian[3] = 3750;     druid[3] = 3750;     assassin[3] = 3750;
    amazon[4] = 7875;     sorceress[4] = 7875;     necromancer[4] = 7875;     paladin[4] = 7875;     barbarian[4] = 7875;     druid[4] = 7875;     assassin[4] = 7875;
    amazon[5] = 14175;    sorceress[5] = 14175;    necromancer[5] = 14175;    paladin[5] = 14175;    barbarian[5] = 14175;    druid[5] = 14175;    assassin[5] = 14175;
    amazon[6] = 22625;    sorceress[6] = 22625;    necromancer[6] = 22625;    paladin[6] = 22625;    barbarian[6] = 22625;    druid[6] = 22625;    assassin[6] = 22625;
    amazon[7] = 32800;    sorceress[7] = 32800;    necromancer[7] = 32800;    paladin[7] = 32800;    barbarian[7] = 32800;    druid[7] = 32800;    assassin[7] = 32800;
    amazon[8] = 46000;    sorceress[8] = 46000;    necromancer[8] = 46000;    paladin[8] = 46000;    barbarian[8] = 46000;    druid[8] = 46000;    assassin[8] = 46000;
    amazon[9] = 62750;    sorceress[9] = 62750;    necromancer[9] = 62750;    paladin[9] = 62750;    barbarian[9] = 62750;    druid[9] = 62750;    assassin[9] = 62750;
    amazon[10] = 84000;   sorceress[10] = 84000;   necromancer[10] = 84000;   paladin[10] = 84000;   barbarian[10] = 84000;   druid[10] = 84000;   assassin[10] = 84000;
    amazon[11] = 110250;  sorceress[11] = 110250;  necromancer[11] = 110250;  paladin[11] = 110250;  barbarian[11] = 110250;  druid[11] = 110250;  assassin[11] = 110250;
    amazon[12] = 142650;  sorceress[12] = 142650;  necromancer[12] = 142650;  paladin[12] = 142650;  barbarian[12] = 142650;  druid[12] = 142650;  assassin[12] = 142650;
    amazon[13] = 182325;  sorceress[13] = 182325;  necromancer[13] = 182325;  paladin[13] = 182325;  barbarian[13] = 182325;  druid[13] = 182325;  assassin[13] = 182325;
    amazon[14] = 230500;  sorceress[14] = 230500;  necromancer[14] = 230500;  paladin[14] = 230500;  barbarian[14] = 230500;  druid[14] = 230500;  assassin[14] = 230500;
    amazon[15] = 288450;  sorceress[15] = 288450;  necromancer[15] = 288450;  paladin[15] = 288450;  barbarian[15] = 288450;  druid[15] = 288450;  assassin[15] = 288450;
    amazon[16] = 357650;  sorceress[16] = 357650;  necromancer[16] = 357650;  paladin[16] = 357650;  barbarian[16] = 357650;  druid[16] = 357650;  assassin[16] = 357650;
    amazon[17] = 439550;  sorceress[17] = 439550;  necromancer[17] = 439550;  paladin[17] = 439550;  barbarian[17] = 439550;  druid[17] = 439550;  assassin[17] = 439550;
    amazon[18] = 535275;  sorceress[18] = 535275;  necromancer[18] = 535275;  paladin[18] = 535275;  barbarian[18] = 535275;  druid[18] = 535275;  assassin[18] = 535275;
    amazon[19] = 646175;  sorceress[19] = 646175;  necromancer[19] = 646175;  paladin[19] = 646175;  barbarian[19] = 646175;  druid[19] = 646175;  assassin[19] = 646175;
    amazon[20] = 773475;  sorceress[20] = 773475;  necromancer[20] = 773475;  paladin[20] = 773475;  barbarian[20] = 773475;  druid[20] = 773475;  assassin[20] = 773475;
    amazon[21] = 918675;  sorceress[21] = 918675;  necromancer[21] = 918675;  paladin[21] = 918675;  barbarian[21] = 918675;  druid[21] = 918675;  assassin[21] = 918675;
    amazon[22] = 1083450; sorceress[22] = 1083450; necromancer[22] = 1083450; paladin[22] = 1083450; barbarian[22] = 1083450; druid[22] = 1083450; assassin[22] = 1083450;
    amazon[23] = 1267350; sorceress[23] = 1267350; necromancer[23] = 1267350; paladin[23] = 1267350; barbarian[23] = 1267350; druid[23] = 1267350; assassin[23] = 1267350;
    amazon[24] = 1472100; sorceress[24] = 1472100; necromancer[24] = 1472100; paladin[24] = 1472100; barbarian[24] = 1472100; druid[24] = 1472100; assassin[24] = 1472100;
    amazon[25] = 1699450; sorceress[25] = 1699450; necromancer[25] = 1699450; paladin[25] = 1699450; barbarian[25] = 1699450; druid[25] = 1699450; assassin[25] = 1699450;
    amazon[26] = 1951200; sorceress[26] = 1951200; necromancer[26] = 1951200; paladin[26] = 1951200; barbarian[26] = 1951200; druid[26] = 1951200; assassin[26] = 1951200;
    amazon[27] = 2229400; sorceress[27] = 2229400; necromancer[27] = 2229400; paladin[27] = 2229400; barbarian[27] = 2229400; druid[27] = 2229400; assassin[27] = 2229400;
    amazon[28] = 2536150; sorceress[28] = 2536150; necromancer[28] = 2536150; paladin[28] = 2536150; barbarian[28] = 2536150; druid[28] = 2536150; assassin[28] = 2536150;
    amazon[29] = 2873600; sorceress[29] = 2873600; necromancer[29] = 2873600; paladin[29] = 2873600; barbarian[29] = 2873600; druid[29] = 2873600; assassin[29] = 2873600;
    amazon[30] = 3244050; sorceress[30] = 3244050; necromancer[30] = 3244050; paladin[30] = 3244050; barbarian[30] = 3244050; druid[30] = 3244050; assassin[30] = 3244050;
    amazon[31] = 3649850; sorceress[31] = 3649850; necromancer[31] = 3649850; paladin[31] = 3649850; barbarian[31] = 3649850; druid[31] = 3649850; assassin[31] = 3649850;
    amazon[32] = 4093500; sorceress[32] = 4093500; necromancer[32] = 4093500; paladin[32] = 4093500; barbarian[32] = 4093500; druid[32] = 4093500; assassin[32] = 4093500;
    amazon[33] = 4577650; sorceress[33] = 4577650; necromancer[33] = 4577650; paladin[33] = 4577650; barbarian[33] = 4577650; druid[33] = 4577650; assassin[33] = 4577650;
    amazon[34] = 5105100; sorceress[34] = 5105100; necromancer[34] = 5105100; paladin[34] = 5105100; barbarian[34] = 5105100; druid[34] = 5105100; assassin[34] = 5105100;
    amazon[35] = 5678800; sorceress[35] = 5678800; necromancer[35] = 5678800; paladin[35] = 5678800; barbarian[35] = 5678800; druid[35] = 5678800; assassin[35] = 5678800;
    amazon[36] = 6301875; sorceress[36] = 6301875; necromancer[36] = 6301875; paladin[36] = 6301875; barbarian[36] = 6301875; druid[36] = 6301875; assassin[36] = 6301875;
    amazon[37] = 6977500; sorceress[37] = 6977500; necromancer[37] = 6977500; paladin[37] = 6977500; barbarian[37] = 6977500; druid[37] = 6977500; assassin[37] = 6977500;
    amazon[38] = 7713000; sorceress[38] = 7713000; necromancer[38] = 7713000; paladin[38] = 7713000; barbarian[38] = 7713000; druid[38] = 7713000; assassin[38] = 7713000;
    amazon[39] = 8513650; sorceress[39] = 8513650; necromancer[39] = 8513650; paladin[39] = 8513650; barbarian[39] = 8513650; druid[39] = 8513650; assassin[39] = 8513650;
    amazon[40] = 9384800; sorceress[40] = 9384800; necromancer[40] = 9384800; paladin[40] = 9384800; barbarian[40] = 9384800; druid[40] = 9384800; assassin[40] = 9384800;
    amazon[41] = 10332150; sorceress[41] = 10332150; necromancer[41] = 10332150; paladin[41] = 10332150; barbarian[41] = 10332150; druid[41] = 10332150; assassin[41] = 10332150;
    amazon[42] = 11357700; sorceress[42] = 11357700; necromancer[42] = 11357700; paladin[42] = 11357700; barbarian[42] = 11357700; druid[42] = 11357700; assassin[42] = 11357700;
    amazon[43] = 12477225; sorceress[43] = 12477225; necromancer[43] = 12477225; paladin[43] = 12477225; barbarian[43] = 12477225; druid[43] = 12477225; assassin[43] = 12477225;
    amazon[44] = 13697425; sorceress[44] = 13697425; necromancer[44] = 13697425; paladin[44] = 13697425; barbarian[44] = 13697425; druid[44] = 13697425; assassin[44] = 13697425;
    amazon[45] = 15025500; sorceress[45] = 15025500; necromancer[45] = 15025500; paladin[45] = 15025500; barbarian[45] = 15025500; druid[45] = 15025500; assassin[45] = 15025500;
    amazon[46] = 16468975; sorceress[46] = 16468975; necromancer[46] = 16468975; paladin[46] = 16468975; barbarian[46] = 16468975; druid[46] = 16468975; assassin[46] = 16468975;
    amazon[47] = 18035750; sorceress[47] = 18035750; necromancer[47] = 18035750; paladin[47] = 18035750; barbarian[47] = 18035750; druid[47] = 18035750; assassin[47] = 18035750;
    amazon[48] = 19734075; sorceress[48] = 19734075; necromancer[48] = 19734075; paladin[48] = 19734075; barbarian[48] = 19734075; druid[48] = 19734075; assassin[48] = 19734075;
    amazon[49] = 21572625; sorceress[49] = 21572625; necromancer[49] = 21572625; paladin[49] = 21572625; barbarian[49] = 21572625; druid[49] = 21572625; assassin[49] = 21572625;
    amazon[50] = 23560475; sorceress[50] = 23560475; necromancer[50] = 23560475; paladin[50] = 23560475; barbarian[50] = 23560475; druid[50] = 23560475; assassin[50] = 23560475;
    amazon[51] = 25707125; sorceress[51] = 25707125; necromancer[51] = 25707125; paladin[51] = 25707125; barbarian[51] = 25707125; druid[51] = 25707125; assassin[51] = 25707125;
    amazon[52] = 28022525; sorceress[52] = 28022525; necromancer[52] = 28022525; paladin[52] = 28022525; barbarian[52] = 28022525; druid[52] = 28022525; assassin[52] = 28022525;
    amazon[53] = 30517125; sorceress[53] = 30517125; necromancer[53] = 30517125; paladin[53] = 30517125; barbarian[53] = 30517125; druid[53] = 30517125; assassin[53] = 30517125;
    amazon[54] = 33201825; sorceress[54] = 33201825; necromancer[54] = 33201825; paladin[54] = 33201825; barbarian[54] = 33201825; druid[54] = 33201825; assassin[54] = 33201825;
    amazon[55] = 36088125; sorceress[55] = 36088125; necromancer[55] = 36088125; paladin[55] = 36088125; barbarian[55] = 36088125; druid[55] = 36088125; assassin[55] = 36088125;
    amazon[56] = 39188125; sorceress[56] = 39188125; necromancer[56] = 39188125; paladin[56] = 39188125; barbarian[56] = 39188125; druid[56] = 39188125; assassin[56] = 39188125;
    amazon[57] = 42514525; sorceress[57] = 42514525; necromancer[57] = 42514525; paladin[57] = 42514525; barbarian[57] = 42514525; druid[57] = 42514525; assassin[57] = 42514525;
    amazon[58] = 46080725; sorceress[58] = 46080725; necromancer[58] = 46080725; paladin[58] = 46080725; barbarian[58] = 46080725; druid[58] = 46080725; assassin[58] = 46080725;
    amazon[59] = 49902825; sorceress[59] = 49902825; necromancer[59] = 49902825; paladin[59] = 49902825; barbarian[59] = 49902825; druid[59] = 49902825; assassin[59] = 49902825;
    amazon[60] = 53995725L; sorceress[60] = 53995725L; necromancer[60] = 53995725L; paladin[60] = 53995725L; barbarian[60] = 53995725L; druid[60] = 53995725L; assassin[60] = 53995725L;
    amazon[61] = 58375125L; sorceress[61] = 58375125L; necromancer[61] = 58375125L; paladin[61] = 58375125L; barbarian[61] = 58375125L; druid[61] = 58375125L; assassin[61] = 58375125L;
    amazon[62] = 63057525L; sorceress[62] = 63057525L; necromancer[62] = 63057525L; paladin[62] = 63057525L; barbarian[62] = 63057525L; druid[62] = 63057525L; assassin[62] = 63057525L;
    amazon[63] = 68060225L; sorceress[63] = 68060225L; necromancer[63] = 68060225L; paladin[63] = 68060225L; barbarian[63] = 68060225L; druid[63] = 68060225L; assassin[63] = 68060225L;
    amazon[64] = 73401425L; sorceress[64] = 73401425L; necromancer[64] = 73401425L; paladin[64] = 73401425L; barbarian[64] = 73401425L; druid[64] = 73401425L; assassin[64] = 73401425L;
    amazon[65] = 79100325L; sorceress[65] = 79100325L; necromancer[65] = 79100325L; paladin[65] = 79100325L; barbarian[65] = 79100325L; druid[65] = 79100325L; assassin[65] = 79100325L;
    amazon[66] = 85177025L; sorceress[66] = 85177025L; necromancer[66] = 85177025L; paladin[66] = 85177025L; barbarian[66] = 85177025L; druid[66] = 85177025L; assassin[66] = 85177025L;
    amazon[67] = 91651625L; sorceress[67] = 91651625L; necromancer[67] = 91651625L; paladin[67] = 91651625L; barbarian[67] = 91651625L; druid[67] = 91651625L; assassin[67] = 91651625L;
    amazon[68] = 98545325L; sorceress[68] = 98545325L; necromancer[68] = 98545325L; paladin[68] = 98545325L; barbarian[68] = 98545325L; druid[68] = 98545325L; assassin[68] = 98545325L;
    amazon[69] = 1059347325L; sorceress[69] = 1059347325L; necromancer[69] = 1059347325L; paladin[69] = 1059347325L; barbarian[69] = 1059347325L; druid[69] = 1059347325L; assassin[69] = 1059347325L;
    amazon[70] = 1139137325L; sorceress[70] = 1139137325L; necromancer[70] = 1139137325L; paladin[70] = 1139137325L; barbarian[70] = 1139137325L; druid[70] = 1139137325L; assassin[70] = 1139137325L;
    amazon[71] = 1221977325L; sorceress[71] = 1221977325L; necromancer[71] = 1221977325L; paladin[71] = 1221977325L; barbarian[71] = 1221977325L; druid[71] = 1221977325L; assassin[71] = 1221977325L;
    amazon[72] = 1307977325L; sorceress[72] = 1307977325L; necromancer[72] = 1307977325L; paladin[72] = 1307977325L; barbarian[72] = 1307977325L; druid[72] = 1307977325L; assassin[72] = 1307977325L;
    amazon[73] = 1397257325L; sorceress[73] = 1397257325L; necromancer[73] = 1397257325L; paladin[73] = 1397257325L; barbarian[73] = 1397257325L; druid[73] = 1397257325L; assassin[73] = 1397257325L;
    amazon[74] = 1489927325L; sorceress[74] = 1489927325L; necromancer[74] = 1489927325L; paladin[74] = 1489927325L; barbarian[74] = 1489927325L; druid[74] = 1489927325L; assassin[74] = 1489927325L;
    amazon[75] = 1586117325L; sorceress[75] = 1586117325L; necromancer[75] = 1586117325L; paladin[75] = 1586117325L; barbarian[75] = 1586117325L; druid[75] = 1586117325L; assassin[75] = 1586117325L;
    amazon[76] = 1685967325L; sorceress[76] = 1685967325L; necromancer[76] = 1685967325L; paladin[76] = 1685967325L; barbarian[76] = 1685967325L; druid[76] = 1685967325L; assassin[76] = 1685967325L;
    amazon[77] = 1789617325L; sorceress[77] = 1789617325L; necromancer[77] = 1789617325L; paladin[77] = 1789617325L; barbarian[77] = 1789617325L; druid[77] = 1789617325L; assassin[77] = 1789617325L;
    amazon[78] = 1897207325L; sorceress[78] = 1897207325L; necromancer[78] = 1897207325L; paladin[78] = 1897207325L; barbarian[78] = 1897207325L; druid[78] = 1897207325L; assassin[78] = 1897207325L;
    amazon[79] = 2008887325L; sorceress[79] = 2008887325L; necromancer[79] = 2008887325L; paladin[79] = 2008887325L; barbarian[79] = 2008887325L; druid[79] = 2008887325L; assassin[79] = 2008887325L;
    amazon[80] = 2124807325L; sorceress[80] = 2124807325L; necromancer[80] = 2124807325L; paladin[80] = 2124807325L; barbarian[80] = 2124807325L; druid[80] = 2124807325L; assassin[80] = 2124807325L;
    amazon[81] = 2245127325L; sorceress[81] = 2245127325L; necromancer[81] = 2245127325L; paladin[81] = 2245127325L; barbarian[81] = 2245127325L; druid[81] = 2245127325L; assassin[81] = 2245127325L;
    amazon[82] = 2370007325L; sorceress[82] = 2370007325L; necromancer[82] = 2370007325L; paladin[82] = 2370007325L; barbarian[82] = 2370007325L; druid[82] = 2370007325L; assassin[82] = 2370007325L;
    amazon[83] = 2499617325L; sorceress[83] = 2499617325L; necromancer[83] = 2499617325L; paladin[83] = 2499617325L; barbarian[83] = 2499617325L; druid[83] = 2499617325L; assassin[83] = 2499617325L;
    amazon[84] = 2634137325L; sorceress[84] = 2634137325L; necromancer[84] = 2634137325L; paladin[84] = 2634137325L; barbarian[84] = 2634137325L; druid[84] = 2634137325L; assassin[84] = 2634137325L;
    amazon[85] = 2773747325L; sorceress[85] = 2773747325L; necromancer[85] = 2773747325L; paladin[85] = 2773747325L; barbarian[85] = 2773747325L; druid[85] = 2773747325L; assassin[85] = 2773747325L;
    amazon[86] = 2918637325L; sorceress[86] = 2918637325L; necromancer[86] = 2918637325L; paladin[86] = 2918637325L; barbarian[86] = 2918637325L; druid[86] = 2918637325L; assassin[86] = 2918637325L;
    amazon[87] = 3068987325L; sorceress[87] = 3068987325L; necromancer[87] = 3068987325L; paladin[87] = 3068987325L; barbarian[87] = 3068987325L; druid[87] = 3068987325L; assassin[87] = 3068987325L;
    amazon[88] = 3224997325L; sorceress[88] = 3224997325L; necromancer[88] = 3224997325L; paladin[88] = 3224997325L; barbarian[88] = 3224997325L; druid[88] = 3224997325L; assassin[88] = 3224997325L;
    amazon[89] = 3386867325L; sorceress[89] = 3386867325L; necromancer[89] = 3386867325L; paladin[89] = 3386867325L; barbarian[89] = 3386867325L; druid[89] = 3386867325L; assassin[89] = 3386867325L;
    amazon[90] = 3554797325L; sorceress[90] = 3554797325L; necromancer[90] = 3554797325L; paladin[90] = 3554797325L; barbarian[90] = 3554797325L; druid[90] = 3554797325L; assassin[90] = 3554797325L;
    amazon[91] = 3728997325L; sorceress[91] = 3728997325L; necromancer[91] = 3728997325L; paladin[91] = 3728997325L; barbarian[91] = 3728997325L; druid[91] = 3728997325L; assassin[91] = 3728997325L;
    amazon[92] = 3909687325L; sorceress[92] = 3909687325L; necromancer[92] = 3909687325L; paladin[92] = 3909687325L; barbarian[92] = 3909687325L; druid[92] = 3909687325L; assassin[92] = 3909687325L;
    amazon[93] = 4097087325L; sorceress[93] = 4097087325L; necromancer[93] = 4097087325L; paladin[93] = 4097087325L; barbarian[93] = 4097087325L; druid[93] = 4097087325L; assassin[93] = 4097087325L;
    amazon[94] = 4291427325L; sorceress[94] = 4291427325L; necromancer[94] = 4291427325L; paladin[94] = 4291427325L; barbarian[94] = 4291427325L; druid[94] = 4291427325L; assassin[94] = 4291427325L;
    amazon[95] = 4492947325L; sorceress[95] = 4492947325L; necromancer[95] = 4492947325L; paladin[95] = 4492947325L; barbarian[95] = 4492947325L; druid[95] = 4492947325L; assassin[95] = 4492947325L;
    amazon[96] = 4701887325L; sorceress[96] = 4701887325L; necromancer[96] = 4701887325L; paladin[96] = 4701887325L; barbarian[96] = 4701887325L; druid[96] = 4701887325L; assassin[96] = 4701887325L;
    amazon[97] = 4918497325L; sorceress[97] = 4918497325L; necromancer[97] = 4918497325L; paladin[97] = 4918497325L; barbarian[97] = 4918497325L; druid[97] = 4918497325L; assassin[97] = 4918497325L;
    amazon[98] = 5143027325L; sorceress[98] = 5143027325L; necromancer[98] = 5143027325L; paladin[98] = 5143027325L; barbarian[98] = 5143027325L; druid[98] = 5143027325L; assassin[98] = 5143027325L;
    amazon[99] = 5375737325L; sorceress[99] = 5375737325L; necromancer[99] = 5375737325L; paladin[99] = 5375737325L; barbarian[99] = 5375737325L; druid[99] = 5375737325L; assassin[99] = 5375737325L;
  }

  public static synchronized ExperienceTable getInstance() {
    if (instance == null) {
      instance = new ExperienceTable();
    }
    return instance;
  }

  /**
   * Get experience required for the next level
   * @param level Current level (1-99)
   * @param classId Character class ID (0=Amazon, 1=Sorceress, 2=Necro, 3=Paladin, 4=Barbarian, 5=Druid, 6=Assassin)
   * @return Experience points required for next level
   */
  public long getExperienceForNextLevel(int level, int classId) {
    if (level >= MAX_LEVEL) {
      return Long.MAX_VALUE; // Max level reached
    }
    // The table stores the amount required to reach level n + 1 at index n:
    // level 1 -> 500, level 2 -> 1500, ...
    int nextLevel = Math.max(1, level);
    switch (classId) {
      case 0: return amazon[nextLevel];
      case 1: return sorceress[nextLevel];
      case 2: return necromancer[nextLevel];
      case 3: return paladin[nextLevel];
      case 4: return barbarian[nextLevel];
      case 5: return druid[nextLevel];
      case 6: return assassin[nextLevel];
      default: return amazon[nextLevel];
    }
  }

  /**
   * Get experience required for current level (for progress bar)
   * @param level Current level (1-99)
   * @param classId Character class ID
   * @return Experience points for current level
   */
  public long getExperienceForCurrentLevel(int level, int classId) {
    if (level < 1) level = 1;
    if (level == 1) return 0;
    int reached = level - 1;
    switch (classId) {
      case 0: return amazon[reached];
      case 1: return sorceress[reached];
      case 2: return necromancer[reached];
      case 3: return paladin[reached];
      case 4: return barbarian[reached];
      case 5: return druid[reached];
      case 6: return assassin[reached];
      default: return amazon[reached];
    }
  }

  /** Returns normalized progress within the supplied level, clamped to [0, 1]. */
  public float getProgress(int level, int classId, long experience) {
    int clampedLevel = Math.max(1, Math.min(MAX_LEVEL, level));
    if (clampedLevel >= MAX_LEVEL) return 1f;
    long start = getExperienceForCurrentLevel(clampedLevel, classId);
    long end = getExperienceForNextLevel(clampedLevel, classId);
    if (end <= start) return 0f;
    double progress = (double) (experience - start) / (double) (end - start);
    return (float) Math.max(0d, Math.min(1d, progress));
  }
}
