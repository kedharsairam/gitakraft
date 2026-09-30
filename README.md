# GitaKraft

The Bhagavad Gita in plain English. All 700 verses — original Sanskrit, transliteration, one simple meaning, and one line to carry with you.

No accounts. No ads. No tracking. Fully offline; the internet permission exists for one thing only: a manual update check you trigger yourself.

## Get it

Latest release (APK): [Releases](https://github.com/kedharsairam/gitakraft/releases)

## How the text is verified

Every verse passed through a five-stage verification engine before shipping:

1. **Mechanical** — structure, grading (grade ≤ 8 reading level), provenance, glossary
2. **Sanskrit witnesses** — each verse diffed against the GRETIL critical edition, seconded by Śaṅkara's commentary; disagreements quarantined, never averaged
3. **Second transliteration engine** — an independent Deva↔IAST implementation diffed over every string; zero mismatches
4. **Review packs** — 77 read-through packs with sign-off tracking
5. **English consensus** — each meaning scored independently against two public-domain translations (Telang 1882, Arnold 1885)

Sources are public domain. Where editions disagree, the app follows the vulgate text and says so.

## Project layout

- `app/` — Android app (Kotlin + Compose, Room, DataStore)
- `tools/content/` — content pipeline CLI (Python, standard library only)
- `data/content/` — staged records: `raw/` → `work/` → `review/` (every stage diffable)

## Status

v1.2.0 — verified content, first-run welcome, redesigned verse rows and notices.

## Support

If you enjoy GitaKraft, buy me a coffee:

<p align="center">
  <a href="https://buymeacoffee.com/kedhartech"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="182"></a>
</p>

## License

MIT — see [LICENSE](LICENSE) for details.
