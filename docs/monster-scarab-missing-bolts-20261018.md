# Ordinary scarab missing charged bolts

User game.log evidence: scarab1 entity 157 takes 9 damage (43 -> 34)
at line 14147 and enters GH at 14146. Entity 158 takes multiple surviving
arrow hits, including 49 -> 39 at 12795. No buglightning appears in this log.
Thus lethal freezing-arrow hits elsewhere do not explain all missing bursts.

AIStepper required CHARGED_BOLTS in the unique-affix mask, but only explicit
MonUMod conversion assigned that bit. Ordinary scarabs had no such assignment.
Monster.set now captures BaseId scarab1's native charged-bolt ability separately
from rank/unique affixes, like its existing native self-resurrection field.
This survives setRank and resets when the component is reused for another base
class. D2MOO MonsterSpawn.cpp's reconstructed assignment around 1083 identifies
MONSTER_SCARAB1 -> MONUMOD_CHARGEDBOLTS (that source section is commented out).

ScarabNativeAbilityTest covers ordinary rank, no affixes, actual damage handler
to emitter, duplicate suppression and component reset. Existing bolt snapshot
tests cover emission/damage data separately. SCARAB_BOLTS logs created count to
distinguish trigger failure from missile creation/presentation failure.

Limit: the existing positive-damage/two-frame cooldown adapter remains; this
fix does not implement the native delayed mode callback or charged paths, and
does not establish live-client visual acceptance.
