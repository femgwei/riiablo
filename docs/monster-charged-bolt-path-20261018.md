# Monster charged-bolt direction correction

MonsterUnique.cpp:1316 creates buglightning in cardinal target pairs, not eight
equally spaced rays. Each pair uses init args 0 and 1. Lightunique uses the
same initialization. SkillSor.cpp:258 seeds from ordinal + first-point X,
selects PATHTYPE_CHARGEDBOLT, and caps lifetimes >=78 at 77 frames.
PathMisc.cpp:742 selects a neighbouring direction per path point, preserving
the historical discontinuity in its 32-entry direction-offset table.

Both monster emitters now share cardinal-pair initialization and connect to
the existing charged-bolt turn handler. Creation uses integer subtiles,
per-pair seeds, the first randomized segment, and the native lifetime cap.
MonsterChargedBoltPathTest covers both missile types, pair targets, seed
reset per cardinal direction, first velocity and a subsequent production
turn after two subtiles. Existing damage and LE timing tests are retained.

Limit: the shared runtime turns based on travelled distance rather than
precomputing the complete native integer PathPoints array. This fixes the
straight star-shaped emission, but does not claim exact native trajectories
or live-client acceptance. Scarab's earlier damage-event timing adapter is
unchanged.
