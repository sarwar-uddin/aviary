#!/usr/bin/env python3
"""Aviary data pipeline: builds the Bangladesh bird dataset.

    python build.py                 # all stages (xeno-canto only if XC_API_KEY is set)
    python build.py --pack          # ...and download offline thumbnails
    python build.py export          # re-run just some stages on the saved work file
    python build.py --refresh       # ignore the HTTP cache

Stages: checklist titles wikidata extracts commons xenocanto curate [pack] export
Intermediate state lives in work/species.json so any stage can be re-run alone.
Output: out/aviary.db, out/web/*.json, out/media/thumbs/, work/report.txt
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from collections import Counter
from pathlib import Path

from aviary_data import checklist, export, media, wiki
from aviary_data.http import Http

HERE = Path(__file__).resolve().parent
WORK = HERE / "work" / "species.json"
OUT = HERE / "out"
CURATED = HERE / "curated" / "overrides.json"
STAGES = ["checklist", "titles", "wikidata", "extracts", "commons", "xenocanto", "curate", "pack", "export"]

SOURCES = {
    "checklist": "Wikipedia: List of birds of Bangladesh (Clements taxonomy), CC BY-SA 4.0",
    "facts": "Wikipedia (en, bn), CC BY-SA 4.0; Wikidata, CC0",
    "photos": "Wikimedia Commons; licence and author per file",
    "audio": "xeno-canto and Wikimedia Commons; licence and recordist per file",
}


def load_work():
    if not WORK.exists():
        sys.exit("no work file yet: run the 'checklist' stage first")
    return json.loads(WORK.read_text())


def save_work(data):
    WORK.parent.mkdir(parents=True, exist_ok=True)
    tmp = WORK.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=1))
    tmp.replace(WORK)


def curate(data, report):
    """Apply curated/overrides.json, then every other curated/*.json (alphabetical).
    Each file has the shape {"species": {"<id>": {field: value}}}."""
    files = [CURATED] + sorted(p for p in CURATED.parent.glob("*.json") if p != CURATED)
    merged: dict[str, dict] = {}
    for f in files:
        if not f.exists():
            continue
        for sid, fields in json.loads(f.read_text()).get("species", {}).items():
            merged.setdefault(sid, {}).update(fields)
    by_id = {s["id"]: s for s in data["species"]}
    for sid, fields in merged.items():
        s = by_id.get(sid)
        if not s:
            report.append(f"curated override for unknown species id: {sid}")
            continue
        s.update({k: v for k, v in fields.items() if not k.startswith("_")})


def summary(data):
    sp = data["species"]
    has = lambda f: sum(1 for s in sp if f(s))
    photos = has(lambda s: any(m["kind"] == "photo" for m in s.get("media", [])))
    audio = has(lambda s: any(m["kind"] == "audio" for m in s.get("media", [])))
    nc = sum(1 for s in sp for m in s.get("media", []) if not m.get("commercial_ok"))
    lines = [
        f"species            {len(sp)}  in {len(data['families'])} families",
        f"Bangla name        {has(lambda s: s.get('name_bn'))}",
        f"Wikidata match     {has(lambda s: s.get('wikidata_id'))}",
        f"English summary    {has(lambda s: s.get('summary_en'))}",
        f"Bangla summary     {has(lambda s: s.get('summary_bn'))}",
        f"photo              {photos}",
        f"audio              {audio}",
        f"non-commercial media files  {nc}",
        f"IUCN               {dict(Counter(s.get('iucn') or '?' for s in sp).most_common())}",
        f"BD status          {dict(Counter(export._status(s) for s in sp))}",
    ]
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("stages", nargs="*", help="stages to run (default: all): " + " ".join(STAGES))
    ap.add_argument("--pack", action="store_true", help="download thumbnails for the offline bundle")
    ap.add_argument("--refresh", action="store_true", help="ignore the HTTP cache")
    ap.add_argument("--limit", type=int, help="only process the first N species (for testing)")
    args = ap.parse_args()

    bad = [s for s in args.stages if s not in STAGES]
    if bad:
        ap.error(f"unknown stage(s) {bad}; choose from {STAGES}")
    stages = args.stages or [s for s in STAGES if s != "pack" or args.pack]
    http = Http(HERE / "cache", refresh=args.refresh)
    report: list[str] = []
    t0 = time.time()

    if "checklist" in stages:
        cur = json.loads(CURATED.read_text()) if CURATED.exists() else {}
        data = checklist.run(http, cur.get("checklist_fixes"), report)
        if args.limit:
            data["species"] = data["species"][:args.limit]
        save_work(data)
    data = load_work()
    sp = data["species"]

    steps = {
        "titles": lambda: wiki.resolve_titles(http, sp),
        "wikidata": lambda: wiki.wikidata(http, sp, report),
        "extracts": lambda: (wiki.extracts(http, sp, "en"), wiki.extracts(http, sp, "bn")),
        "commons": lambda: media.commons(http, sp, report),
        "xenocanto": lambda: media.xenocanto(http, sp, report),
        "curate": lambda: curate(data, report),
        "pack": lambda: print(f"  {media.pack(http, sp, OUT, report)} thumbnails"),
    }
    for name in STAGES:
        if name in stages and name in steps:
            t = time.time()
            print(f"[{name}] ...", flush=True)
            steps[name]()
            save_work(data)
            print(f"[{name}] done in {time.time() - t:.1f}s", flush=True)

    if "export" in stages:
        sources = dict(SOURCES, checklist_revid=data.get("source", {}).get("revid"))
        meta = export.run(OUT, data, sources)
        print(f"[export] {OUT / 'aviary.db'} ({meta['db_bytes'] / 1024:.0f} KB), {OUT / 'web'}")

    text = summary(data)
    rep = HERE / "work" / "report.txt"
    rep.write_text(text + "\n\n" + "\n".join(report) + "\n")
    print("\n" + text)
    print(f"\n{len(report)} notes in {rep}   (http: {http.misses} fetched, {http.hits} cached, {time.time() - t0:.0f}s)")


if __name__ == "__main__":
    main()
