# Necromancer client fixes (2026-11-01)

## Changes

- Corpse skills can hover dead monsters after DeathHandler removes Selectable. Shared corpse-skill classification covers Raise Skeleton/Mage, Revive, Corpse/Poison Explosion and the other native corpse callbacks; consumed/fading corpses remain excluded.
- Actioneer permits explosion targets through the animation keyframe rather than rejecting them as dead combat targets.
- Clay/Blood Golem death detaches the pet from its owner without deleting the corpse. Corpses cannot be reused for corpse skills. D2MOO SUnitDmg.cpp / PlayerPets.cpp removes the pet-list entry without removing the monster unit.
- Right-click targeting no longer rejects a new live target because a previous attack target died. This also affects Bone Prison targeting. Poison Dagger uses melee approach and still requires a dagger.
- Bone Spear trail, curse area particles and finite explosion presentation are described in [native visual evidence](necromancer-client-area-trail-2026-11-01.md). Presentation changes are already present in commit 77143420a alongside another task's changes.

## Verification and handoff

JDK 17, explicit `-Pd2Home=G:\BaiduNetdiskDownload\Diablo II 1.10F`: 147 targeted tests ran, 146 passed. The existing Poison Dagger successful-hit test fails only its durability assertion: its helper seeds MathUtils.random, while ItemDurabilityManager now uses a separate NativeRng stream. Its damage and poison assertions pass. This test fixture mismatch was not changed as part of the client fixes.

No in-game visual verification was performed. Verify all seven reported categories on the other computer, especially Bone Prison after killing another target, consumed-corpse rejection, dagger approach and attack, golem death poses, and curse area/trail density. Native animation loop-count mapping is not yet fully validated; these changes do not claim pixel-identical rendering.
