# Necromancer skeletal mage original D2Gfx capture (2026-11-25)

The hidden/minimized `CXNecro` fixture was reused with skill 80 (Raise
Skeletal Mage), a same-room preserved corpse, and the read-only D2Gfx export
probe. The run directory was:

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261125-075240-05f8887d/
```

The refreshed scene snapshot confirmed the target corpse before the click:

```text
type=1 class_id=0 unit_id=25 mode=0 position=(4527,5641)
```

The normalized trace contains 730 `SK` draws. Entity 0 is the prepared
corpse (`DT`/`DD`). Entity 364 is the newly created skeletal mage and shows:

```text
SK/S1 frames 3..11  elapsed 1360..2282 ms   entity 364
SK/NU frames 0..7   elapsed 2344..3063 ms   entity 364
SK/WL frames 0..3   elapsed 3032..3610 ms   entity 364
SK/WL frames 0..2   elapsed 3672..3938 ms   entity 364
```

The mage's walk direction changed during the observed segment (`direction`
8, 56 and 40), and its screen position moved from approximately `(425,261)`
to `(567,272)`. This is a bounded draw-call observation; it is not a complete
frame capture or a pixel-equivalence claim. The enclosing process session
again reported the original process exiting after the helper's bounded cleanup,
so the runtime status is `observed_with_cleanup_warning`.

The normalized output is:

```text
tools/original-observer/broker/20261121-165133-4490d598/runs/20261125-075240-05f8887d/orchestrator/process-session/001-raise-skeletal-mage-d2gfx-minimized/skeleton-mage-draw-trace.json
```

The shared `Sequence(S1 -> NU)` correction in
`ServerSkillSystem.raiseNecromancerSkeleton` covers both skills 70 and 80.
The existing movement data remains distinct: `necroskeleton` uses native
`Velocity=13`, while `necromage` uses `Velocity=12`; both use the native 75%
monster movement state and the existing animation-rate coupling. No separate
mage speed reduction is justified by this capture.
