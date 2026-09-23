#!/usr/bin/env python3
"""Generate the app's launcher icons from tools/app_icon_source.png.

Produces, for each density:
  mipmap-<dpi>/ic_launcher.png                  legacy full-bleed icon (API < 26)
  mipmap-<dpi>/ic_launcher_adaptive_back.png    adaptive background (solid green)
  mipmap-<dpi>/ic_launcher_adaptive_fore.png    adaptive foreground (design in safe zone)

The source is a rounded-square composite with transparent corners. For the
adaptive icon the composite is scaled to 72% of the 108dp canvas and centered,
keeping the artwork inside the launcher mask safe zone while the matching solid
background fills the rest.
"""

import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SRC = os.path.join(HERE, "app_icon_source.png")

# Legacy icon sizes per density (dp -> px).
DENSITY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
# Adaptive canvas is 108dp -> px.
ADAPTIVE_SCALE = 0.72
# Solid fill for the adaptive background, matching the composite's top tone.
BACKGROUND = (18, 29, 21, 255)


def main() -> None:
    composite = Image.open(SRC).convert("RGBA")

    for dpi, size in DENSITY_SIZES.items():
        res_dir = os.path.join(ROOT, "app", "src", "main", "res", f"mipmap-{dpi}")

        # Legacy full-bleed icon.
        composite.resize((size, size), Image.LANCZOS).save(
            os.path.join(res_dir, "ic_launcher.png")
        )

        # Adaptive layers on the 108dp canvas.
        canvas = int(size * 2.25)
        Image.new("RGBA", (canvas, canvas), BACKGROUND).save(
            os.path.join(res_dir, "ic_launcher_adaptive_back.png")
        )
        inner = int(canvas * ADAPTIVE_SCALE)
        scaled = composite.resize((inner, inner), Image.LANCZOS)
        foreground = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        offset = (canvas - inner) // 2
        foreground.paste(scaled, (offset, offset), scaled)
        foreground.save(os.path.join(res_dir, "ic_launcher_adaptive_fore.png"))

        print(f"{dpi}: legacy {size}px, adaptive {canvas}px")


if __name__ == "__main__":
    main()
