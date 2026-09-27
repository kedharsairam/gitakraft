# GitaKraft

The Bhagavad Gita for a ten-year-old's reading level. Every verse: original Sanskrit + transliteration + one plain-English meaning + one one-line life takeaway. No commentary wars, no login, fully offline — no INTERNET permission in the manifest.

## Project layout

- `app/` — Android app (Kotlin + Compose, Room, DataStore, WorkManager, Glance)
- `tools/content/` — content pipeline CLI (Python, stdlib only): fetch → normalize → transliterate → align → draft → validate → export
- `data/content/` — staged records: `raw/` → `work/` → `reviewed/` → `frozen/` (every stage diffable, checksummed)

## Content law

- Public-domain sources are quotable (Arnold 1885, Telang 1882, +1). Copyrighted translations are reference-only — their wording can never enter shipped fields (enforced by construction + validator).
- The engine publishes only multi-source consensus. Real disagreement is quarantined with an honest note, never averaged.
- Kedhar writes nothing; status moves on engine gates. His review is app-POV.

## Status

v0.1 scaffold — pipeline deterministic half (import + transliteration + validation), app placeholder. Chapter 1 first.
