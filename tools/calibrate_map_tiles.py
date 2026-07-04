#!/usr/bin/env python3
"""Empirically calibrate marker projection per baked map tile.

The frontend projects markers assuming each 3840x3840 tile covers
(3840 x MapScaleValue) world units centred on the gameplay-bounds offset.
That holds for most bakes, but a handful (Speranova, Altipetra, the Iris
Hamlet boss variant, ...) were rendered with a different framing, so pins
drift off the terrain.

This script fits the framing EMPIRICALLY per map: it tries three candidate
projections — (a) the default, (b) shifted so the frame centre matches the
terrain-art bbox centre, (c) shifted+scaled so the gameplay-bounds rect maps
onto the art bbox — and scores each by the fraction of that map's markers
(exits/pickups/spawn points, culled to gameplay bounds like the app does)
that land on non-background pixels. A non-default candidate is adopted only
when it beats the default by a clear margin, so maps whose art is genuinely
sparse (destroyed-city variants) are left alone.

Writes data/curated/map_tile_calibration.json:
    { "<map_guid>": {"centerX","centerZ","spanX","spanZ"} }
Backend MapService merges these into the served bounds; the frontend prefers
them when present. Idempotent — rerun after any tile/bounds change.
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TILES = ROOT / "data" / "assets" / "maps"
OUT = ROOT / "data" / "curated" / "map_tile_calibration.json"

PAD = 0.04           # same bounds-cull pad as the frontend
WIN = 15             # half-window (px) sampled around a pin
MIN_PINS = 4         # too few pins -> no reliable signal, skip
ADOPT_MARGIN = 0.10  # optimum must beat default by this much
DEFAULT_OK = 0.90    # maps scoring at/above this are left untouched


def art_mask(img):
    a = np.array(img.convert("RGBA"))
    if (a[..., 3] < 10).any():
        return a[..., 3] > 10
    corner = a[0, 0, :3].astype(int)
    return np.abs(a[..., :3].astype(int) - corner).sum(axis=2) > 30


def main():
    maps = json.load(open(ROOT / "data/seed/maps.json"))
    v2 = {m["mapGuid"]: m for m in json.load(open(ROOT / "data/seed/map_placements_v2.json"))}

    out, report = {}, []
    for m in maps:
        guid = m["guid"]
        tile = TILES / guid / "tile.png"
        geo = v2.get(guid)
        msv = (m.get("raw") or {}).get("MapScaleValue") or 0
        if not tile.is_file() or not geo or not geo.get("bounds") or msv <= 0:
            continue
        b = geo["bounds"]
        span = 3840.0 * msv
        sx, sz = b["sizeX"], b["sizeZ"]
        ox, oz = b["offsetX"], b["offsetZ"]

        pins = []
        for kind in ("exits", "pickups", "spawnPoints"):
            for e in geo.get(kind) or []:
                # v2 stores map-local positions as flat x/z fields
                if e.get("x") is None or e.get("z") is None:
                    continue
                # the app culls pins outside the padded gameplay bounds
                if not (ox - sx / 2 - sx * PAD <= e["x"] <= ox + sx / 2 + sx * PAD):
                    continue
                if not (oz - sz / 2 - sz * PAD <= e["z"] <= oz + sz / 2 + sz * PAD):
                    continue
                pins.append((e["x"], e["z"]))
        if len(pins) < MIN_PINS:
            continue

        img = Image.open(tile)
        mask = art_mask(img)
        H, W = mask.shape
        ys, xs = np.where(mask)
        bx0, bx1 = xs.min() / W, xs.max() / W
        by0, by1 = ys.min() / H, ys.max() / H

        def score(cx, cz, spanX, spanZ):
            on = 0
            for (x, z) in pins:
                fx = (x - (cx - spanX / 2)) / spanX
                fy = 1 - (z - (cz - spanZ / 2)) / spanZ
                if not (0 <= fx <= 1 and 0 <= fy <= 1):
                    continue
                px, py = int(fx * (W - 1)), int(fy * (H - 1))
                w = mask[max(0, py - WIN):py + WIN, max(0, px - WIN):px + WIN]
                if w.size and w.any():
                    on += 1
            return on / len(pins)

        base = score(ox, oz, span, span)
        if base >= DEFAULT_OK:
            continue

        # Coarse-to-fine grid search over a uniform scale factor + centre shift.
        # The bakes that misframe do so with a similarity transform (same aspect),
        # so (k, dx, dz) is the right model; independent x/z scales overfit.
        best = (base, 1.0, 0.0, 0.0)
        for step, (ks, ds) in enumerate([(np.arange(0.6, 1.85, 0.05), np.arange(-0.2, 0.21, 0.05)),
                                         (None, None)]):
            if step == 1:  # refine around the coarse winner
                _, k0, dx0, dz0 = best
                ks = np.arange(k0 - 0.06, k0 + 0.061, 0.0125)
                ds = np.arange(-0.04, 0.041, 0.01)
                base_dx, base_dz = dx0, dz0
            else:
                base_dx = base_dz = 0.0
            for k in ks:
                s = span * k
                for fx in ds:
                    for fz in ds:
                        dx, dz = base_dx + fx * span, base_dz + fz * span
                        sc = score(ox + dx, oz + dz, s, s)
                        if sc > best[0]:
                            best = (sc, float(k), float(dx), float(dz))

        sc, k, dx, dz = best
        if sc - base >= ADOPT_MARGIN:
            s = span * k
            out[guid] = {"centerX": round(ox + dx, 3), "centerZ": round(oz + dz, 3),
                         "spanX": round(s, 3), "spanZ": round(s, 3)}
            report.append((m["internal_name"], f"calibrated k={k:.2f} dx={dx:.0f} dz={dz:.0f}",
                           {"default": base, "best": sc}))
        else:
            report.append((m["internal_name"], "left alone", {"default": base, "best": sc}))

    OUT.write_text(json.dumps(out, indent=1))
    print(f"calibrated {len(out)} maps -> {OUT.relative_to(ROOT)}")
    for name, choice, s in report:
        print(f"  {name}: {choice}  " +
              " ".join(f"{k}={v:.2f}" for k, v in s.items()))


if __name__ == "__main__":
    main()
