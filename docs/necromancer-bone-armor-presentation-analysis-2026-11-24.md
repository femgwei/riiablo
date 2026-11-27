# Necromancer Bone Armor presentation analysis (2026-11-24)

**Correction (2026-11-26):** The shared-setfunc8 conclusion below was wrong.
Retail 1.10F Bone Armor uses setfunc6/remfunc3, and the setfunc8-only selector
returned no layers for it. The earlier test checked only the count formula.
The corrected emergence/null-timer/front-back chain, implementation and evidence
are documented in `necromancer-bone-armor-animation-chain-analysis-2026-11-26.md`.
The original historical text follows; it is not a current validation claim.

Bone Armor (skill 68) uses the same native `States.txt` set-function as
Cyclone Armor: the client divides the synchronized `bonearmor` pool into
three thirds and renders paired front/back shield overlays. The prior client
implementation restricted that presentation helper to `CYCLONEARMOR`, so a
Bone Armor state could carry correct authoritative absorption data while
showing no shield layers.

The helper and reconciliation path now accept both `CYCLONEARMOR` and
`BONEARMOR`; state id remains the overlay ownership key, so refresh, depletion,
and expiry remove only the correct state layers. A focused regression covers
three/two/one-stage thresholds and expired-state cleanup.

Original runtime was not started (`runtime_status=not_run_in_class_batch`);
the change is based on native set-function evidence and the existing
server/client state snapshot path, without claiming pixel or GPU equivalence.
