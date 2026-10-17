# D2MOO runtime fixes — 2026-10-17

This batch changes production code for two previously recorded gaps.

Missile creation initializes signed Accel and MaxVel state on the pooled Missile
component. MissileCollisionSystem applies the PathMisc.cpp fifth-step update
before ordinary movement. Accel uses the native 8.8 velocity increment; the
existing Java velocity remains in its existing world units. This does not add
the complete native fixed-point path loop or guarantee variable-timestep parity.

ItemGenerator owns unique-drop history for its game World. A selected limited
unique already generated in that instance falls back to rare. NoLimit bypasses
history; negative identities and limited identities >=4096 are rejected. History
is not process-global, so a new game generator starts empty. Existing quest reward
generation is separate. This does not implement native failed-unique triple
durability or every special quest exception.

Verification: five tests across NativeMissileAccelerationTest,
UniqueDropHistoryTest and UniqueDropIntegrationTest passed, with zero failures,
errors or skips. The integration test uses the actual item generation entry point
and 1.10f data. Log: core/build/upstream-runtime-02.log.
XML: .gradle/path-port/upstream-runtime-02/core/test-results/pathPortTest.

Earlier aggregate claims that all 101 upstream commits were verified, or that
eleven unlisted repairs were completed by classification alone, are unsupported.
Those documentation-only conclusions must not be used as completion evidence.
This batch verifies only the production changes and boundaries described above.
