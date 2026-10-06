# GitaKraft

The Bhagavad Gita in plain English. All 700 verses across 18 chapters — original Sanskrit, transliteration, one simple meaning, and one line to carry with you.

You can also start from how you feel. Twelve states — grieving, afraid, angry, confused, doubtful, restless, failing, guilty, envious, indecisive, weary, seeking purpose — each mapped to the verses that actually speak to it, because "what does the Gita say about feeling like this" is a more honest way in than the table of contents.

No accounts. No ads. No tracking. Fully offline. The internet permission exists for one thing only: an update check **you** trigger.

<p align="center">
  <a href="https://github.com/kedharsairam/gitakraft/releases/latest"><img src="https://img.shields.io/github/v/release/kedharsairam/gitakraft?style=for-the-badge&label=Download" alt="Download APK"></a>
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="MIT License">
  <img src="https://img.shields.io/badge/Android-8.0%2B-blue?style=for-the-badge" alt="Android 8.0 and newer">
</p>

---

## What it does

**Reads** — Chapter list, verse rows, and a reader tuned for long-form text: adjustable type, and a takeaway line on every verse so you can leave with one sentence instead of a page.

**Searches** — Across the whole text, in the Sanskrit, the transliteration and the English.

**Starts from a feeling** — Pick a state, get the verses for it. Twelve mappings, written rather than keyword-matched.

**Bookmarks** — Keep the verses you want to come back to.

**Works on a plane** — The entire text is in the bundle. Nothing is fetched to read a verse.

## Permissions

One, and it is not used unless you ask for it:

| Permission | Used by | Why |
| --- | --- | --- |
| `INTERNET` | the update check, which you trigger | To tell you a newer version exists. No verse is ever fetched. |

---

## How the text is verified

Every verse passed through a five-stage engine before shipping. This is the part of the project that took the longest and the part that matters most, because a plain-English rendering of a sacred text is only worth reading if somebody checked it.

1. **Mechanical** — structure, grading (grade ≤ 8 reading level), provenance, glossary
2. **Sanskrit witnesses** — each verse diffed against the GRETIL critical edition, seconded by Śaṅkara's commentary; disagreements quarantined, never averaged
3. **Second transliteration engine** — an independent Deva↔IAST implementation diffed over every string; zero mismatches
4. **Review packs** — 77 read-through packs with sign-off tracking
5. **English consensus** — each meaning scored independently against two public-domain translations (Telang 1882, Arnold 1885)

Sources are public domain. Where editions disagree, the app follows the vulgate text and says so.

---

<details>
<summary><strong>Build from source</strong></summary>

```bash
git clone https://github.com/kedharsairam/gitakraft.git
cd gitakraft
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest        # 13 unit tests
./gradlew :app:connectedDebugAndroidTest  # 7 instrumented, needs a device
```

Kotlin and Compose, Room, DataStore. JDK 17, `minSdk 26`.

The content pipeline is separate and deliberately dull — Python, standard library only — so the text can be rebuilt and diffed without a toolchain:

```
tools/content/   the pipeline CLI
data/content/    staged records: raw/ → work/ → review/ → frozen
app/src/main/assets/gita-bundle.json   the 700 verses that ship
```

</details>

## Design

Spacing, type, radius, motion and touch targets come from
[kraft-foundation](https://github.com/kedharsairam/kraft-foundation), which is also where the
standard this app is built to is written down. It targets **standard 1.0.0**, and
`kraft-lint` in that repository is what checks it — 22 of the standard's 29 rules are decided
by reading source, and this app passes all of them.

The accent and the app's own dimensions stay local. `GitaTints` and `GitaMetrics` in
`ui/theme/` hold the handful of values that would be wrong in any other app, each with the
reason it has the value it has. That is the same test the standard applies to colour: a
speed test and a barometer should not look like the same product.

GitaKraft is the first of nine apps to move. The others still carry their own spacing.

## Support

If you enjoy GitaKraft, buy me a coffee:

<p align="center">
  <a href="https://buymeacoffee.com/kedhartech"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="182"></a>
</p>

## License

MIT — see [LICENSE](LICENSE) for details.
