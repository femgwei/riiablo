# Necromancer skeleton draw trace preparation (2026-11-24)

The previous animation research already established a reusable hidden-window
client path: `d2gfx_export_trace.exe` observes the 1.10f D2Gfx export calls
while the original remains unfocused or minimized. Its decoded cell candidate
contains frame, direction, component, unit type/index, mode, token and screen
coordinates. A skeleton can therefore be isolated by `token=SK` and monster
`unit_type=1`, without relying on the player-only UnitAny observer.

Riiablo now supports a dynamic summon group in `AnimationTraceSystem` through
`focusEntities(int...)`, and every canonical frame can carry `entity_id`.
This is needed because Raise Skeleton creates entity ids after the cast. The
new `normalize_d2gfx_skeleton_trace.py` converts filtered original D2Gfx cell
calls into canonical frame records plus ordered `LAYER_DRAW` events. It keeps
the probe's incomplete coverage explicit; it does not promote a bounded
breakpoint sample to a complete visual capture.

The infrastructure regression `AnimationTraceSystemTest` passes with two
selected entities and one excluded entity. Python syntax validation also
passes. No original runtime was launched in this step, so no skeleton runtime
comparison is claimed yet.
