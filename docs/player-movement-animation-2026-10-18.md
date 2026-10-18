# Player movement and animation percentage

Pure rules live in server-domain's NativePlayerMovement, without ECS, assets or audio dependencies.
StateUpdater supplies diminished equipment FRW and additive velocity modifiers. Velocity uses the
same effective percentage for displacement that VelocityModeChanger uses for animation. Client
AnimationStepper and FootstepEmitter continue consuming animation rates and displayed frames.

Source: D2MOO 1.10f Units.cpp UNITS_GetBaseAnimSpeed / UNITS_UpdateRunWalkAnimRateAndVelocity;
dark-magic adapter/movement/rules.go is a cross-check, not a version-specific numeric authority.
Walk starts at 100; run uses the integer RunVelocity/WalkVelocity percentage. The combined
percentage is floored at 25. Animation uses integer truncation with walk 213 / run 101.
For 6/9 base movement, the unmodified run rate is 151, not 101. Terminal waypoint clipping
does not lower the configured animation rate. Stopping still selects the neutral mode.

Scope: player movement only; monster rules and foot-plant frame selection are unchanged.
Real-client visual/audio and multiplayer acceptance remain to be performed on the user's machine.

Validation: production compilation and verifyModuleBoundaries passed. NativePlayerMovementTest
(including a headless ECS displacement check), PlayerMovementPercentageTest,
PlayerVelocityUnitsTest, MovementMultiplierContractTest and VelocityModeChangerTest passed.
The ordinary test compilation is blocked by pre-existing missing Vulture constants in
MonsterAiParamParityTest. The focused run used a local build/player-movement-test.init.gradle
to exclude that unrelated source from compileTestJava; no repository test was disabled.
