# Skill Effect Viewer

Standalone desktop shell for skill-effect experiments. It is deliberately
separate from `GameScreen`, so a test can be reset without entering a game or
loading a DS1/DT1 map.

## Run

```text
gradlew :tools:skill-viewer:run
```

The viewer uses the same installation discovery as the main Riiablo client
(Windows registry / platform-specific search, then the `riiablo` fallback
directory under the user home). `--d2 <path>` remains available as an optional
override for testing another Diablo II 1.10f installation. Session logs are
created under `tools/skill-viewer/logs/` (the tool's local `logs` directory).

## Current milestone

The first milestone provides the resource-loading/UI shell, seven class
presets, data-driven active-skill lists, a deterministic grey isometric arena,
monster/corpse placeholders, reset and resource-reload controls, and a
per-skill log. Combat/ECS integration will use these controls in the next
milestone; Java class hot replacement is intentionally not part of the tool.
