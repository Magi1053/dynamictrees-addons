"""Generate dtrubber latex item textures from bundled sources."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ITEM_DIR = ROOT / "bundled_assets" / "textures" / "item"
SRC_RAW = ITEM_DIR / "_src_raw_latex.png"
OUT_RAW = ITEM_DIR / "raw_latex.png"
SRC_COAGULATED = ITEM_DIR / "_src_coagulated_latex.png"
OUT_COAGULATED = ITEM_DIR / "coagulated_latex.png"
SRC_RUBBER_BALL = ITEM_DIR / "_src_rubber_ball.png"
OUT_RUBBER_BALL = ITEM_DIR / "rubber_ball.png"



def copy_raw_latex() -> None:
    if not SRC_RAW.is_file():
        raise FileNotFoundError(f"Missing source texture: {SRC_RAW}")
    shutil.copy2(SRC_RAW, OUT_RAW)


def copy_coagulated_latex() -> None:
    if not SRC_COAGULATED.is_file():
        raise FileNotFoundError(f"Missing source texture: {SRC_COAGULATED}")
    shutil.copy2(SRC_COAGULATED, OUT_COAGULATED)


def write_rubber_ball() -> None:
    if SRC_RUBBER_BALL.is_file():
        shutil.copy2(SRC_RUBBER_BALL, OUT_RUBBER_BALL)
        return

    if not SRC_COAGULATED.is_file():
        raise FileNotFoundError(f"Missing source texture: {SRC_COAGULATED}")

    with Image.open(SRC_COAGULATED).convert("RGBA") as image:
        pixels = image.load()
        for y in range(image.height):
            for x in range(image.width):
                red, green, blue, alpha = pixels[x, y]
                if alpha == 0:
                    continue

                # Keep the coagulated-latex shape while remapping to a dark rubber tone.
                gray = int((red * 0.299) + (green * 0.587) + (blue * 0.114))
                dark = max(18, int(gray * 0.42))
                pixels[x, y] = (dark, dark, min(255, dark + 8), alpha)

        image.save(OUT_RUBBER_BALL)


def main() -> None:
    ITEM_DIR.mkdir(parents=True, exist_ok=True)
    copy_raw_latex()
    copy_coagulated_latex()
    write_rubber_ball()
    print(f"Wrote {OUT_RAW.name}, {OUT_COAGULATED.name}, and {OUT_RUBBER_BALL.name}")


if __name__ == "__main__":
    main()
