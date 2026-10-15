# DiabCold delayed-death absorb

Status: PARTIAL. This change covers absorb healing during DEATH_DELAY only.

D2MOO `source/D2Game/src/UNIT/SUnitDmg.cpp:1292` allows absorb healing only while the defender is alive and does not have STATE_DEATH_DELAY. DiabCold already rejects nonpositive life and corpses; its direct damage resolver now also guards absorb healing with this state check.

Three new integration cases use the real Diablo S2 animation marker, rather than injected damage. Delayed full absorb leaves life at 500, delayed partial absorb subtracts the recorded damage without healing, and ordinary full absorb still heals by the native damage roll. Each checks chill duration, death count and absence of missile entities. The baseline has 14 tests with two expected failures.

`prepare-source.ps1` archives source-base-sha.txt and overlays the test; `-Repaired` also overlays the production resolver. `run-tests.ps1 -Pass final` and `-Pass repeat` retain logs and XML. `verify.ps1` requires 305 passing tests in each run, matching 64 Nova samples, and matching production/test source hashes between the workspace and tested snapshot.

DiabCold fractional death thresholds, complete native RNG and death callbacks remain outside this acceptance. Existing damage, cold-length and invalid-target tests remain in the regression selection.
