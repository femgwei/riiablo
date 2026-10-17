# Scarab command boundary checks — 2026-10-18

This batch adds regression coverage only; production AI was not changed.

D2MOO Fn020 command 1 reads the current AiTickParam target, not a target saved in the command. The melee branch requires a configured Skill1; otherwise it requests boosted approach and retains the command on success. Leader command generation is a probability gate that falls through to ordinary decision logic when declined.

Added checks:
- An attack marker containing an invalid saved entity ID still uses the current selected target for Jab, pops the current marker and consumes no decision RNG.
- An actor with no configured Skill1 approaches even at melee distance, applies +100 percent bonus, retains its marker and consumes no decision RNG. The fixture substitutes only this actor's MonStats row reference; shared table entries are not mutated.
- A qualified leader with zero command chance does not distribute a command and then consumes the ordinary attack chance, across all difficulties. The exact subsequent native RNG value is checked.

No pre-fix failure is claimed: these verify already implemented command behavior against the native branch conditions. They supplement earlier queue, ownership, distance and pursuit tests.

Investigation limit: a missing target does not itself prove a native command must be freed. AiTactics MoveToTarget can pass a null unit target as coordinates into the general mode-change path, so target-loss command lifetime requires evidence about that path before changing Java semantics. Existing no-target behavior remains unmodified. Native event/control flags, inactive-owner lifecycle and Jab animation remain partial. No new arena/client run was performed, and the original real-map immobility remains unconfirmed as resolved.

The focused Scarab/Baboon regression run passed 60 tests, including all three new boundary methods. No full suite claim.
