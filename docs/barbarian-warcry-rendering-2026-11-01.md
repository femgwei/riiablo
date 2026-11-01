# Barbarian warcry drawing audit (1.10f)

## Confirmed and corrected

- Shout, Battle Orders and Battle Command persistent buffs already use native aura drawing.
- Howl's `curseterror`, Taunt's `taunt`, Battle Cry's `battlecry`, and War Cry's shared `stun` target overlays are not marked `States.aura`. They nevertheless use the same native overlay renderer. Enable native additive palette drawing, table animation rate and unit-height/XY offsets for these rows, plus `curse_hit` and the `warcry` cast overlay. Shared fear/stun consumers intentionally receive the same correction. Persistent/one-shot lifetime and PreDraw placement remain unchanged.
- CltDo025 uses 64 integer target vectors, not the missile DCC's 32 animation directions. Correct the standalone presentation fallback to 64 native vectors. Local SrvDo022/068 already emits the authoritative wave; reuse it instead of drawing an extra ownerless ring. Network clients already reuse synchronized missiles.
- Taunt has no radial missile in the original Skills table; do not add a wave.

## Evidence

Source: original installed 1.10f MPQs and D2Client.dll, SHA256
`94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`.
REA static evidence (not runtime observation):

- Callback table: `0x6FB775A8 + 25*4` contains `0x6FACA570`;
  `ev_bebcfd8ebcb1d5c36ce3ae27fc872b4fb76e72cf395232d7064a24f639b27769`.
- Disassembly of `0x6FACA570` calls `0x6FABD4D0`, then `0x6FABD5D0`;
  recovered latter pseudocode loops 0..0x100 by 4, i.e. 64 missiles:
  `ev_58c0661a456599cebf04d90c3de3d3341b941fa7b654ba457f8f4a2cdcb10908`.
- Exact X/Y arrays at `0x6FB6E2E0` / `0x6FB6E3E0`:
  `ev_9b08f969224db0ca5374f22779c4d739d05708905e8ebbc250253632d9c7bfca`.
- Native overlay renderer evidence and coordinates are recorded in
  `paladin-aura-rendering-2026-11-01.md`; this renderer is not aura-only.
- D2MOO `SkillBar.cpp`, `SKILLS_SrvDo068_BasicShout` calls `sub_6FD14170`;
  `SKILLS_SrvDo071_Taunt` applies the target state without emitting a ring.

`WarCryVisualResourceTest` checks actual MPQ rows, native rendering selection,
rates, lifetime, and integer-vector symmetry. Existing aura/state lifecycle
tests remain applicable. REA bundle is retained locally under
`.gradle/rea-warcry-rendering-20261101-evidence.json` (ignored).

## Verification boundary

This aligns verified drawing parameters and wave generation; no original-game
runtime capture or pixel-by-pixel comparison was performed. Grim Ward's corpse
totem/missile behavior is a separate mechanism and is not changed by this audit.
