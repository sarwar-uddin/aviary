"""Stage 1: the Bangladesh checklist.

Source: Wikipedia "List of birds of Bangladesh" (Clements taxonomy, CC BY-SA).
Gives us order -> family -> species, English + scientific names, the BD status
tags (accidental / introduced / extirpated) and a short blurb for each family.
"""
from __future__ import annotations

import re
from urllib.parse import unquote

from bs4 import BeautifulSoup, Tag

from .http import Http
from .util import slugify, clean_text

WP_API = "https://en.wikipedia.org/w/api.php"
LIST_PAGE = "List_of_birds_of_Bangladesh"
TAGS = {"A": "accidental", "I": "introduced", "Ex": "extirpated"}

ORDER_RE = re.compile(r"Order:\s*([A-Za-z]+)\s*Family:\s*([A-Za-z]+)")
LI_RE = re.compile(r"^(?P<en>[^,]+?)\s*,\s*(?P<sci>[A-Z][a-z]+ [a-z][a-z-]+)\s*(?P<rest>.*)$")
TAG_RE = re.compile(r"\((A|I|Ex)\)")


def fetch(http: Http) -> dict:
    data = http.get_json(WP_API, {
        "action": "parse", "page": LIST_PAGE, "prop": "text|revid",
        "format": "json", "formatversion": "2", "redirects": "1",
    })
    return data["parse"]


def parse(html: str) -> tuple[list[dict], list[dict]]:
    root = BeautifulSoup(html, "html.parser").select_one(".mw-parser-output")
    families: list[dict] = []
    species: list[dict] = []
    heading = None
    fam = None

    for el in root.children:
        if not isinstance(el, Tag):
            continue
        cls = el.get("class") or []
        if "mw-heading2" in cls or el.name == "h2":
            h = el.find("h2") or el
            heading = h.get_text(strip=True)
            fam = None
            continue
        if el.name == "p":
            text = clean_text(el.get_text())
            m = ORDER_RE.search(text)
            if m and heading:
                order, family = m.groups()
                fam = {
                    "id": family.lower(),
                    "name": family,
                    "common_name": heading,
                    "order": order,
                    "blurb": "",
                    "sort": len(families),
                }
                families.append(fam)
            elif fam and not fam["blurb"] and text:
                fam["blurb"] = text
            continue
        if fam and (el.name == "ul" or "div-col" in cls):
            for li in el.find_all("li"):
                sp = _parse_li(li, fam["id"], len(species))
                if sp:
                    species.append(sp)
    return families, species


def _parse_li(li: Tag, family_id: str, sort: int) -> dict | None:
    text = clean_text(li.get_text())
    m = LI_RE.match(text)
    if not m:
        return None
    en, sci, rest = m.group("en").strip(), m.group("sci").strip(), m.group("rest")
    tags = [TAGS[t] for t in TAG_RE.findall(rest)]
    a = li.find("a", href=True)
    title = None
    if a and a["href"].startswith("/wiki/"):
        title = unquote(a["href"][len("/wiki/"):].split("#")[0]).replace("_", " ")
    return {
        "id": slugify(sci),
        "sort": sort,
        "family_id": family_id,
        "name_en": en,
        "name_sci": sci,
        "bd_tags": tags,
        "wiki_en_link": title,  # as linked from the list (may be a redirect)
    }


def run(http: Http, fixes: dict | None = None, report: list | None = None) -> dict:
    """fixes: {"English name": {"name_sci": "..."}} for typos on the Wikipedia list."""
    fixes = fixes or {}
    report = report if report is not None else []
    page = fetch(http)
    families, species = parse(page["text"])

    for sp in species:
        fx = fixes.get(sp["name_en"])
        if fx:
            for k, v in fx.items():
                if not k.startswith("_"):
                    sp[k] = v
            sp["id"] = slugify(sp["name_sci"])
            report.append(f"checklist fix: {sp['name_en']} -> {sp['name_sci']}")

    # de-dupe; if the list repeats a species, keep the first and only keep tags both copies agree on
    by_id, uniq = {}, []
    for sp in species:
        prev = by_id.get(sp["id"])
        if prev:
            if prev["name_en"] != sp["name_en"]:
                report.append(f"DUPLICATE sci name {sp['name_sci']}: '{prev['name_en']}' vs '{sp['name_en']}' (needs a checklist fix)")
            else:
                report.append(f"duplicate entry merged: {sp['name_en']}")
            prev["bd_tags"] = [t for t in prev["bd_tags"] if t in sp["bd_tags"]]
            continue
        by_id[sp["id"]] = sp
        sp["sort"] = len(uniq)
        uniq.append(sp)
    return {
        "source": {"page": LIST_PAGE, "revid": page.get("revid")},
        "families": families,
        "species": uniq,
    }
