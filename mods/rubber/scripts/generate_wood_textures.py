"""Generate derived rubber wood textures.

rubber_log.png is a bundled source texture (do not overwrite here).
Only rubber_stripped_log and rubber_planks are generated:
- stripped side: stripped jungle log retinted toward rubber_log_top (green cleanup)
- planks: birch planks retinted toward rubber_log_top
"""

from __future__ import annotations

import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
BLOCK = ROOT / "bundled_assets" / "textures" / "block"
VANILLA = {
    "birch_planks.png": "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.1/assets/minecraft/textures/block/birch_planks.png",
    "stripped_jungle_log.png": "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.1/assets/minecraft/textures/block/stripped_jungle_log.png",
}


def avg_rgb(path: Path) -> tuple[float, float, float]:
    img = Image.open(path).convert("RGBA")
    px = [p[:3] for p in img.getdata() if p[3] > 0]
    if not px:
        return (128.0, 128.0, 128.0)
    n = len(px)
    return (sum(p[0] for p in px) / n, sum(p[1] for p in px) / n, sum(p[2] for p in px) / n)


def ensure_vanilla_sources() -> None:
    BLOCK.mkdir(parents=True, exist_ok=True)
    for filename, url in VANILLA.items():
        dest = BLOCK / f"_src_{filename}"
        if dest.exists():
            continue
        print(f"Fetching {filename}...")
        with urllib.request.urlopen(url, timeout=30) as response:
            dest.write_bytes(response.read())


def tint_pixel(r: int, g: int, b: int, src_avg: tuple[float, float, float], dst_avg: tuple[float, float, float], green_pull: float) -> tuple[int, int, int]:
    sr, sg, sb = src_avg
    tr, tg, tb = dst_avg
    if sr < 1:
        sr = 1
    if sg < 1:
        sg = 1
    if sb < 1:
        sb = 1

    nr = int(max(0, min(255, r * (tr / sr))))
    ng = int(max(0, min(255, g * (tg / sg))))
    nb = int(max(0, min(255, b * (tb / sb))))

    # Pull green toward the warm log-top average to remove leftover jungle green cast.
    target_g = int((tr + tb) / 2)
    ng = int(ng * (1.0 - green_pull) + target_g * green_pull)
    return nr, ng, nb


def tint_image(source: Path, target: Path, reference: Path, green_pull: float = 0.45) -> None:
    src_avg = avg_rgb(source)
    dst_avg = avg_rgb(reference)
    img = Image.open(source).convert("RGBA")
    out = Image.new("RGBA", img.size)
    pixels = []
    for r, g, b, a in img.getdata():
        if a == 0:
            pixels.append((0, 0, 0, 0))
            continue
        nr, ng, nb = tint_pixel(r, g, b, src_avg, dst_avg, green_pull)
        pixels.append((nr, ng, nb, a))
    out.putdata(pixels)
    out.save(target)
    print(f"Wrote {target.name} (src {src_avg} -> ref {dst_avg}, green_pull={green_pull})")


def main() -> None:
    ensure_vanilla_sources()
    top = BLOCK / "rubber_log_top.png"
    if not top.exists():
        raise SystemExit(f"Missing {top}")
    if not (BLOCK / "rubber_log.png").exists():
        raise SystemExit(f"Missing bundled source {BLOCK / 'rubber_log.png'}")

    tint_image(BLOCK / "_src_stripped_jungle_log.png", BLOCK / "rubber_stripped_log.png", top, green_pull=0.55)
    tint_image(BLOCK / "_src_birch_planks.png", BLOCK / "rubber_planks.png", top, green_pull=0.35)

    # Thick branch ring texture uses the same end grain as primitive logs.
    top_img = Image.open(top).convert("RGBA")
    top_img.save(BLOCK / "rubber_log_top_thick.png")


if __name__ == "__main__":
    main()
