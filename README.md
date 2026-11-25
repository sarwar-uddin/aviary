# Aviary

A field guide to the birds of Bangladesh for Android. It covers every species recorded in the country, with photos, calls, Bangla names and a birdwatch log. The core works offline and the extras stream when you're online. The same dataset can power a website later.

## Layout

- `data/`: Python pipeline that builds the dataset. See [data/SCHEMA.md](data/SCHEMA.md).
- `app/`: Android app (Kotlin + Compose). Coming next.

## Build the data

```sh
cd data
python build.py --pack           # full build with offline thumbnails (cached after the first run)
XC_API_KEY=... python build.py   # also pull bird sounds from xeno-canto
```

Credits: Wikipedia and Wikimedia Commons contributors, Wikidata, and xeno-canto recordists, licensed per item as listed in the dataset.
