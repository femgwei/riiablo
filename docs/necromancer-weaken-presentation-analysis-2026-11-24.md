# Necromancer Weaken presentation analysis (2026-11-24)

Weaken (skill 72) was the next uncovered class-batch entry after Teeth. Its
client path is the native curse-area carrier shared by Amplify Damage: an
invisible `cursecenter` controller stores the target radius and child missile
ID, then emits late area children from the authoritative lifetime. The child
effects are presentation-only and are removed by the normal missile lifecycle.

Added `weakenUsesTheSameAuthoritativeCurseControllerAndCleansChildren` to
exercise the real Skills.txt row through `SkillCastHandler`, verify the
controller and child resource, and cover the full lifetime cleanup boundary.
This is a client lifecycle regression; original runtime was not started and
pixel/GPU equivalence is not claimed (`runtime_status=not_run_in_class_batch`).
