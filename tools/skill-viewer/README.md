# Skill Effect Viewer

Standalone desktop shell for skill-effect experiments. It is deliberately
separate from `GameScreen`, so a test can be reset without entering a game or
loading a DS1/DT1 map.

## Run

```text
gradlew :tools:skill-viewer:run --args="--d2 C:\\Diablo II"
```

The `--d2` directory must contain the Diablo II 1.10f MPQ files. The viewer
creates its session logs under `tools/skill-viewer/logs/`.

## Current milestone

The first milestone provides the resource-loading/UI shell, seven class
presets, data-driven active-skill lists, a deterministic grey isometric arena,
monster/corpse placeholders, reset and resource-reload controls, and a
per-skill log. Combat/ECS integration will use these controls in the next
milestone; Java class hot replacement is intentionally not part of the tool.
