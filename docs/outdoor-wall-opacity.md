# Outdoor scenery opacity correction

The A2 p3/p4 comparison shows ground texture through Riiablo's roof and tree
canopies. RenderSystem's logical-group cutaway applied alpha 0.55 to every
wall in the player's group, including outdoor street scenery. Group equality
is not sufficient evidence of outdoor occlusion.

Restrict group-based fading to indoor levels. Keep explicit native Pop
cutaways outdoors. Regression tests cover outdoor group opacity and indoor
group cutaways. This corrects the identified transparency path; final visual
parity (including gamma and native shadow blending) still needs game testing.

Revert the preceding daylight-midpoint workaround: D2MOO
source/D2Common/src/D2Environment.cpp, ENVIRONMENT_AllocDrlgEnvironment,
initializes ticks from gNormalEnvironmentCycle[ENVCYCLE_NOON].nTicksBegin
(zero), not 90 degrees. The supplied game.log confirms the workaround was
active (ticks=11520) but did not resolve the discrepancy.
