"""Polite, disk-cached HTTP client.

Every JSON response is cached under data/cache/, so re-running the pipeline
costs nothing and doesn't hammer Wikimedia / xeno-canto. Delete the cache
(or pass --refresh) to pull fresh data.
"""
from __future__ import annotations

import hashlib
import json
import os
import time
from pathlib import Path

import requests

DEFAULT_UA = "AviaryDataBuilder/0.1 (offline bird field guide for Bangladesh; python-requests)"


class Http:
    def __init__(self, cache_dir: Path, delay: float = 0.3, refresh: bool = False):
        self.cache_dir = Path(cache_dir)
        self.delay = delay
        self.refresh = refresh
        self._last = 0.0
        self.s = requests.Session()
        # Wikimedia asks bots to identify themselves; set AVIARY_UA to add contact info.
        self.s.headers["User-Agent"] = os.environ.get("AVIARY_UA", DEFAULT_UA)
        self.hits = 0
        self.misses = 0

    def _key(self, url: str, params: dict | None) -> Path:
        raw = url + "?" + json.dumps(params or {}, sort_keys=True, ensure_ascii=False)
        h = hashlib.sha256(raw.encode()).hexdigest()
        return self.cache_dir / h[:2] / f"{h}.json"

    def _throttle(self):
        wait = self.delay - (time.monotonic() - self._last)
        if wait > 0:
            time.sleep(wait)
        self._last = time.monotonic()

    def get_json(self, url: str, params: dict | None = None, *, secret: dict | None = None,
                 headers: dict | None = None, method: str = "GET") -> dict:
        """GET (or POST) and return JSON. `secret` params (API keys) are sent but
        left out of the cache key, so the cache survives a key change."""
        path = self._key(url, params)
        if not self.refresh and path.exists():
            self.hits += 1
            return json.loads(path.read_text())

        send = dict(params or {})
        send.update(secret or {})
        last_err = None
        for attempt in range(6):
            self._throttle()
            try:
                if method == "POST":
                    r = self.s.post(url, data=send, headers=headers, timeout=90)
                else:
                    r = self.s.get(url, params=send, headers=headers, timeout=90)
            except requests.RequestException as e:
                last_err = e
                time.sleep(2 ** attempt)
                continue
            if r.status_code in (429, 500, 502, 503, 504):
                retry = r.headers.get("Retry-After")
                time.sleep(float(retry) if retry and retry.isdigit() else 2 ** attempt + 1)
                last_err = RuntimeError(f"HTTP {r.status_code} for {url}")
                continue
            r.raise_for_status()
            data = r.json()
            break
        else:
            raise RuntimeError(f"giving up on {url}: {last_err}")

        self.misses += 1
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(data, ensure_ascii=False))
        tmp.replace(path)
        return data

    def download(self, url: str, dest: Path) -> bool:
        """Download a binary file once. Returns True if the file exists afterwards."""
        dest = Path(dest)
        if dest.exists() and dest.stat().st_size > 0 and not self.refresh:
            return True
        dest.parent.mkdir(parents=True, exist_ok=True)
        for attempt in range(5):
            self._throttle()
            try:
                r = self.s.get(url, timeout=120)
            except requests.RequestException:
                time.sleep(2 ** attempt)
                continue
            if r.status_code in (429, 500, 502, 503, 504):
                time.sleep(2 ** attempt + 1)
                continue
            if r.status_code != 200:
                return False
            tmp = dest.with_suffix(dest.suffix + ".part")
            tmp.write_bytes(r.content)
            tmp.replace(dest)
            return True
        return False
