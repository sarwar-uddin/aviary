"""Stages 2-3: Wikipedia + Wikidata enrichment.

  resolve_titles  list links -> canonical enwiki titles (follows redirects)
  wikidata        enwiki title (fallback: scientific name, P225) -> QID, Bangla
                  name, IUCN status, Commons photos (P18) + audio (P51), eBird
                  code, bnwiki article
  extracts        intro paragraphs from en + bn Wikipedia (CC BY-SA)
"""
from __future__ import annotations

from collections import defaultdict

from .http import Http
from .util import IUCN, chunks, clean_text, commons_file_from_url

WP = "https://{lang}.wikipedia.org/w/api.php"
WDQS = "https://query.wikidata.org/sparql"


# ---------------------------------------------------------------- titles
def resolve_titles(http: Http, species: list[dict]) -> None:
    titles = sorted({s["wiki_en_link"] for s in species if s.get("wiki_en_link")})
    canon: dict[str, str | None] = {}
    for batch in chunks(titles, 50):
        d = http.get_json(WP.format(lang="en"), {
            "action": "query", "titles": "|".join(batch), "redirects": "1",
            "format": "json", "formatversion": "2",
        })["query"]
        norm = {n["from"]: n["to"] for n in d.get("normalized", [])}
        redir = {r["from"]: r["to"] for r in d.get("redirects", [])}
        missing = {p["title"] for p in d.get("pages", []) if p.get("missing")}
        for t in batch:
            c = norm.get(t, t)
            c = redir.get(c, c)
            canon[t] = None if c in missing else c
    for s in species:
        s["wiki_en"] = canon.get(s.get("wiki_en_link"))


# ---------------------------------------------------------------- wikidata
_SELECT = """
SELECT ?key ?item ?name ?bn ?iucnLabel ?img ?audio ?ebird ?bnTitle ?enTitle WHERE {
  %(values)s
  %(match)s
  OPTIONAL { ?item wdt:P225 ?name }
  OPTIONAL { ?item rdfs:label ?bn FILTER(LANG(?bn) = "bn") }
  OPTIONAL { ?item wdt:P141 ?iucn }
  OPTIONAL { ?item wdt:P18 ?img }
  OPTIONAL { ?item wdt:P51 ?audio }
  OPTIONAL { ?item wdt:P3444 ?ebird }
  OPTIONAL { ?bnw schema:about ?item; schema:isPartOf <https://bn.wikipedia.org/>; schema:name ?bnTitle }
  OPTIONAL { ?enw schema:about ?item; schema:isPartOf <https://en.wikipedia.org/>; schema:name ?enTitle }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
}"""


def _q(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def _sparql(http: Http, query: str) -> list[dict]:
    d = http.get_json(WDQS, {"query": query, "format": "json"}, method="POST",
                      headers={"Accept": "application/sparql-results+json"})
    return [{k: v["value"] for k, v in b.items()} for b in d["results"]["bindings"]]


def _by_title(http, titles):
    values = "VALUES ?key { " + " ".join(_q(t) + "@en" for t in titles) + " }"
    match = "?art schema:about ?item; schema:isPartOf <https://en.wikipedia.org/>; schema:name ?key ."
    return _sparql(http, _SELECT % {"values": values, "match": match})


def _by_name(http, names):
    values = "VALUES ?key { " + " ".join(_q(n) for n in names) + " }"
    match = "?item wdt:P225 ?key ; wdt:P105 wd:Q7432 ."  # rank = species
    return _sparql(http, _SELECT % {"values": values, "match": match})


def _aggregate(rows):
    out = defaultdict(lambda: {"images": [], "audio": [], "items": set()})
    for r in rows:
        a = out[r["key"]]
        a["items"].add(r["item"].rsplit("/", 1)[-1])
        for k_src, k_dst in (("name", "wd_name"), ("bn", "name_bn"), ("iucnLabel", "iucn_label"),
                             ("ebird", "ebird_code"), ("bnTitle", "wiki_bn"), ("enTitle", "wiki_en")):
            if r.get(k_src) and not a.get(k_dst):
                a[k_dst] = r[k_src]
        for k_src, k_dst in (("img", "images"), ("audio", "audio")):
            if r.get(k_src):
                f = commons_file_from_url(r[k_src])
                if f not in a[k_dst]:
                    a[k_dst].append(f)
    return out


def wikidata(http: Http, species: list[dict], report: list) -> None:
    titled = [s for s in species if s.get("wiki_en")]
    found = {}
    for batch in chunks(sorted({s["wiki_en"] for s in titled}), 60):
        found.update(_aggregate(_by_title(http, batch)))

    def apply(s, a, how):
        items = sorted(a["items"])
        s["wikidata_id"] = items[0]
        s["wd_match"] = how
        if len(items) > 1:
            report.append(f"several Wikidata items for {s['name_sci']}: {items} (took {items[0]})")
        for k in ("name_bn", "ebird_code", "wiki_bn"):
            if a.get(k):
                s[k] = a[k]
        if a.get("wiki_en") and not s.get("wiki_en"):
            s["wiki_en"] = a["wiki_en"]
        if a.get("iucn_label"):
            s["iucn"] = IUCN.get(a["iucn_label"].lower(), a["iucn_label"])
        s["wd_images"] = a["images"]
        s["wd_audio"] = a["audio"]
        if a.get("wd_name") and a["wd_name"] != s["name_sci"]:
            s["wd_name"] = a["wd_name"]

    for s in species:
        a = found.get(s.get("wiki_en"))
        if a:
            apply(s, a, "enwiki")

    # fallback: match the scientific name directly
    rest = [s for s in species if not s.get("wikidata_id")]
    by_name = {}
    for batch in chunks(sorted({s["name_sci"] for s in rest}), 60):
        by_name.update(_aggregate(_by_name(http, batch)))
    for s in rest:
        a = by_name.get(s["name_sci"])
        if a:
            apply(s, a, "taxon-name")
        else:
            report.append(f"NO Wikidata match: {s['name_en']} ({s['name_sci']})")

    for s in species:
        if s.get("wd_name"):
            report.append(f"taxonomy differs: list '{s['name_sci']}' vs Wikidata '{s['wd_name']}' ({s['name_en']})")


# ---------------------------------------------------------------- extracts
def extracts(http: Http, species: list[dict], lang: str) -> None:
    key = "wiki_en" if lang == "en" else "wiki_bn"
    titles = sorted({s[key] for s in species if s.get(key)})
    text, desc = {}, {}
    for batch in chunks(titles, 20):  # exlimit max is 20 for intro extracts
        d = http.get_json(WP.format(lang=lang), {
            "action": "query", "prop": "extracts|description", "exintro": "1",
            "explaintext": "1", "exlimit": "20", "titles": "|".join(batch),
            "redirects": "1", "format": "json", "formatversion": "2",
        })["query"]
        back = {}
        for n in d.get("normalized", []) + d.get("redirects", []):
            back[n["to"]] = back.get(n["from"], n["from"])
        for p in d.get("pages", []):
            orig = back.get(p["title"], p["title"])
            if p.get("extract"):
                text[orig] = "\n\n".join(clean_text(x) for x in p["extract"].split("\n") if x.strip())
            if p.get("description"):
                desc[orig] = p["description"]
    for s in species:
        t = s.get(key)
        if t in text:
            s[f"summary_{lang}"] = text[t]
        if lang == "en" and t in desc:
            s["description_en"] = desc[t]
