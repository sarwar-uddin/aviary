"""Stages 4-5: media with licences.

  commons     photo + audio files named by Wikidata -> URLs, author, licence
  xenocanto   (optional, needs XC_API_KEY) best calls/songs per species
  pack        (optional) download small thumbnails for the offline bundle

Every media record carries author + licence + source page, because the app
and website must credit them (CC BY / BY-SA) and we need to know which ones
are non-commercial (NC) before anything is ever sold.
"""
from __future__ import annotations

import os
from pathlib import Path

from .http import Http
from .util import chunks, strip_html, strip_tracking

COMMONS = "https://commons.wikimedia.org/w/api.php"
XC = "https://xeno-canto.org/api/3/recordings"
LARGE, SMALL = 960, 330
MAX_PHOTOS = 3
MAX_CALLS = 3
NEAR_BD = ["Bangladesh", "India", "Myanmar", "Nepal", "Bhutan"]


def commercial_ok(license_text: str) -> bool:
    t = (license_text or "").lower().replace(" ", "-")
    return "-nc" not in t and "noncommercial" not in t


def _imageinfo(http: Http, files: list[str], width: int) -> dict[str, dict]:
    out = {}
    for batch in chunks(files, 50):
        d = http.get_json(COMMONS, {
            "action": "query", "prop": "imageinfo",
            "titles": "|".join("File:" + f for f in batch),
            "iiprop": "url|size|mime|extmetadata", "iiurlwidth": str(width),
            "iiextmetadatafilter": "Artist|Credit|LicenseShortName|LicenseUrl|AttributionRequired|UsageTerms",
            "format": "json", "formatversion": "2",
        })["query"]
        back = {n["to"]: n["from"] for n in d.get("normalized", [])}
        for p in d.get("pages", []):
            ii = (p.get("imageinfo") or [None])[0]
            if not ii:
                continue
            name = back.get(p["title"], p["title"])
            out[name.removeprefix("File:")] = ii
    return out


def commons(http: Http, species: list[dict], report: list) -> None:
    photos = sorted({f for s in species for f in s.get("wd_images", [])[:MAX_PHOTOS]})
    audio = sorted({f for s in species for f in s.get("wd_audio", [])})
    big = _imageinfo(http, photos, LARGE)
    small = _imageinfo(http, photos, SMALL)
    snd = _imageinfo(http, audio, 0) if audio else {}

    def record(f, ii, kind, rank, thumb=None, small_url=None):
        m = ii.get("extmetadata", {})
        val = lambda k: (m.get(k) or {}).get("value")
        lic = val("LicenseShortName") or val("UsageTerms") or ""
        return {
            "kind": kind, "rank": rank, "source": "commons",
            "url": strip_tracking(ii.get("url")),
            "thumb_url": strip_tracking(thumb),
            "small_url": strip_tracking(small_url),
            "page_url": ii.get("descriptionurl"),
            "author": strip_html(val("Artist")) or strip_html(val("Credit")) or None,
            "license": lic or None,
            "license_url": val("LicenseUrl"),
            "commercial_ok": commercial_ok(lic),
            "width": ii.get("width"), "height": ii.get("height"),
            "mime": ii.get("mime"),
            "file": f,
        }

    for s in species:
        media = [m for m in s.get("media", []) if m["source"] != "commons"]
        for i, f in enumerate(s.get("wd_images", [])[:MAX_PHOTOS]):
            ii = big.get(f)
            if not ii:
                report.append(f"commons photo not found: {f} ({s['name_en']})")
                continue
            sm = small.get(f, {})
            media.append(record(f, ii, "photo", i, ii.get("thumburl"), sm.get("thumburl") or ii.get("thumburl")))
        for i, f in enumerate(s.get("wd_audio", [])):
            ii = snd.get(f)
            if ii:
                media.append(record(f, ii, "audio", 100 + i))
        s["media"] = media
        if not any(m["kind"] == "photo" for m in media):
            report.append(f"no photo: {s['name_en']} ({s['name_sci']})")


# ---------------------------------------------------------------- xeno-canto
def _xc_pick(recs: list[dict]) -> list[dict]:
    def score(r):
        cnt = r.get("cnt", "")
        near = NEAR_BD.index(cnt) if cnt in NEAR_BD else len(NEAR_BD)
        q = "ABCDE".find(r.get("q", "E")) if r.get("q") in list("ABCDE") else 5
        return (q, near)
    picked, types = [], set()
    for r in sorted(recs, key=score):
        t = (r.get("type") or "").split(",")[0].strip().lower()
        if t in types and len(recs) > MAX_CALLS:
            continue  # prefer variety: a song, a call, a flight call...
        types.add(t)
        picked.append(r)
        if len(picked) == MAX_CALLS:
            break
    return picked


def xenocanto(http: Http, species: list[dict], report: list) -> bool:
    key = os.environ.get("XC_API_KEY")
    if not key:
        report.append("xeno-canto skipped: set XC_API_KEY (free key at https://xeno-canto.org/account)")
        return False
    for s in species:
        gen, sp = s["name_sci"].split(" ")[:2]
        recs = []
        for query in (f"gen:{gen} sp:{sp} q:A", f"gen:{gen} sp:{sp}"):
            d = http.get_json(XC, {"query": query, "per_page": "100"}, secret={"key": key})
            recs = d.get("recordings", [])
            if len(recs) >= MAX_CALLS:
                break
        media = [m for m in s.get("media", []) if m["source"] != "xeno-canto"]
        for i, r in enumerate(_xc_pick(recs)):
            lic = r.get("lic") or ""
            if lic.startswith("//"):
                lic = "https:" + lic
            media.append({
                "kind": "audio", "rank": i, "source": "xeno-canto",
                "url": r.get("file"),
                "page_url": f"https://xeno-canto.org/{r.get('id')}",
                "author": r.get("rec"),
                "license": _cc_name(lic), "license_url": lic,
                "commercial_ok": commercial_ok(lic),
                "xc_id": r.get("id"), "call_type": r.get("type"),
                "country": r.get("cnt"), "locality": r.get("loc"),
                "length": r.get("length"), "quality": r.get("q"),
            })
        s["media"] = media
        if not recs:
            report.append(f"no xeno-canto recordings: {s['name_en']} ({s['name_sci']})")
    return True


def _cc_name(url: str) -> str:
    # https://creativecommons.org/licenses/by-nc-sa/4.0/ -> CC BY-NC-SA 4.0
    parts = [p for p in url.split("/") if p]
    try:
        i = parts.index("licenses")
        return f"CC {parts[i + 1].upper()} {parts[i + 2]}"
    except (ValueError, IndexError):
        return url


# ---------------------------------------------------------------- offline pack
def pack(http: Http, species: list[dict], out_dir: Path, report: list) -> int:
    """Download the first photo's small thumbnail per species into out/media/thumbs."""
    n = 0
    for s in species:
        photos = sorted((m for m in s.get("media", []) if m["kind"] == "photo"), key=lambda m: m["rank"])
        if not photos or not photos[0].get("small_url"):
            continue
        m = photos[0]
        ext = Path(m["small_url"].split("?")[0]).suffix.lower() or ".jpg"
        rel = Path("media/thumbs") / f"{s['id']}{ext}"
        if http.download(m["small_url"], out_dir / rel):
            m["local_path"] = str(rel)
            n += 1
        else:
            report.append(f"thumbnail download failed: {s['id']}")
    return n
