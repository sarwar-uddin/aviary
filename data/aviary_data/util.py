from __future__ import annotations

import re
import unicodedata
from urllib.parse import unquote

from bs4 import BeautifulSoup

_REF = re.compile(r"\[\d+\]|\[citation needed\]|\[note \d+\]")
_WS = re.compile(r"\s+")


def slugify(s: str) -> str:
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")


def clean_text(s: str) -> str:
    return _WS.sub(" ", _REF.sub("", s or "")).strip()


def strip_html(s: str | None) -> str:
    if not s:
        return ""
    return clean_text(BeautifulSoup(s, "html.parser").get_text(" "))


def chunks(seq, n):
    seq = list(seq)
    for i in range(0, len(seq), n):
        yield seq[i:i + n]


def commons_file_from_url(url: str) -> str:
    """http://commons.wikimedia.org/wiki/Special:FilePath/Foo%20bar.jpg -> 'Foo bar.jpg'"""
    return unquote(url.rsplit("/", 1)[-1]).replace("_", " ")


def strip_tracking(url: str | None) -> str | None:
    if not url:
        return url
    return url.split("?utm_", 1)[0]


IUCN = {
    "least concern": "LC",
    "near threatened": "NT",
    "vulnerable": "VU",
    "endangered": "EN",
    "critically endangered": "CR",
    "extinct in the wild": "EW",
    "extinct": "EX",
    "data deficient": "DD",
    "not evaluated": "NE",
}
