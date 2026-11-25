"""Stage 6: export.

Writes two views of the same dataset (see data/SCHEMA.md):

  out/aviary.db           SQLite, bundled in the Android app (offline core)
  out/web/                static JSON "API": manifest, index, families,
                          species/<id>.json. Host it anywhere (GitHub Pages,
                          Cloudflare) and both the app's online mode and a
                          future website read the same files.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1

DDL = """
CREATE TABLE meta (key TEXT NOT NULL PRIMARY KEY, value TEXT);
CREATE TABLE family (
  id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, common_name TEXT NOT NULL,
  order_name TEXT NOT NULL, blurb TEXT, sort INTEGER NOT NULL
);
CREATE TABLE species (
  id TEXT NOT NULL PRIMARY KEY, sort INTEGER NOT NULL,
  family_id TEXT NOT NULL REFERENCES family(id),
  name_en TEXT NOT NULL, name_bn TEXT, name_sci TEXT NOT NULL,
  bd_status TEXT NOT NULL,          -- regular | accidental | introduced | extirpated
  season TEXT,                      -- resident | winter | summer | passage | vagrant (curated)
  iucn TEXT,                        -- LC NT VU EN CR DD ...
  ebird_code TEXT, wikidata_id TEXT, wiki_en TEXT, wiki_bn TEXT,
  description_en TEXT, summary_en TEXT, summary_bn TEXT,
  facts TEXT                        -- JSON array of strings (curated)
);
CREATE TABLE media (
  id INTEGER NOT NULL PRIMARY KEY,
  species_id TEXT NOT NULL REFERENCES species(id),
  kind TEXT NOT NULL,               -- photo | audio
  rank INTEGER NOT NULL,
  source TEXT NOT NULL,             -- commons | xeno-canto
  url TEXT NOT NULL, thumb_url TEXT, small_url TEXT, page_url TEXT,
  author TEXT, license TEXT, license_url TEXT,
  commercial_ok INTEGER NOT NULL,
  local_path TEXT,                  -- set when the file ships in the offline pack
  extra TEXT                        -- JSON: width/height, call type, country...
);
CREATE INDEX idx_species_family ON species(family_id);
CREATE INDEX idx_media_species ON media(species_id, kind, rank);
"""

EXTRA_KEYS = ("width", "height", "mime", "file", "xc_id", "call_type", "country", "locality", "length", "quality")


def _status(s):
    for t in ("extirpated", "introduced", "accidental"):
        if t in s.get("bd_tags", []):
            return t
    return "regular"


def _media_sorted(s):
    order = {"photo": 0, "audio": 1}
    src = {"xeno-canto": 0, "commons": 1}  # xeno-canto recordings beat Commons audio
    return sorted(s.get("media", []), key=lambda m: (order[m["kind"]], src.get(m["source"], 9), m["rank"]))


def species_public(s: dict) -> dict:
    return {
        "id": s["id"], "sort": s["sort"], "family_id": s["family_id"],
        "name_en": s["name_en"], "name_bn": " ".join(s["name_bn"].split()) if s.get("name_bn") else None,
        "name_sci": s["name_sci"],
        "bd_status": _status(s), "season": s.get("season"), "iucn": s.get("iucn"),
        "ebird_code": s.get("ebird_code"), "wikidata_id": s.get("wikidata_id"),
        "wiki_en": s.get("wiki_en"), "wiki_bn": s.get("wiki_bn"),
        "description_en": s.get("description_en"),
        "summary_en": s.get("summary_en"), "summary_bn": s.get("summary_bn"),
        "facts": s.get("facts", []),
        "media": [{k: v for k, v in m.items() if v is not None} for m in _media_sorted(s)],
    }


def write_sqlite(path: Path, data: dict, meta: dict) -> None:
    if path.exists():
        path.unlink()
    db = sqlite3.connect(path)
    db.executescript(DDL)
    db.executemany("INSERT INTO meta VALUES (?,?)", [(k, str(v)) for k, v in meta.items()])
    db.executemany("INSERT INTO family VALUES (?,?,?,?,?,?)", [
        (f["id"], f["name"], f["common_name"], f["order"], f.get("blurb"), f["sort"]) for f in data["families"]])
    for s in data["species"]:
        p = species_public(s)
        db.execute("INSERT INTO species VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", (
            p["id"], p["sort"], p["family_id"], p["name_en"], p["name_bn"], p["name_sci"],
            p["bd_status"], p["season"], p["iucn"], p["ebird_code"], p["wikidata_id"],
            p["wiki_en"], p["wiki_bn"], p["description_en"], p["summary_en"], p["summary_bn"],
            json.dumps(p["facts"], ensure_ascii=False)))
        for m in p["media"]:
            extra = {k: m[k] for k in EXTRA_KEYS if k in m}
            db.execute("""INSERT INTO media (species_id, kind, rank, source, url, thumb_url, small_url,
                          page_url, author, license, license_url, commercial_ok, local_path, extra)
                          VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", (
                p["id"], m["kind"], m["rank"], m["source"], m["url"], m.get("thumb_url"), m.get("small_url"),
                m.get("page_url"), m.get("author"), m.get("license"), m.get("license_url"),
                int(m.get("commercial_ok", False)), m.get("local_path"),
                json.dumps(extra, ensure_ascii=False) if extra else None))
    db.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")
    db.commit()
    db.execute("VACUUM")
    db.close()


def write_web(out: Path, data: dict, meta: dict) -> None:
    web = out / "web"
    if web.exists():
        shutil.rmtree(web)
    (web / "species").mkdir(parents=True)
    dump = lambda p, obj: p.write_text(json.dumps(obj, ensure_ascii=False, separators=(",", ":")))

    index = []
    for s in data["species"]:
        p = species_public(s)
        dump(web / "species" / f"{p['id']}.json", p)
        photo = next((m for m in p["media"] if m["kind"] == "photo"), None)
        index.append({
            "id": p["id"], "family_id": p["family_id"], "en": p["name_en"], "bn": p["name_bn"],
            "sci": p["name_sci"], "status": p["bd_status"], "season": p["season"], "iucn": p["iucn"],
            "thumb": photo.get("small_url") if photo else None,
            "has_audio": any(m["kind"] == "audio" for m in p["media"]),
        })
    dump(web / "index.json", index)
    dump(web / "families.json", [{k: f[k] for k in ("id", "name", "common_name", "order", "blurb", "sort")}
                                 for f in data["families"]])
    dump(web / "manifest.json", meta)


def run(out: Path, data: dict, sources: dict) -> dict:
    out.mkdir(parents=True, exist_ok=True)
    meta = {
        "schema_version": SCHEMA_VERSION,
        "data_version": datetime.now(timezone.utc).strftime("%Y%m%d%H%M"),
        "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "species_count": len(data["species"]),
        "family_count": len(data["families"]),
        "sources": json.dumps(sources, ensure_ascii=False),
    }
    db_path = out / "aviary.db"
    write_sqlite(db_path, data, meta)
    meta["db_sha256"] = hashlib.sha256(db_path.read_bytes()).hexdigest()
    meta["db_bytes"] = db_path.stat().st_size
    write_web(out, data, meta)
    return meta
