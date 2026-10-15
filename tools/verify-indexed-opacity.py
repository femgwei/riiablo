"""Render the production palette shader with all source indices and PL2 light rows.

Requires moderngl. Supply ACT2 Pal.PL2 and pal.dat extracted from the target MPQ:
  python tools/verify-indexed-opacity.py --pl2 <Pal.PL2> --palette <pal.dat>
Uses a hidden standalone GL context; no project compilation or saves are needed.
"""

import argparse
import struct
from pathlib import Path

import moderngl


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pl2", required=True, type=Path)
    parser.add_argument("--palette", required=True, type=Path)
    args = parser.parse_args()
    pl2 = args.pl2.read_bytes()
    palette = args.palette.read_bytes()
    if len(pl2) < 1024 + 32 * 256 or len(palette) != 768:
        parser.error("Expected a native PL2 with 32 shadow rows and a 768-byte palette")
    root = Path(__file__).resolve().parents[1]
    ctx = moderngl.create_standalone_context(require=210)
    program = ctx.program(
        vertex_shader=(root / "assets/shaders/indexpalette3.vert").read_text(),
        fragment_shader=(root / "assets/shaders/indexpalette3.frag").read_text(),
    )
    # GL_INTENSITY, used by PaletteIndexedPixmap, replicates the index to alpha.
    source = ctx.texture((256, 1), 1, bytes(range(256)))
    source.swizzle = "RRRR"
    light = ctx.texture((256, 32), 1, pl2[1024:1024 + 32 * 256])
    light.swizzle = "RRRR"
    rgba = bytes(
        component
        for i in range(256)
        for component in (palette[3*i + 2], palette[3*i + 1], palette[3*i],
                          0 if i == 0 else 255)
    )
    colors = ctx.texture((256, 1), 4, rgba)
    for texture in (source, light, colors):
        texture.filter = (moderngl.NEAREST, moderngl.NEAREST)
        texture.repeat_x = texture.repeat_y = False
    source.use(0)
    colors.use(1)
    # The colormap is disabled but gets a separate sampler unit as in the client.
    light.use(2)
    light.use(3)
    for name, value in (("u_texture", 0), ("ColorTable", 1), ("ColorMap", 2),
                        ("LightMap", 3), ("colormapId", 0), ("lightCount", 0),
                        ("lightAnchorEnabled", 0), ("gamma", 1.0)):
        program[name].value = value
    program["u_projTrans"].write(struct.pack("16f", *(
        1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1)))
    vertices = ctx.buffer(struct.pack("32f", *(
        -1, -1, 1, 1, 1, 1, 0, 0,
         1, -1, 1, 1, 1, 1, 1, 0,
        -1,  1, 1, 1, 1, 1, 0, 1,
         1,  1, 1, 1, 1, 1, 1, 1)))
    vao = ctx.vertex_array(program, [(vertices, "2f 4f 2f",
                                      "a_position", "a_color", "a_texCoord0")])
    target = ctx.simple_framebuffer((256, 1), components=4)
    target.use()
    ctx.disable(moderngl.BLEND)
    failures = []
    comparisons = 0
    for mode in (1, 4, 7, 8, 9, 10, 13, 16):
        program["blendMode"].value = mode
        # Include unlit/daylight plus every PL2 row, including deep night.
        for row in (-1, *range(32)):
            program["lightingEnabled"].value = int(row >= 0)
            program["ambientIntensity"].value = (
                ((row * 8 + 4) / 255.0) ** (1 / 0.80) if row >= 0 else 1.0)
            target.clear()
            vao.render(moderngl.TRIANGLE_STRIP)
            pixels = target.read(components=4, alignment=1)
            missing = [i for i in range(256)
                       if pixels[4*i + 3] != (0 if i == 0 else 255)]
            comparisons += 256
            if missing:
                failures.append((mode, row, missing))
    print("GPU:", ctx.info["GL_RENDERER"])
    print(f"Opacity comparisons={comparisons}, failed draws={len(failures)}")
    for mode, row, missing in failures[:12]:
        print(f"  blend={mode}, PL2 row={row}, wrong source indices={missing[:20]}")
    if failures:
        raise SystemExit(1)
    print("PASS: index-zero stays transparent; all authored pixels survive lighting")


if __name__ == "__main__":
    main()
