# Holy Shield client presentation — 1.10f

## Confirmed original behavior

Target: `G:\BaiduNetdiskDownload\Diablo II 1.10F\D2Client.dll`.
SHA-256: `94466b84c0593ab682f1d3d8dc65ef4f8170f33e430865d802bc752458b2b282`.
Static analysis: REA / Ghidra 12.1.4. This is not a runtime screenshot comparison.

The component-selection branch at `0x6FB5E7FC` compares the component with 7
(SH). It checks state 101 through D2Common ordinal 10487, then checks an
equipped usable shield through ordinal 10273. On success it selects the packed
component token `0x20687368` (`hsh `), rather than the equipped shield's token.

Auditable branch bytes at `0x6FB5E800` (32 bytes):

```text
1f6a6557e809c3000085c074136a0055e8c3c6000085c07407b868736820eb02
```

Evidence:

- `ev_ff81b6c9c86654282cc4f74cc0535129371cd8bc9fd7abfbd3578f8f517148f9`: complete 105-byte neighborhood at `0x6FB5E7D0`.
- `ev_dd6eae05217d4e6dde9f513a3467b38ea85755d79d5d7aee7a87fd3c83360916`: decoded SH-component comparison.
- `ev_3a82d90eb1bca755b2aa0b805e0759b606a39355c1155b743421aea18ea61162`: decoded call to the state-check thunk `0x6FB6AB12`.
- `ev_3148dd1cb8ced19b885683c963e3a2fa7e2145a168f2605e1bec239473ee74cc`: surrounding dispatcher pseudocode. Ghidra failed to reconstruct its full jump table; the SH conclusion uses decoded instructions and bytes, not an assumed complete decompilation.

D2MOO cross-checks:

- `source/D2Common/src/D2States.cpp`: ordinal 10487 is `STATES_CheckState`.
- `source/D2Common/src/D2Inventory.cpp`: ordinal 10273 is `INVENTORY_GetEquippedShield`.
- `source/D2Common/src/Items/Items.cpp`: `ITEMS_CanBeEquipped` excludes broken/no-equip items.
- `source/D2Common/src/Units/Units.cpp`, `UNITS_UpdateBlockAnimRateAndVelocity`:
  effective FBR = `120 * FBR / (FBR + 120)` (integer arithmetic);
  speed = base AnimData speed times `(50 + effective FBR) / 100` normally,
  or `(100 + effective FBR) / 100` with Holy Shield; clamp to 1..32767.

Full local evidence export: `.gradle/rea-holyshield-20261101-evidence.json`
(ignored build evidence, 76 records). The important branch bytes and source
anchors above are retained in Git for verification on another machine.

## Corrections

- `CofLayerLoader` selects native HSH DCCs for the SH layer while the state
  and equipped shield are present. It observes synchronized state each frame,
  including an initial snapshot and changes while the COF/mode remains idle.
- The override is presentation-only. Expiry/removal restores the actual
  equipment component, and weapon/equipment changes continue using the
  synchronized component code. Known local broken/no-equip shield context
  suppresses HSH. Remote replicas without that runtime equipment context use
  their synchronized SH component as equipment evidence.
- `AnimDataResolver` applies the native block-rate formula on BL resolution
  in both authority and client worlds. Other modes retain their original rate.

## Verification and limits

`HolyShieldPresentationTest` uses the actual 1.10f MPQ COFs/DCC paths for
Paladin NU/A1/BL and checks initial-state display, state removal, reapplication,
shield removal/re-equipping, unusable-shield context, and preservation of the
equipment component. Asset loading is mocked; no GPU pixel parity is claimed.

`AnimDataResolverTest` checks native integer rates, bounds and an ECS replica
state lifecycle (ordinary block, Holy Shield block, removal, ordinary attack).
`CofModeTransitionTest` guards existing composite reload behavior.

This pass covers Holy Shield's shield selection and block animation rate.
Its cast overlay/compositing and the remaining Paladin skills are not yet
certified as original-client-equivalent.
