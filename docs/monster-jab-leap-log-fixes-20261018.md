# Scarab Jab and Leap log corrections

game.log:12309 and later shows SCS1HTH missing after scarab Jab's A2.
SequenceHandler restarted a remaining player Jab strike using Player.MODE_A2
(8), which means S1 for monsters. MonSeq owns monster action points and must
not enter that restart or the player 3x COF-speed fallback. Actioneer's
end-animation continuation is now restricted to players so monster casts can
finish at the end of MonSeq even when the player three-hit counter remains.
Native SrvDo007 (SkillAma.cpp:427) resolves a hit per callback; it does not
require the player's three-thrust animation replay for monsters.

The Leap warning was an unrecognized client-start callback, not evidence that
server movement failed: the same log contains LeapSystem land events starting
at 11930. CltSt29 is now explicitly accepted after common sound/overlay handling.
The existing MonSeq, LeapSystem position interpolation, and client assembly
provide presentation; this callback must not initialize a second leap or reset
elapsed time. No additional vertical-arc fidelity is claimed by this change.

Regression coverage: monster Jab completion avoids S1/player speed fallback;
real seq_scarabjab and seq_leaperleap actions run against their MonSeq points;
Leap start presentation preserves an airborne leap and reaches its landing;
existing movement and resurrect presentation checks remain included.
