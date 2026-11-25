# Aviary dataset

One pipeline (`build.py`) produces **one dataset in two shapes**:

| Output | Used by | What |
|---|---|---|
| `out/aviary.db` | Android app (bundled asset) | SQLite: every species, its text, and media *records* (URLs, author, licence) |
| `out/media/thumbs/` | Android app (optional offline pack) | one small photo per species |
| `out/web/` | online mode + future website | static JSON "API", no server needed |

## Offline / online hybrid

- **Offline core (ships in the APK):** `aviary.db` + thumbnails. That covers browsing, search, names in three languages, summaries, status and the birdwatch log with no signal, e.g. out in a haor.
- **Online extras (streamed, then cached):** full-size photos and sound files come from `media.url`, either Commons or xeno-canto directly. The app caches whatever you open.
- **Download packs (later):** the same media can be pre-fetched per region or family, and `media.local_path` marks what's on the device.
- **Data updates:** host `out/web/` on GitHub Pages or anywhere similar. The app reads `manifest.json`, and when `data_version` is newer than its bundled copy it can download the new `aviary.db` (check `db_sha256`) or patch from `species/<id>.json`.
- **Website:** a static site renders `index.json`, `families.json` and `species/<id>.json`. It's the same data, so it needs no backend.

## Tables (`aviary.db`, `PRAGMA user_version` = schema version)

- `meta(key, value)`: schema_version, data_version, built_at, counts, sources.
- `family(id, name, common_name, order_name, blurb, sort)`: `id` is the lowercase family name, e.g. `anatidae`.
- `species(...)`:
  - `id` is the slug of the scientific name (`copsychus-saularis`). It's stable and also used in URLs.
  - `bd_status` is one of `regular`, `accidental`, `introduced` or `extirpated`, taken from the checklist.
  - `season` is one of `resident`, `winter`, `summer`, `passage` or `vagrant`. It's **curated** and still mostly empty.
  - `iucn` is the global Red List status from Wikidata.
  - `summary_en` / `summary_bn` are the Wikipedia intro paragraphs.
  - `facts` is a JSON array of curated facts.
- `media(...)`:
  - `kind` is `photo` or `audio`; `rank` gives the order within a species.
  - `source` is `commons` or `xeno-canto`.
  - URLs: `url` (original), `thumb_url` (960 px), `small_url` (330 px).
  - Credit fields: `page_url`, `author`, `license`, `license_url`.
  - `commercial_ok` is false for NC licences.
  - `extra` is JSON holding size, call type, country and similar details.

## Licences: the app *must* show credits

- Text from Wikipedia is **CC BY-SA**: credit "Wikipedia" and link the article (`wiki_en` / `wiki_bn`).
- Every photo and sound shows `author` and `license`, linking to `page_url`.
- `commercial_ok = 0` media (common on xeno-canto) is fine for a free app, but has to be filtered out if Aviary is ever sold or carries ads.

## Curating

`curated/overrides.json` layers hand edits on top of the scraped data:

- `checklist_fixes`: typo fixes for the Wikipedia list, keyed by English name.
- `species.<id>`: any field, usually `season`, `facts` or a better `name_bn`.

After every run, `work/report.txt` lists everything that needs a human: missing photos, taxonomy mismatches, unmatched species.
