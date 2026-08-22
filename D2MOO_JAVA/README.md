# D2MOO_JAVA

Java port and compatibility layer for the D2MOO DRLG implementation used by
riiablo's Act I map generator.

The module is stored directly in this repository so every riiablo revision is
bound to the exact DRLG source revision it was tested against. Gradle includes
it as `:D2MOO_JAVA`; no sibling checkout is required.

The port is based on [D2MOO](https://github.com/ThePhrozenKeep/D2MOO). See
`LICENSE` for its MIT license.

Generated directories (`.gradle`, `build`, and `bin`) are intentionally not
versioned. From the riiablo repository root, build the module with:

```text
gradlew.bat :D2MOO_JAVA:build
```

The fixed-seed integration test is:

```text
gradlew.bat :core:test --tests com.riiablo.map.d2moo.Act1D2MOOLayoutBridgeTest
```

The Act I room layout and floor-tile export are operational. Native room-data
vertex splicing, complete secondary-border generation, and wall/shadow export
remain follow-up work.
